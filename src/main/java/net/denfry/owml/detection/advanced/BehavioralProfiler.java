package net.denfry.owml.detection.advanced;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced behavioral profiling system for comprehensive player analysis.
 * Creates detailed behavioral profiles and detects anomalies in real-time.
 *
 * Features:
 * - Multi-dimensional behavioral analysis
 * - Temporal pattern recognition
 * - Social interaction analysis
 * - Risk scoring and profiling
 * - Adaptive threshold learning
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.8
 */
public class BehavioralProfiler {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Behavioral profiles
    private final Map<UUID, PlayerBehavioralProfile> profiles = new ConcurrentHashMap<>();

    // Behavioral baselines (learned from normal players)
    private final Map<String, BehavioralBaseline> baselines = new ConcurrentHashMap<>();

    // Risk assessment
    private final RiskAssessmentEngine riskEngine = new RiskAssessmentEngine();

    public BehavioralProfiler() {
        initializeBaselines();
        MessageManager.log("info", "Behavioral Profiler initialized with advanced analysis capabilities");
    }

    /**
     * Update player behavioral profile with new data
     */
    public void updateProfile(UUID playerId, BehavioralData data) {
        PlayerBehavioralProfile profile = profiles.computeIfAbsent(playerId,
            k -> new PlayerBehavioralProfile(playerId));

        profile.update(data);
        profile.updateLastActivity();
    }

    /**
     * Analyze player behavior for anomalies
     */
    public BehavioralAnalysisResult analyzeBehavior(UUID playerId) {
        PlayerBehavioralProfile profile = profiles.get(playerId);
        if (profile == null || !profile.hasEnoughData()) {
            return new BehavioralAnalysisResult(0.0, Collections.emptyList(), "insufficient_data");
        }

        List<Anomaly> anomalies = new ArrayList<>();
        double riskScore = 0.0;

        // Analyze each behavioral dimension
        riskScore += analyzeMiningBehavior(profile, anomalies);
        riskScore += analyzeMovementBehavior(profile, anomalies);
        riskScore += analyzeInteractionBehavior(profile, anomalies);
        riskScore += analyzeTemporalBehavior(profile, anomalies);
        riskScore += analyzeSocialBehavior(profile, anomalies);

        // Normalize risk score
        riskScore = Math.min(1.0, riskScore);

        // Determine risk level
        String riskLevel = getRiskLevel(riskScore);

        return new BehavioralAnalysisResult(riskScore, anomalies, riskLevel);
    }

    /**
     * Analyze mining behavior patterns
     */
    private double analyzeMiningBehavior(PlayerBehavioralProfile profile, List<Anomaly> anomalies) {
        double riskScore = 0.0;

        // Mining speed analysis
        double avgMiningSpeed = profile.getAverageMiningSpeed();
        BehavioralBaseline speedBaseline = baselines.get("mining_speed");
        if (speedBaseline != null) {
            double deviation = Math.abs(avgMiningSpeed - speedBaseline.mean) / speedBaseline.stdDev;
            if (deviation > 3.0) {
                riskScore += 0.3;
                anomalies.add(new Anomaly("mining_speed", deviation, "unusual_mining_speed"));
            }
        }

        // Mining pattern consistency
        double patternConsistency = profile.getMiningPatternConsistency();
        if (patternConsistency < 0.3) {
            riskScore += 0.2;
            anomalies.add(new Anomaly("pattern_consistency", patternConsistency, "inconsistent_mining"));
        }

        // Rare ore targeting
        double rareOreRatio = profile.getRareOreRatio();
        if (rareOreRatio > 0.8) {
            riskScore += 0.4;
            anomalies.add(new Anomaly("rare_ore_ratio", rareOreRatio, "excessive_rare_ore_mining"));
        }

        return riskScore;
    }

    /**
     * Analyze movement behavior patterns
     */
    private double analyzeMovementBehavior(PlayerBehavioralProfile profile, List<Anomaly> anomalies) {
        double riskScore = 0.0;

        // Movement speed analysis
        double avgSpeed = profile.getAverageMovementSpeed();
        BehavioralBaseline speedBaseline = baselines.get("movement_speed");
        if (speedBaseline != null) {
            double deviation = Math.abs(avgSpeed - speedBaseline.mean) / speedBaseline.stdDev;
            if (deviation > 2.5) {
                riskScore += 0.2;
                anomalies.add(new Anomaly("movement_speed", deviation, "unusual_movement_speed"));
            }
        }

        // Teleportation patterns
        int teleportCount = profile.getTeleportCount();
        if (teleportCount > 10) {
            riskScore += 0.15;
            anomalies.add(new Anomaly("teleport_frequency", teleportCount, "frequent_teleportation"));
        }

        // Path efficiency
        double pathEfficiency = profile.getPathEfficiency();
        if (pathEfficiency > 0.9) {
            riskScore += 0.1;
            anomalies.add(new Anomaly("path_efficiency", pathEfficiency, "suspiciously_efficient_paths"));
        }

        return riskScore;
    }

    /**
     * Analyze interaction behavior patterns
     */
    private double analyzeInteractionBehavior(PlayerBehavioralProfile profile, List<Anomaly> anomalies) {
        double riskScore = 0.0;

        // Command usage patterns
        Map<String, Integer> commandUsage = profile.getCommandUsage();
        int suspiciousCommands = 0;

        for (Map.Entry<String, Integer> entry : commandUsage.entrySet()) {
            String command = entry.getKey();
            int count = entry.getValue();

            // Check for suspicious commands
            if (command.contains("gamemode") && count > 5) {
                suspiciousCommands++;
            }
            if (command.contains("give") && count > 3) {
                suspiciousCommands++;
            }
            if (command.contains("op") && count > 0) {
                suspiciousCommands += 2;
            }
        }

        if (suspiciousCommands > 0) {
            riskScore += Math.min(0.3, suspiciousCommands * 0.1);
            anomalies.add(new Anomaly("command_usage", suspiciousCommands, "suspicious_command_usage"));
        }

        // Inventory manipulation
        int inventoryChanges = profile.getInventoryChanges();
        if (inventoryChanges > 50) {
            riskScore += 0.1;
            anomalies.add(new Anomaly("inventory_changes", inventoryChanges, "excessive_inventory_manipulation"));
        }

        return riskScore;
    }

    /**
     * Analyze temporal behavior patterns
     */
    private double analyzeTemporalBehavior(PlayerBehavioralProfile profile, List<Anomaly> anomalies) {
        double riskScore = 0.0;

        // Session duration analysis
        long avgSessionTime = profile.getAverageSessionTime();
        if (avgSessionTime > 8 * 60 * 60 * 1000) { // 8 hours
            riskScore += 0.1;
            anomalies.add(new Anomaly("session_duration", avgSessionTime, "extended_sessions"));
        }

        // Play time distribution
        Map<Integer, Integer> hourlyActivity = profile.getHourlyActivity();
        double activityVariance = calculateVariance(hourlyActivity.values());

        BehavioralBaseline activityBaseline = baselines.get("activity_variance");
        if (activityBaseline != null) {
            double deviation = Math.abs(activityVariance - activityBaseline.mean) / activityBaseline.stdDev;
            if (deviation > 2.0) {
                riskScore += 0.15;
                anomalies.add(new Anomaly("activity_pattern", deviation, "unusual_activity_pattern"));
            }
        }

        // Idle time analysis
        double idleRatio = profile.getIdleTimeRatio();
        if (idleRatio > 0.7) {
            riskScore += 0.1;
            anomalies.add(new Anomaly("idle_ratio", idleRatio, "excessive_idle_time"));
        }

        return riskScore;
    }

    /**
     * Analyze social behavior patterns
     */
    private double analyzeSocialBehavior(PlayerBehavioralProfile profile, List<Anomaly> anomalies) {
        double riskScore = 0.0;

        // Chat activity analysis
        int messageCount = profile.getMessageCount();
        int spamMessages = profile.getSpamMessageCount();

        if (spamMessages > messageCount * 0.3) {
            riskScore += 0.1;
            anomalies.add(new Anomaly("chat_spam", spamMessages, "excessive_spam"));
        }

        // Friend/association analysis
        Set<UUID> associations = profile.getPlayerAssociations();
        if (associations.size() > 20) {
            riskScore += 0.05;
            anomalies.add(new Anomaly("social_connections", associations.size(), "large_social_network"));
        }

        return riskScore;
    }

    /**
     * Initialize behavioral baselines from historical data
     */
    private void initializeBaselines() {
        // Initialize with reasonable defaults (would be learned from data)
        baselines.put("mining_speed", new BehavioralBaseline(2.5, 0.8));
        baselines.put("movement_speed", new BehavioralBaseline(4.5, 1.2));
        baselines.put("activity_variance", new BehavioralBaseline(150.0, 50.0));

        MessageManager.log("info", "Behavioral baselines initialized with {COUNT} metrics",
            "COUNT", String.valueOf(baselines.size()));
    }

    /**
     * Update baselines with new normal behavior data
     */
    public void updateBaselines(Map<String, Double> normalBehaviorData) {
        for (Map.Entry<String, Double> entry : normalBehaviorData.entrySet()) {
            String metric = entry.getKey();
            double value = entry.getValue();

            BehavioralBaseline baseline = baselines.computeIfAbsent(metric,
                k -> new BehavioralBaseline(value, 1.0));
            baseline.update(value);
        }
    }

    /**
     * Get risk level from score
     */
    private String getRiskLevel(double score) {
        if (score >= 0.8) return "critical";
        if (score >= 0.6) return "high";
        if (score >= 0.4) return "medium";
        if (score >= 0.2) return "low";
        return "normal";
    }

    /**
     * Calculate variance of a collection of numbers
     */
    private double calculateVariance(Collection<Integer> values) {
        if (values.isEmpty()) return 0.0;

        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);

        return variance;
    }

    /**
     * Get profiling statistics
     */
    public Map<String, Object> getProfilingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("profilesTracked", profiles.size());
        stats.put("baselinesLearned", baselines.size());

        long activeProfiles = profiles.values().stream()
            .mapToLong(p -> System.currentTimeMillis() - p.getLastActivity() < 24 * 60 * 60 * 1000 ? 1 : 0)
            .sum();
        stats.put("activeProfiles", activeProfiles);

        return stats;
    }

    /**
     * Get behavioral fingerprint for a specific player
     * Returns aggregated metrics that represent the player's behavioral profile
     */
    public Map<String, Double> getBehavioralFingerprint(UUID playerId) {
        PlayerBehavioralProfile profile = profiles.get(playerId);
        if (profile == null) {
            return new HashMap<>(); // Return empty map for unknown players
        }

        // Return the aggregated metrics as the behavioral fingerprint
        return new HashMap<>(profile.aggregatedMetrics);
    }

    /**
     * Calculate behavioral similarity between two players (0.0 to 1.0)
     * Returns 1.0 for identical behavior, 0.0 for completely different behavior
     */
    public double calculateSimilarity(UUID playerId1, UUID playerId2) {
        Map<String, Double> fingerprint1 = getBehavioralFingerprint(playerId1);
        Map<String, Double> fingerprint2 = getBehavioralFingerprint(playerId2);

        // If either player has no data, return 0 similarity
        if (fingerprint1.isEmpty() || fingerprint2.isEmpty()) {
            return 0.0;
        }

        // Calculate cosine similarity between the behavioral fingerprints
        return calculateCosineSimilarity(fingerprint1, fingerprint2);
    }

    /**
     * Calculate cosine similarity between two behavioral fingerprints
     */
    private double calculateCosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        // Get all unique keys from both vectors
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(vector1.keySet());
        allKeys.addAll(vector2.keySet());

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String key : allKeys) {
            double val1 = vector1.getOrDefault(key, 0.0);
            double val2 = vector2.getOrDefault(key, 0.0);

            dotProduct += val1 * val2;
            norm1 += val1 * val1;
            norm2 += val2 * val2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0; // Avoid division by zero
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // ===== INNER CLASSES =====

    /**
     * Behavioral data container
     */
    public static class BehavioralData {
        public final long timestamp;
        public final String actionType;
        public final Map<String, Double> metrics;
        public final Map<String, Object> context;

        public BehavioralData(String actionType, Map<String, Double> metrics, Map<String, Object> context) {
            this.timestamp = System.currentTimeMillis();
            this.actionType = actionType;
            this.metrics = metrics != null ? metrics : new HashMap<>();
            this.context = context != null ? context : new HashMap<>();
        }
    }

    /**
     * Player behavioral profile
     */
    private static class PlayerBehavioralProfile {
        private final UUID playerId;
        private final List<BehavioralData> behaviorHistory = new ArrayList<>();
        private final Map<String, Double> aggregatedMetrics = new HashMap<>();
        private final Map<String, Integer> commandUsage = new HashMap<>();
        private final Map<Integer, Integer> hourlyActivity = new HashMap<>();
        private final Set<UUID> associations = new HashSet<>();

        private long lastActivity = System.currentTimeMillis();
        private int teleportCount = 0;
        private int inventoryChanges = 0;
        private int messageCount = 0;
        private int spamMessageCount = 0;

        public PlayerBehavioralProfile(UUID playerId) {
            this.playerId = playerId;
        }

        public void update(BehavioralData data) {
            behaviorHistory.add(data);

            // Keep only recent history (last 1000 actions)
            if (behaviorHistory.size() > 1000) {
                behaviorHistory.remove(0);
            }

            // Update aggregated metrics
            for (Map.Entry<String, Double> entry : data.metrics.entrySet()) {
                String metric = entry.getKey();
                double value = entry.getValue();

                double current = aggregatedMetrics.getOrDefault(metric, 0.0);
                int count = getMetricCount(metric) + 1;
                aggregatedMetrics.put(metric, (current * (count - 1) + value) / count);
            }

            // Update specific counters
            updateSpecificMetrics(data);
        }

        private void updateSpecificMetrics(BehavioralData data) {
            String actionType = data.actionType;

            switch (actionType) {
                case "command":
                    String command = (String) data.context.getOrDefault("command", "");
                    commandUsage.put(command, commandUsage.getOrDefault(command, 0) + 1);
                    break;

                case "teleport":
                    teleportCount++;
                    break;

                case "inventory_change":
                    inventoryChanges++;
                    break;

                case "chat_message":
                    messageCount++;
                    String message = (String) data.context.getOrDefault("message", "");
                    if (isSpamMessage(message)) {
                        spamMessageCount++;
                    }
                    break;

                case "player_interaction":
                    UUID otherPlayer = (UUID) data.context.get("other_player");
                    if (otherPlayer != null) {
                        associations.add(otherPlayer);
                    }
                    break;
            }

            // Update hourly activity
            int hour = (int) ((data.timestamp / (60 * 60 * 1000)) % 24);
            hourlyActivity.put(hour, hourlyActivity.getOrDefault(hour, 0) + 1);
        }

        private boolean isSpamMessage(String message) {
            if (message == null || message.length() < 10) return false;

            // Simple spam detection
            String lower = message.toLowerCase();
            return lower.contains("spam") || lower.matches(".*(.)\\1{10,}.*") || // repeated characters
                   message.length() > 200; // very long messages
        }

        private int getMetricCount(String metric) {
            // Simplified: count how many times this metric was updated
            return (int) behaviorHistory.stream()
                .filter(data -> data.metrics.containsKey(metric))
                .count();
        }

        public boolean hasEnoughData() {
            return behaviorHistory.size() >= 50; // Need at least 50 data points
        }

        // Getters
        public double getAverageMiningSpeed() { return aggregatedMetrics.getOrDefault("mining_speed", 0.0); }
        public double getMiningPatternConsistency() { return aggregatedMetrics.getOrDefault("pattern_consistency", 1.0); }
        public double getRareOreRatio() { return aggregatedMetrics.getOrDefault("rare_ore_ratio", 0.0); }
        public double getAverageMovementSpeed() { return aggregatedMetrics.getOrDefault("movement_speed", 0.0); }
        public double getPathEfficiency() { return aggregatedMetrics.getOrDefault("path_efficiency", 0.0); }
        public long getAverageSessionTime() { return aggregatedMetrics.getOrDefault("session_time", 0.0).longValue(); }
        public double getIdleTimeRatio() { return aggregatedMetrics.getOrDefault("idle_ratio", 0.0); }
        public Map<String, Integer> getCommandUsage() { return new HashMap<>(commandUsage); }
        public Map<Integer, Integer> getHourlyActivity() { return new HashMap<>(hourlyActivity); }
        public Set<UUID> getPlayerAssociations() { return new HashSet<>(associations); }
        public int getTeleportCount() { return teleportCount; }
        public int getInventoryChanges() { return inventoryChanges; }
        public int getMessageCount() { return messageCount; }
        public int getSpamMessageCount() { return spamMessageCount; }
        public long getLastActivity() { return lastActivity; }
        public void updateLastActivity() { this.lastActivity = System.currentTimeMillis(); }
    }

    /**
     * Behavioral baseline for anomaly detection
     */
    private static class BehavioralBaseline {
        private double sum = 0.0;
        private double sumSquares = 0.0;
        private int count = 0;

        public BehavioralBaseline(double initialValue, double initialStdDev) {
            update(initialValue);
        }

        public void update(double value) {
            sum += value;
            sumSquares += value * value;
            count++;
        }

        public double getMean() {
            return count > 0 ? sum / count : 0.0;
        }

        public double getStdDev() {
            if (count <= 1) return 1.0;
            double mean = getMean();
            return Math.sqrt((sumSquares / count) - (mean * mean));
        }

        public final double mean = getMean();
        public final double stdDev = getStdDev();
    }

    /**
     * Anomaly detection result
     */
    public static class Anomaly {
        public final String metric;
        public final double severity;
        public final String type;

        public Anomaly(String metric, double severity, String type) {
            this.metric = metric;
            this.severity = severity;
            this.type = type;
        }
    }

    /**
     * Behavioral analysis result
     */
    public static class BehavioralAnalysisResult {
        public final double riskScore;
        public final List<Anomaly> anomalies;
        public final String riskLevel;

        public BehavioralAnalysisResult(double riskScore, List<Anomaly> anomalies, String riskLevel) {
            this.riskScore = riskScore;
            this.anomalies = anomalies;
            this.riskLevel = riskLevel;
        }
    }

    /**
     * Risk assessment engine
     */
    private static class RiskAssessmentEngine {
        // Would implement more sophisticated risk assessment
        // For now, it's integrated into the main analysis
    }
}