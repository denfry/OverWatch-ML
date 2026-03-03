package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.PlayerDetectionData;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced player clustering engine that groups players by behavior patterns.
 * Uses machine learning techniques to identify player archetypes and detect
 * anomalies within clusters.
 *
 * Features:
 * - Behavioral clustering
 * - Anomaly detection within clusters
 * - Predictive risk scoring
 * - Dynamic cluster evolution
 * - Cross-cluster analysis
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class PlayerClusteringEngine {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Clustering data
    private final Map<UUID, PlayerCluster> playerClusters = new ConcurrentHashMap<>();
    private final Map<Integer, ClusterProfile> clusterProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerFeatures> playerFeatures = new ConcurrentHashMap<>();

    // Clustering parameters
    private static final int MAX_CLUSTERS = 8;
    private static final double CLUSTER_SIMILARITY_THRESHOLD = 0.7;
    private static final int MIN_CLUSTER_SIZE = 3;
    private static final int FEATURE_DIMENSIONS = 6; // Number of behavioral features

    // Risk assessment
    private final Map<UUID, RiskAssessment> playerRisks = new ConcurrentHashMap<>();

    public PlayerClusteringEngine() {
        // Start periodic clustering updates
        startClusteringTask();

        MessageManager.log("info", "Player Clustering Engine initialized with {MAX} max clusters",
            "MAX", String.valueOf(MAX_CLUSTERS));
    }

    /**
     * Update player features for clustering
     */
    public void updatePlayerFeatures(UUID playerId, PlayerDetectionData data) {
        PlayerFeatures features = new PlayerFeatures(playerId);

        // Extract behavioral features
        features.addFeature("mining_efficiency", normalizeValue(data.getMiningEfficiency(), 0, 100));
        features.addFeature("suspicion_ratio", data.getSuspicionRatio());
        features.addFeature("mining_speed", normalizeValue(data.getAverageMiningSpeed(), 0, 10));
        features.addFeature("consistency", calculateConsistencyScore(data));
        features.addFeature("exploration_ratio", data.getRecentDetectionTrend());
        features.addFeature("session_length", normalizeValue(
            (System.currentTimeMillis() - data.getFirstSeen()) / (1000.0 * 60 * 60), 0, 24)); // Hours

        playerFeatures.put(playerId, features);

        // Trigger incremental clustering if we have enough players
        if (playerFeatures.size() >= MIN_CLUSTER_SIZE * 2) {
            AsyncExecutor.executeComputation(this::performIncrementalClustering);
        }
    }

    /**
     * Perform incremental clustering update
     */
    private void performIncrementalClustering() {
        try {
            List<PlayerFeatures> allFeatures = new ArrayList<>(playerFeatures.values());

            if (allFeatures.size() < MIN_CLUSTER_SIZE) return;

            // Perform k-means clustering
            Map<UUID, Integer> newClusters = performKMeansClustering(allFeatures);

            // Update cluster assignments
            for (Map.Entry<UUID, Integer> entry : newClusters.entrySet()) {
                UUID playerId = entry.getKey();
                int clusterId = entry.getValue();

                playerClusters.computeIfAbsent(playerId, k -> new PlayerCluster(playerId))
                    .setClusterId(clusterId);
            }

            // Update cluster profiles
            updateClusterProfiles(allFeatures, newClusters);

            // Assess risks for all players
            assessClusterRisks();

            MessageManager.log("info", "Clustering updated: {PLAYERS} players in {CLUSTERS} clusters",
                "PLAYERS", String.valueOf(allFeatures.size()),
                "CLUSTERS", String.valueOf(clusterProfiles.size()));

        } catch (Exception e) {
            MessageManager.log("error", "Error in incremental clustering: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Perform k-means clustering algorithm
     */
    private Map<UUID, Integer> performKMeansClustering(List<PlayerFeatures> players) {
        Map<UUID, Integer> clusters = new HashMap<>();

        // Initialize centroids using k-means++
        double[][] centroids = initializeCentroids(players, Math.min(MAX_CLUSTERS, players.size() / MIN_CLUSTER_SIZE));

        // Perform clustering iterations
        for (int iteration = 0; iteration < 10; iteration++) { // Max 10 iterations
            // Assign players to nearest centroids
            Map<Integer, List<PlayerFeatures>> clusterGroups = new HashMap<>();

            for (PlayerFeatures player : players) {
                int nearestCentroid = findNearestCentroid(player.getFeatureVector(), centroids);
                clusterGroups.computeIfAbsent(nearestCentroid, k -> new ArrayList<>()).add(player);
            }

            // Update centroids
            boolean centroidsChanged = updateCentroids(centroids, clusterGroups);

            // If centroids didn't change significantly, we're done
            if (!centroidsChanged && iteration > 2) {
                break;
            }
        }

        // Assign final clusters
        for (PlayerFeatures player : players) {
            int clusterId = findNearestCentroid(player.getFeatureVector(), centroids);
            clusters.put(player.getPlayerId(), clusterId);
        }

        return clusters;
    }

    /**
     * Initialize centroids using k-means++ algorithm
     */
    private double[][] initializeCentroids(List<PlayerFeatures> players, int k) {
        double[][] centroids = new double[k][FEATURE_DIMENSIONS];
        Random random = new Random();

        // First centroid: random player
        PlayerFeatures firstPlayer = players.get(random.nextInt(players.size()));
        System.arraycopy(firstPlayer.getFeatureVector(), 0, centroids[0], 0, FEATURE_DIMENSIONS);

        // Subsequent centroids: probability proportional to squared distance
        for (int i = 1; i < k; i++) {
            double[] distances = new double[players.size()];

            // Calculate distances to nearest existing centroid
            for (int j = 0; j < players.size(); j++) {
                double minDistance = Double.MAX_VALUE;
                for (int c = 0; c < i; c++) {
                    double distance = calculateDistance(players.get(j).getFeatureVector(), centroids[c]);
                    minDistance = Math.min(minDistance, distance);
                }
                distances[j] = minDistance * minDistance; // Square the distance
            }

            // Select next centroid based on probability
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

            System.arraycopy(players.get(selectedIndex).getFeatureVector(), 0, centroids[i], 0, FEATURE_DIMENSIONS);
        }

        return centroids;
    }

    /**
     * Find nearest centroid for a feature vector
     */
    private int findNearestCentroid(double[] features, double[][] centroids) {
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < centroids.length; i++) {
            double distance = calculateDistance(features, centroids[i]);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }

        return nearest;
    }

    /**
     * Update centroids based on cluster assignments
     */
    private boolean updateCentroids(double[][] centroids, Map<Integer, List<PlayerFeatures>> clusterGroups) {
        boolean changed = false;

        for (int i = 0; i < centroids.length; i++) {
            List<PlayerFeatures> cluster = clusterGroups.get(i);
            if (cluster == null || cluster.isEmpty()) continue;

            // Calculate new centroid (mean of all points in cluster)
            double[] newCentroid = new double[FEATURE_DIMENSIONS];
            for (PlayerFeatures player : cluster) {
                double[] features = player.getFeatureVector();
                for (int j = 0; j < FEATURE_DIMENSIONS; j++) {
                    newCentroid[j] += features[j];
                }
            }

            for (int j = 0; j < FEATURE_DIMENSIONS; j++) {
                newCentroid[j] /= cluster.size();
            }

            // Check if centroid changed significantly
            double distance = calculateDistance(centroids[i], newCentroid);
            if (distance > 0.01) { // Significant change threshold
                changed = true;
                System.arraycopy(newCentroid, 0, centroids[i], 0, FEATURE_DIMENSIONS);
            }
        }

        return changed;
    }

    /**
     * Calculate Euclidean distance between two vectors
     */
    private double calculateDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Update cluster profiles with statistics
     */
    private void updateClusterProfiles(List<PlayerFeatures> allPlayers, Map<UUID, Integer> clusters) {
        clusterProfiles.clear();

        // Group players by cluster
        Map<Integer, List<PlayerFeatures>> clusterGroups = new HashMap<>();
        for (PlayerFeatures player : allPlayers) {
            int clusterId = clusters.get(player.getPlayerId());
            clusterGroups.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(player);
        }

        // Create profiles for each cluster
        for (Map.Entry<Integer, List<PlayerFeatures>> entry : clusterGroups.entrySet()) {
            int clusterId = entry.getKey();
            List<PlayerFeatures> clusterPlayers = entry.getValue();

            if (clusterPlayers.size() < MIN_CLUSTER_SIZE) continue;

            // Calculate cluster statistics
            double[] avgFeatures = new double[FEATURE_DIMENSIONS];
            double[] featureStdDevs = new double[FEATURE_DIMENSIONS];

            // Calculate averages
            for (PlayerFeatures player : clusterPlayers) {
                double[] features = player.getFeatureVector();
                for (int i = 0; i < FEATURE_DIMENSIONS; i++) {
                    avgFeatures[i] += features[i];
                }
            }

            for (int i = 0; i < FEATURE_DIMENSIONS; i++) {
                avgFeatures[i] /= clusterPlayers.size();
            }

            // Calculate standard deviations
            for (PlayerFeatures player : clusterPlayers) {
                double[] features = player.getFeatureVector();
                for (int i = 0; i < FEATURE_DIMENSIONS; i++) {
                    double diff = features[i] - avgFeatures[i];
                    featureStdDevs[i] += diff * diff;
                }
            }

            for (int i = 0; i < FEATURE_DIMENSIONS; i++) {
                featureStdDevs[i] = Math.sqrt(featureStdDevs[i] / clusterPlayers.size());
            }

            clusterProfiles.put(clusterId, new ClusterProfile(
                clusterId, avgFeatures, featureStdDevs, clusterPlayers.size()
            ));
        }
    }

    /**
     * Assess risks for players based on cluster analysis
     */
    private void assessClusterRisks() {
        for (Map.Entry<UUID, PlayerCluster> entry : playerClusters.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerCluster cluster = entry.getValue();

            PlayerFeatures features = playerFeatures.get(playerId);
            ClusterProfile profile = clusterProfiles.get(cluster.getClusterId());

            if (features == null || profile == null) continue;

            // Calculate anomaly score within cluster
            double anomalyScore = calculateAnomalyScore(features, profile);

            // Calculate cluster risk level
            double clusterRisk = profile.calculateRiskLevel();

            // Combine scores for final risk assessment
            RiskLevel riskLevel = determineRiskLevel(anomalyScore, clusterRisk);

            playerRisks.put(playerId, new RiskAssessment(
                playerId,
                cluster.getClusterId(),
                anomalyScore,
                clusterRisk,
                riskLevel,
                System.currentTimeMillis()
            ));
        }
    }

    /**
     * Calculate anomaly score for player within their cluster
     */
    private double calculateAnomalyScore(PlayerFeatures player, ClusterProfile cluster) {
        double[] playerFeatures = player.getFeatureVector();
        double[] clusterMeans = cluster.avgFeatures;
        double[] clusterStdDevs = cluster.stdDevs;

        double totalAnomaly = 0.0;
        int featureCount = 0;

        for (int i = 0; i < FEATURE_DIMENSIONS; i++) {
            if (clusterStdDevs[i] > 0) {
                double zScore = Math.abs(playerFeatures[i] - clusterMeans[i]) / clusterStdDevs[i];
                totalAnomaly += Math.min(zScore, 5.0); // Cap at 5 standard deviations
                featureCount++;
            }
        }

        return featureCount > 0 ? totalAnomaly / featureCount : 0.0;
    }

    /**
     * Determine risk level based on anomaly and cluster scores
     */
    private RiskLevel determineRiskLevel(double anomalyScore, double clusterRisk) {
        double combinedScore = (anomalyScore * 0.7) + (clusterRisk * 0.3);

        if (combinedScore >= 2.5) return RiskLevel.CRITICAL;
        if (combinedScore >= 1.8) return RiskLevel.HIGH;
        if (combinedScore >= 1.2) return RiskLevel.MEDIUM;
        if (combinedScore >= 0.8) return RiskLevel.LOW;
        return RiskLevel.SAFE;
    }

    /**
     * Start periodic clustering task
     */
    private void startClusteringTask() {
        AsyncExecutor.executeComputation(() -> {
            while (plugin.isEnabled()) {
                try {
                    Thread.sleep(15 * 60 * 1000L); // Every 15 minutes
                    performIncrementalClustering();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    MessageManager.log("error", "Error in clustering task: {ERROR}", "ERROR", e.getMessage());
                }
            }
        });
    }

    // ===== UTILITY METHODS =====

    private double normalizeValue(double value, double min, double max) {
        if (max == min) return 0.5;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    private double calculateConsistencyScore(PlayerDetectionData data) {
        // Measure how consistent the player's behavior is over time
        Queue<DetectionResult> detections = data.getRecentDetections();
        if (detections.size() < 3) return 0.5;

        List<DetectionResult> results = new ArrayList<>(detections);
        double[] scores = results.stream()
            .mapToDouble(DetectionResult::getConfidence)
            .toArray();

        // Calculate coefficient of variation
        double mean = Arrays.stream(scores).average().orElse(0.0);
        if (mean == 0) return 0.0;

        double variance = Arrays.stream(scores)
            .map(score -> Math.pow(score - mean, 2))
            .average()
            .orElse(0.0);

        double cv = Math.sqrt(variance) / mean;

        // Lower CV = more consistent = higher score (0-1 scale)
        return Math.max(0.0, 1.0 - cv);
    }

    // ===== PUBLIC API =====

    /**
     * Get player's cluster ID
     */
    public int getPlayerCluster(UUID playerId) {
        PlayerCluster cluster = playerClusters.get(playerId);
        return cluster != null ? cluster.getClusterId() : -1;
    }

    /**
     * Get cluster profile
     */
    public ClusterProfile getClusterProfile(int clusterId) {
        return clusterProfiles.get(clusterId);
    }

    /**
     * Get player risk assessment
     */
    public RiskAssessment getPlayerRisk(UUID playerId) {
        return playerRisks.get(playerId);
    }

    /**
     * Get clustering statistics
     */
    public ClusteringStats getStats() {
        return new ClusteringStats(
            playerClusters.size(),
            clusterProfiles.size(),
            playerFeatures.size(),
            playerRisks.size(),
            playerFeatures.size(), // trackedPlayers
            System.currentTimeMillis()
        );
    }

    // ===== DATA CLASSES =====

    /**
     * Player cluster assignment
     */
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

        public int getClusterId() { return clusterId; }
        public long getAssignedAt() { return assignedAt; }
    }

    /**
     * Player behavioral features vector
     */
    private static class PlayerFeatures {
        private final UUID playerId;
        private final Map<String, Double> features = new HashMap<>();
        private final double[] featureVector = new double[FEATURE_DIMENSIONS];
        private int featureCount = 0;

        public PlayerFeatures(UUID playerId) {
            this.playerId = playerId;
        }

        public void addFeature(String name, double value) {
            features.put(name, value);
            if (featureCount < FEATURE_DIMENSIONS) {
                featureVector[featureCount++] = value;
            }
        }

        public double[] getFeatureVector() {
            return Arrays.copyOf(featureVector, FEATURE_DIMENSIONS);
        }

        public UUID getPlayerId() { return playerId; }
        public Map<String, Double> getFeatures() { return new HashMap<>(features); }
    }

    /**
     * Cluster profile with statistical information
     */
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
            // Calculate cluster risk based on average features
            // Higher suspicion ratios and mining speeds indicate higher risk
            double suspicionWeight = avgFeatures[1] * 0.5; // suspicion_ratio
            double speedWeight = avgFeatures[2] * 0.3; // mining_speed
            double efficiencyWeight = avgFeatures[0] * 0.2; // mining_efficiency

            return suspicionWeight + speedWeight + efficiencyWeight;
        }
    }

    /**
     * Risk assessment for a player
     */
    public static class RiskAssessment {
        public final UUID playerId;
        public final int clusterId;
        public final double anomalyScore;
        public final double clusterRisk;
        public final RiskLevel riskLevel;
        public final long assessedAt;

        public RiskAssessment(UUID playerId, int clusterId, double anomalyScore,
                            double clusterRisk, RiskLevel riskLevel, long assessedAt) {
            this.playerId = playerId;
            this.clusterId = clusterId;
            this.anomalyScore = anomalyScore;
            this.clusterRisk = clusterRisk;
            this.riskLevel = riskLevel;
            this.assessedAt = assessedAt;
        }
    }

    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        SAFE("Safe", 0),
        LOW("Low Risk", 1),
        MEDIUM("Medium Risk", 2),
        HIGH("High Risk", 3),
        CRITICAL("Critical Risk", 4);

        private final String displayName;
        private final int severity;

        RiskLevel(String displayName, int severity) {
            this.displayName = displayName;
            this.severity = severity;
        }

        public String getDisplayName() { return displayName; }
        public int getSeverity() { return severity; }
    }

    /**
     * Clustering statistics
     */
    public static class ClusteringStats {
        public final int clusteredPlayers;
        public final int totalClusters;
        public final int featureVectors;
        public final int riskAssessments;
        public final int trackedPlayers;
        public final long lastUpdate;

        public ClusteringStats(int clusteredPlayers, int totalClusters, int featureVectors,
                             int riskAssessments, int trackedPlayers, long lastUpdate) {
            this.clusteredPlayers = clusteredPlayers;
            this.totalClusters = totalClusters;
            this.featureVectors = featureVectors;
            this.riskAssessments = riskAssessments;
            this.trackedPlayers = trackedPlayers;
            this.lastUpdate = lastUpdate;
        }
    }
}
