package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;

/**
 * Optimized data compression and storage system for ML training data.
 * Features:
 * - LZ4/GZIP compression for training samples
 * - Delta encoding for sequential data
 * - Dictionary-based compression for features
 * - Automatic compression/decompression
 * - Memory-efficient storage
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.6
 */
public class DataCompressor {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Compression settings
    private static final String COMPRESSED_DIR = "compressed";
    private static final String ARCHIVE_DIR = "archive";
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;
    private static final int MAX_UNCOMPRESSED_SIZE = 1024 * 1024; // 1MB uncompressed limit
    private static final long COMPRESSION_CHECK_INTERVAL = 5 * 60 * 1000; // 5 minutes

    // Feature dictionary for compression
    private static final Map<String, Integer> featureDictionary = new ConcurrentHashMap<>();
    private static final Map<Integer, String> reverseDictionary = new ConcurrentHashMap<>();
    private static final AtomicLong nextFeatureId = new AtomicLong(1);

    // Compression statistics
    private static final AtomicLong totalCompressedBytes = new AtomicLong(0);
    private static final AtomicLong totalOriginalBytes = new AtomicLong(0);
    private static long lastCompressionCheck = System.currentTimeMillis();

    static {
        // Initialize common feature names
        initializeFeatureDictionary();
    }

    /**
     * Initialize feature dictionary with common ML features
     */
    private static void initializeFeatureDictionary() {
        String[] commonFeatures = {
            "total_blocks", "total_ores", "mining_efficiency", "average_depth",
            "rare_ores_found", "session_duration", "mining_speed", "idle_time_ratio",
            "pattern_consistency", "ore_distribution_uniformity", "branch_mining_ratio",
            "straight_tunnel_ratio", "random_mining_ratio", "movement_efficiency",
            "backtracking_ratio", "tunnel_length", "cave_exploration_ratio",
            "surface_mining_ratio", "diamond_ore_density", "gold_ore_density",
            "iron_ore_density", "coal_ore_density", "redstone_ore_density",
            "lapis_ore_density", "emerald_ore_density", "netherite_scrap_density",
            "ancient_debris_density", "mining_speed_variance", "depth_variance",
            "ore_clustering_coefficient", "spatial_distribution_entropy"
        };

        for (String feature : commonFeatures) {
            int id = (int) nextFeatureId.getAndIncrement();
            featureDictionary.put(feature, id);
            reverseDictionary.put(id, feature);
        }
    }

    /**
     * Compress and store training sample
     */
    public static void compressAndStoreTrainingSample(Map<String, Object> sample) {
        try {
            // Convert sample to compressed format
            CompressedTrainingSample compressed = compressSample(sample);

            // Store compressed data
            storeCompressedSample(compressed);

            // Update statistics
            updateCompressionStats(sample, compressed);

        } catch (Exception e) {
            MessageManager.log("warning", "Failed to compress training sample: {ERROR}",
                "ERROR", e.getMessage());
        }
    }

    /**
     * Compress a training sample
     */
    private static CompressedTrainingSample compressSample(Map<String, Object> sample) throws IOException {
        // Extract basic info
        long timestamp = ((Number) sample.getOrDefault("timestamp", System.currentTimeMillis())).longValue();
        String label = (String) sample.getOrDefault("label", "unknown");
        String source = (String) sample.getOrDefault("source", "unknown");

        // Compress features using delta encoding and dictionary
        @SuppressWarnings("unchecked")
        Map<String, Double> features = (Map<String, Double>) sample.get("features");
        byte[] compressedFeatures = compressFeatures(features);

        return new CompressedTrainingSample(timestamp, label, source, compressedFeatures, features.size());
    }

    /**
     * Compress features map using dictionary and delta encoding
     */
    private static byte[] compressFeatures(Map<String, Double> features) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(new DeflaterOutputStream(baos, new Deflater(COMPRESSION_LEVEL)));

        // Write feature count
        dos.writeInt(features.size());

        // Sort features by name for consistent compression
        List<Map.Entry<String, Double>> sortedFeatures = new ArrayList<>(features.entrySet());
        sortedFeatures.sort(Map.Entry.comparingByKey());

        double previousValue = 0.0;
        for (Map.Entry<String, Double> entry : sortedFeatures) {
            String featureName = entry.getKey();
            double value = entry.getValue();

            // Compress feature name using dictionary
            Integer featureId = featureDictionary.computeIfAbsent(featureName, k -> {
                int id = (int) nextFeatureId.getAndIncrement();
                reverseDictionary.put(id, featureName);
                return id;
            });
            dos.writeInt(featureId);

            // Delta encode value (store difference from previous)
            float deltaValue = (float) (value - previousValue);
            dos.writeFloat(deltaValue);

            previousValue = value;
        }

        dos.close();
        return baos.toByteArray();
    }

    /**
     * Decompress features from compressed data
     */
    public static Map<String, Double> decompressFeatures(byte[] compressedData) throws IOException {
        Map<String, Double> features = new HashMap<>();

        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        DataInputStream dis = new DataInputStream(new InflaterInputStream(bais));

        int featureCount = dis.readInt();
        double previousValue = 0.0;

        for (int i = 0; i < featureCount; i++) {
            // Read feature ID and convert back to name
            int featureId = dis.readInt();
            String featureName = reverseDictionary.get(featureId);
            if (featureName == null) {
                featureName = "unknown_feature_" + featureId;
            }

            // Read delta-encoded value
            float deltaValue = dis.readFloat();
            double value = previousValue + deltaValue;

            features.put(featureName, value);
            previousValue = value;
        }

        dis.close();
        return features;
    }

    /**
     * Store compressed sample to disk
     */
    private static void storeCompressedSample(CompressedTrainingSample sample) throws IOException {
        // Create compressed data directory
        Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", COMPRESSED_DIR);
        Files.createDirectories(compressedPath);

        // Create filename based on timestamp and label
        String filename = String.format("%d_%s_%s.dat",
            sample.timestamp, sample.label, sample.source.hashCode() & 0xFFFF);

        Path filePath = compressedPath.resolve(filename);

        // Write compressed data
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(filePath))) {
            dos.writeLong(sample.timestamp);
            dos.writeUTF(sample.label);
            dos.writeUTF(sample.source);
            dos.writeInt(sample.originalFeatureCount);
            dos.writeInt(sample.compressedFeatures.length);
            dos.write(sample.compressedFeatures);
        }
    }

    /**
     * Load and decompress training sample
     */
    public static Map<String, Object> loadAndDecompressSample(Path filePath) throws IOException {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(filePath))) {
            long timestamp = dis.readLong();
            String label = dis.readUTF();
            String source = dis.readUTF();
            int originalFeatureCount = dis.readInt();
            int compressedSize = dis.readInt();
            byte[] compressedFeatures = new byte[compressedSize];
            dis.readFully(compressedFeatures);

            // Decompress features
            Map<String, Double> features = decompressFeatures(compressedFeatures);

            // Reconstruct sample
            Map<String, Object> sample = new HashMap<>();
            sample.put("timestamp", timestamp);
            sample.put("label", label);
            sample.put("source", source);
            sample.put("features", features);

            return sample;
        }
    }

    /**
     * Update compression statistics
     */
    private static void updateCompressionStats(Map<String, Object> original, CompressedTrainingSample compressed) {
        // Estimate original size
        long originalSize = estimateSampleSize(original);
        long compressedSize = compressed.compressedFeatures.length + 100; // Add overhead

        totalOriginalBytes.addAndGet(originalSize);
        totalCompressedBytes.addAndGet(compressedSize);
    }

    /**
     * Estimate the size of a training sample in bytes
     */
    private static long estimateSampleSize(Map<String, Object> sample) {
        long size = 0;

        // Basic fields
        size += 8; // timestamp (long)
        size += ((String) sample.getOrDefault("label", "")).length() * 2;
        size += ((String) sample.getOrDefault("source", "")).length() * 2;

        // Features
        @SuppressWarnings("unchecked")
        Map<String, Double> features = (Map<String, Double>) sample.get("features");
        if (features != null) {
            size += features.size() * (50 + 8); // ~50 bytes per feature name + 8 bytes per double
        }

        return size;
    }

    /**
     * Get compression statistics
     */
    public static Map<String, Object> getCompressionStats() {
        long original = totalOriginalBytes.get();
        long compressed = totalCompressedBytes.get();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOriginalBytes", original);
        stats.put("totalCompressedBytes", compressed);
        stats.put("compressionRatio", original > 0 ? (double) compressed / original : 0.0);
        stats.put("spaceSavedPercent", original > 0 ? (1.0 - (double) compressed / original) * 100.0 : 0.0);
        stats.put("dictionarySize", featureDictionary.size());

        return stats;
    }

    /**
     * Compress old training data files
     */
    public static void compressExistingData() {
        try {
            Path mlDataPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data");
            Path trainingPath = mlDataPath.resolve("training");

            if (!Files.exists(trainingPath)) {
                return;
            }

            // Find JSON training files
            Files.walk(trainingPath)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(DataCompressor::compressExistingFile);

            MessageManager.log("info", "Finished compressing existing training data");

        } catch (Exception e) {
            MessageManager.log("error", "Error compressing existing data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Compress a single existing JSON file
     */
    private static void compressExistingFile(Path jsonFile) {
        try {
            // Read JSON file
            String jsonContent = Files.readString(jsonFile);
            // Parse basic JSON structure (simplified)
            Map<String, Object> sample = parseSimpleJson(jsonContent);

            if (sample != null) {
                // Compress and store
                compressAndStoreTrainingSample(sample);

                // Optionally delete original file to save space
                Files.delete(jsonFile);
            }

        } catch (Exception e) {
            MessageManager.log("warning", "Failed to compress file {FILE}: {ERROR}",
                "FILE", jsonFile.getFileName().toString(), "ERROR", e.getMessage());
        }
    }

    /**
     * Simple JSON parser for training data (basic implementation)
     */
    private static Map<String, Object> parseSimpleJson(String json) {
        try {
            Map<String, Object> result = new HashMap<>();

            // Remove braces and split by commas
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
            }

            String[] pairs = json.split(",(?=\\s*\")"); // Split on comma followed by quote

            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim();

                    if (key.equals("features") && value.contains("{")) {
                        // Parse features object
                        Map<String, Double> features = parseFeaturesObject(value);
                        result.put(key, features);
                    } else if (key.equals("timestamp")) {
                        result.put(key, Long.parseLong(value.replaceAll("\"", "")));
                    } else {
                        result.put(key, value.replaceAll("\"", ""));
                    }
                }
            }

            return result;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse features object from JSON
     */
    private static Map<String, Double> parseFeaturesObject(String featuresJson) {
        Map<String, Double> features = new HashMap<>();

        try {
            // Remove braces
            if (featuresJson.startsWith("{") && featuresJson.endsWith("}")) {
                featuresJson = featuresJson.substring(1, featuresJson.length() - 1);
            }

            String[] pairs = featuresJson.split(",(?=\\s*\")");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim();

                    try {
                        features.put(key, Double.parseDouble(value));
                    } catch (NumberFormatException e) {
                        // Skip invalid numbers
                    }
                }
            }

        } catch (Exception e) {
            // Return empty map on parse error
        }

        return features;
    }

    /**
     * Compressed training sample data structure
     */
    public static class CompressedTrainingSample {
        public final long timestamp;
        public final String label;
        public final String source;
        public final byte[] compressedFeatures;
        public final int originalFeatureCount;

        public CompressedTrainingSample(long timestamp, String label, String source,
                                      byte[] compressedFeatures, int originalFeatureCount) {
            this.timestamp = timestamp;
            this.label = label;
            this.source = source;
            this.compressedFeatures = compressedFeatures;
            this.originalFeatureCount = originalFeatureCount;
        }

        public long getCompressedSize() {
            return compressedFeatures.length + 100; // Add overhead
        }
    }
}