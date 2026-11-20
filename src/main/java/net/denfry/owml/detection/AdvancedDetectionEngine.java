package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Locs;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.PerformanceMonitor;
import net.denfry.owml.utils.Schedulers;
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

public class AdvancedDetectionEngine implements Listener {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final BehavioralAnalyzer behavioralAnalyzer;
   private final PatternRecognitionEngine patternEngine;
   private final AnomalyDetector anomalyDetector;
   private final MultiAccountDetector multiAccountDetector;
   private final PathPredictor pathPredictor;
   private final net.denfry.owml.detection.advanced.BehavioralProfiler behavioralProfiler;
   private final Map<UUID, PlayerDetectionData> playerData = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastReported = new ConcurrentHashMap<>();
   private final AtomicLong totalDetections = new AtomicLong(0L);
   private final Map<UUID, Long> lastMoveAnalysis = new ConcurrentHashMap<>();

   public AdvancedDetectionEngine() {
      this.behavioralAnalyzer = new BehavioralAnalyzer();
      this.patternEngine = new PatternRecognitionEngine();
      this.anomalyDetector = new AnomalyDetector();
      this.multiAccountDetector = new MultiAccountDetector();
      // Off by default: official twinks (up to 5 per owner) share an IP by design, so "N accounts from one IP" was
      // reported as CRITICAL for honest players.
      this.multiAccountDetector.setEnabled(plugin.getConfig().getBoolean("detection.multi-account.enabled", false));
      this.pathPredictor = new PathPredictor();
      this.behavioralProfiler = new net.denfry.owml.detection.advanced.BehavioralProfiler();
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      MessageManager.log("info", "Advanced Detection Engine initialized with behavioral analysis and pattern recognition");
   }

   public DetectionResult analyzePlayer(@NotNull Player player, @NotNull DetectionContext context) {
      long startTime = System.nanoTime();
      UUID playerId = player.getUniqueId();

      try {
         PlayerDetectionData data = this.playerData.computeIfAbsent(playerId, k -> new PlayerDetectionData(playerId));
         data.updateActivity(player.getLocation());
         this.updateBehavioralProfile(playerId, context);
         DetectionResult behavioral = this.behavioralAnalyzer.analyze(player, data, context);
         DetectionResult pattern = this.patternEngine.analyze(player, data, context);
         DetectionResult anomaly = this.anomalyDetector.analyze(player, data, context);
         DetectionResult multiAccount = this.multiAccountDetector.analyze(player, data, context);
         DetectionResult pathPrediction = this.pathPredictor.analyze(player, data, context);
         DetectionResult combined = this.combineResults(behavioral, pattern, anomaly, multiAccount, pathPrediction);
         net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralAnalysisResult behaviorAnalysis = this.behavioralProfiler.analyzeBehavior(playerId);
         DetectionResult behavioralResult = this.createDetectionFromBehavioralAnalysis(behaviorAnalysis);
         combined = this.combineResults(combined, behavioralResult);
         data.addDetectionResult(combined);
         PerformanceMonitor.recordMLAnalysis();
         long processingTime = System.nanoTime() - startTime;
         if (processingTime > 10000000L) {
            MessageManager.log(
               "warning",
               "Advanced detection analysis took {TIME}ms for player {PLAYER}",
               "TIME",
               String.format("%.2f", processingTime / 1000000.0),
               "PLAYER",
               player.getName()
            );
         }

         return combined;
      } catch (Exception var17) {
         MessageManager.log(
            "error", "Error in advanced detection analysis for player {PLAYER}: {ERROR}", "PLAYER", player.getName(), "ERROR", var17.getMessage()
         );
         return DetectionResult.createSafe();
      }
   }

   private DetectionResult combineResults(DetectionResult... results) {
      double totalScore = 0.0;
      double maxScore = 0.0;
      DetectionLevel highestLevel = DetectionLevel.SAFE;
      List<String> allReasons = new ArrayList<>();
      Map<String, Object> combinedData = new HashMap<>();

      for (DetectionResult result : results) {
         if (result != null) {
            double weight = this.getDetectionWeight(result.getDetectionType());
            totalScore += result.getConfidence() * weight;
            maxScore = Math.max(maxScore, result.getConfidence());
            if (result.getLevel().getSeverity() > highestLevel.getSeverity()) {
               highestLevel = result.getLevel();
            }

            allReasons.addAll(result.getReasons());
            combinedData.putAll(result.getAdditionalData());
         }
      }

      double finalScore = Math.min(1.0, totalScore / results.length);
      DetectionLevel finalLevel = this.determineFinalLevel(finalScore, highestLevel);
      return new DetectionResult(finalLevel, finalScore, allReasons, DetectionType.COMBINED, combinedData);
   }

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

   private DetectionLevel determineFinalLevel(double score, DetectionLevel highestIndividual) {
      if (highestIndividual == DetectionLevel.CRITICAL) {
         return DetectionLevel.CRITICAL;
      } else if (score >= 0.85) {
         return DetectionLevel.CRITICAL;
      } else if (score >= 0.7) {
         return DetectionLevel.HIGH;
      } else if (score >= 0.55) {
         return DetectionLevel.MEDIUM;
      } else {
         return score >= 0.35 ? DetectionLevel.LOW : DetectionLevel.SAFE;
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("owml.bypass")) {
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
         DetectionResult result = this.analyzePlayer(player, context);
         if (result.getLevel() != DetectionLevel.SAFE) {
            this.handleDetection(player, result, context);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerMove(PlayerMoveEvent event) {
      if (!event.getPlayer().hasPermission("owml.bypass")) {
         Player player = event.getPlayer();
         Location from = event.getFrom();
         Location to = event.getTo();
         if (to != null && from.getWorld() != null && to.getWorld() != null && from.getWorld().equals(to.getWorld()) && !(from.distance(to) < 0.1)) {
            long now = System.currentTimeMillis();
            Long lastTime = this.lastMoveAnalysis.get(player.getUniqueId());
            if (lastTime == null || now - lastTime >= 1000L) {
               this.lastMoveAnalysis.put(player.getUniqueId(), now);
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
               DetectionResult result = this.analyzePlayer(player, context);
               if (result.getLevel().getSeverity() >= DetectionLevel.HIGH.getSeverity()) {
                  this.handleDetection(player, result, context);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      this.playerData.computeIfAbsent(playerId, k -> new PlayerDetectionData(playerId));
      DetectionContext context = DetectionContext.builder()
         .player(player)
         .action(DetectionContext.Action.JOIN)
         .timestamp(System.currentTimeMillis())
         .sessionId(player.getUniqueId().toString())
         .world(player.getWorld().getName())
         .timeOfDay(player.getWorld().getTime())
         .build();
      DetectionResult result = this.multiAccountDetector.analyze(player, this.playerData.get(playerId), context);
      if (result.getLevel() != DetectionLevel.SAFE) {
         this.handleDetection(player, result, context);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      PlayerDetectionData data = this.playerData.get(playerId);
      if (data != null) {
         data.markInactive();
      }
   }

   private void handleDetection(Player player, DetectionResult result, DetectionContext context) {
      if (!player.hasPermission("owml.bypass")) {
         this.totalDetections.incrementAndGet();
         boolean notable = result.getLevel().ordinal() >= DetectionLevel.HIGH.ordinal() && result.getConfidence() >= 0.6;
         long now = System.currentTimeMillis();
         boolean report = notable && now - this.lastReported.getOrDefault(player.getUniqueId(), 0L) >= 600000L;
         if (report) {
            this.lastReported.put(player.getUniqueId(), now);
         } else if (plugin.getConfigManager().isCachedDebugEnabled()) {
            plugin.getLogger()
               .info("[debug] detection " + player.getName() + " " + result.getLevel() + " " + String.format("%.1f", result.getConfidence() * 100.0) + "%");
         }

         if (report) {
            MessageManager.log(
               "warning",
               "Advanced detection alert for {PLAYER}: {LEVEL} ({SCORE}%) - {REASONS}",
               "PLAYER",
               player.getName(),
               "LEVEL",
               result.getLevel().name(),
               "SCORE",
               String.format("%.1f", result.getConfidence() * 100.0),
               "REASONS",
               String.join(", ", result.getReasons())
            );
         }

         if (report && plugin.getConfigManager().isCachedStaffAlertEnabled()) {
            Location at = context.getLocation();
            MessageManager.broadcastToPermission(
               "owml.staff",
               "alert.suspicious-mining",
               "PLAYER",
               player.getName(),
               "LOCATION",
               at != null && at.getWorld() != null ? at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() : "?",
               "DETECTION_TYPE",
               result.getDetectionType().name(),
               "CONFIDENCE",
               String.format("%.1f%%", result.getConfidence() * 100.0)
            );
         }

         if (plugin.getMLManager() != null && plugin.getMLManager().isEnabled()) {
            Schedulers.async(() -> {
               try {
                  plugin.getMLManager().startAnalysis(player);
               } catch (Exception var2x) {
                  MessageManager.log("error", "Failed to trigger ML analysis for {PLAYER}: {ERROR}", "PLAYER", player.getName(), "ERROR", var2x.getMessage());
               }
            });
         }
      }
   }

   private int calculateSuspicionIncrease(DetectionResult result) {
      return switch (result.getLevel()) {
         case LOW -> 5;
         case MEDIUM -> 15;
         case HIGH -> 35;
         case CRITICAL -> 75;
         default -> 0;
      };
   }

   public PlayerDetectionData getPlayerData(UUID playerId) {
      return this.playerData.get(playerId);
   }

   public long getTotalDetections() {
      return this.totalDetections.get();
   }

   public void cleanupInactiveData() {
      long cutoffTime = System.currentTimeMillis() - 86400000L;
      this.playerData.entrySet().removeIf(entry -> {
         PlayerDetectionData data = entry.getValue();
         return data.getLastActivity() < cutoffTime && !data.isActive();
      });
      this.lastMoveAnalysis.entrySet().removeIf(entry -> {
         UUID playerId = entry.getKey();
         PlayerDetectionData data = this.playerData.get(playerId);
         return data == null || data.getLastActivity() < cutoffTime && !data.isActive();
      });
      MessageManager.log("info", "Cleaned up inactive player detection data");
   }

   public AdvancedDetectionEngine.DetectionStats getStats() {
      int activePlayers = this.playerData.values().stream().mapToInt(data -> data.isActive() ? 1 : 0).sum();
      return new AdvancedDetectionEngine.DetectionStats(this.totalDetections.get(), this.playerData.size(), activePlayers, System.currentTimeMillis());
   }

   private void updateBehavioralProfile(UUID playerId, DetectionContext context) {
      PlayerDetectionData playerData = this.playerData.get(playerId);
      if (playerData != null) {
         Map<String, Object> profileContext = new HashMap<>();
         profileContext.put("session_id", context.getSessionId());
         profileContext.put("world", context.getWorld());
         profileContext.put("time_of_day", context.getTimeOfDay());
         Map<String, Double> metrics = new HashMap<>();
         double miningSpeed = this.calculateMiningSpeed(playerData, context);
         if (miningSpeed > 0.0) {
            metrics.put("mining_speed", miningSpeed);
         }

         double oreUniformity = this.calculateOreDistributionUniformity(playerData);
         metrics.put("ore_distribution_uniformity", oreUniformity);
         long sessionDuration = this.calculateSessionDuration(playerData);
         if (sessionDuration > 0L) {
            metrics.put("session_duration", (double)sessionDuration);
         }

         double idleRatio = this.calculateIdleTimeRatio(playerData);
         metrics.put("idle_time_ratio", idleRatio);
         double movementEfficiency = this.calculateMovementEfficiency(playerData);
         metrics.put("movement_efficiency", movementEfficiency);
         double backtrackingRatio = this.calculateBacktrackingRatio(playerData);
         metrics.put("backtracking_ratio", backtrackingRatio);
         if (!metrics.isEmpty()) {
            net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralData behavioralData = new net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralData(
               "detection_analysis", metrics, profileContext
            );
            this.behavioralProfiler.updateProfile(playerId, behavioralData);
         }
      }
   }

   private double calculateMiningSpeed(PlayerDetectionData playerData, DetectionContext context) {
      long fiveMinutesAgo = System.currentTimeMillis() - 300000L;
      long recentBreaks = playerData.getDetectionResults().stream().filter(result -> result.getTimestamp() > fiveMinutesAgo).count();
      return recentBreaks / 5.0 * 60.0;
   }

   private double calculateOreDistributionUniformity(PlayerDetectionData playerData) {
      Map<Material, Integer> minedBlocks = playerData.getMinedBlocksByType();
      if (minedBlocks.isEmpty()) {
         return 0.5;
      } else {
         int totalBlocks = minedBlocks.values().stream().mapToInt(Integer::intValue).sum();
         if (totalBlocks < 10) {
            return 0.5;
         } else {
            Set<Material> valuableOres = Set.of(
               Material.DIAMOND_ORE,
               Material.GOLD_ORE,
               Material.IRON_ORE,
               Material.COAL_ORE,
               Material.LAPIS_ORE,
               Material.REDSTONE_ORE,
               Material.EMERALD_ORE,
               Material.NETHER_GOLD_ORE,
               Material.NETHER_QUARTZ_ORE
            );
            int valuableOreCount = 0;
            int valuableOreTypes = 0;

            for (Entry<Material, Integer> entry : minedBlocks.entrySet()) {
               if (valuableOres.contains(entry.getKey())) {
                  valuableOreCount += entry.getValue();
                  valuableOreTypes++;
               }
            }

            if (valuableOreCount == 0) {
               return 0.5;
            } else {
               double averagePerType = (double)valuableOreCount / valuableOreTypes;
               double variance = 0.0;

               for (Entry<Material, Integer> entryx : minedBlocks.entrySet()) {
                  if (valuableOres.contains(entryx.getKey())) {
                     double diff = entryx.getValue().intValue() - averagePerType;
                     variance += diff * diff;
                  }
               }

               variance /= valuableOreTypes;
               double standardDeviation = Math.sqrt(variance);
               return Math.max(0.0, 1.0 - standardDeviation / averagePerType);
            }
         }
      }
   }

   private long calculateSessionDuration(PlayerDetectionData playerData) {
      return System.currentTimeMillis() - playerData.getSessionStartTime();
   }

   private double calculateIdleTimeRatio(PlayerDetectionData playerData) {
      long sessionDuration = this.calculateSessionDuration(playerData);
      if (sessionDuration <= 0L) {
         return 0.0;
      } else {
         long lastActivity = playerData.getLastActivity();
         long timeSinceLastActivity = System.currentTimeMillis() - lastActivity;
         return timeSinceLastActivity > 30000L ? Math.min(1.0, (double)timeSinceLastActivity / sessionDuration) : 0.0;
      }
   }

   private double calculateMovementEfficiency(PlayerDetectionData playerData) {
      List<Location> recentLocations = new ArrayList<>(playerData.getRecentLocations());
      if (recentLocations.size() < 3) {
         return 0.5;
      } else {
         double actualDistance = 0.0;

         for (int i = 1; i < recentLocations.size(); i++) {
            Location prev = recentLocations.get(i - 1);
            Location curr = recentLocations.get(i);
            if (prev.getWorld() != null && curr.getWorld() != null && prev.getWorld().equals(curr.getWorld())) {
               actualDistance += Locs.distance(prev, curr);
            }
         }

         Location start = recentLocations.get(0);
         Location end = recentLocations.get(recentLocations.size() - 1);
         if (start.getWorld() != null && end.getWorld() != null && start.getWorld().equals(end.getWorld())) {
            double straightDistance = Locs.distance(start, end);
            if (straightDistance <= 0.1) {
               return 1.0;
            } else {
               double efficiency = Math.min(1.0, straightDistance / actualDistance);
               if (efficiency > 0.95) {
                  efficiency *= 0.9;
               }

               return efficiency;
            }
         } else {
            return 0.5;
         }
      }
   }

   private double calculateBacktrackingRatio(PlayerDetectionData playerData) {
      List<Location> recentLocations = new ArrayList<>(playerData.getRecentLocations());
      if (recentLocations.size() < 10) {
         return 0.0;
      } else {
         int backtrackCount = 0;
         int totalMovements = 0;

         for (int i = 2; i < recentLocations.size(); i++) {
            Location current = recentLocations.get(i);
            totalMovements++;

            for (int j = Math.max(0, i - 5); j < i; j++) {
               Location previous = recentLocations.get(j);
               if (current.getWorld() != null
                  && previous.getWorld() != null
                  && current.getWorld().equals(previous.getWorld())
                  && Locs.distance(current, previous) < 2.0) {
                  backtrackCount++;
                  break;
               }
            }
         }

         if (totalMovements == 0) {
            return 0.0;
         } else {
            double backtrackRatio = (double)backtrackCount / totalMovements;
            return Math.min(1.0, backtrackRatio * 2.0);
         }
      }
   }

   private DetectionResult createDetectionFromBehavioralAnalysis(net.denfry.owml.detection.advanced.BehavioralProfiler.BehavioralAnalysisResult analysis) {
      String reason = "Behavioral analysis: " + analysis.riskLevel;
      String riskLevel = analysis.riskLevel.toLowerCase();

      DetectionLevel level = switch (riskLevel) {
         case "low" -> DetectionLevel.LOW;
         case "medium" -> DetectionLevel.MEDIUM;
         case "high" -> DetectionLevel.HIGH;
         case "critical" -> DetectionLevel.CRITICAL;
         default -> DetectionLevel.SAFE;
      };
      if (analysis.anomalies != null && !analysis.anomalies.isEmpty()) {
         List<String> anomalyNames = new ArrayList<>();

         for (net.denfry.owml.detection.advanced.BehavioralProfiler.Anomaly anomaly : analysis.anomalies) {
            anomalyNames.add(anomaly.type + "_" + String.format("%.2f", anomaly.severity));
         }

         reason = reason + " (" + String.join(", ", anomalyNames) + ")";
      }

      return new DetectionResult(level, analysis.riskScore, List.of(reason), DetectionType.CUSTOM, Collections.emptyMap());
   }

   public Map<String, Object> getBehavioralStats() {
      return this.behavioralProfiler.getProfilingStats();
   }

   public Map<String, Double> getPlayerFingerprint(UUID playerId) {
      return this.behavioralProfiler.getBehavioralFingerprint(playerId);
   }

   public double getPlayerSimilarity(UUID playerId1, UUID playerId2) {
      return this.behavioralProfiler.calculateSimilarity(playerId1, playerId2);
   }

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
}
