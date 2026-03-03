package net.denfry.owml.detection;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.PerformanceMonitor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive behavioral profiling system for players.
 * Creates detailed profiles of player behavior patterns and detects anomalies.
 *
 * Features:
 * - Multi-dimensional behavioral fingerprinting
 * - Temporal pattern analysis
 * - Social behavior tracking
 * - Adaptive threshold learning
 * - Cross-session consistency checking
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.8
 */
public class BehavioralProfiler {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Player profiles
    private final Map<UUID, PlayerProfile> playerProfiles = new ConcurrentHashMap<>();

    // Global behavior statistics
    private final Map<String, BehaviorStatistics> globalStats = new ConcurrentHashMap<>();

    // Adaptive thresholds
    private final Map<String, AdaptiveThreshold> adaptiveThresholds = new ConcurrentHashMap<>();

    // Profile statistics
    private final AtomicLong totalProfiles = new AtomicLong(0);
    private final AtomicLong anomalyDetections = new AtomicLong(0);

    public BehavioralProfiler() {
        initializeGlobalStats();
        initializeAdaptiveThresholds();

        MessageManager.log("info", "Behavioral Profiler initialized");
    }

    /**
     * Initialize global behavior statistics
     */
    private void initializeGlobalStats() {
        // Mining behaviors
        globalStats.put("mining_speed", new BehaviorStatistics("mining_speed"));
        globalStats.put("ore_distribution_uniformity", new BehaviorStatistics("ore_distribution_uniformity"));
        globalStats.put("tunnel_straightness", new BehaviorStatistics("tunnel_straightness"));
        globalStats.put("vertical_mining_ratio", new BehaviorStatistics("vertical_mining_ratio"));

        // Temporal behaviors
        globalStats.put("session_duration", new BehaviorStatistics("session_duration"));
        globalStats.put("peak_activity_hour", new BehaviorStatistics("peak_activity_hour"));
        globalStats.put("consistency_score", new BehaviorStatistics("consistency_score"));
        globalStats.put("idle_time_ratio", new BehaviorStatistics("idle_time_ratio"));

        // Movement behaviors
        globalStats.put("movement_efficiency", new BehaviorStatistics("movement_efficiency"));
        globalStats.put("backtracking_ratio", new BehaviorStatistics("backtracking_ratio"));
        globalStats.put("exploration_pattern", new BehaviorStatistics("exploration_pattern"));
        globalStats.put("speed_consistency", new BehaviorStatistics("speed_consistency"));

        // Social behaviors
        globalStats.put("interaction_frequency", new BehaviorStatistics("interaction_frequency"));
        globalStats.put("group_size_preference", new BehaviorStatistics("group_size_preference"));
        globalStats.put("communication_pattern", new BehaviorStatistics("communication_pattern"));
        globalStats.put("help_request_frequency", new BehaviorStatistics("help_request_frequency"));
    }

    /**
     * Initialize adaptive thresholds
     */
    private void initializeAdaptiveThresholds() {
        adaptiveThresholds.put("mining_anomaly", new AdaptiveThreshold(0.7, 0.01));
        adaptiveThresholds.put("temporal_anomaly", new AdaptiveThreshold(0.6, 0.005));
        adaptiveThresholds.put("movement_anomaly", new AdaptiveThreshold(0.8, 0.008));
        adaptiveThresholds.put("social_anomaly", new AdaptiveThreshold(0.5, 0.003));
        adaptiveThresholds.put("consistency_anomaly", new AdaptiveThreshold(0.75, 0.007));
    }

    /**
     * Update player profile with new behavior data
     */
    public void updateProfile(UUID playerId, String behaviorType, double value, Map<String, Object> context) {
        PlayerProfile profile = playerProfiles.computeIfAbsent(playerId, k -> {
            totalProfiles.incrementAndGet();
            return new PlayerProfile(playerId);
        });

        profile.updateBehavior(behaviorType, value, context);

        // Update global statistics
        BehaviorStatistics globalStat = globalStats.get(behaviorType);
        if (globalStat != null) {
            globalStat.update(value);
        }
    }

    /**
     * Analyze player behavior for anomalies
     */
    public BehaviorAnalysis analyzeBehavior(UUID playerId) {
        PlayerProfile profile = playerProfiles.get(playerId);
        if (profile == null || profile.getSessionCount() < 2) {
            return new BehaviorAnalysis(0.0, "insufficient_data", new ArrayList<>());
        }

        List<String> anomalies = new ArrayList<>();
        double totalAnomalyScore = 0.0;
        int analysisCount = 0;

        // Analyze each behavior dimension
        for (Map.Entry<String, BehaviorStatistics> entry : profile.getBehaviorStats().entrySet()) {
            String behaviorType = entry.getKey();
            BehaviorStatistics playerStats = entry.getValue();

            AdaptiveThreshold threshold = adaptiveThresholds.get(behaviorType + "_anomaly");
            if (threshold == null) continue;

            double anomalyScore = calculateAnomalyScore(playerStats, behaviorType);
            totalAnomalyScore += anomalyScore;
            analysisCount++;

            if (anomalyScore >= threshold.getCurrentThreshold()) {
                anomalies.add(behaviorType + "_anomaly_" + String.format("%.2f", anomalyScore));
                anomalyDetections.incrementAndGet();

                // Adapt threshold based on false positive feedback
                threshold.adaptThreshold(anomalyScore < 0.9); // Assume scores < 0.9 are more likely false positives
            }
        }

        double averageAnomalyScore = analysisCount > 0 ? totalAnomalyScore / analysisCount : 0.0;

        // Determine overall assessment
        String assessment = determineAssessment(averageAnomalyScore, anomalies.size());

        return new BehaviorAnalysis(averageAnomalyScore, assessment, anomalies);
    }

    /**
     * Calculate anomaly score for specific behavior
     */
    private double calculateAnomalyScore(BehaviorStatistics playerStats, String behaviorType) {
        BehaviorStatistics globalStats = this.globalStats.get(behaviorType);
        if (globalStats == null || globalStats.getCount() < 10) {
            return 0.0; // Not enough global data for comparison
        }

        // Calculate z-score based on global statistics
        double mean = globalStats.getMean();
        double stdDev = Math.max(globalStats.getStdDev(), 0.001); // Avoid division by zero

        double playerMean = playerStats.getMean();
        double zScore = Math.abs(playerMean - mean) / stdDev;

        // Convert to anomaly score (0-1)
        double anomalyScore = Math.min(1.0, zScore / 3.0); // 3-sigma rule normalized

        // Factor in consistency (lower consistency = higher anomaly)
        double consistencyFactor = 1.0 - playerStats.getConsistency();
        anomalyScore = anomalyScore * (1.0 + consistencyFactor * 0.5);

        return anomalyScore;
    }

    /**
     * Determine overall behavioral assessment
     */
    private String determineAssessment(double averageScore, int anomalyCount) {
        if (averageScore < 0.3 && anomalyCount == 0) {
            return "normal";
        } else if (averageScore < 0.5 && anomalyCount <= 1) {
            return "slight_anomaly";
        } else if (averageScore < 0.7 && anomalyCount <= 3) {
            return "moderate_anomaly";
        } else if (averageScore < 0.85) {
            return "high_anomaly";
        } else {
            return "critical_anomaly";
        }
    }

    /**
     * Get behavioral fingerprint for player
     */
    public Map<String, Double> getBehavioralFingerprint(UUID playerId) {
        PlayerProfile profile = playerProfiles.get(playerId);
        if (profile == null) {
            return new HashMap<>();
        }

        Map<String, Double> fingerprint = new HashMap<>();

        // Add normalized behavior scores
        for (Map.Entry<String, BehaviorStatistics> entry : profile.getBehaviorStats().entrySet()) {
            String behaviorType = entry.getKey();
            BehaviorStatistics stats = entry.getValue();

            // Normalize by global statistics
            BehaviorStatistics global = globalStats.get(behaviorType);
            if (global != null) {
                double normalizedScore = (stats.getMean() - global.getMean()) /
                                       Math.max(global.getStdDev(), 0.001);
                fingerprint.put(behaviorType + "_normalized", normalizedScore);
                fingerprint.put(behaviorType + "_consistency", stats.getConsistency());
            }
        }

        // Add profile metadata
        fingerprint.put("session_count", (double) profile.getSessionCount());
        fingerprint.put("total_observations", (double) profile.getTotalObservations());
        fingerprint.put("profile_age_days", (double) profile.getProfileAgeDays());

        return fingerprint;
    }

    /**
     * Compare two players' behavioral similarity
     */
    public double calculateSimilarity(UUID playerId1, UUID playerId2) {
        Map<String, Double> fingerprint1 = getBehavioralFingerprint(playerId1);
        Map<String, Double> fingerprint2 = getBehavioralFingerprint(playerId2);

        if (fingerprint1.isEmpty() || fingerprint2.isEmpty()) {
            return 0.0;
        }

        // Cosine similarity
        Set<String> allFeatures = new HashSet<>();
        allFeatures.addAll(fingerprint1.keySet());
        allFeatures.addAll(fingerprint2.keySet());

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String feature : allFeatures) {
            double val1 = fingerprint1.getOrDefault(feature, 0.0);
            double val2 = fingerprint2.getOrDefault(feature, 0.0);

            dotProduct += val1 * val2;
            norm1 += val1 * val1;
            norm2 += val2 * val2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Get profiling statistics
     */
    public Map<String, Object> getProfilingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProfiles", totalProfiles.get());
        stats.put("activeProfiles", playerProfiles.size());
        stats.put("anomalyDetections", anomalyDetections.get());
        stats.put("globalStatsTracked", globalStats.size());

        // Calculate anomaly detection rate
        long totalAnalyses = playerProfiles.values().stream()
            .mapToLong(PlayerProfile::getSessionCount)
            .sum();
        stats.put("anomalyRate", totalAnalyses > 0 ?
            (double) anomalyDetections.get() / totalAnalyses : 0.0);

        // Adaptive threshold stats
        Map<String, Object> thresholdStats = new HashMap<>();
        for (Map.Entry<String, AdaptiveThreshold> entry : adaptiveThresholds.entrySet()) {
            thresholdStats.put(entry.getKey(), entry.getValue().getCurrentThreshold());
        }
        stats.put("adaptiveThresholds", thresholdStats);

        return stats;
    }

    // ===== INNER CLASSES =====

    /**
     * Player behavior profile
     */
    public static class PlayerProfile {
        private final UUID playerId;
        private final Map<String, BehaviorStatistics> behaviorStats = new ConcurrentHashMap<>();
        private final long createdAt = System.currentTimeMillis();
        private long lastActivity = System.currentTimeMillis();
        private int sessionCount = 0;

        public PlayerProfile(UUID playerId) {
            this.playerId = playerId;
        }

        public void updateBehavior(String behaviorType, double value, Map<String, Object> context) {
            BehaviorStatistics stats = behaviorStats.computeIfAbsent(behaviorType,
                k -> new BehaviorStatistics(behaviorType));
            stats.update(value);

            lastActivity = System.currentTimeMillis();

            // Update session count if this is a new session
            Long sessionId = (Long) context.get("session_id");
            if (sessionId != null && sessionId > sessionCount) {
                sessionCount = sessionId.intValue();
            }
        }

        public Map<String, BehaviorStatistics> getBehaviorStats() {
            return new HashMap<>(behaviorStats);
        }

        public int getSessionCount() {
            return sessionCount;
        }

        public long getTotalObservations() {
            return behaviorStats.values().stream()
                .mapToLong(BehaviorStatistics::getCount)
                .sum();
        }

        public long getProfileAgeDays() {
            return (System.currentTimeMillis() - createdAt) / (24 * 60 * 60 * 1000);
        }
    }

    /**
     * Behavior analysis result
     */
    public static class BehaviorAnalysis {
        public final double anomalyScore;
        public final String assessment;
        public final List<String> detectedAnomalies;

        public BehaviorAnalysis(double anomalyScore, String assessment, List<String> detectedAnomalies) {
            this.anomalyScore = anomalyScore;
            this.assessment = assessment;
            this.detectedAnomalies = detectedAnomalies;
        }

        public boolean isAnomalous() {
            return !"normal".equals(assessment);
        }

        public String getSeverity() {
            switch (assessment) {
                case "slight_anomaly": return "low";
                case "moderate_anomaly": return "medium";
                case "high_anomaly": return "high";
                case "critical_anomaly": return "critical";
                default: return "none";
            }
        }
    }

    /**
     * Behavior statistics tracker
     */
    public static class BehaviorStatistics {
        private final String behaviorType;
        private double sum = 0.0;
        private double sumSquares = 0.0;
        private long count = 0;
        private double min = Double.MAX_VALUE;
        private double max = Double.MIN_VALUE;

        // Recent values for consistency calculation
        private final List<Double> recentValues = new ArrayList<>();
        private static final int RECENT_WINDOW_SIZE = 20;

        public BehaviorStatistics(String behaviorType) {
            this.behaviorType = behaviorType;
        }

        public synchronized void update(double value) {
            sum += value;
            sumSquares += value * value;
            count++;
            min = Math.min(min, value);
            max = Math.max(max, value);

            // Update recent values
            recentValues.add(value);
            if (recentValues.size() > RECENT_WINDOW_SIZE) {
                recentValues.remove(0);
            }
        }

        public double getMean() {
            return count > 0 ? sum / count : 0.0;
        }

        public double getStdDev() {
            if (count <= 1) return 0.0;
            double mean = getMean();
            return Math.sqrt((sumSquares / count) - (mean * mean));
        }

        public double getConsistency() {
            if (recentValues.size() < 3) return 1.0; // Assume consistent with few samples

            double mean = recentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = recentValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

            double stdDev = Math.sqrt(variance);
            return mean != 0 ? Math.max(0.0, 1.0 - (stdDev / Math.abs(mean))) : 1.0;
        }

        public long getCount() { return count; }
        public double getMin() { return min; }
        public double getMax() { return max; }
    }

    /**
     * Adaptive threshold with learning
     */
    public static class AdaptiveThreshold {
        private volatile double currentThreshold;
        private final double initialThreshold;
        private final double adaptationRate;
        private int adjustmentCount = 0;

        public AdaptiveThreshold(double initialThreshold, double adaptationRate) {
            this.initialThreshold = initialThreshold;
            this.currentThreshold = initialThreshold;
            this.adaptationRate = adaptationRate;
        }

        public synchronized void adaptThreshold(boolean isFalsePositive) {
            adjustmentCount++;

            if (isFalsePositive) {
                // Increase threshold to reduce false positives
                currentThreshold = Math.min(0.95, currentThreshold + adaptationRate);
            } else {
                // Slightly decrease threshold over time to catch more anomalies
                currentThreshold = Math.max(initialThreshold * 0.8,
                    currentThreshold - adaptationRate * 0.1);
            }

            // Gradually return toward initial threshold
            double returnRate = 0.001;
            currentThreshold += (initialThreshold - currentThreshold) * returnRate;
        }

        public double getCurrentThreshold() {
            return currentThreshold;
        }

        public double getInitialThreshold() {
            return initialThreshold;
        }

        public int getAdjustmentCount() {
            return adjustmentCount;
        }
    }
}