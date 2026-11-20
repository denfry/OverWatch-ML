package net.denfry.owml.ml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class DataDeduplicator {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private static final double SIMILARITY_THRESHOLD = 0.95;
   private static final int MAX_CLUSTERS = 1000;
   private static final int MIN_CLUSTER_SIZE = 3;
   private static final long DEDUPLICATION_INTERVAL = 1800000L;
   private static final Map<String, DataDeduplicator.SampleCluster> featureHashClusters = new ConcurrentHashMap<>();
   private static final Set<String> knownHashes = ConcurrentHashMap.newKeySet();
   private static final AtomicLong duplicatesRemoved = new AtomicLong(0L);
   private static final AtomicLong totalSamplesProcessed = new AtomicLong(0L);
   private static final Map<String, Map<String, Double>> similarityCache = new ConcurrentHashMap<>();
   private static final int MAX_CACHE_SIZE = 10000;
   private static volatile double currentSimilarityThreshold = 0.95;
   private static final double THRESHOLD_ADJUSTMENT_RATE = 0.001;

   public static boolean isDuplicate(Map<String, Object> sample) {
      totalSamplesProcessed.incrementAndGet();
      String featureHash = generateFeatureHash(sample);
      if (knownHashes.contains(featureHash)) {
         duplicatesRemoved.incrementAndGet();
         return true;
      } else if (isSimilarToExisting(sample)) {
         duplicatesRemoved.incrementAndGet();
         return true;
      } else {
         knownHashes.add(featureHash);
         addToCluster(sample, featureHash);
         return false;
      }
   }

   private static String generateFeatureHash(Map<String, Object> sample) {
      Map<String, Double> features = (Map<String, Double>)sample.get("features");
      if (features == null) {
         return "null";
      } else {
         List<Entry<String, Double>> sortedFeatures = new ArrayList<>(features.entrySet());
         sortedFeatures.sort(Entry.comparingByKey());
         StringBuilder hashBuilder = new StringBuilder();

         for (Entry<String, Double> entry : sortedFeatures) {
            String featureName = entry.getKey();
            double value = entry.getValue();
            double roundedValue = Math.round(value * 100.0) / 100.0;
            hashBuilder.append(featureName).append(":").append(roundedValue).append("|");
         }

         return String.valueOf(hashBuilder.toString().hashCode());
      }
   }

   private static boolean isSimilarToExisting(Map<String, Object> newSample) {
      Map<String, Double> newFeatures = (Map<String, Double>)newSample.get("features");
      if (newFeatures == null) {
         return false;
      } else {
         for (DataDeduplicator.SampleCluster cluster : featureHashClusters.values()) {
            if (!cluster.samples.isEmpty()) {
               Map<String, Double> representative = (Map<String, Double>)cluster.samples.get(0).get("features");
               double similarity = calculateSimilarity(newFeatures, representative);
               if (similarity >= currentSimilarityThreshold) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static double calculateSimilarity(Map<String, Double> features1, Map<String, Double> features2) {
      String cacheKey1 = getFeatureSignature(features1);
      String cacheKey2 = getFeatureSignature(features2);
      Map<String, Double> similarities = similarityCache.get(cacheKey1);
      if (similarities != null && similarities.containsKey(cacheKey2)) {
         return similarities.get(cacheKey2);
      } else {
         double similarity = calculateCosineSimilarity(features1, features2);
         similarityCache.computeIfAbsent(cacheKey1, k -> new HashMap<>()).put(cacheKey2, similarity);
         if (similarityCache.size() > 10000) {
            cleanSimilarityCache();
         }

         return similarity;
      }
   }

   private static double calculateCosineSimilarity(Map<String, Double> features1, Map<String, Double> features2) {
      Set<String> allFeatures = new HashSet<>(features1.keySet());
      allFeatures.addAll(features2.keySet());
      double dotProduct = 0.0;
      double norm1 = 0.0;
      double norm2 = 0.0;

      for (String feature : allFeatures) {
         double val1 = features1.getOrDefault(feature, 0.0);
         double val2 = features2.getOrDefault(feature, 0.0);
         dotProduct += val1 * val2;
         norm1 += val1 * val1;
         norm2 += val2 * val2;
      }

      return norm1 != 0.0 && norm2 != 0.0 ? dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)) : 0.0;
   }

   private static String getFeatureSignature(Map<String, Double> features) {
      List<String> sortedKeys = new ArrayList<>(features.keySet());
      Collections.sort(sortedKeys);
      return String.join(",", sortedKeys);
   }

   private static void addToCluster(Map<String, Object> sample, String hash) {
      DataDeduplicator.SampleCluster cluster = featureHashClusters.computeIfAbsent(hash, k -> new DataDeduplicator.SampleCluster(hash));
      cluster.addSample(sample);
      if (featureHashClusters.size() > 1000) {
         removeSmallestCluster();
      }
   }

   private static void removeSmallestCluster() {
      DataDeduplicator.SampleCluster smallest = null;

      for (DataDeduplicator.SampleCluster cluster : featureHashClusters.values()) {
         if (smallest == null || cluster.samples.size() < smallest.samples.size()) {
            smallest = cluster;
         }
      }

      if (smallest != null) {
         featureHashClusters.remove(smallest.hash);
      }
   }

   private static void cleanSimilarityCache() {
      Iterator<Entry<String, Map<String, Double>>> iterator = similarityCache.entrySet().iterator();
      int toRemove = similarityCache.size() - 5000;

      for (int i = 0; i < toRemove && iterator.hasNext(); i++) {
         iterator.next();
         iterator.remove();
      }
   }

   public static void deduplicateExistingData() {
      try {
         MessageManager.log("info", "Starting deduplication of existing training data");
         Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
         if (!Files.exists(compressedPath)) {
            MessageManager.log("info", "No compressed data found for deduplication");
            return;
         }

         featureHashClusters.clear();
         knownHashes.clear();
         duplicatesRemoved.set(0L);
         Files.walk(compressedPath).filter(path -> path.toString().endsWith(".dat")).forEach(DataDeduplicator::processFileForDeduplication);
         adjustSimilarityThreshold();
         MessageManager.log("info", "Deduplication completed. Removed {COUNT} duplicates", "COUNT", String.valueOf(duplicatesRemoved.get()));
      } catch (Exception var1) {
         MessageManager.log("error", "Error during deduplication: {ERROR}", "ERROR", var1.getMessage());
      }
   }

   private static void processFileForDeduplication(Path filePath) {
      try {
         Map<String, Object> sample = DataCompressor.loadAndDecompressSample(filePath);
         if (isDuplicate(sample)) {
            Files.delete(filePath);
         }
      } catch (Exception var2) {
         MessageManager.log("warning", "Failed to process file for deduplication: {FILE}", "FILE", filePath.getFileName().toString());
      }
   }

   private static void adjustSimilarityThreshold() {
      long total = totalSamplesProcessed.get();
      long removed = duplicatesRemoved.get();
      if (total > 100L) {
         double removalRate = (double)removed / total;
         if (removalRate > 0.3) {
            currentSimilarityThreshold = Math.min(0.99, currentSimilarityThreshold + 0.001);
         } else if (removalRate < 0.05) {
            currentSimilarityThreshold = Math.max(0.8, currentSimilarityThreshold - 0.001);
         }
      }
   }

   public static Map<String, Object> getDeduplicationStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("totalSamplesProcessed", totalSamplesProcessed.get());
      stats.put("duplicatesRemoved", duplicatesRemoved.get());
      stats.put("duplicateRate", totalSamplesProcessed.get() > 0L ? (double)duplicatesRemoved.get() / totalSamplesProcessed.get() : 0.0);
      stats.put("currentSimilarityThreshold", currentSimilarityThreshold);
      stats.put("activeClusters", featureHashClusters.size());
      stats.put("knownHashes", knownHashes.size());
      stats.put("similarityCacheSize", similarityCache.size());
      return stats;
   }

   public static void reset() {
      featureHashClusters.clear();
      knownHashes.clear();
      similarityCache.clear();
      duplicatesRemoved.set(0L);
      totalSamplesProcessed.set(0L);
      currentSimilarityThreshold = 0.95;
   }

   private static class SampleCluster {
      private final String hash;
      private final List<Map<String, Object>> samples = new ArrayList<>();
      private final Map<String, Double> centroid = new ConcurrentHashMap<>();

      public SampleCluster(String hash) {
         this.hash = hash;
      }

      public void addSample(Map<String, Object> sample) {
         this.samples.add(sample);
         Map<String, Double> features = (Map<String, Double>)sample.get("features");
         if (features != null) {
            this.updateCentroid(features);
         }
      }

      private void updateCentroid(Map<String, Double> features) {
         int count = this.samples.size();

         for (Entry<String, Double> entry : features.entrySet()) {
            String feature = entry.getKey();
            double value = entry.getValue();
            double currentCentroid = this.centroid.getOrDefault(feature, 0.0);
            this.centroid.put(feature, currentCentroid + (value - currentCentroid) / count);
         }
      }

      public Map<String, Double> getCentroid() {
         return new HashMap<>(this.centroid);
      }
   }
}
