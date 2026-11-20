package net.denfry.owml.detection;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.AntiCheatIntegration;
import net.denfry.owml.integrations.CorrelationEngine;
import net.denfry.owml.ml.impl.CombatDetection;
import net.denfry.owml.ml.impl.MovementDetection;
import net.denfry.owml.ml.impl.WorldDetection;
import net.denfry.owml.ml.impl.XrayDetection;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.entity.Player;

public class DetectionOrchestrator {
   private final OverWatchML plugin;
   private final XrayDetection xrayDetection;
   private final CombatDetection combatDetection;
   private final MovementDetection movementDetection;
   private final WorldDetection worldDetection;
   private final CorrelationEngine correlationEngine;
   private final BehaviorProfileManager profileManager;
   private final ScheduledExecutorService executorService;
   private static final long DETECTION_COOLDOWN_MS = 2000L;
   private final Map<UUID, Map<String, Long>> lastDetectionTime = new ConcurrentHashMap<>();

   public DetectionOrchestrator(OverWatchML plugin, BehaviorProfileManager profileManager, List<AntiCheatIntegration> integrations) {
      this.plugin = plugin;
      this.profileManager = profileManager;
      this.xrayDetection = new XrayDetection();
      this.combatDetection = new CombatDetection();
      this.movementDetection = new MovementDetection();
      this.worldDetection = new WorldDetection();
      this.correlationEngine = new CorrelationEngine(integrations);
      this.executorService = Executors.newScheduledThreadPool(2);
   }

   public void runDetection(Player player, CheatCategory category) {
      UUID playerId = player.getUniqueId();
      String categoryKey = category.getKey();
      long now = System.currentTimeMillis();
      Map<String, Long> playerDetections = this.lastDetectionTime.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
      Long lastTime = playerDetections.get(categoryKey);
      if (lastTime == null || now - lastTime >= 2000L) {
         playerDetections.put(categoryKey, now);
         this.executorService.submit(() -> {
            PlayerBehaviorProfile profile = this.profileManager.getProfile(playerId);
            PlayerEventBuffer buffer = this.profileManager.getEventBuffer(playerId);
            this.updateMetrics(profile, buffer, category);
            double internalScore = 0.0;

            double finalScore = this.correlationEngine.calculateCombinedScore(player, switch (category) {
               case XRAY -> this.xrayDetection.analyze(profile).getConfidence();
               case COMBAT, AUTOCLICKER -> this.combatDetection.analyze(profile).getConfidence();
               case MOVEMENT -> this.movementDetection.analyze(profile).getConfidence();
               case SCAFFOLD, WORLD -> this.worldDetection.analyze(profile).getConfidence();
               default -> 0.0;
            }, categoryKey);
            profile.setDetectionScore(categoryKey, finalScore);
            profile.updateAnalysisTime();
            if (finalScore > 0.85) {
               Schedulers.entity(player, () -> {
                  String message = String.format("§c%s failed §f%s §cdetection (Score: §e%.2f§c)", player.getName(), categoryKey.toUpperCase(), finalScore);
                  if (this.plugin.getStaffAlertManager() != null) {
                     this.plugin.getStaffAlertManager().alertStaffWithTeleport(player, player.getLocation(), message);
                  }
               });
            }
         });
      }
   }

   public void cleanupThrottling() {
      long cutoff = System.currentTimeMillis() - 60000L;
      this.lastDetectionTime.entrySet().removeIf(entry -> {
         Map<String, Long> playerMap = entry.getValue();
         playerMap.entrySet().removeIf(innerEntry -> innerEntry.getValue() < cutoff);
         return playerMap.isEmpty();
      });
   }

   private void updateMetrics(PlayerBehaviorProfile profile, PlayerEventBuffer buffer, CheatCategory category) {
      List<PlayerEventBuffer.BehavioralEvent> events = buffer.getEvents();
      if (!events.isEmpty()) {
         if (category == CheatCategory.AUTOCLICKER) {
            long now = System.currentTimeMillis();
            long count = events.stream().filter(e -> e.eventType().equals("interact") && now - e.timestamp() < 1000L).count();
            profile.setMetric("combat_cps", count);
         }

         if (category == CheatCategory.MOVEMENT) {
            long now = System.currentTimeMillis();
            long count = events.stream().filter(e -> e.eventType().equals("move") && now - e.timestamp() < 1000L).count();
            profile.setMetric("movement_speed", count / 20.0);
            long inAirCount = events.stream()
               .filter(e -> e.eventType().equals("move") && now - e.timestamp() < 1000L)
               .filter(e -> e.context() instanceof Boolean && !(Boolean)e.context())
               .count();
            profile.setMetric("in_air_time", (double)inAirCount / Math.max(1L, count));
         }
      }
   }

   public void shutdown() {
      this.executorService.shutdown();

      try {
         if (!this.executorService.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.executorService.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.executorService.shutdownNow();
      }

      this.xrayDetection.dispose();
      this.combatDetection.dispose();
      this.movementDetection.dispose();
      this.worldDetection.dispose();
   }

   public XrayDetection getXrayDetection() {
      return this.xrayDetection;
   }

   public CombatDetection getCombatDetection() {
      return this.combatDetection;
   }

   public MovementDetection getMovementDetection() {
      return this.movementDetection;
   }

   public WorldDetection getWorldDetection() {
      return this.worldDetection;
   }

   public CorrelationEngine getCorrelationEngine() {
      return this.correlationEngine;
   }
}
