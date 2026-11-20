package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.PlayerDetectionData;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.MessageManager;

public class PlayerClusteringEngine {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, PlayerClusteringEngine.PlayerCluster> playerClusters = new ConcurrentHashMap<>();
   private final Map<Integer, PlayerClusteringEngine.ClusterProfile> clusterProfiles = new ConcurrentHashMap<>();
   private final Map<UUID, PlayerClusteringEngine.PlayerFeatures> playerFeatures = new ConcurrentHashMap<>();
   private static final int MAX_CLUSTERS = 8;
   private static final double CLUSTER_SIMILARITY_THRESHOLD = 0.7;
   private static final int MIN_CLUSTER_SIZE = 3;
   private static final int FEATURE_DIMENSIONS = 6;
   private final Map<UUID, PlayerClusteringEngine.RiskAssessment> playerRisks = new ConcurrentHashMap<>();

   public PlayerClusteringEngine() {
      this.startClusteringTask();
      MessageManager.log("info", "Player Clustering Engine initialized with {MAX} max clusters", "MAX", String.valueOf(8));
   }

   public void updatePlayerFeatures(UUID playerId, PlayerDetectionData data) {
      PlayerClusteringEngine.PlayerFeatures features = new PlayerClusteringEngine.PlayerFeatures(playerId);
      features.addFeature("mining_efficiency", this.normalizeValue(data.getMiningEfficiency(), 0.0, 100.0));
      features.addFeature("suspicion_ratio", data.getSuspicionRatio());
      features.addFeature("mining_speed", this.normalizeValue(data.getAverageMiningSpeed(), 0.0, 10.0));
      features.addFeature("consistency", this.calculateConsistencyScore(data));
      features.addFeature("exploration_ratio", data.getRecentDetectionTrend());
      features.addFeature("session_length", this.normalizeValue((System.currentTimeMillis() - data.getFirstSeen()) / 3600000.0, 0.0, 24.0));
      this.playerFeatures.put(playerId, features);
      if (this.playerFeatures.size() >= 6) {
         AsyncExecutor.executeComputation(this::performIncrementalClustering);
      }
   }

   private void performIncrementalClustering() {
      try {
         List<PlayerClusteringEngine.PlayerFeatures> allFeatures = new ArrayList<>(this.playerFeatures.values());
         if (allFeatures.size() < 3) {
            return;
         }

         Map<UUID, Integer> newClusters = this.performKMeansClustering(allFeatures);

         for (Entry<UUID, Integer> entry : newClusters.entrySet()) {
            UUID playerId = entry.getKey();
            int clusterId = entry.getValue();
            this.playerClusters.computeIfAbsent(playerId, k -> new PlayerClusteringEngine.PlayerCluster(playerId)).setClusterId(clusterId);
         }

         this.updateClusterProfiles(allFeatures, newClusters);
         this.assessClusterRisks();
         MessageManager.log(
            "info",
            "Clustering updated: {PLAYERS} players in {CLUSTERS} clusters",
            "PLAYERS",
            String.valueOf(allFeatures.size()),
            "CLUSTERS",
            String.valueOf(this.clusterProfiles.size())
         );
      } catch (Exception var7) {
         MessageManager.log("error", "Error in incremental clustering: {ERROR}", "ERROR", var7.getMessage());
      }
   }

   private Map<UUID, Integer> performKMeansClustering(List<PlayerClusteringEngine.PlayerFeatures> players) {
      Map<UUID, Integer> clusters = new HashMap<>();
      double[][] centroids = this.initializeCentroids(players, Math.min(8, players.size() / 3));

      for (int iteration = 0; iteration < 10; iteration++) {
         Map<Integer, List<PlayerClusteringEngine.PlayerFeatures>> clusterGroups = new HashMap<>();

         for (PlayerClusteringEngine.PlayerFeatures player : players) {
            int nearestCentroid = this.findNearestCentroid(player.getFeatureVector(), centroids);
            clusterGroups.computeIfAbsent(nearestCentroid, k -> new ArrayList<>()).add(player);
         }

         boolean centroidsChanged = this.updateCentroids(centroids, clusterGroups);
         if (!centroidsChanged && iteration > 2) {
            break;
         }
      }

      for (PlayerClusteringEngine.PlayerFeatures player : players) {
         int clusterId = this.findNearestCentroid(player.getFeatureVector(), centroids);
         clusters.put(player.getPlayerId(), clusterId);
      }

      return clusters;
   }

   private double[][] initializeCentroids(List<PlayerClusteringEngine.PlayerFeatures> players, int k) {
      double[][] centroids = new double[k][6];
      Random random = new Random();
      PlayerClusteringEngine.PlayerFeatures firstPlayer = players.get(random.nextInt(players.size()));
      System.arraycopy(firstPlayer.getFeatureVector(), 0, centroids[0], 0, 6);

      for (int i = 1; i < k; i++) {
         double[] distances = new double[players.size()];

         for (int j = 0; j < players.size(); j++) {
            double minDistance = Double.MAX_VALUE;

            for (int c = 0; c < i; c++) {
               double distance = this.calculateDistance(players.get(j).getFeatureVector(), centroids[c]);
               minDistance = Math.min(minDistance, distance);
            }

            distances[j] = minDistance * minDistance;
         }

         double totalDistance = Arrays.stream(distances).sum();
         double randomValue = random.nextDouble() * totalDistance;
         double cumulative = 0.0;
         int selectedIndex = 0;

         for (int j = 0; j < distances.length; j++) {
            cumulative += distances[j];
            if (cumulative >= randomValue) {
               selectedIndex = j;
               break;
            }
         }

         System.arraycopy(players.get(selectedIndex).getFeatureVector(), 0, centroids[i], 0, 6);
      }

      return centroids;
   }

   private int findNearestCentroid(double[] features, double[][] centroids) {
      int nearest = 0;
      double minDistance = Double.MAX_VALUE;

      for (int i = 0; i < centroids.length; i++) {
         double distance = this.calculateDistance(features, centroids[i]);
         if (distance < minDistance) {
            minDistance = distance;
            nearest = i;
         }
      }

      return nearest;
   }

   private boolean updateCentroids(double[][] centroids, Map<Integer, List<PlayerClusteringEngine.PlayerFeatures>> clusterGroups) {
      boolean changed = false;

      for (int i = 0; i < centroids.length; i++) {
         List<PlayerClusteringEngine.PlayerFeatures> cluster = clusterGroups.get(i);
         if (cluster != null && !cluster.isEmpty()) {
            double[] newCentroid = new double[6];

            for (PlayerClusteringEngine.PlayerFeatures player : cluster) {
               double[] features = player.getFeatureVector();

               for (int j = 0; j < 6; j++) {
                  newCentroid[j] += features[j];
               }
            }

            for (int j = 0; j < 6; j++) {
               newCentroid[j] /= cluster.size();
            }

            double distance = this.calculateDistance(centroids[i], newCentroid);
            if (distance > 0.01) {
               changed = true;
               System.arraycopy(newCentroid, 0, centroids[i], 0, 6);
            }
         }
      }

      return changed;
   }

   private double calculateDistance(double[] a, double[] b) {
      double sum = 0.0;

      for (int i = 0; i < Math.min(a.length, b.length); i++) {
         double diff = a[i] - b[i];
         sum += diff * diff;
      }

      return Math.sqrt(sum);
   }

   private void updateClusterProfiles(List<PlayerClusteringEngine.PlayerFeatures> allPlayers, Map<UUID, Integer> clusters) {
      this.clusterProfiles.clear();
      Map<Integer, List<PlayerClusteringEngine.PlayerFeatures>> clusterGroups = new HashMap<>();

      for (PlayerClusteringEngine.PlayerFeatures player : allPlayers) {
         int clusterId = clusters.get(player.getPlayerId());
         clusterGroups.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(player);
      }

      for (Entry<Integer, List<PlayerClusteringEngine.PlayerFeatures>> entry : clusterGroups.entrySet()) {
         int clusterId = entry.getKey();
         List<PlayerClusteringEngine.PlayerFeatures> clusterPlayers = entry.getValue();
         if (clusterPlayers.size() >= 3) {
            double[] avgFeatures = new double[6];
            double[] featureStdDevs = new double[6];

            for (PlayerClusteringEngine.PlayerFeatures player : clusterPlayers) {
               double[] features = player.getFeatureVector();

               for (int i = 0; i < 6; i++) {
                  avgFeatures[i] += features[i];
               }
            }

            for (int i = 0; i < 6; i++) {
               avgFeatures[i] /= clusterPlayers.size();
            }

            for (PlayerClusteringEngine.PlayerFeatures player : clusterPlayers) {
               double[] features = player.getFeatureVector();

               for (int i = 0; i < 6; i++) {
                  double diff = features[i] - avgFeatures[i];
                  featureStdDevs[i] += diff * diff;
               }
            }

            for (int i = 0; i < 6; i++) {
               featureStdDevs[i] = Math.sqrt(featureStdDevs[i] / clusterPlayers.size());
            }

            this.clusterProfiles.put(clusterId, new PlayerClusteringEngine.ClusterProfile(clusterId, avgFeatures, featureStdDevs, clusterPlayers.size()));
         }
      }
   }

   private void assessClusterRisks() {
      for (Entry<UUID, PlayerClusteringEngine.PlayerCluster> entry : this.playerClusters.entrySet()) {
         UUID playerId = entry.getKey();
         PlayerClusteringEngine.PlayerCluster cluster = entry.getValue();
         PlayerClusteringEngine.PlayerFeatures features = this.playerFeatures.get(playerId);
         PlayerClusteringEngine.ClusterProfile profile = this.clusterProfiles.get(cluster.getClusterId());
         if (features != null && profile != null) {
            double anomalyScore = this.calculateAnomalyScore(features, profile);
            double clusterRisk = profile.calculateRiskLevel();
            PlayerClusteringEngine.RiskLevel riskLevel = this.determineRiskLevel(anomalyScore, clusterRisk);
            this.playerRisks
               .put(
                  playerId,
                  new PlayerClusteringEngine.RiskAssessment(playerId, cluster.getClusterId(), anomalyScore, clusterRisk, riskLevel, System.currentTimeMillis())
               );
         }
      }
   }

   private double calculateAnomalyScore(PlayerClusteringEngine.PlayerFeatures player, PlayerClusteringEngine.ClusterProfile cluster) {
      double[] playerFeatures = player.getFeatureVector();
      double[] clusterMeans = cluster.avgFeatures;
      double[] clusterStdDevs = cluster.stdDevs;
      double totalAnomaly = 0.0;
      int featureCount = 0;

      for (int i = 0; i < 6; i++) {
         if (clusterStdDevs[i] > 0.0) {
            double zScore = Math.abs(playerFeatures[i] - clusterMeans[i]) / clusterStdDevs[i];
            totalAnomaly += Math.min(zScore, 5.0);
            featureCount++;
         }
      }

      return featureCount > 0 ? totalAnomaly / featureCount : 0.0;
   }

   private PlayerClusteringEngine.RiskLevel determineRiskLevel(double anomalyScore, double clusterRisk) {
      double combinedScore = anomalyScore * 0.7 + clusterRisk * 0.3;
      if (combinedScore >= 2.5) {
         return PlayerClusteringEngine.RiskLevel.CRITICAL;
      } else if (combinedScore >= 1.8) {
         return PlayerClusteringEngine.RiskLevel.HIGH;
      } else if (combinedScore >= 1.2) {
         return PlayerClusteringEngine.RiskLevel.MEDIUM;
      } else {
         return combinedScore >= 0.8 ? PlayerClusteringEngine.RiskLevel.LOW : PlayerClusteringEngine.RiskLevel.SAFE;
      }
   }

   private void startClusteringTask() {
      AsyncExecutor.executeComputation(() -> {
         while (plugin.isEnabled()) {
            try {
               Thread.sleep(900000L);
               this.performIncrementalClustering();
            } catch (InterruptedException var2) {
               Thread.currentThread().interrupt();
               break;
            } catch (Exception var3) {
               MessageManager.log("error", "Error in clustering task: {ERROR}", "ERROR", var3.getMessage());
            }
         }
      });
   }

   private double normalizeValue(double value, double min, double max) {
      return max == min ? 0.5 : Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
   }

   private double calculateConsistencyScore(PlayerDetectionData data) {
      Queue<DetectionResult> detections = data.getRecentDetections();
      if (detections.size() < 3) {
         return 0.5;
      } else {
         List<DetectionResult> results = new ArrayList<>(detections);
         double[] scores = results.stream().mapToDouble(DetectionResult::getConfidence).toArray();
         double mean = Arrays.stream(scores).average().orElse(0.0);
         if (mean == 0.0) {
            return 0.0;
         } else {
            double variance = Arrays.stream(scores).map(score -> Math.pow(score - mean, 2.0)).average().orElse(0.0);
            double cv = Math.sqrt(variance) / mean;
            return Math.max(0.0, 1.0 - cv);
         }
      }
   }

   public int getPlayerCluster(UUID playerId) {
      PlayerClusteringEngine.PlayerCluster cluster = this.playerClusters.get(playerId);
      return cluster != null ? cluster.getClusterId() : -1;
   }

   public PlayerClusteringEngine.ClusterProfile getClusterProfile(int clusterId) {
      return this.clusterProfiles.get(clusterId);
   }

   public PlayerClusteringEngine.RiskAssessment getPlayerRisk(UUID playerId) {
      return this.playerRisks.get(playerId);
   }

   public PlayerClusteringEngine.ClusteringStats getStats() {
      return new PlayerClusteringEngine.ClusteringStats(
         this.playerClusters.size(),
         this.clusterProfiles.size(),
         this.playerFeatures.size(),
         this.playerRisks.size(),
         this.playerFeatures.size(),
         System.currentTimeMillis()
      );
   }

   public static class ClusterProfile {
      public final int clusterId;
      public final double[] avgFeatures;
      public final double[] stdDevs;
      public final int size;

      public ClusterProfile(int clusterId, double[] avgFeatures, double[] stdDevs, int size) {
         this.clusterId = clusterId;
         this.avgFeatures = Arrays.copyOf(avgFeatures, avgFeatures.length);
         this.stdDevs = Arrays.copyOf(stdDevs, stdDevs.length);
         this.size = size;
      }

      public double calculateRiskLevel() {
         double suspicionWeight = this.avgFeatures[1] * 0.5;
         double speedWeight = this.avgFeatures[2] * 0.3;
         double efficiencyWeight = this.avgFeatures[0] * 0.2;
         return suspicionWeight + speedWeight + efficiencyWeight;
      }
   }

   public static class ClusteringStats {
      public final int clusteredPlayers;
      public final int totalClusters;
      public final int featureVectors;
      public final int riskAssessments;
      public final int trackedPlayers;
      public final long lastUpdate;

      public ClusteringStats(int clusteredPlayers, int totalClusters, int featureVectors, int riskAssessments, int trackedPlayers, long lastUpdate) {
         this.clusteredPlayers = clusteredPlayers;
         this.totalClusters = totalClusters;
         this.featureVectors = featureVectors;
         this.riskAssessments = riskAssessments;
         this.trackedPlayers = trackedPlayers;
         this.lastUpdate = lastUpdate;
      }
   }

   private static class PlayerCluster {
      private final UUID playerId;
      private int clusterId;
      private long assignedAt;

      public PlayerCluster(UUID playerId) {
         this.playerId = playerId;
         this.clusterId = -1;
         this.assignedAt = System.currentTimeMillis();
      }

      public void setClusterId(int clusterId) {
         this.clusterId = clusterId;
         this.assignedAt = System.currentTimeMillis();
      }

      public int getClusterId() {
         return this.clusterId;
      }

      public long getAssignedAt() {
         return this.assignedAt;
      }
   }

   private static class PlayerFeatures {
      private final UUID playerId;
      private final Map<String, Double> features = new ConcurrentHashMap<>();
      private final double[] featureVector = new double[6];
      private int featureCount = 0;

      public PlayerFeatures(UUID playerId) {
         this.playerId = playerId;
      }

      public void addFeature(String name, double value) {
         this.features.put(name, value);
         if (this.featureCount < 6) {
            this.featureVector[this.featureCount++] = value;
         }
      }

      public double[] getFeatureVector() {
         return Arrays.copyOf(this.featureVector, 6);
      }

      public UUID getPlayerId() {
         return this.playerId;
      }

      public Map<String, Double> getFeatures() {
         return new HashMap<>(this.features);
      }
   }

   public static class RiskAssessment {
      public final UUID playerId;
      public final int clusterId;
      public final double anomalyScore;
      public final double clusterRisk;
      public final PlayerClusteringEngine.RiskLevel riskLevel;
      public final long assessedAt;

      public RiskAssessment(UUID playerId, int clusterId, double anomalyScore, double clusterRisk, PlayerClusteringEngine.RiskLevel riskLevel, long assessedAt) {
         this.playerId = playerId;
         this.clusterId = clusterId;
         this.anomalyScore = anomalyScore;
         this.clusterRisk = clusterRisk;
         this.riskLevel = riskLevel;
         this.assessedAt = assessedAt;
      }
   }

   public static enum RiskLevel {
      SAFE("Safe", 0),
      LOW("Low Risk", 1),
      MEDIUM("Medium Risk", 2),
      HIGH("High Risk", 3),
      CRITICAL("Critical Risk", 4);

      private final String displayName;
      private final int severity;

      private RiskLevel(String displayName, int severity) {
         this.displayName = displayName;
         this.severity = severity;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public int getSeverity() {
         return this.severity;
      }
   }
}
