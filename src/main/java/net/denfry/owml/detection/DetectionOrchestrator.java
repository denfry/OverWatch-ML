package net.denfry.owml.detection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import net.denfry.owml.ml.impl.XrayDetection;
import net.denfry.owml.ml.impl.CombatDetection;
import net.denfry.owml.ml.impl.MovementDetection;
import net.denfry.owml.ml.impl.WorldDetection;
import net.denfry.owml.integrations.CorrelationEngine;
import net.denfry.owml.integrations.AntiCheatIntegration;
import net.denfry.owml.OverWatchML;

/**
 * The central orchestrator for all detection systems in OverWatch-ML.
 * Coordinates Xray, Combat, Movement, and World detection.
 */
public class DetectionOrchestrator {
    private final OverWatchML plugin;
    private final XrayDetection xrayDetection;
    private final CombatDetection combatDetection;
    private final MovementDetection movementDetection;
    private final WorldDetection worldDetection;
    private final CorrelationEngine correlationEngine;
    private final BehaviorProfileManager profileManager;
    private final ScheduledExecutorService executorService;
    
    // Throttling: only run detection once per X seconds per player per category
    private static final long DETECTION_COOLDOWN_MS = 2000; // 2 seconds between detections per player
    private final Map<UUID, Map<String, Long>> lastDetectionTime = new ConcurrentHashMap<>();

    public DetectionOrchestrator(OverWatchML plugin, BehaviorProfileManager profileManager, List<AntiCheatIntegration> integrations) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.xrayDetection = new XrayDetection();
        this.combatDetection = new CombatDetection();
        this.movementDetection = new MovementDetection();
        this.worldDetection = new WorldDetection();
        this.correlationEngine = new CorrelationEngine(integrations);
        
        // Rules: ScheduledExecutorService has pool size of 2 threads maximum.
        this.executorService = Executors.newScheduledThreadPool(2);
    }

    /**
     * Performs a comprehensive check on a player, combining internal and external scores.
     * Uses throttling to prevent excessive detections.
     * 
     * @param player The player to analyze.
     * @param category The cheat category.
     */
    public void runDetection(Player player, CheatCategory category) {
        UUID playerId = player.getUniqueId();
        String categoryKey = category.getKey();
        long now = System.currentTimeMillis();
        
        // Throttling: check if we should skip this detection
        Map<String, Long> playerDetections = lastDetectionTime.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Long lastTime = playerDetections.get(categoryKey);
        if (lastTime != null && (now - lastTime) < DETECTION_COOLDOWN_MS) {
            return; // Skip - too soon since last detection
        }
        playerDetections.put(categoryKey, now);
        
        executorService.submit(() -> {
            PlayerBehaviorProfile profile = profileManager.getProfile(playerId);
            PlayerEventBuffer buffer = profileManager.getEventBuffer(playerId);
            
            // Simple Feature Extraction from Event Buffer
            updateMetrics(profile, buffer, category);
            
            double internalScore = 0.0;
            // Determine internal score based on category
            switch (category) {
                case XRAY -> internalScore = xrayDetection.analyze(profile).getConfidence();
                case COMBAT, AUTOCLICKER -> internalScore = combatDetection.analyze(profile).getConfidence();
                case MOVEMENT -> internalScore = movementDetection.analyze(profile).getConfidence();
                case SCAFFOLD, WORLD -> internalScore = worldDetection.analyze(profile).getConfidence();
                default -> internalScore = 0.0;
            }

            // Combine with external scores using CorrelationEngine
            double finalScore = correlationEngine.calculateCombinedScore(player, internalScore, categoryKey);
            
            // Update the player's behavior profile
            profile.setDetectionScore(categoryKey, finalScore);
            profile.updateAnalysisTime();

            // Trigger alerts or punishments if necessary
            if (finalScore > 0.85) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String message = String.format("§c%s failed §f%s §cdetection (Score: §e%.2f§c)", 
                        player.getName(), categoryKey.toUpperCase(), finalScore);
                    
                    if (plugin.getStaffAlertManager() != null) {
                        plugin.getStaffAlertManager().alertStaffWithTeleport(player, player.getLocation(), message);
                    }
                });
            }
        });
    }

    /**
     * Clean up old entries from throttling map
     */
    public void cleanupThrottling() {
        long cutoff = System.currentTimeMillis() - 60000; // Remove entries older than 1 minute
        lastDetectionTime.entrySet().removeIf(entry -> {
            Map<String, Long> playerMap = entry.getValue();
            playerMap.entrySet().removeIf(innerEntry -> innerEntry.getValue() < cutoff);
            return playerMap.isEmpty();
        });
    }

    private void updateMetrics(PlayerBehaviorProfile profile, PlayerEventBuffer buffer, CheatCategory category) {
        List<PlayerEventBuffer.BehavioralEvent> events = buffer.getEvents();
        if (events.isEmpty()) return;

        // Example: Update CPS (Clicks Per Second) if it's an interaction check
        if (category == CheatCategory.AUTOCLICKER) {
            long now = System.currentTimeMillis();
            long count = events.stream()
                .filter(e -> e.eventType().equals("interact") && (now - e.timestamp()) < 1000)
                .count();
            profile.setMetric("combat_cps", (double) count);
        }
        
        // Example: Update movement speed
        if (category == CheatCategory.MOVEMENT) {
            long now = System.currentTimeMillis();
            long count = events.stream()
                .filter(e -> e.eventType().equals("move") && (now - e.timestamp()) < 1000)
                .count();
            profile.setMetric("movement_speed", count / 20.0); // Assuming 20 tps move events

            // Update in_air_time
            long inAirCount = events.stream()
                .filter(e -> e.eventType().equals("move") && (now - e.timestamp()) < 1000)
                .filter(e -> e.context() instanceof Boolean && !(Boolean) e.context()) // isOnGround = false
                .count();
            profile.setMetric("in_air_time", (double) inAirCount / Math.max(1, count));
        }
    }

    /**
     * Shuts down the orchestrator and its executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        
        xrayDetection.dispose();
        combatDetection.dispose();
        movementDetection.dispose();
        worldDetection.dispose();
    }

    public XrayDetection getXrayDetection() {
        return xrayDetection;
    }

    public CombatDetection getCombatDetection() {
        return combatDetection;
    }

    public MovementDetection getMovementDetection() {
        return movementDetection;
    }

    public WorldDetection getWorldDetection() {
        return worldDetection;
    }

    public CorrelationEngine getCorrelationEngine() {
        return correlationEngine;
    }
}
