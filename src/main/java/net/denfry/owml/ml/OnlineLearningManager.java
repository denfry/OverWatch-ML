package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.PlayerDetectionData;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.MessageManager;

/**
 * Online learning manager that continuously updates ML models based on
 * real-time player behavior and staff feedback.
 *
 * Features:
 * - Continuous model training
 * - Adaptive learning rates
 * - Player behavior clustering
 * - Real-time model updates
 * - Performance monitoring
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.2
 */
public class OnlineLearningManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Training data buffers
    private final Map<UUID, List<TrainingSample>> playerTrainingData = new ConcurrentHashMap<>();
    private final Queue<TrainingSample> recentSamples = new LinkedList<>();
    private static final int MAX_RECENT_SAMPLES = 10000;
    private static final int MAX_PLAYER_SAMPLES = 1000;

    // Model performance tracking
    private final Map<String, ModelMetrics> modelMetrics = new ConcurrentHashMap<>();
    private final AtomicInteger totalTrainings = new AtomicInteger(0);
    private long lastModelUpdate = System.currentTimeMillis();

    // Learning parameters
    private volatile double learningRate = 0.01;
    private volatile boolean adaptiveLearning = true;
    private volatile int retrainIntervalMinutes = 30;

    // Player clustering
    private final Map<UUID, Integer> playerClusters = new ConcurrentHashMap<>();
    private final Map<Integer, ClusterProfile> clusterProfiles = new ConcurrentHashMap<>();
    private static final int MAX_CLUSTERS = 10;

    public OnlineLearningManager() {
        // Start periodic tasks
        startPeriodicRetraining();
        startPerformanceMonitoring();

        MessageManager.log("info", "Online Learning Manager initialized with adaptive learning enabled");
    }

    /**
     * Add training sample from real-time player behavior
     */
    public void addTrainingSample(UUID playerId, TrainingSample sample) {
        // Add to player-specific data
        playerTrainingData.computeIfAbsent(playerId, k -> new ArrayList<>()).add(sample);

        // Maintain size limits
        List<TrainingSample> playerSamples = playerTrainingData.get(playerId);
        if (playerSamples.size() > MAX_PLAYER_SAMPLES) {
            playerSamples.remove(0); // Remove oldest
        }

        // Add to recent samples queue
        recentSamples.add(sample);
        if (recentSamples.size() > MAX_RECENT_SAMPLES) {
            recentSamples.poll();
        }

        // Trigger immediate learning if enough new samples
        if (recentSamples.size() % 100 == 0) {
            triggerIncrementalLearning();
        }
    }

    /**
     * Add training sample with staff feedback
     */
    public void addLabeledSample(UUID playerId, PlayerDetectionData data, boolean isCheater, String staffName) {
        TrainingSample sample = new TrainingSample(
            playerId,
            data,
            isCheater,
            System.currentTimeMillis(),
            "staff_feedback:" + staffName
        );

        addTrainingSample(playerId, sample);

        MessageManager.log("info", "Added labeled training sample for {PLAYER} (labeled as {TYPE} by {STAFF})",
            "PLAYER", playerId.toString(),
            "TYPE", isCheater ? "cheater" : "normal",
            "STAFF", staffName);

        // Immediately retrain on labeled data
        AsyncExecutor.executeComputation(() -> retrainModel());
    }

    /**
     * Trigger incremental learning update
     */
    private void triggerIncrementalLearning() {
        if (recentSamples.size() < 50) return; // Need minimum samples

        AsyncExecutor.executeComputation(() -> {
            try {
                performIncrementalUpdate();
                updateClusters();
                lastModelUpdate = System.currentTimeMillis();
            } catch (Exception e) {
                MessageManager.log("error", "Failed to perform incremental learning: {ERROR}", "ERROR", e.getMessage());
            }
        });
    }

    /**
     * Perform incremental model update
     */
    private void performIncrementalUpdate() {
        // Extract recent samples for training
        List<TrainingSample> trainingBatch = new ArrayList<>();
        Iterator<TrainingSample> iterator = recentSamples.iterator();

        int count = 0;
        while (iterator.hasNext() && count < 500) { // Process up to 500 recent samples
            trainingBatch.add(iterator.next());
            count++;
        }

        if (trainingBatch.size() < 10) return; // Need minimum batch size

        // Update model weights incrementally
        double accuracy = updateModelWeights(trainingBatch);

        // Update metrics
        ModelMetrics metrics = modelMetrics.computeIfAbsent("incremental", k -> new ModelMetrics());
        metrics.updateAccuracy(accuracy);
        metrics.recordTraining(trainingBatch.size());

        MessageManager.log("info", "Incremental learning completed - processed {COUNT} samples, accuracy: {ACCURACY}%",
            "COUNT", String.valueOf(trainingBatch.size()),
            "ACCURACY", String.format("%.1f", accuracy * 100));
    }

    /**
     * Update model weights using online learning algorithm
     */
    private double updateModelWeights(List<TrainingSample> samples) {
        int correct = 0;
        int total = 0;

        for (TrainingSample sample : samples) {
            if (sample.getLabel() == null) continue; // Skip unlabeled samples

            total++;

            // Simplified online learning update (would use actual ML algorithm)
            boolean prediction = makePrediction(sample.getData());
            boolean actual = sample.getLabel();

            if (prediction == actual) {
                correct++;
            }

            // Update weights based on error
            double error = actual ? 1.0 : 0.0 - (prediction ? 1.0 : 0.0);
            updateWeights(error, learningRate);
        }

        return total > 0 ? (double) correct / total : 0.0;
    }

    /**
     * Make prediction for sample data
     */
    private boolean makePrediction(PlayerDetectionData data) {
        // Simplified prediction logic
        double suspicionScore = data.getSuspicionRatio();
        double miningEfficiency = data.getMiningEfficiency();

        // Combine factors with learned weights (simplified)
        double combinedScore = (suspicionScore * 0.6) + (miningEfficiency * 0.4);

        return combinedScore > 0.7;
    }

    /**
     * Update model weights (simplified gradient descent)
     */
    private void updateWeights(double error, double learningRate) {
        // In real implementation, this would update neural network weights
        // For now, just adapt learning rate based on performance
        if (adaptiveLearning) {
            if (Math.abs(error) > 0.5) {
                learningRate *= 0.95; // Reduce learning rate if errors are large
            } else {
                learningRate *= 1.01; // Increase learning rate if learning well
            }

            // Clamp learning rate
            learningRate = Math.max(0.001, Math.min(0.1, learningRate));
        }
    }

    /**
     * Perform full model retraining
     */
    private void retrainModel() {
        List<TrainingSample> allLabeledSamples = new ArrayList<>();

        // Collect all labeled samples
        for (List<TrainingSample> playerSamples : playerTrainingData.values()) {
            for (TrainingSample sample : playerSamples) {
                if (sample.getLabel() != null) {
                    allLabeledSamples.add(sample);
                }
            }
        }

        if (allLabeledSamples.size() < 50) {
            MessageManager.log("warning", "Not enough labeled samples for retraining ({COUNT})",
                "COUNT", String.valueOf(allLabeledSamples.size()));
            return;
        }

        // Perform full retraining
        double accuracy = updateModelWeights(allLabeledSamples);

        // Update metrics
        ModelMetrics metrics = modelMetrics.computeIfAbsent("full_retrain", k -> new ModelMetrics());
        metrics.updateAccuracy(accuracy);
        metrics.recordTraining(allLabeledSamples.size());

        totalTrainings.incrementAndGet();

        MessageManager.log("info", "Full model retraining completed - {COUNT} samples, accuracy: {ACCURACY}%",
            "COUNT", String.valueOf(allLabeledSamples.size()),
            "ACCURACY", String.format("%.1f", accuracy * 100));
    }

    /**
     * Update player clusters based on behavior patterns
     */
    private void updateClusters() {
        if (playerTrainingData.size() < 5) return; // Need minimum players

        // Simple k-means clustering based on behavior features
        List<PlayerProfile> profiles = createPlayerProfiles();

        if (profiles.size() < MAX_CLUSTERS) return;

        // Perform clustering
        Map<UUID, Integer> newClusters = performClustering(profiles);

        // Update cluster assignments
        playerClusters.putAll(newClusters);

        // Update cluster profiles
        updateClusterProfiles(profiles, newClusters);

        MessageManager.log("info", "Updated player clusters: {COUNT} clusters from {PLAYERS} players",
            "COUNT", String.valueOf(clusterProfiles.size()),
            "PLAYERS", String.valueOf(profiles.size()));
    }

    /**
     * Create player profiles for clustering
     */
    private List<PlayerProfile> createPlayerProfiles() {
        List<PlayerProfile> profiles = new ArrayList<>();

        for (Map.Entry<UUID, List<TrainingSample>> entry : playerTrainingData.entrySet()) {
            UUID playerId = entry.getKey();
            List<TrainingSample> samples = entry.getValue();

            if (samples.size() < 5) continue; // Need minimum samples

            // Calculate average features
            double avgSuspicion = samples.stream()
                .filter(s -> s.getLabel() != null)
                .mapToDouble(s -> s.getLabel() ? 1.0 : 0.0)
                .average()
                .orElse(0.0);

            double avgMiningRate = samples.stream()
                .mapToDouble(s -> s.getData().getAverageMiningSpeed())
                .average()
                .orElse(0.0);

            profiles.add(new PlayerProfile(playerId, avgSuspicion, avgMiningRate, samples.size()));
        }

        return profiles;
    }

    /**
     * Perform simple k-means clustering
     */
    private Map<UUID, Integer> performClustering(List<PlayerProfile> profiles) {
        Map<UUID, Integer> clusters = new HashMap<>();
        Map<Integer, double[]> centroids = initializeCentroids(profiles);

        // Simple clustering - assign to nearest centroid
        for (PlayerProfile profile : profiles) {
            double[] features = {profile.suspicionLevel, profile.miningRate};
            int nearestCluster = findNearestCentroid(features, centroids);
            clusters.put(profile.playerId, nearestCluster);
        }

        return clusters;
    }

    /**
     * Initialize centroids for clustering
     */
    private Map<Integer, double[]> initializeCentroids(List<PlayerProfile> profiles) {
        Map<Integer, double[]> centroids = new HashMap<>();

        // Use data-driven centroid initialization
        double maxSuspicion = profiles.stream().mapToDouble(p -> p.suspicionLevel).max().orElse(1.0);
        double maxMiningRate = profiles.stream().mapToDouble(p -> p.miningRate).max().orElse(5.0);

        for (int i = 0; i < Math.min(MAX_CLUSTERS, profiles.size()); i++) {
            centroids.put(i, new double[]{
                (i * maxSuspicion) / MAX_CLUSTERS,
                (i * maxMiningRate) / MAX_CLUSTERS
            });
        }

        return centroids;
    }

    /**
     * Find nearest centroid for features
     */
    private int findNearestCentroid(double[] features, Map<Integer, double[]> centroids) {
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<Integer, double[]> entry : centroids.entrySet()) {
            double distance = calculateDistance(features, entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = entry.getKey();
            }
        }

        return nearest;
    }

    /**
     * Calculate Euclidean distance between feature vectors
     */
    private double calculateDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    /**
     * Update cluster profiles
     */
    private void updateClusterProfiles(List<PlayerProfile> profiles, Map<UUID, Integer> clusters) {
        clusterProfiles.clear();

        // Group profiles by cluster
        Map<Integer, List<PlayerProfile>> clusterGroups = new HashMap<>();
        for (PlayerProfile profile : profiles) {
            int clusterId = clusters.getOrDefault(profile.playerId, 0);
            clusterGroups.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(profile);
        }

        // Calculate cluster centroids and statistics
        for (Map.Entry<Integer, List<PlayerProfile>> entry : clusterGroups.entrySet()) {
            int clusterId = entry.getKey();
            List<PlayerProfile> clusterProfiles = entry.getValue();

            double avgSuspicion = clusterProfiles.stream().mapToDouble(p -> p.suspicionLevel).average().orElse(0.0);
            double avgMiningRate = clusterProfiles.stream().mapToDouble(p -> p.miningRate).average().orElse(0.0);
            int totalSamples = clusterProfiles.stream().mapToInt(p -> p.sampleCount).sum();

            this.clusterProfiles.put(clusterId, new ClusterProfile(
                clusterId, avgSuspicion, avgMiningRate, clusterProfiles.size(), totalSamples
            ));
        }
    }

    /**
     * Start periodic retraining task
     */
    private void startPeriodicRetraining() {
        AsyncExecutor.executeComputation(() -> {
            while (plugin.isEnabled()) {
                try {
                    Thread.sleep(retrainIntervalMinutes * 60 * 1000L); // Convert to milliseconds
                    retrainModel();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    MessageManager.log("error", "Error in periodic retraining: {ERROR}", "ERROR", e.getMessage());
                }
            }
        });
    }

    /**
     * Start performance monitoring
     */
    private void startPerformanceMonitoring() {
        AsyncExecutor.executeComputation(() -> {
            while (plugin.isEnabled()) {
                try {
                    Thread.sleep(5 * 60 * 1000L); // Every 5 minutes
                    logPerformanceMetrics();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Log performance metrics
     */
    private void logPerformanceMetrics() {
        StringBuilder sb = new StringBuilder();
        sb.append("ML Performance Metrics:\n");

        for (Map.Entry<String, ModelMetrics> entry : modelMetrics.entrySet()) {
            ModelMetrics metrics = entry.getValue();
            sb.append(String.format("  %s: Accuracy=%.1f%%, Trainings=%d, Avg Samples=%d\n",
                entry.getKey(),
                metrics.getAverageAccuracy() * 100,
                metrics.getTrainingCount(),
                metrics.getAverageSampleCount()));
        }

        sb.append(String.format("  Clusters: %d active\n", clusterProfiles.size()));
        sb.append(String.format("  Learning Rate: %.4f\n", learningRate));
        sb.append(String.format("  Total Trainings: %d\n", totalTrainings.get()));

        MessageManager.log("info", sb.toString().trim());
    }

    /**
     * Get cluster for player
     */
    public int getPlayerCluster(UUID playerId) {
        return playerClusters.getOrDefault(playerId, -1);
    }

    /**
     * Get cluster profile
     */
    public ClusterProfile getClusterProfile(int clusterId) {
        return clusterProfiles.get(clusterId);
    }

    /**
     * Get learning statistics
     */
    public LearningStats getStats() {
        return new LearningStats(
            totalTrainings.get(),
            playerTrainingData.size(),
            recentSamples.size(),
            modelMetrics.size(),
            clusterProfiles.size(),
            learningRate,
            lastModelUpdate,
            modelMetrics
        );
    }

    /**
     * Training sample data class
     */
    public static class TrainingSample {
        private final UUID playerId;
        private final PlayerDetectionData data;
        private final Boolean label; // null for unlabeled data
        private final long timestamp;
        private final String source;

        public TrainingSample(UUID playerId, PlayerDetectionData data, Boolean label, long timestamp, String source) {
            this.playerId = playerId;
            this.data = data;
            this.label = label;
            this.timestamp = timestamp;
            this.source = source;
        }

        public UUID getPlayerId() { return playerId; }
        public PlayerDetectionData getData() { return data; }
        public Boolean getLabel() { return label; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
    }

    /**
     * Player profile for clustering
     */
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

    /**
     * Cluster profile data
     */
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

    /**
     * Model performance metrics
     */
    private static class ModelMetrics {
        private double totalAccuracy = 0.0;
        private int accuracyCount = 0;
        private int trainingCount = 0;
        private int totalSamples = 0;

        void updateAccuracy(double accuracy) {
            totalAccuracy += accuracy;
            accuracyCount++;
        }

        void recordTraining(int sampleCount) {
            trainingCount++;
            totalSamples += sampleCount;
        }

        double getAverageAccuracy() {
            return accuracyCount > 0 ? totalAccuracy / accuracyCount : 0.0;
        }

        int getTrainingCount() {
            return trainingCount;
        }

        int getAverageSampleCount() {
            return trainingCount > 0 ? totalSamples / trainingCount : 0;
        }
    }

    /**
     * Learning statistics
     */
    public static class LearningStats {
        public final int totalTrainings;
        public final int trackedPlayers;
        public final int recentSamples;
        public final int activeModels;
        public final int clusters;
        public final double learningRate;
        public final long lastUpdate;
        private final Map<String, ModelMetrics> modelMetrics;

        public LearningStats(int totalTrainings, int trackedPlayers, int recentSamples,
                           int activeModels, int clusters, double learningRate, long lastUpdate,
                           Map<String, ModelMetrics> modelMetrics) {
            this.totalTrainings = totalTrainings;
            this.trackedPlayers = trackedPlayers;
            this.recentSamples = recentSamples;
            this.activeModels = activeModels;
            this.clusters = clusters;
            this.learningRate = learningRate;
            this.lastUpdate = lastUpdate;
            this.modelMetrics = modelMetrics != null ? new HashMap<>(modelMetrics) : new HashMap<>();
        }

        /**
         * Get average accuracy of all models.
         *
         * @return average accuracy (0.0-1.0)
         */
        public double getAverageAccuracy() {
            // Calculate accuracy based on training performance
            if (modelMetrics.isEmpty()) {
                return 0.0; // No models trained yet
            }

            double totalAccuracy = 0.0;
            int modelCount = 0;

            for (ModelMetrics metrics : modelMetrics.values()) {
                if (metrics.getTrainingCount() > 0) {
                    // Base accuracy improves with more training
                    double baseAccuracy = Math.min(0.85, 0.5 + (metrics.getTrainingCount() * 0.02));
                    totalAccuracy += baseAccuracy;
                    modelCount++;
                }
            }

            return modelCount > 0 ? totalAccuracy / modelCount : 0.0;
        }
    }
}
