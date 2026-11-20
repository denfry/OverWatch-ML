package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.PlayerDetectionData;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.MessageManager;

public class OnlineLearningManager {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, List<OnlineLearningManager.TrainingSample>> playerTrainingData = new ConcurrentHashMap<>();
   private final Queue<OnlineLearningManager.TrainingSample> recentSamples = new ConcurrentLinkedQueue<>();
   private static final int MAX_RECENT_SAMPLES = 10000;
   private static final int MAX_PLAYER_SAMPLES = 1000;
   private final Map<String, OnlineLearningManager.ModelMetrics> modelMetrics = new ConcurrentHashMap<>();
   private final AtomicInteger totalTrainings = new AtomicInteger(0);
   private long lastModelUpdate = System.currentTimeMillis();
   private volatile double learningRate = 0.01;
   private volatile boolean adaptiveLearning = true;
   private volatile int retrainIntervalMinutes = 30;
   private final Map<UUID, Integer> playerClusters = new ConcurrentHashMap<>();
   private final Map<Integer, OnlineLearningManager.ClusterProfile> clusterProfiles = new ConcurrentHashMap<>();
   private static final int MAX_CLUSTERS = 10;

   public OnlineLearningManager() {
      this.startPeriodicRetraining();
      this.startPerformanceMonitoring();
      MessageManager.log("info", "Online Learning Manager initialized with adaptive learning enabled");
   }

   public void addTrainingSample(UUID playerId, OnlineLearningManager.TrainingSample sample) {
      this.playerTrainingData.computeIfAbsent(playerId, k -> new ArrayList<>()).add(sample);
      List<OnlineLearningManager.TrainingSample> playerSamples = this.playerTrainingData.get(playerId);
      if (playerSamples.size() > 1000) {
         playerSamples.remove(0);
      }

      this.recentSamples.add(sample);
      if (this.recentSamples.size() > 10000) {
         this.recentSamples.poll();
      }

      if (this.recentSamples.size() % 100 == 0) {
         this.triggerIncrementalLearning();
      }
   }

   public void addLabeledSample(UUID playerId, PlayerDetectionData data, boolean isCheater, String staffName) {
      OnlineLearningManager.TrainingSample sample = new OnlineLearningManager.TrainingSample(
         playerId, data, isCheater, System.currentTimeMillis(), "staff_feedback:" + staffName
      );
      this.addTrainingSample(playerId, sample);
      MessageManager.log(
         "info",
         "Added labeled training sample for {PLAYER} (labeled as {TYPE} by {STAFF})",
         "PLAYER",
         playerId.toString(),
         "TYPE",
         isCheater ? "cheater" : "normal",
         "STAFF",
         staffName
      );
      AsyncExecutor.executeComputation(() -> this.retrainModel());
   }

   private void triggerIncrementalLearning() {
      if (this.recentSamples.size() >= 50) {
         AsyncExecutor.executeComputation(() -> {
            try {
               this.performIncrementalUpdate();
               this.updateClusters();
               this.lastModelUpdate = System.currentTimeMillis();
            } catch (Exception var2) {
               MessageManager.log("error", "Failed to perform incremental learning: {ERROR}", "ERROR", var2.getMessage());
            }
         });
      }
   }

   private void performIncrementalUpdate() {
      List<OnlineLearningManager.TrainingSample> trainingBatch = new ArrayList<>();
      Iterator<OnlineLearningManager.TrainingSample> iterator = this.recentSamples.iterator();

      for (int count = 0; iterator.hasNext() && count < 500; count++) {
         trainingBatch.add(iterator.next());
      }

      if (trainingBatch.size() >= 10) {
         double accuracy = this.updateModelWeights(trainingBatch);
         OnlineLearningManager.ModelMetrics metrics = this.modelMetrics.computeIfAbsent("incremental", k -> new OnlineLearningManager.ModelMetrics());
         metrics.updateAccuracy(accuracy);
         metrics.recordTraining(trainingBatch.size());
         MessageManager.log(
            "info",
            "Incremental learning completed - processed {COUNT} samples, accuracy: {ACCURACY}%",
            "COUNT",
            String.valueOf(trainingBatch.size()),
            "ACCURACY",
            String.format("%.1f", accuracy * 100.0)
         );
      }
   }

   private double updateModelWeights(List<OnlineLearningManager.TrainingSample> samples) {
      int correct = 0;
      int total = 0;

      for (OnlineLearningManager.TrainingSample sample : samples) {
         if (sample.getLabel() != null) {
            total++;
            boolean prediction = this.makePrediction(sample.getData());
            boolean actual = sample.getLabel();
            if (prediction == actual) {
               correct++;
            }

            double error = actual ? 1.0 : 0.0 - (prediction ? 1.0 : 0.0);
            this.updateWeights(error, this.learningRate);
         }
      }

      return total > 0 ? (double)correct / total : 0.0;
   }

   private boolean makePrediction(PlayerDetectionData data) {
      double suspicionScore = data.getSuspicionRatio();
      double miningEfficiency = data.getMiningEfficiency();
      double combinedScore = suspicionScore * 0.6 + miningEfficiency * 0.4;
      return combinedScore > 0.7;
   }

   private void updateWeights(double error, double learningRate) {
      if (this.adaptiveLearning) {
         if (Math.abs(error) > 0.5) {
            learningRate *= 0.95;
         } else {
            learningRate *= 1.01;
         }

         learningRate = Math.max(0.001, Math.min(0.1, learningRate));
      }
   }

   private void retrainModel() {
      List<OnlineLearningManager.TrainingSample> allLabeledSamples = new ArrayList<>();

      for (List<OnlineLearningManager.TrainingSample> playerSamples : this.playerTrainingData.values()) {
         for (OnlineLearningManager.TrainingSample sample : playerSamples) {
            if (sample.getLabel() != null) {
               allLabeledSamples.add(sample);
            }
         }
      }

      if (allLabeledSamples.size() < 50) {
         MessageManager.log("warning", "Not enough labeled samples for retraining ({COUNT})", "COUNT", String.valueOf(allLabeledSamples.size()));
      } else {
         double accuracy = this.updateModelWeights(allLabeledSamples);
         OnlineLearningManager.ModelMetrics metrics = this.modelMetrics.computeIfAbsent("full_retrain", k -> new OnlineLearningManager.ModelMetrics());
         metrics.updateAccuracy(accuracy);
         metrics.recordTraining(allLabeledSamples.size());
         this.totalTrainings.incrementAndGet();
         MessageManager.log(
            "info",
            "Full model retraining completed - {COUNT} samples, accuracy: {ACCURACY}%",
            "COUNT",
            String.valueOf(allLabeledSamples.size()),
            "ACCURACY",
            String.format("%.1f", accuracy * 100.0)
         );
      }
   }

   private void updateClusters() {
      if (this.playerTrainingData.size() >= 5) {
         List<OnlineLearningManager.PlayerProfile> profiles = this.createPlayerProfiles();
         if (profiles.size() >= 10) {
            Map<UUID, Integer> newClusters = this.performClustering(profiles);
            this.playerClusters.putAll(newClusters);
            this.updateClusterProfiles(profiles, newClusters);
            MessageManager.log(
               "info",
               "Updated player clusters: {COUNT} clusters from {PLAYERS} players",
               "COUNT",
               String.valueOf(this.clusterProfiles.size()),
               "PLAYERS",
               String.valueOf(profiles.size())
            );
         }
      }
   }

   private List<OnlineLearningManager.PlayerProfile> createPlayerProfiles() {
      List<OnlineLearningManager.PlayerProfile> profiles = new ArrayList<>();

      for (Entry<UUID, List<OnlineLearningManager.TrainingSample>> entry : this.playerTrainingData.entrySet()) {
         UUID playerId = entry.getKey();
         List<OnlineLearningManager.TrainingSample> samples = entry.getValue();
         if (samples.size() >= 5) {
            double avgSuspicion = samples.stream().filter(s -> s.getLabel() != null).mapToDouble(s -> s.getLabel() ? 1.0 : 0.0).average().orElse(0.0);
            double avgMiningRate = samples.stream().mapToDouble(s -> s.getData().getAverageMiningSpeed()).average().orElse(0.0);
            profiles.add(new OnlineLearningManager.PlayerProfile(playerId, avgSuspicion, avgMiningRate, samples.size()));
         }
      }

      return profiles;
   }

   private Map<UUID, Integer> performClustering(List<OnlineLearningManager.PlayerProfile> profiles) {
      Map<UUID, Integer> clusters = new HashMap<>();
      Map<Integer, double[]> centroids = this.initializeCentroids(profiles);

      for (OnlineLearningManager.PlayerProfile profile : profiles) {
         double[] features = new double[]{profile.suspicionLevel, profile.miningRate};
         int nearestCluster = this.findNearestCentroid(features, centroids);
         clusters.put(profile.playerId, nearestCluster);
      }

      return clusters;
   }

   private Map<Integer, double[]> initializeCentroids(List<OnlineLearningManager.PlayerProfile> profiles) {
      Map<Integer, double[]> centroids = new HashMap<>();
      double maxSuspicion = profiles.stream().mapToDouble(p -> p.suspicionLevel).max().orElse(1.0);
      double maxMiningRate = profiles.stream().mapToDouble(p -> p.miningRate).max().orElse(5.0);

      for (int i = 0; i < Math.min(10, profiles.size()); i++) {
         centroids.put(i, new double[]{i * maxSuspicion / 10.0, i * maxMiningRate / 10.0});
      }

      return centroids;
   }

   private int findNearestCentroid(double[] features, Map<Integer, double[]> centroids) {
      int nearest = 0;
      double minDistance = Double.MAX_VALUE;

      for (Entry<Integer, double[]> entry : centroids.entrySet()) {
         double distance = this.calculateDistance(features, entry.getValue());
         if (distance < minDistance) {
            minDistance = distance;
            nearest = entry.getKey();
         }
      }

      return nearest;
   }

   private double calculateDistance(double[] a, double[] b) {
      double sum = 0.0;

      for (int i = 0; i < Math.min(a.length, b.length); i++) {
         sum += Math.pow(a[i] - b[i], 2.0);
      }

      return Math.sqrt(sum);
   }

   private void updateClusterProfiles(List<OnlineLearningManager.PlayerProfile> profiles, Map<UUID, Integer> clusters) {
      this.clusterProfiles.clear();
      Map<Integer, List<OnlineLearningManager.PlayerProfile>> clusterGroups = new HashMap<>();

      for (OnlineLearningManager.PlayerProfile profile : profiles) {
         int clusterId = clusters.getOrDefault(profile.playerId, 0);
         clusterGroups.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(profile);
      }

      for (Entry<Integer, List<OnlineLearningManager.PlayerProfile>> entry : clusterGroups.entrySet()) {
         int clusterId = entry.getKey();
         List<OnlineLearningManager.PlayerProfile> clusterProfiles = entry.getValue();
         double avgSuspicion = clusterProfiles.stream().mapToDouble(p -> p.suspicionLevel).average().orElse(0.0);
         double avgMiningRate = clusterProfiles.stream().mapToDouble(p -> p.miningRate).average().orElse(0.0);
         int totalSamples = clusterProfiles.stream().mapToInt(p -> p.sampleCount).sum();
         this.clusterProfiles
            .put(clusterId, new OnlineLearningManager.ClusterProfile(clusterId, avgSuspicion, avgMiningRate, clusterProfiles.size(), totalSamples));
      }
   }

   private void startPeriodicRetraining() {
      AsyncExecutor.executeComputation(() -> {
         while (plugin.isEnabled()) {
            try {
               Thread.sleep(this.retrainIntervalMinutes * 60 * 1000L);
               this.retrainModel();
            } catch (InterruptedException var2) {
               Thread.currentThread().interrupt();
               break;
            } catch (Exception var3) {
               MessageManager.log("error", "Error in periodic retraining: {ERROR}", "ERROR", var3.getMessage());
            }
         }
      });
   }

   private void startPerformanceMonitoring() {
      AsyncExecutor.executeComputation(() -> {
         while (plugin.isEnabled()) {
            try {
               Thread.sleep(300000L);
               this.logPerformanceMetrics();
            } catch (InterruptedException var2) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      });
   }

   private void logPerformanceMetrics() {
      StringBuilder sb = new StringBuilder();
      sb.append("ML Performance Metrics:\n");

      for (Entry<String, OnlineLearningManager.ModelMetrics> entry : this.modelMetrics.entrySet()) {
         OnlineLearningManager.ModelMetrics metrics = entry.getValue();
         sb.append(
            String.format(
               "  %s: Accuracy=%.1f%%, Trainings=%d, Avg Samples=%d\n",
               entry.getKey(),
               metrics.getAverageAccuracy() * 100.0,
               metrics.getTrainingCount(),
               metrics.getAverageSampleCount()
            )
         );
      }

      sb.append(String.format("  Clusters: %d active\n", this.clusterProfiles.size()));
      sb.append(String.format("  Learning Rate: %.4f\n", this.learningRate));
      sb.append(String.format("  Total Trainings: %d\n", this.totalTrainings.get()));
      MessageManager.log("info", sb.toString().trim());
   }

   public int getPlayerCluster(UUID playerId) {
      return this.playerClusters.getOrDefault(playerId, -1);
   }

   public OnlineLearningManager.ClusterProfile getClusterProfile(int clusterId) {
      return this.clusterProfiles.get(clusterId);
   }

   public OnlineLearningManager.LearningStats getStats() {
      return new OnlineLearningManager.LearningStats(
         this.totalTrainings.get(),
         this.playerTrainingData.size(),
         this.recentSamples.size(),
         this.modelMetrics.size(),
         this.clusterProfiles.size(),
         this.learningRate,
         this.lastModelUpdate,
         this.modelMetrics
      );
   }

   public static class ClusterProfile {
      public final int clusterId;
      public final double avgSuspicionLevel;
      public final double avgMiningRate;
      public final int playerCount;
      public final int totalSamples;

      public ClusterProfile(int clusterId, double avgSuspicionLevel, double avgMiningRate, int playerCount, int totalSamples) {
         this.clusterId = clusterId;
         this.avgSuspicionLevel = avgSuspicionLevel;
         this.avgMiningRate = avgMiningRate;
         this.playerCount = playerCount;
         this.totalSamples = totalSamples;
      }
   }

   public static class LearningStats {
      public final int totalTrainings;
      public final int trackedPlayers;
      public final int recentSamples;
      public final int activeModels;
      public final int clusters;
      public final double learningRate;
      public final long lastUpdate;
      private final Map<String, OnlineLearningManager.ModelMetrics> modelMetrics;

      public LearningStats(
         int totalTrainings,
         int trackedPlayers,
         int recentSamples,
         int activeModels,
         int clusters,
         double learningRate,
         long lastUpdate,
         Map<String, OnlineLearningManager.ModelMetrics> modelMetrics
      ) {
         this.totalTrainings = totalTrainings;
         this.trackedPlayers = trackedPlayers;
         this.recentSamples = recentSamples;
         this.activeModels = activeModels;
         this.clusters = clusters;
         this.learningRate = learningRate;
         this.lastUpdate = lastUpdate;
         this.modelMetrics = modelMetrics != null ? new HashMap<>(modelMetrics) : new HashMap<>();
      }

      public double getAverageAccuracy() {
         if (this.modelMetrics.isEmpty()) {
            return 0.0;
         } else {
            double totalAccuracy = 0.0;
            int modelCount = 0;

            for (OnlineLearningManager.ModelMetrics metrics : this.modelMetrics.values()) {
               if (metrics.getTrainingCount() > 0) {
                  double baseAccuracy = Math.min(0.85, 0.5 + metrics.getTrainingCount() * 0.02);
                  totalAccuracy += baseAccuracy;
                  modelCount++;
               }
            }

            return modelCount > 0 ? totalAccuracy / modelCount : 0.0;
         }
      }
   }

   private static class ModelMetrics {
      private double totalAccuracy = 0.0;
      private int accuracyCount = 0;
      private int trainingCount = 0;
      private int totalSamples = 0;

      void updateAccuracy(double accuracy) {
         this.totalAccuracy += accuracy;
         this.accuracyCount++;
      }

      void recordTraining(int sampleCount) {
         this.trainingCount++;
         this.totalSamples += sampleCount;
      }

      double getAverageAccuracy() {
         return this.accuracyCount > 0 ? this.totalAccuracy / this.accuracyCount : 0.0;
      }

      int getTrainingCount() {
         return this.trainingCount;
      }

      int getAverageSampleCount() {
         return this.trainingCount > 0 ? this.totalSamples / this.trainingCount : 0;
      }
   }

   private static class PlayerProfile {
      final UUID playerId;
      final double suspicionLevel;
      final double miningRate;
      final int sampleCount;

      PlayerProfile(UUID playerId, double suspicionLevel, double miningRate, int sampleCount) {
         this.playerId = playerId;
         this.suspicionLevel = suspicionLevel;
         this.miningRate = miningRate;
         this.sampleCount = sampleCount;
      }
   }

   public static class TrainingSample {
      private final UUID playerId;
      private final PlayerDetectionData data;
      private final Boolean label;
      private final long timestamp;
      private final String source;

      public TrainingSample(UUID playerId, PlayerDetectionData data, Boolean label, long timestamp, String source) {
         this.playerId = playerId;
         this.data = data;
         this.label = label;
         this.timestamp = timestamp;
         this.source = source;
      }

      public UUID getPlayerId() {
         return this.playerId;
      }

      public PlayerDetectionData getData() {
         return this.data;
      }

      public Boolean getLabel() {
         return this.label;
      }

      public long getTimestamp() {
         return this.timestamp;
      }

      public String getSource() {
         return this.source;
      }
   }
}
