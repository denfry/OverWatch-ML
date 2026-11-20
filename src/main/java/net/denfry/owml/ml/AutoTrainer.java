package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.Schedulers;

public class AutoTrainer {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<String, AutoTrainer.BehaviorPattern> learnedPatterns = new ConcurrentHashMap<>();
   private final Map<UUID, AutoTrainer.PlayerBehaviorProfile> playerProfiles = new ConcurrentHashMap<>();
   private final Map<String, AutoTrainer.FeatureDistribution> featureDistributions = new ConcurrentHashMap<>();
   private final Queue<AutoTrainer.UnlabeledSample> unlabeledData = new ConcurrentLinkedQueue<>();
   private final AtomicLong syntheticSamplesGenerated = new AtomicLong(0L);
   private volatile double confidenceThreshold = 0.8;
   private volatile int minPatternSamples = 50;
   private volatile int maxSyntheticSamplesPerPattern = 1000;
   private volatile boolean autoGenerateEnabled = true;
   private volatile long lastAutoTraining = System.currentTimeMillis();
   private volatile long autoTrainingInterval = 1800000L;

   public AutoTrainer() {
      this.initializePatternLearning();
      this.startAutoLearningLoop();
   }

   private void initializePatternLearning() {
      this.learnedPatterns.put("normal_mining", new AutoTrainer.BehaviorPattern("normal_mining"));
      this.learnedPatterns.put("efficient_mining", new AutoTrainer.BehaviorPattern("efficient_mining"));
      this.learnedPatterns.put("random_mining", new AutoTrainer.BehaviorPattern("random_mining"));
      this.learnedPatterns.put("tunnel_mining", new AutoTrainer.BehaviorPattern("tunnel_mining"));
      this.learnedPatterns.put("xray_suspicious", new AutoTrainer.BehaviorPattern("xray_suspicious"));
      MessageManager.log("info", "AutoTrainer initialized with {COUNT} behavior patterns", "COUNT", String.valueOf(this.learnedPatterns.size()));
   }

   private void startAutoLearningLoop() {
      Schedulers.asyncTimer(() -> {
         try {
            if (this.autoGenerateEnabled && this.shouldPerformAutoTraining()) {
               this.performAutoTraining();
            }
         } catch (Exception var2) {
            MessageManager.log("error", "Error in auto-training loop: {ERROR}", "ERROR", var2.getMessage());
         }
      }, 1200L, 1200L);
      MessageManager.log("info", "Auto-training loop started");
   }

   private boolean shouldPerformAutoTraining() {
      long timeSinceLastTraining = System.currentTimeMillis() - this.lastAutoTraining;
      return timeSinceLastTraining >= this.autoTrainingInterval && this.unlabeledData.size() >= 50;
   }

   private void performAutoTraining() {
      this.lastAutoTraining = System.currentTimeMillis();
      MessageManager.log("info", "Starting auto-training cycle with {COUNT} unlabeled samples", "COUNT", String.valueOf(this.unlabeledData.size()));
      this.learnFromUnlabeledData();
      this.updateFeatureDistributions();
      this.generateSyntheticData();
      this.refinePatterns();
      MessageManager.log(
         "info", "Auto-training cycle completed. Total synthetic samples: {COUNT}", "COUNT", String.valueOf(this.syntheticSamplesGenerated.get())
      );
   }

   private void learnFromUnlabeledData() {
      List<AutoTrainer.UnlabeledSample> samples = new ArrayList<>();
      int processed = 0;

      while (!this.unlabeledData.isEmpty() && processed < 1000) {
         AutoTrainer.UnlabeledSample sample = this.unlabeledData.poll();
         if (sample != null) {
            samples.add(sample);
            processed++;
         }
      }

      for (AutoTrainer.UnlabeledSample sample : samples) {
         this.classifyAndLearnFromSample(sample);
      }

      if (processed > 0) {
         MessageManager.log("info", "Processed {COUNT} unlabeled samples for pattern learning", "COUNT", String.valueOf(processed));
      }
   }

   private void classifyAndLearnFromSample(AutoTrainer.UnlabeledSample sample) {
      String bestPattern = this.findBestMatchingPattern(sample);
      if (bestPattern != null) {
         AutoTrainer.BehaviorPattern pattern = this.learnedPatterns.get(bestPattern);
         pattern.addSample(sample.features, sample.confidence);
         this.updatePlayerProfile(sample.playerId, bestPattern, sample.features);
      }
   }

   private String findBestMatchingPattern(AutoTrainer.UnlabeledSample sample) {
      String bestPattern = null;
      double bestScore = 0.0;

      for (Entry<String, AutoTrainer.BehaviorPattern> entry : this.learnedPatterns.entrySet()) {
         double score = entry.getValue().calculateMatchScore(sample.features);
         if (score > bestScore && score > this.confidenceThreshold) {
            bestScore = score;
            bestPattern = entry.getKey();
         }
      }

      return bestPattern;
   }

   private void updatePlayerProfile(UUID playerId, String pattern, Map<String, Double> features) {
      AutoTrainer.PlayerBehaviorProfile profile = this.playerProfiles.computeIfAbsent(playerId, k -> new AutoTrainer.PlayerBehaviorProfile(playerId));
      profile.updatePattern(pattern, features);
      profile.updateLastActivity();
   }

   private void updateFeatureDistributions() {
      for (AutoTrainer.BehaviorPattern pattern : this.learnedPatterns.values()) {
         Map<String, Double> patternFeatures = pattern.getAverageFeatures();

         for (Entry<String, Double> entry : patternFeatures.entrySet()) {
            String featureName = entry.getKey();
            double value = entry.getValue();
            AutoTrainer.FeatureDistribution dist = this.featureDistributions
               .computeIfAbsent(featureName, k -> new AutoTrainer.FeatureDistribution(featureName));
            dist.update(value, pattern.getSampleCount());
         }
      }
   }

   private void generateSyntheticData() {
      for (AutoTrainer.BehaviorPattern pattern : this.learnedPatterns.values()) {
         if (pattern.getSampleCount() >= this.minPatternSamples) {
            int samplesToGenerate = Math.min(this.maxSyntheticSamplesPerPattern - pattern.getSyntheticSamplesGenerated(), 100);

            for (int i = 0; i < samplesToGenerate; i++) {
               Map<String, Double> syntheticFeatures = pattern.generateSyntheticSample();
               if (syntheticFeatures != null) {
                  this.addSyntheticTrainingSample(pattern.getName(), syntheticFeatures);
                  pattern.incrementSyntheticSamples();
                  this.syntheticSamplesGenerated.incrementAndGet();
               }
            }
         }
      }
   }

   private void addSyntheticTrainingSample(String patternName, Map<String, Double> features) {
      try {
         boolean isCheater = patternName.contains("xray") || patternName.contains("suspicious");
         Map<String, Object> trainingSample = new HashMap<>();
         trainingSample.put("features", new HashMap<>(features));
         trainingSample.put("label", isCheater ? "cheater" : "normal");
         trainingSample.put("source", "synthetic_" + patternName);
         trainingSample.put("timestamp", System.currentTimeMillis());
         MLDataManager.saveSyntheticTrainingSample(trainingSample);
      } catch (Exception var5) {
         MessageManager.log("warning", "Failed to save synthetic training sample: {ERROR}", "ERROR", var5.getMessage());
      }
   }

   private void refinePatterns() {
      for (AutoTrainer.BehaviorPattern pattern : this.learnedPatterns.values()) {
         pattern.refinePattern();
      }
   }

   public void addUnlabeledSample(UUID playerId, Map<String, Double> features, double confidence) {
      if (this.unlabeledData.size() < 20000) {
         this.unlabeledData.add(new AutoTrainer.UnlabeledSample(playerId, features, confidence));
      }
   }

   public Map<String, Object> getLearningStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("patternsLearned", this.learnedPatterns.size());
      stats.put("unlabeledSamplesQueued", this.unlabeledData.size());
      stats.put("syntheticSamplesGenerated", this.syntheticSamplesGenerated.get());
      stats.put("playerProfiles", this.playerProfiles.size());
      stats.put("featureDistributions", this.featureDistributions.size());
      stats.put("autoGenerateEnabled", this.autoGenerateEnabled);
      stats.put("confidenceThreshold", this.confidenceThreshold);
      return stats;
   }

   public void setAutoGenerateEnabled(boolean enabled) {
      this.autoGenerateEnabled = enabled;
      MessageManager.log("info", "Auto-training " + (enabled ? "enabled" : "disabled"));
   }

   public void setConfidenceThreshold(double threshold) {
      this.confidenceThreshold = Math.max(0.1, Math.min(1.0, threshold));
   }

   private static class BehaviorPattern {
      private final String name;
      private final List<Map<String, Double>> samples = new ArrayList<>();
      private final Map<String, Double> featureSums = new ConcurrentHashMap<>();
      private final Map<String, Double> featureMeans = new ConcurrentHashMap<>();
      private final Map<String, Double> featureVariances = new ConcurrentHashMap<>();
      private int syntheticSamplesGenerated = 0;

      public BehaviorPattern(String name) {
         this.name = name;
      }

      public synchronized void addSample(Map<String, Double> features, double confidence) {
         this.samples.add(new HashMap<>(features));

         for (Entry<String, Double> entry : features.entrySet()) {
            String feature = entry.getKey();
            double value = entry.getValue();
            this.featureSums.put(feature, this.featureSums.getOrDefault(feature, 0.0) + value);
         }

         if (this.samples.size() > 2000) {
            Map<String, Double> removed = this.samples.remove(0);

            for (Entry<String, Double> entry : removed.entrySet()) {
               String feature = entry.getKey();
               double value = entry.getValue();
               this.featureSums.put(feature, Math.max(0.0, this.featureSums.getOrDefault(feature, 0.0) - value));
            }
         }
      }

      public synchronized double calculateMatchScore(Map<String, Double> features) {
         if (this.samples.isEmpty() || this.featureMeans.isEmpty()) {
            if (this.samples.isEmpty()) {
               return 0.0;
            }

            this.updateStatistics();
         }

         double totalScore = 0.0;
         int featureMatches = 0;

         for (Entry<String, Double> entry : features.entrySet()) {
            String feature = entry.getKey();
            double value = entry.getValue();
            Double mean = this.featureMeans.get(feature);
            if (mean != null) {
               Double variance = this.featureVariances.get(feature);
               double stdDev = variance != null ? Math.sqrt(variance) : 1.0;
               double zScore = Math.abs(value - mean) / Math.max(stdDev, 0.001);
               double score = Math.max(0.0, 1.0 - zScore / 3.0);
               totalScore += score;
               featureMatches++;
            }
         }

         return featureMatches > 0 ? totalScore / featureMatches : 0.0;
      }

      public synchronized Map<String, Double> getAverageFeatures() {
         this.updateStatistics();
         return new HashMap<>(this.featureMeans);
      }

      public synchronized Map<String, Double> generateSyntheticSample() {
         this.updateStatistics();
         if (this.featureMeans.isEmpty()) {
            return null;
         } else {
            Map<String, Double> synthetic = new HashMap<>();
            Random random = new Random();

            for (Entry<String, Double> entry : this.featureMeans.entrySet()) {
               String feature = entry.getKey();
               double mean = entry.getValue();
               double variance = this.featureVariances.getOrDefault(feature, 1.0);
               double stdDev = Math.sqrt(variance);
               double syntheticValue = mean + random.nextGaussian() * stdDev * 0.4;
               syntheticValue = Math.max(0.0, syntheticValue);
               synthetic.put(feature, syntheticValue);
            }

            return synthetic;
         }
      }

      private void updateStatistics() {
         int sampleCount = this.samples.size();
         if (sampleCount != 0) {
            for (Entry<String, Double> entry : this.featureSums.entrySet()) {
               this.featureMeans.put(entry.getKey(), entry.getValue() / sampleCount);
            }

            for (Entry<String, Double> entry : this.featureMeans.entrySet()) {
               String feature = entry.getKey();
               double mean = entry.getValue();
               double sumSquaredDiffs = 0.0;

               for (Map<String, Double> sample : this.samples) {
                  Double value = sample.get(feature);
                  if (value != null) {
                     double diff = value - mean;
                     sumSquaredDiffs += diff * diff;
                  }
               }

               double variance = sampleCount > 1 ? sumSquaredDiffs / (sampleCount - 1) : 0.0;
               this.featureVariances.put(feature, Math.max(variance, 1.0E-4));
            }
         }
      }

      public synchronized void refinePattern() {
         this.updateStatistics();
      }

      public String getName() {
         return this.name;
      }

      public synchronized int getSampleCount() {
         return this.samples.size();
      }

      public synchronized int getSyntheticSamplesGenerated() {
         return this.syntheticSamplesGenerated;
      }

      public synchronized void incrementSyntheticSamples() {
         this.syntheticSamplesGenerated++;
      }
   }

   private static class FeatureDistribution {
      private final String featureName;
      private double sum = 0.0;
      private double sumSquares = 0.0;
      private int count = 0;

      public FeatureDistribution(String featureName) {
         this.featureName = featureName;
      }

      public synchronized void update(double value, int weight) {
         if (weight <= 0) {
            weight = 1;
         }

         this.sum += value * weight;
         this.sumSquares += value * value * weight;
         this.count += weight;
      }

      public synchronized double getMean() {
         return this.count > 0 ? this.sum / this.count : 0.0;
      }

      public synchronized double getVariance() {
         if (this.count <= 1) {
            return 1.0;
         } else {
            double mean = this.getMean();
            double variance = this.sumSquares / this.count - mean * mean;
            return Math.max(1.0E-4, variance);
         }
      }
   }

   private static class PlayerBehaviorProfile {
      private final UUID playerId;
      private final Map<String, Integer> patternCounts = new ConcurrentHashMap<>();
      private final Map<String, Double> averageFeatures = new ConcurrentHashMap<>();
      private volatile long lastActivity = System.currentTimeMillis();

      public PlayerBehaviorProfile(UUID playerId) {
         this.playerId = playerId;
      }

      public synchronized void updatePattern(String pattern, Map<String, Double> features) {
         this.patternCounts.put(pattern, this.patternCounts.getOrDefault(pattern, 0) + 1);
         int totalPatterns = this.patternCounts.values().stream().mapToInt(Integer::intValue).sum();

         for (Entry<String, Double> entry : features.entrySet()) {
            String feature = entry.getKey();
            double value = entry.getValue();
            double currentAvg = this.averageFeatures.getOrDefault(feature, 0.0);
            this.averageFeatures.put(feature, (currentAvg * (totalPatterns - 1) + value) / totalPatterns);
         }
      }

      public void updateLastActivity() {
         this.lastActivity = System.currentTimeMillis();
      }

      public UUID getPlayerId() {
         return this.playerId;
      }

      public long getLastActivity() {
         return this.lastActivity;
      }
   }

   private static class UnlabeledSample {
      public final UUID playerId;
      public final Map<String, Double> features;
      public final double confidence;

      public UnlabeledSample(UUID playerId, Map<String, Double> features, double confidence) {
         this.playerId = playerId;
         this.features = features;
         this.confidence = confidence;
      }
   }
}
