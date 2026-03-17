package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.advanced.BehavioralProfiler;
import net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralAnalysisResult;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.PerformanceMonitor;

/**
 * Advanced detection engine with behavioral analysis, pattern recognition,
 * and multi-layered detection algorithms.
 *
 * Features:
 * - Behavioral pattern analysis
 * - Mining speed anomaly detection
 * - Path prediction and analysis
 * - Inventory manipulation detection
 * - Multi-account detection
 * - Time-based anomaly detection
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.2
 */
public class AdvancedDetectionEngine implements Listener {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Detection components
    private final BehavioralAnalyzer behavioralAnalyzer;
    private final PatternRecognitionEngine patternEngine;
    private final AnomalyDetector anomalyDetector;
    private final MultiAccountDetector multiAccountDetector;
    private final PathPredictor pathPredictor;
    private final BehavioralProfiler behavioralProfiler;

    // Player tracking data
    private final Map<UUID, PlayerDetectionData> playerData = new ConcurrentHashMap<>();
    private final AtomicLong totalDetections = new AtomicLong(0);

    public AdvancedDetectionEngine() {
        this.behavioralAnalyzer = new BehavioralAnalyzer();
        this.patternEngine = new PatternRecognitionEngine();
        this.anomalyDetector = new AnomalyDetector();
        this.multiAccountDetector = new MultiAccountDetector();
        this.pathPredictor = new PathPredictor();
        this.behavioralProfiler = new BehavioralProfiler();

        // Register event listeners
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        MessageManager.log("info", "Advanced Detection Engine initialized with behavioral analysis and pattern recognition");
    }

    /**
     * Main detection method that combines multiple detection algorithms
     */
    public DetectionResult analyzePlayer(@NotNull Player player, @NotNull DetectionContext context) {
        long startTime = System.nanoTime();
        UUID playerId = player.getUniqueId();

        try {
            // Get or create player detection data
            PlayerDetectionData data = playerData.computeIfAbsent(playerId, k -> new PlayerDetectionData(playerId));

            // Update player activity
            data.updateActivity(player.getLocation());

            // Update behavioral profile with current context
            updateBehavioralProfile(playerId, context);

            // Run detection algorithms
            DetectionResult behavioral = behavioralAnalyzer.analyze(player, data, context);
            DetectionResult pattern = patternEngine.analyze(player, data, context);
            DetectionResult anomaly = anomalyDetector.analyze(player, data, context);
            DetectionResult multiAccount = multiAccountDetector.analyze(player, data, context);
            DetectionResult pathPrediction = pathPredictor.analyze(player, data, context);

            // Combine results using weighted scoring
            DetectionResult combined = combineResults(behavioral, pattern, anomaly, multiAccount, pathPrediction);

            // Analyze behavioral profile for additional insights
            BehavioralAnalysisResult behaviorAnalysis = behavioralProfiler.analyzeBehavior(playerId);
            DetectionResult behavioralResult = createDetectionFromBehavioralAnalysis(behaviorAnalysis);

            // Combine with behavioral analysis
            combined = combineResults(combined, behavioralResult);

            // Update player data with results
            data.addDetectionResult(combined);

            // Record performance metrics
            PerformanceMonitor.recordMLAnalysis();
            long processingTime = System.nanoTime() - startTime;

            if (processingTime > 10_000_000) { // More than 10ms
                MessageManager.log("warning", "Advanced detection analysis took {TIME}ms for player {PLAYER}",
                    "TIME", String.format("%.2f", processingTime / 1_000_000.0),
                    "PLAYER", player.getName());
            }

            return combined;

        } catch (Exception e) {
            MessageManager.log("error", "Error in advanced detection analysis for player {PLAYER}: {ERROR}",
                "PLAYER", player.getName(), "ERROR", e.getMessage());
            return DetectionResult.createSafe();
        }
    }

    /**
     * Combine multiple detection results using weighted scoring
     */
    private DetectionResult combineResults(DetectionResult... results) {
        double totalScore = 0.0;
        double maxScore = 0.0;
        DetectionLevel highestLevel = DetectionLevel.SAFE;
        List<String> allReasons = new ArrayList<>();
        Map<String, Object> combinedData = new HashMap<>();

        for (DetectionResult result : results) {
            if (result == null) continue;

            // Weighted scoring based on detection type
            double weight = getDetectionWeight(result.getDetectionType());
            totalScore += result.getConfidence() * weight;
            maxScore = Math.max(maxScore, result.getConfidence());

            if (result.getLevel().getSeverity() > highestLevel.getSeverity()) {
                highestLevel = result.getLevel();
            }

            allReasons.addAll(result.getReasons());
            combinedData.putAll(result.getAdditionalData());
        }

        // Normalize score
        double finalScore = Math.min(1.0, totalScore / results.length);

        // Determine final detection level
        DetectionLevel finalLevel = determineFinalLevel(finalScore, highestLevel);

        return new DetectionResult(finalLevel, finalScore, allReasons, DetectionType.COMBINED, combinedData);
    }

    /**
     * Get weight for detection type
     */
    private double getDetectionWeight(DetectionType type) {
        return switch (type) {
            case BEHAVIORAL -> 1.2;
            case PATTERN -> 1.0;
            case ANOMALY -> 1.3;
            case MULTI_ACCOUNT -> 1.5;
            case PATH_PREDICTION -> 0.8;
            case COMBINED -> 1.0;
            case CUSTOM -> 1.1;
            case UNKNOWN -> 1.0;
        };
    }

    /**
     * Determine final detection level based on score and individual results
     */
    private DetectionLevel determineFinalLevel(double score, DetectionLevel highestIndividual) {
        // If any individual detection is CRITICAL, escalate
        if (highestIndividual == DetectionLevel.CRITICAL) {
            return DetectionLevel.CRITICAL;
        }

        // Use score thresholds with hysteresis
        if (score >= 0.85) return DetectionLevel.CRITICAL;
        if (score >= 0.70) return DetectionLevel.HIGH;
        if (score >= 0.55) return DetectionLevel.MEDIUM;
        if (score >= 0.35) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    // ===== EVENT HANDLERS =====

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        DetectionContext context = DetectionContext.builder()
            .player(player)
            .location(location)
            .material(material)
            .action(DetectionContext.Action.BLOCK_BREAK)
            .timestamp(System.currentTimeMillis())
            .sessionId(player.getUniqueId().toString())
            .world(location.getWorld().getName())
            .timeOfDay(location.getWorld().getTime())
            .build();

        DetectionResult result = analyzePlayer(player, context);

        if (result.getLevel() != DetectionLevel.SAFE) {
            handleDetection(player, result, context);
        }
    }

    private final Map<UUID, Long> lastMoveAnalysis = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld()) || from.distance(to) < 0.1) return; // Filter micro-movements
        
        long now = System.currentTimeMillis();
        Long lastTime = lastMoveAnalysis.get(player.getUniqueId());
        // Throttle to 1 analysis per second per player
        if (lastTime != null && now - lastTime < 1000) {
            return;
        }
        lastMoveAnalysis.put(player.getUniqueId(), now);

        DetectionContext context = DetectionContext.builder()
            .player(player)
            .location(to)
            .fromLocation(from)
            .action(DetectionContext.Action.MOVEMENT)
            .timestamp(System.currentTimeMillis())
            .sessionId(player.getUniqueId().toString())
            .world(to.getWorld().getName())
            .timeOfDay(to.getWorld().getTime())
            .build();

        DetectionResult result = analyzePlayer(player, context);

        if (result.getLevel().getSeverity() >= DetectionLevel.HIGH.getSeverity()) {
            handleDetection(player, result, context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Initialize player data if not exists
        playerData.computeIfAbsent(playerId, k -> new PlayerDetectionData(playerId));

        // Check for multi-account patterns
        DetectionContext context = DetectionContext.builder()
            .player(player)
            .action(DetectionContext.Action.JOIN)
            .timestamp(System.currentTimeMillis())
            .sessionId(player.getUniqueId().toString())
            .world(player.getWorld().getName())
            .timeOfDay(player.getWorld().getTime())
            .build();

        DetectionResult result = multiAccountDetector.analyze(player, playerData.get(playerId), context);

        if (result.getLevel() != DetectionLevel.SAFE) {
            handleDetection(player, result, context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        PlayerDetectionData data = playerData.get(playerId);
        if (data != null) {
            data.markInactive();
        }
    }

    /**
     * Handle detection results
     */
    private void handleDetection(Player player, DetectionResult result, DetectionContext context) {
        totalDetections.incrementAndGet();

        // Log detection
        MessageManager.log("warning", "Advanced detection alert for {PLAYER}: {LEVEL} ({SCORE}%) - {REASONS}",
            "PLAYER", player.getName(),
            "LEVEL", result.getLevel().name(),
            "SCORE", String.format("%.1f", result.getConfidence() * 100),
            "REASONS", String.join(", ", result.getReasons()));

        // Broadcast to staff if configured
        if (plugin.getConfigManager().isCachedStaffAlertEnabled()) {
            MessageManager.broadcastToPermission("owml.staff",
                "alert.suspicious-mining",
                "PLAYER", player.getName(),
                "LOCATION", context.getLocation() != null ? context.getLocation().toString() : "unknown",
                "DETECTION_TYPE", result.getDetectionType().name(),
                "CONFIDENCE", String.format("%.1f%%", result.getConfidence() * 100));
        }

        // Update player suspicion level (SuspiciousManager removed)
        // int suspicionIncrease = calculateSuspicionIncrease(result);
        // SuspiciousManager.addSuspicionPoints(player.getUniqueId(), suspicionIncrease);

        // Trigger ML analysis if available
        if (plugin.getMLManager() != null && plugin.getMLManager().isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getMLManager().startAnalysis(player);
                } catch (Exception e) {
                    MessageManager.log("error", "Failed to trigger ML analysis for {PLAYER}: {ERROR}",
                        "PLAYER", player.getName(), "ERROR", e.getMessage());
                }
            });
        }
    }

    /**
     * Calculate suspicion increase based on detection result
     */
    private int calculateSuspicionIncrease(DetectionResult result) {
        return switch (result.getLevel()) {
            case LOW -> 5;
            case MEDIUM -> 15;
            case HIGH -> 35;
            case CRITICAL -> 75;
            default -> 0;
        };
    }

    /**
     * Get player detection data
     */
    public PlayerDetectionData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    /**
     * Get total detections count
     */
    public long getTotalDetections() {
        return totalDetections.get();
    }

    /**
     * Clear old inactive player data
     */
    public void cleanupInactiveData() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000L); // 24 hours ago

        playerData.entrySet().removeIf(entry -> {
            PlayerDetectionData data = entry.getValue();
            return data.getLastActivity() < cutoffTime && !data.isActive();
        });

        // Also cleanup lastMoveAnalysis for inactive players
        lastMoveAnalysis.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            PlayerDetectionData data = playerData.get(playerId);
            return data == null || (data.getLastActivity() < cutoffTime && !data.isActive());
        });

        MessageManager.log("info", "Cleaned up inactive player detection data");
    }

    /**
     * Get detection statistics
     */
    public DetectionStats getStats() {
        int activePlayers = (int) playerData.values().stream()
            .mapToInt(data -> data.isActive() ? 1 : 0)
            .sum();

        return new DetectionStats(
            totalDetections.get(),
            playerData.size(),
            activePlayers,
            System.currentTimeMillis()
        );
    }

    /**
     * Detection statistics
     */
    public static class DetectionStats {
        public final long totalDetections;
        public final int trackedPlayers;
        public final int activePlayers;
        public final long lastUpdate;

        public DetectionStats(long totalDetections, int trackedPlayers, int activePlayers, long lastUpdate) {
            this.totalDetections = totalDetections;
            this.trackedPlayers = trackedPlayers;
            this.activePlayers = activePlayers;
            this.lastUpdate = lastUpdate;
        }
    }

    /**
     * Update behavioral profile with current detection context
     */
    private void updateBehavioralProfile(UUID playerId, DetectionContext context) {
        PlayerDetectionData playerData = this.playerData.get(playerId);
        if (playerData == null) return;

        Map<String, Object> profileContext = new HashMap<>();
        profileContext.put("session_id", context.getSessionId());
        profileContext.put("world", context.getWorld());
        profileContext.put("time_of_day", context.getTimeOfDay());

        // Calculate and update behavioral metrics automatically

        Map<String, Double> metrics = new HashMap<>();

        // Mining speed (blocks per minute)
        double miningSpeed = calculateMiningSpeed(playerData, context);
        if (miningSpeed > 0) {
            metrics.put("mining_speed", miningSpeed);
        }

        // Ore distribution uniformity (0-1, higher = more uniform)
        double oreUniformity = calculateOreDistributionUniformity(playerData);
        metrics.put("ore_distribution_uniformity", oreUniformity);

        // Session duration (milliseconds)
        long sessionDuration = calculateSessionDuration(playerData);
        if (sessionDuration > 0) {
            metrics.put("session_duration", (double) sessionDuration);
        }

        // Idle time ratio (0-1, higher = more idle time)
        double idleRatio = calculateIdleTimeRatio(playerData);
        metrics.put("idle_time_ratio", idleRatio);

        // Movement efficiency (0-1, higher = more efficient/straight line movement)
        double movementEfficiency = calculateMovementEfficiency(playerData);
        metrics.put("movement_efficiency", movementEfficiency);

        // Backtracking ratio (0-1, higher = more backtracking)
        double backtrackingRatio = calculateBacktrackingRatio(playerData);
        metrics.put("backtracking_ratio", backtrackingRatio);

        // Create and update behavioral data
        if (!metrics.isEmpty()) {
            net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralData behavioralData =
                new net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralData(
                    "detection_analysis", metrics, profileContext);
            behavioralProfiler.updateProfile(playerId, behavioralData);
        }
    }

    /**
     * Calculate mining speed (blocks per minute)
     */
    private double calculateMiningSpeed(PlayerDetectionData playerData, DetectionContext context) {
        // Calculate based on recent block breaks within last 5 minutes
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000L);
        long recentBreaks = playerData.getDetectionResults().stream()
            .filter(result -> result.getTimestamp() > fiveMinutesAgo)
            .count();

        // Convert to blocks per minute (rough estimate based on detection events)
        return (recentBreaks / 5.0) * 60.0;
    }

    /**
     * Calculate ore distribution uniformity (0-1)
     * Higher values indicate more uniform mining patterns (less suspicious)
     */
    private double calculateOreDistributionUniformity(PlayerDetectionData playerData) {
        Map<Material, Integer> minedBlocks = playerData.getMinedBlocksByType();
        if (minedBlocks.isEmpty()) return 0.5;

        int totalBlocks = minedBlocks.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBlocks < 10) return 0.5; // Not enough data

        // Define valuable ore types
        Set<Material> valuableOres = Set.of(
            Material.DIAMOND_ORE, Material.GOLD_ORE, Material.IRON_ORE,
            Material.COAL_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.EMERALD_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE
        );

        int valuableOreCount = 0;
        int valuableOreTypes = 0;

        for (Map.Entry<Material, Integer> entry : minedBlocks.entrySet()) {
            if (valuableOres.contains(entry.getKey())) {
                valuableOreCount += entry.getValue();
                valuableOreTypes++;
            }
        }

        if (valuableOreCount == 0) return 0.5;

        // Calculate uniformity based on distribution across different ore types
        double averagePerType = (double) valuableOreCount / valuableOreTypes;
        double variance = 0.0;

        for (Map.Entry<Material, Integer> entry : minedBlocks.entrySet()) {
            if (valuableOres.contains(entry.getKey())) {
                double diff = entry.getValue() - averagePerType;
                variance += diff * diff;
            }
        }

        variance /= valuableOreTypes;
        double standardDeviation = Math.sqrt(variance);

        // Convert to uniformity score (0-1)
        // Lower variance = more uniform = higher score
        double uniformityScore = Math.max(0.0, 1.0 - (standardDeviation / averagePerType));

        return uniformityScore;
    }

    /**
     * Calculate session duration (milliseconds)
     */
    private long calculateSessionDuration(PlayerDetectionData playerData) {
        // Time since first activity in current session
        return System.currentTimeMillis() - playerData.getSessionStartTime();
    }

    /**
     * Calculate idle time ratio (0-1)
     */
    private double calculateIdleTimeRatio(PlayerDetectionData playerData) {
        long sessionDuration = calculateSessionDuration(playerData);
        if (sessionDuration <= 0) return 0.0;

        // Estimate idle time based on lack of recent activity
        long lastActivity = playerData.getLastActivity();
        long timeSinceLastActivity = System.currentTimeMillis() - lastActivity;

        // If no activity for more than 30 seconds, consider it idle time
        if (timeSinceLastActivity > 30000) {
            return Math.min(1.0, timeSinceLastActivity / (double) sessionDuration);
        }

        return 0.0;
    }

    /**
     * Calculate movement efficiency (0-1)
     * Higher values indicate more efficient/direct movement patterns
     */
    private double calculateMovementEfficiency(PlayerDetectionData playerData) {
        List<Location> recentLocations = new ArrayList<>(playerData.getRecentLocations());
        if (recentLocations.size() < 3) return 0.5; // Not enough data

        // Calculate actual path distance
        double actualDistance = 0.0;
        for (int i = 1; i < recentLocations.size(); i++) {
            Location prev = recentLocations.get(i-1);
            Location curr = recentLocations.get(i);
            if (prev.getWorld() != null && curr.getWorld() != null && prev.getWorld().equals(curr.getWorld())) {
                actualDistance += prev.distance(curr);
            }
        }

        // Calculate straight-line distance from start to end
        Location start = recentLocations.get(0);
        Location end = recentLocations.get(recentLocations.size() - 1);
        
        if (start.getWorld() == null || end.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return 0.5;
        }
        
        double straightDistance = start.distance(end);

        if (straightDistance <= 0.1) return 1.0; // No movement

        // Efficiency = straight distance / actual distance
        // Higher ratio means more direct movement (more efficient)
        double efficiency = Math.min(1.0, straightDistance / actualDistance);

        // Adjust for realistic movement patterns (perfect efficiency is suspicious)
        // Slightly penalize overly perfect movement
        if (efficiency > 0.95) {
            efficiency *= 0.9; // Reduce score for suspiciously perfect movement
        }

        return efficiency;
    }

    /**
     * Calculate backtracking ratio (0-1)
     * Higher values indicate more frequent returns to previously visited areas
     */
    private double calculateBacktrackingRatio(PlayerDetectionData playerData) {
        List<Location> recentLocations = new ArrayList<>(playerData.getRecentLocations());
        if (recentLocations.size() < 10) return 0.0; // Not enough data

        int backtrackCount = 0;
        int totalMovements = 0;

        // Analyze movement patterns for backtracking
        for (int i = 2; i < recentLocations.size(); i++) {
            Location current = recentLocations.get(i);
            totalMovements++;

            // Check if this location was visited recently (within last 5 movements)
            for (int j = Math.max(0, i - 5); j < i; j++) {
                Location previous = recentLocations.get(j);
                if (current.getWorld() != null && previous.getWorld() != null && 
                    current.getWorld().equals(previous.getWorld()) && 
                    current.distance(previous) < 2.0) { // Within 2 blocks
                    backtrackCount++;
                    break; // Count each backtrack only once
                }
            }
        }

        if (totalMovements == 0) return 0.0;

        double backtrackRatio = (double) backtrackCount / totalMovements;

        // Normalize to reasonable range
        // High backtracking might indicate systematic mining patterns
        return Math.min(1.0, backtrackRatio * 2.0); // Scale up for better detection
    }

    /**
     * Create detection result from behavioral analysis
     */
    private DetectionResult createDetectionFromBehavioralAnalysis(net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralAnalysisResult analysis) {
        DetectionLevel level;
        String reason = "Behavioral analysis: " + analysis.riskLevel;

        // Convert risk level to detection level
        switch (analysis.riskLevel.toLowerCase()) {
            case "low":
                level = DetectionLevel.LOW;
                break;
            case "medium":
                level = DetectionLevel.MEDIUM;
                break;
            case "high":
                level = DetectionLevel.HIGH;
                break;
            case "critical":
                level = DetectionLevel.CRITICAL;
                break;
            default:
                level = DetectionLevel.SAFE;
        }

        // Add details about detected anomalies
        if (analysis.anomalies != null && !analysis.anomalies.isEmpty()) {
            List<String> anomalyNames = new ArrayList<>();
            for (var anomaly : analysis.anomalies) {
                anomalyNames.add(anomaly.type + "_" + String.format("%.2f", anomaly.severity));
            }
            reason += " (" + String.join(", ", anomalyNames) + ")";
        }

        return new DetectionResult(level, analysis.riskScore, List.of(reason), DetectionType.CUSTOM, Collections.emptyMap());
    }

    /**
     * Get behavioral profiler statistics
     */
    public Map<String, Object> getBehavioralStats() {
        return behavioralProfiler.getProfilingStats();
    }

    /**
     * Get player behavioral fingerprint
     */
    public Map<String, Double> getPlayerFingerprint(UUID playerId) {
        return behavioralProfiler.getBehavioralFingerprint(playerId);
    }

    /**
     * Calculate behavioral similarity between two players
     */
    public double getPlayerSimilarity(UUID playerId1, UUID playerId2) {
        return behavioralProfiler.calculateSimilarity(playerId1, playerId2);
    }
}
