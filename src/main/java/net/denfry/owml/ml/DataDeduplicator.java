package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intelligent data deduplication system for ML training samples.
 * Features:
 * - Similarity-based deduplication
 * - Clustering for similar samples
 * - Feature-based hashing
 * - Adaptive similarity thresholds
 * - Memory-efficient storage
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.6
 */
public class DataDeduplicator {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Deduplication settings
    private static final double SIMILARITY_THRESHOLD = 0.95; // 95% similarity = duplicate
    private static final int MAX_CLUSTERS = 1000;
    private static final int MIN_CLUSTER_SIZE = 3;
    private static final long DEDUPLICATION_INTERVAL = 30 * 60 * 1000; // 30 minutes

    // Data structures for deduplication
    private static final Map<String, SampleCluster> featureHashClusters = new ConcurrentHashMap<>();
    private static final Set<String> knownHashes = ConcurrentHashMap.newKeySet();
    private static final AtomicLong duplicatesRemoved = new AtomicLong(0);
    private static final AtomicLong totalSamplesProcessed = new AtomicLong(0);

    // Similarity cache to avoid recalculating
    private static final Map<String, Map<String, Double>> similarityCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;

    // Adaptive thresholds
    private static volatile double currentSimilarityThreshold = SIMILARITY_THRESHOLD;
    private static final double THRESHOLD_ADJUSTMENT_RATE = 0.001;

    /**
     * Check if a training sample is a duplicate and should be discarded
     */
    public static boolean isDuplicate(Map<String, Object> sample) {
        totalSamplesProcessed.incrementAndGet();

        // Generate feature hash for quick duplicate detection
        String featureHash = generateFeatureHash(sample);

        // Quick hash-based check
        if (knownHashes.contains(featureHash)) {
            duplicatesRemoved.incrementAndGet();
            return true;
        }

        // Detailed similarity check
        if (isSimilarToExisting(sample)) {
            duplicatesRemoved.incrementAndGet();
            return true;
        }

        // Add to known samples
        knownHashes.add(featureHash);
        addToCluster(sample, featureHash);

        return false;
    }

    /**
     * Generate a hash based on feature values (with tolerance for small differences)
     */
    private static String generateFeatureHash(Map<String, Object> sample) {
        @SuppressWarnings("unchecked")
        Map<String, Double> features = (Map<String, Double>) sample.get("features");
        if (features == null) return "null";

        // Sort features for consistent hashing
        List<Map.Entry<String, Double>> sortedFeatures = new ArrayList<>(features.entrySet());
        sortedFeatures.sort(Map.Entry.comparingByKey());

        StringBuilder hashBuilder = new StringBuilder();
        for (Map.Entry<String, Double> entry : sortedFeatures) {
            String featureName = entry.getKey();
            double value = entry.getValue();

            // Round to reduce sensitivity to small changes
            double roundedValue = Math.round(value * 100.0) / 100.0;

            hashBuilder.append(featureName).append(":").append(roundedValue).append("|");
        }

        return String.valueOf(hashBuilder.toString().hashCode());
    }

    /**
     * Check if sample is similar to any existing sample
     */
    private static boolean isSimilarToExisting(Map<String, Object> newSample) {
        @SuppressWarnings("unchecked")
        Map<String, Double> newFeatures = (Map<String, Double>) newSample.get("features");
        if (newFeatures == null) return false;

        // Check against a sample from each cluster
        for (SampleCluster cluster : featureHashClusters.values()) {
            if (!cluster.samples.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Double> representative = (Map<String, Double>) cluster.samples.get(0).get("features");

                double similarity = calculateSimilarity(newFeatures, representative);
                if (similarity >= currentSimilarityThreshold) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculate similarity between two feature sets
     */
    private static double calculateSimilarity(Map<String, Double> features1, Map<String, Double> features2) {
        // Create cache key
        String cacheKey1 = getFeatureSignature(features1);
        String cacheKey2 = getFeatureSignature(features2);
        String cacheKey = cacheKey1.compareTo(cacheKey2) < 0 ?
            cacheKey1 + "|" + cacheKey2 : cacheKey2 + "|" + cacheKey1;

        // Check cache
        Map<String, Double> similarities = similarityCache.get(cacheKey1);
        if (similarities != null && similarities.containsKey(cacheKey2)) {
            return similarities.get(cacheKey2);
        }

        // Calculate cosine similarity
        double similarity = calculateCosineSimilarity(features1, features2);

        // Cache result
        similarityCache.computeIfAbsent(cacheKey1, k -> new HashMap<>()).put(cacheKey2, similarity);

        // Maintain cache size
        if (similarityCache.size() > MAX_CACHE_SIZE) {
            cleanSimilarityCache();
        }

        return similarity;
    }

    /**
     * Calculate cosine similarity between feature vectors
     */
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

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0; // Avoid division by zero
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Generate a signature for features (for caching)
     */
    private static String getFeatureSignature(Map<String, Double> features) {
        List<String> sortedKeys = new ArrayList<>(features.keySet());
        Collections.sort(sortedKeys);
        return String.join(",", sortedKeys);
    }

    /**
     * Add sample to appropriate cluster
     */
    private static void addToCluster(Map<String, Object> sample, String hash) {
        // Find or create cluster
        SampleCluster cluster = featureHashClusters.computeIfAbsent(hash,
            k -> new SampleCluster(hash));

        cluster.addSample(sample);

        // Limit number of clusters
        if (featureHashClusters.size() > MAX_CLUSTERS) {
            removeSmallestCluster();
        }
    }

    /**
     * Remove the smallest cluster to maintain size limits
     */
    private static void removeSmallestCluster() {
        SampleCluster smallest = null;
        for (SampleCluster cluster : featureHashClusters.values()) {
            if (smallest == null || cluster.samples.size() < smallest.samples.size()) {
                smallest = cluster;
            }
        }

        if (smallest != null) {
            featureHashClusters.remove(smallest.hash);
        }
    }

    /**
     * Clean similarity cache to prevent memory overflow
     */
    private static void cleanSimilarityCache() {
        // Remove oldest entries (simple LRU approximation)
        Iterator<Map.Entry<String, Map<String, Double>>> iterator = similarityCache.entrySet().iterator();
        int toRemove = similarityCache.size() - MAX_CACHE_SIZE / 2;

        for (int i = 0; i < toRemove && iterator.hasNext(); i++) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Perform full deduplication pass on existing data
     */
    public static void deduplicateExistingData() {
        try {
            MessageManager.log("info", "Starting deduplication of existing training data");

            Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
            if (!Files.exists(compressedPath)) {
                MessageManager.log("info", "No compressed data found for deduplication");
                return;
            }

            // Clear existing structures
            featureHashClusters.clear();
            knownHashes.clear();
            duplicatesRemoved.set(0);

            // Process all compressed files
            Files.walk(compressedPath)
                .filter(path -> path.toString().endsWith(".dat"))
                .forEach(DataDeduplicator::processFileForDeduplication);

            // Adaptive threshold adjustment
            adjustSimilarityThreshold();

            MessageManager.log("info", "Deduplication completed. Removed {COUNT} duplicates",
                "COUNT", String.valueOf(duplicatesRemoved.get()));

        } catch (Exception e) {
            MessageManager.log("error", "Error during deduplication: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Process a single file for deduplication
     */
    private static void processFileForDeduplication(Path filePath) {
        try {
            // Load sample
            Map<String, Object> sample = DataCompressor.loadAndDecompressSample(filePath);

            if (isDuplicate(sample)) {
                // Remove duplicate file
                Files.delete(filePath);
            }

        } catch (Exception e) {
            MessageManager.log("warning", "Failed to process file for deduplication: {FILE}",
                "FILE", filePath.getFileName().toString());
        }
    }

    /**
     * Adaptively adjust similarity threshold based on deduplication results
     */
    private static void adjustSimilarityThreshold() {
        long total = totalSamplesProcessed.get();
        long removed = duplicatesRemoved.get();

        if (total > 100) {
            double removalRate = (double) removed / total;

            // If too many duplicates are being removed, increase threshold (be less strict)
            if (removalRate > 0.3) {
                currentSimilarityThreshold = Math.min(0.99,
                    currentSimilarityThreshold + THRESHOLD_ADJUSTMENT_RATE);
            }
            // If too few duplicates, decrease threshold (be more strict)
            else if (removalRate < 0.05) {
                currentSimilarityThreshold = Math.max(0.8,
                    currentSimilarityThreshold - THRESHOLD_ADJUSTMENT_RATE);
            }
        }
    }

    /**
     * Get deduplication statistics
     */
    public static Map<String, Object> getDeduplicationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSamplesProcessed", totalSamplesProcessed.get());
        stats.put("duplicatesRemoved", duplicatesRemoved.get());
        stats.put("duplicateRate", totalSamplesProcessed.get() > 0 ?
            (double) duplicatesRemoved.get() / totalSamplesProcessed.get() : 0.0);
        stats.put("currentSimilarityThreshold", currentSimilarityThreshold);
        stats.put("activeClusters", featureHashClusters.size());
        stats.put("knownHashes", knownHashes.size());
        stats.put("similarityCacheSize", similarityCache.size());

        return stats;
    }

    /**
     * Reset deduplication state (useful for testing)
     */
    public static void reset() {
        featureHashClusters.clear();
        knownHashes.clear();
        similarityCache.clear();
        duplicatesRemoved.set(0);
        totalSamplesProcessed.set(0);
        currentSimilarityThreshold = SIMILARITY_THRESHOLD;
    }

    /**
     * Represents a cluster of similar samples
     */
    private static class SampleCluster {
        private final String hash;
        private final List<Map<String, Object>> samples = new ArrayList<>();
        private final Map<String, Double> centroid = new HashMap<>();

        public SampleCluster(String hash) {
            this.hash = hash;
        }

        public void addSample(Map<String, Object> sample) {
            samples.add(sample);

            // Update centroid
            @SuppressWarnings("unchecked")
            Map<String, Double> features = (Map<String, Double>) sample.get("features");
            if (features != null) {
                updateCentroid(features);
            }
        }

        private void updateCentroid(Map<String, Double> features) {
            int count = samples.size();

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String feature = entry.getKey();
                double value = entry.getValue();

                double currentCentroid = centroid.getOrDefault(feature, 0.0);
                // Incremental update of centroid
                centroid.put(feature, currentCentroid + (value - currentCentroid) / count);
            }
        }

        public Map<String, Double> getCentroid() {
            return new HashMap<>(centroid);
        }
    }
}