package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Automatic ML trainer that generates training data and learns from existing patterns.
 * Features:
 * - Synthetic data generation based on behavior patterns
 * - Automatic pattern discovery and learning
 * - Self-supervised learning from unlabeled data
 * - Continuous model improvement
 *
 * @author OverWatch Team
 * @version 1.1.0
 * @since 1.8.5
 */
public class AutoTrainer {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Pattern learning - thread-safe maps
    private final Map<String, BehaviorPattern> learnedPatterns = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerBehaviorProfile> playerProfiles = new ConcurrentHashMap<>();

    // Data generation - thread-safe queue and distributions
    private final Map<String, FeatureDistribution> featureDistributions = new ConcurrentHashMap<>();
    private final Queue<UnlabeledSample> unlabeledData = new ConcurrentLinkedQueue<>();
    private final AtomicLong syntheticSamplesGenerated = new AtomicLong(0);

    // Learning parameters
    private volatile double confidenceThreshold = 0.8;
    private volatile int minPatternSamples = 50;
    private volatile int maxSyntheticSamplesPerPattern = 1000;
    private volatile boolean autoGenerateEnabled = true;

    // Training scheduler
    private volatile long lastAutoTraining = System.currentTimeMillis();
    private volatile long autoTrainingInterval = 30 * 60 * 1000; // 30 minutes

    public AutoTrainer() {
        initializePatternLearning();
        startAutoLearningLoop();
    }

    /**
     * Initialize pattern learning system
     */
    private void initializePatternLearning() {
        // Initialize common behavior patterns
        learnedPatterns.put("normal_mining", new BehaviorPattern("normal_mining"));
        learnedPatterns.put("efficient_mining", new BehaviorPattern("efficient_mining"));
        learnedPatterns.put("random_mining", new BehaviorPattern("random_mining"));
        learnedPatterns.put("tunnel_mining", new BehaviorPattern("tunnel_mining"));
        learnedPatterns.put("xray_suspicious", new BehaviorPattern("xray_suspicious"));

        MessageManager.log("info", "AutoTrainer initialized with {COUNT} behavior patterns",
            "COUNT", String.valueOf(learnedPatterns.size()));
    }

    /**
     * Start automatic learning loop
     */
    private void startAutoLearningLoop() {
        // Schedule periodic auto-training using plugin scheduler
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (autoGenerateEnabled && shouldPerformAutoTraining()) {
                    performAutoTraining();
                }
            } catch (Exception e) {
                MessageManager.log("error", "Error in auto-training loop: {ERROR}", "ERROR", e.getMessage());
            }
        }, 1200L, 1200L); // Check every minute (1200 ticks)

        MessageManager.log("info", "Auto-training loop started");
    }

    /**
     * Check if auto-training should be performed
     */
    private boolean shouldPerformAutoTraining() {
        long timeSinceLastTraining = System.currentTimeMillis() - lastAutoTraining;
        // Require at least 50 samples instead of 100 for more frequent learning during startup
        return timeSinceLastTraining >= autoTrainingInterval && unlabeledData.size() >= 50;
    }

    /**
     * Perform automatic training cycle
     */
    private void performAutoTraining() {
        lastAutoTraining = System.currentTimeMillis();

        MessageManager.log("info", "Starting auto-training cycle with {COUNT} unlabeled samples",
            "COUNT", String.valueOf(unlabeledData.size()));

        // Step 1: Learn from unlabeled data
        learnFromUnlabeledData();

        // Step 2: Update feature distributions
        updateFeatureDistributions();

        // Step 3: Generate synthetic training data
        generateSyntheticData();

        // Step 4: Improve existing patterns
        refinePatterns();

        MessageManager.log("info", "Auto-training cycle completed. Total synthetic samples: {COUNT}",
            "COUNT", String.valueOf(syntheticSamplesGenerated.get()));
    }

    /**
     * Learn behavior patterns from unlabeled player data
     */
    private void learnFromUnlabeledData() {
        List<UnlabeledSample> samples = new ArrayList<>();
        int processed = 0;

        // Collect samples for processing - ConcurrentLinkedQueue is safe
        while (!unlabeledData.isEmpty() && processed < 1000) { // Increased batch size
            UnlabeledSample sample = unlabeledData.poll();
            if (sample != null) {
                samples.add(sample);
                processed++;
            }
        }

        // Process samples and update patterns
        for (UnlabeledSample sample : samples) {
            classifyAndLearnFromSample(sample);
        }

        if (processed > 0) {
            MessageManager.log("info", "Processed {COUNT} unlabeled samples for pattern learning",
                "COUNT", String.valueOf(processed));
        }
    }

    /**
     * Classify sample and learn from it
     */
    private void classifyAndLearnFromSample(UnlabeledSample sample) {
        // Find best matching pattern
        String bestPattern = findBestMatchingPattern(sample);
        if (bestPattern != null) {
            BehaviorPattern pattern = learnedPatterns.get(bestPattern);

            // Add sample to pattern (now thread-safe)
            pattern.addSample(sample.features, sample.confidence);

            // Update player profile
            updatePlayerProfile(sample.playerId, bestPattern, sample.features);
        }
    }

    /**
     * Find best matching behavior pattern for a sample
     */
    private String findBestMatchingPattern(UnlabeledSample sample) {
        String bestPattern = null;
        double bestScore = 0.0;

        for (Map.Entry<String, BehaviorPattern> entry : learnedPatterns.entrySet()) {
            double score = entry.getValue().calculateMatchScore(sample.features);
            if (score > bestScore && score > confidenceThreshold) {
                bestScore = score;
                bestPattern = entry.getKey();
            }
        }

        return bestPattern;
    }

    /**
     * Update player behavior profile
     */
    private void updatePlayerProfile(UUID playerId, String pattern, Map<String, Double> features) {
        PlayerBehaviorProfile profile = playerProfiles.computeIfAbsent(playerId,
            k -> new PlayerBehaviorProfile(playerId));

        profile.updatePattern(pattern, features);
        profile.updateLastActivity();
    }

    /**
     * Update feature distributions for synthetic data generation
     */
    private void updateFeatureDistributions() {
        for (BehaviorPattern pattern : learnedPatterns.values()) {
            Map<String, Double> patternFeatures = pattern.getAverageFeatures();

            for (Map.Entry<String, Double> entry : patternFeatures.entrySet()) {
                String featureName = entry.getKey();
                double value = entry.getValue();

                FeatureDistribution dist = featureDistributions.computeIfAbsent(featureName,
                    k -> new FeatureDistribution(featureName));
                dist.update(value, pattern.getSampleCount());
            }
        }
    }

    /**
     * Generate synthetic training data based on learned patterns
     */
    private void generateSyntheticData() {
        for (BehaviorPattern pattern : learnedPatterns.values()) {
            if (pattern.getSampleCount() >= minPatternSamples) {
                int samplesToGenerate = Math.min(
                    maxSyntheticSamplesPerPattern - pattern.getSyntheticSamplesGenerated(),
                    100 // Generate max 100 samples per pattern per cycle
                );

                for (int i = 0; i < samplesToGenerate; i++) {
                    Map<String, Double> syntheticFeatures = pattern.generateSyntheticSample();
                    if (syntheticFeatures != null) {
                        // Add synthetic sample to training data
                        addSyntheticTrainingSample(pattern.getName(), syntheticFeatures);
                        pattern.incrementSyntheticSamples();
                        syntheticSamplesGenerated.incrementAndGet();
                    }
                }
            }
        }
    }

    /**
     * Add synthetic sample to ML training data
     */
    private void addSyntheticTrainingSample(String patternName, Map<String, Double> features) {
        try {
            // Determine if this is normal or suspicious behavior
            boolean isCheater = patternName.contains("xray") || patternName.contains("suspicious");

            // Create training sample
            Map<String, Object> trainingSample = new HashMap<>();
            trainingSample.put("features", new HashMap<>(features));
            trainingSample.put("label", isCheater ? "cheater" : "normal");
            trainingSample.put("source", "synthetic_" + patternName);
            trainingSample.put("timestamp", System.currentTimeMillis());

            // Save to ML data manager
            MLDataManager.saveSyntheticTrainingSample(trainingSample);

        } catch (Exception e) {
            MessageManager.log("warning", "Failed to save synthetic training sample: {ERROR}",
                "ERROR", e.getMessage());
        }
    }

    /**
     * Refine existing patterns based on new data
     */
    private void refinePatterns() {
        for (BehaviorPattern pattern : learnedPatterns.values()) {
            pattern.refinePattern();
        }
    }

    /**
     * Add unlabeled sample for learning
     */
    public void addUnlabeledSample(UUID playerId, Map<String, Double> features, double confidence) {
        if (unlabeledData.size() < 20000) { // Increased queue limit
            unlabeledData.add(new UnlabeledSample(playerId, features, confidence));
        }
    }

    /**
     * Get learning statistics
     */
    public Map<String, Object> getLearningStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("patternsLearned", learnedPatterns.size());
        stats.put("unlabeledSamplesQueued", unlabeledData.size());
        stats.put("syntheticSamplesGenerated", syntheticSamplesGenerated.get());
        stats.put("playerProfiles", playerProfiles.size());
        stats.put("featureDistributions", featureDistributions.size());
        stats.put("autoGenerateEnabled", autoGenerateEnabled);
        stats.put("confidenceThreshold", confidenceThreshold);

        return stats;
    }

    /**
     * Enable/disable automatic data generation
     */
    public void setAutoGenerateEnabled(boolean enabled) {
        this.autoGenerateEnabled = enabled;
        MessageManager.log("info", "Auto-training " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Set confidence threshold for pattern matching
     */
    public void setConfidenceThreshold(double threshold) {
        this.confidenceThreshold = Math.max(0.1, Math.min(1.0, threshold));
    }

    // ===== INNER CLASSES =====

    /**
     * Represents a behavior pattern - Thread-safe implementation
     */
    private static class BehaviorPattern {
        private final String name;
        private final List<Map<String, Double>> samples = new ArrayList<>();
        private final Map<String, Double> featureSums = new HashMap<>();
        private final Map<String, Double> featureMeans = new HashMap<>();
        private final Map<String, Double> featureVariances = new HashMap<>();
        private int syntheticSamplesGenerated = 0;

        public BehaviorPattern(String name) {
            this.name = name;
        }

        public synchronized void addSample(Map<String, Double> features, double confidence) {
            samples.add(new HashMap<>(features));

            // Update running statistics
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String feature = entry.getKey();
                double value = entry.getValue();

                featureSums.put(feature, featureSums.getOrDefault(feature, 0.0) + value);
            }
            
            // Limit stored samples to prevent memory leaks
            if (samples.size() > 2000) {
                Map<String, Double> removed = samples.remove(0);
                for (Map.Entry<String, Double> entry : removed.entrySet()) {
                    String feature = entry.getKey();
                    double value = entry.getValue();
                    featureSums.put(feature, Math.max(0, featureSums.getOrDefault(feature, 0.0) - value));
                }
            }
        }

        public synchronized double calculateMatchScore(Map<String, Double> features) {
            if (samples.isEmpty() || featureMeans.isEmpty()) {
                // Initial update if empty but has samples
                if (!samples.isEmpty()) updateStatistics();
                else return 0.0;
            }

            double totalScore = 0.0;
            int featureMatches = 0;

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String feature = entry.getKey();
                double value = entry.getValue();
                Double mean = featureMeans.get(feature);

                if (mean != null) {
                    Double variance = featureVariances.get(feature);
                    double stdDev = variance != null ? Math.sqrt(variance) : 1.0;

                    // Calculate z-score
                    double zScore = Math.abs(value - mean) / Math.max(stdDev, 0.001);
                    double score = Math.max(0, 1.0 - zScore / 3.0); // Score between 0-1

                    totalScore += score;
                    featureMatches++;
                }
            }

            return featureMatches > 0 ? totalScore / featureMatches : 0.0;
        }

        public synchronized Map<String, Double> getAverageFeatures() {
            updateStatistics();
            return new HashMap<>(featureMeans);
        }

        public synchronized Map<String, Double> generateSyntheticSample() {
            updateStatistics();

            if (featureMeans.isEmpty()) return null;

            Map<String, Double> synthetic = new HashMap<>();
            Random random = new Random();

            for (Map.Entry<String, Double> entry : featureMeans.entrySet()) {
                String feature = entry.getKey();
                double mean = entry.getValue();
                double variance = featureVariances.getOrDefault(feature, 1.0);
                double stdDev = Math.sqrt(variance);

                // Generate value using normal distribution around mean
                double syntheticValue = mean + random.nextGaussian() * stdDev * 0.4; // Slightly tighter variance

                // Ensure reasonable bounds
                syntheticValue = Math.max(0, syntheticValue);

                synthetic.put(feature, syntheticValue);
            }

            return synthetic;
        }

        private void updateStatistics() {
            int sampleCount = samples.size();
            if (sampleCount == 0) return;

            // Calculate means
            for (Map.Entry<String, Double> entry : featureSums.entrySet()) {
                featureMeans.put(entry.getKey(), entry.getValue() / sampleCount);
            }

            // Calculate variances
            for (Map.Entry<String, Double> entry : featureMeans.entrySet()) {
                String feature = entry.getKey();
                double mean = entry.getValue();
                double sumSquaredDiffs = 0.0;

                for (Map<String, Double> sample : samples) {
                    Double value = sample.get(feature);
                    if (value != null) {
                        double diff = value - mean;
                        sumSquaredDiffs += diff * diff;
                    }
                }

                double variance = sampleCount > 1 ? sumSquaredDiffs / (sampleCount - 1) : 0.0;
                featureVariances.put(feature, Math.max(variance, 0.0001)); // Minimum variance
            }
        }

        public synchronized void refinePattern() {
            updateStatistics();
        }

        public String getName() { return name; }
        public synchronized int getSampleCount() { return samples.size(); }
        public synchronized int getSyntheticSamplesGenerated() { return syntheticSamplesGenerated; }
        public synchronized void incrementSyntheticSamples() { syntheticSamplesGenerated++; }
    }

    /**
     * Player behavior profile - Thread-safe
     */
    private static class PlayerBehaviorProfile {
        private final UUID playerId;
        private final Map<String, Integer> patternCounts = new ConcurrentHashMap<>();
        private final Map<String, Double> averageFeatures = new ConcurrentHashMap<>();
        private volatile long lastActivity = System.currentTimeMillis();

        public PlayerBehaviorProfile(UUID playerId) {
            this.playerId = playerId;
        }

        public synchronized void updatePattern(String pattern, Map<String, Double> features) {
            patternCounts.put(pattern, patternCounts.getOrDefault(pattern, 0) + 1);

            // Update average features
            int totalPatterns = patternCounts.values().stream().mapToInt(Integer::intValue).sum();
            
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String feature = entry.getKey();
                double value = entry.getValue();

                double currentAvg = averageFeatures.getOrDefault(feature, 0.0);
                averageFeatures.put(feature, (currentAvg * (totalPatterns - 1) + value) / totalPatterns);
            }
        }

        public void updateLastActivity() {
            lastActivity = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public long getLastActivity() { return lastActivity; }
    }

    /**
     * Feature distribution for synthetic data generation - Thread-safe
     */
    private static class FeatureDistribution {
        private final String featureName;
        private double sum = 0.0;
        private double sumSquares = 0.0;
        private int count = 0;

        public FeatureDistribution(String featureName) {
            this.featureName = featureName;
        }

        public synchronized void update(double value, int weight) {
            if (weight <= 0) weight = 1;
            sum += value * weight;
            sumSquares += value * value * weight;
            count += weight;
        }

        public synchronized double getMean() {
            return count > 0 ? sum / count : 0.0;
        }

        public synchronized double getVariance() {
            if (count <= 1) return 1.0;
            double mean = getMean();
            double variance = (sumSquares / count) - (mean * mean);
            return Math.max(0.0001, variance);
        }
    }

    /**
     * Unlabeled training sample
     */
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
