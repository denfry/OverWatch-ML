package net.denfry.owml.ml.v2.pipeline;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.v2.core.CheatCategory;
import net.denfry.owml.ml.v2.core.MathUtils;
import net.denfry.owml.ml.v2.core.ServerContextNormalizer;
import net.denfry.owml.ml.v2.models.IncrementalIsolationForest;
import net.denfry.owml.ml.v2.models.OnlineAutoencoder;
import net.denfry.owml.ml.v2.profile.BehavioralProfile;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.entity.Player;

public class DetectionPipeline {
   private final OverWatchML plugin;
   private final ConcurrentHashMap<UUID, BehavioralProfile> profiles = new ConcurrentHashMap<>();
   private final ScheduledExecutorService mlExecutor = Executors.newScheduledThreadPool(2);
   public final ServerContextNormalizer serverContext = new ServerContextNormalizer();
   private final IncrementalIsolationForest xrayIF = new IncrementalIsolationForest(100, 256);
   private final OnlineAutoencoder xrayAE = new OnlineAutoencoder(32, 16, 0.001);
   private final List<double[]> xrayTrainingBatch = new CopyOnWriteArrayList<>();
   private final IncrementalIsolationForest combatIF = new IncrementalIsolationForest(100, 256);
   private final OnlineAutoencoder combatAE = new OnlineAutoencoder(40, 20, 0.001);
   private final List<double[]> combatTrainingBatch = new CopyOnWriteArrayList<>();

   public DetectionPipeline(OverWatchML plugin) {
      this.plugin = plugin;
      this.xrayIF.updateModel(this.serverContext.generateSyntheticXrayData(200));
      this.combatIF.updateModel(this.serverContext.generateSyntheticCombatData(200));
      this.mlExecutor.scheduleAtFixedRate(this::runDeepProfilerBatch, 5L, 5L, TimeUnit.MINUTES);
      this.mlExecutor.scheduleAtFixedRate(this::updateGlobalModels, 10L, 10L, TimeUnit.MINUTES);
   }

   public BehavioralProfile getProfile(UUID uuid) {
      return this.profiles.computeIfAbsent(uuid, BehavioralProfile::new);
   }

   public void removeProfile(UUID uuid) {
      this.profiles.remove(uuid);
   }

   public void processInstantFilter(Player player, CheatCategory category, int pointsAdded, String reason) {
      if (pointsAdded > 0) {
         BehavioralProfile profile = this.getProfile(player.getUniqueId());
         int currentPoints = category == CheatCategory.XRAY
            ? profile.xraySuspicionScore.addAndGet(pointsAdded)
            : profile.combatSuspicionScore.addAndGet(pointsAdded);
         if (currentPoints >= 100) {
            this.triggerPunishment(player, category, "Tier 1 Instant Flag: " + reason);
            if (category == CheatCategory.XRAY) {
               profile.xraySuspicionScore.set(0);
            } else {
               profile.combatSuspicionScore.set(0);
            }
         }
      }
   }

   public void triggerTemporalAnalysis(Player player, CheatCategory category, double[] features) {
      UUID uuid = player.getUniqueId();
      CompletableFuture.runAsync(() -> {
         BehavioralProfile profile = this.getProfile(uuid);
         double isolationScore;
         double sequenceScore;
         if (category == CheatCategory.XRAY) {
            profile.addXrayEvent(features);
            isolationScore = this.xrayIF.getAnomalyScore(features);
            sequenceScore = this.evaluateSequenceAnomaly(profile.xrayEventWindow, profile.xrayWindowIndex);
            this.xrayTrainingBatch.add(features);
         } else {
            profile.addCombatEvent(features);
            isolationScore = this.combatIF.getAnomalyScore(features);
            sequenceScore = this.evaluateSequenceAnomaly(profile.combatEventWindow, profile.combatWindowIndex);
            this.combatTrainingBatch.add(features);
         }

         double combinedScore = isolationScore * 0.7 + sequenceScore * 0.3;
         double threshold = profile.getAdaptiveThreshold(0.85);
         if (combinedScore > threshold) {
            profile.updateTrust(true, combinedScore);
            this.flagTemporalAnomaly(player, category, combinedScore);
         } else {
            profile.updateTrust(false, combinedScore);
         }
      }, this.mlExecutor);
   }

   private double evaluateSequenceAnomaly(double[][] window, int currentIndex) {
      int count = 0;

      for (double[] event : window) {
         if (event != null) {
            count++;
         }
      }

      if (count < 10) {
         return 0.0;
      } else {
         double totalDistance = 0.0;
         int validTransitions = 0;

         for (int i = 1; i < window.length; i++) {
            int curr = (currentIndex - i + window.length) % window.length;
            int prev = (currentIndex - i - 1 + window.length) % window.length;
            if (window[curr] != null && window[prev] != null) {
               totalDistance += MathUtils.distance3D(window[curr], window[prev]);
               validTransitions++;
            }
         }

         if (validTransitions == 0) {
            return 0.0;
         } else {
            double avgDist = totalDistance / validTransitions;
            return Math.min(1.0, 1.0 / (1.0 + avgDist));
         }
      }
   }

   private void runDeepProfilerBatch() {
      if (this.plugin.getConfig().getBoolean("ml.deep-profiler.enabled", false)) {
         for (BehavioralProfile profile : this.profiles.values()) {
            Player player = this.plugin.getServer().getPlayer(profile.playerId);
            if (player != null && player.isOnline()) {
               double[] aggXray = this.extractAggregatedFeatures(profile.xrayEventWindow, 32);
               if (aggXray != null) {
                  double reconstructionError = this.xrayAE.getAnomalyScore(aggXray);
                  if (reconstructionError > profile.getAdaptiveThreshold(0.92)) {
                     this.triggerPunishment(player, CheatCategory.XRAY, "Tier 3 Deep Profiler Flag");
                  }
               }

               double[] aggCombat = this.extractAggregatedFeatures(profile.combatEventWindow, 40);
               if (aggCombat != null) {
                  double reconstructionError = this.combatAE.getAnomalyScore(aggCombat);
                  if (reconstructionError > profile.getAdaptiveThreshold(0.92)) {
                     this.triggerPunishment(player, CheatCategory.COMBAT_MOVEMENT, "Tier 3 Deep Profiler Flag");
                  }
               }
            }
         }
      }
   }

   private double[] extractAggregatedFeatures(double[][] window, int featureSize) {
      int count = 0;
      double[] agg = new double[featureSize];

      for (double[] event : window) {
         if (event != null) {
            count++;

            for (int i = 0; i < featureSize; i++) {
               agg[i] += event[i];
            }
         }
      }

      if (count == 0) {
         return null;
      } else {
         for (int i = 0; i < featureSize; i++) {
            agg[i] /= count;
         }

         return agg;
      }
   }

   private void updateGlobalModels() {
      if (!this.xrayTrainingBatch.isEmpty()) {
         double[][] batch = this.xrayTrainingBatch.toArray(new double[0][]);
         this.xrayTrainingBatch.clear();
         this.xrayAE.trainBatch(batch);
         this.xrayIF.updateModel(batch);
      }

      if (!this.combatTrainingBatch.isEmpty()) {
         double[][] batch = this.combatTrainingBatch.toArray(new double[0][]);
         this.combatTrainingBatch.clear();
         this.combatAE.trainBatch(batch);
         this.combatIF.updateModel(batch);
      }

      this.plugin.getLogger().info("[OverWatch ML] Online models updated with new behavioral data.");
   }

   private void flagTemporalAnomaly(Player player, CheatCategory category, double score) {
      this.plugin
         .getLogger()
         .warning("[OverWatch ML] Temporal anomaly detected for " + player.getName() + " [" + category.name() + "] Score: " + String.format("%.2f", score));
      this.processInstantFilter(player, category, 20, "Temporal Anomaly Aggregation");
   }

   private void triggerPunishment(Player player, CheatCategory category, String reason) {
      Schedulers.entity(player, () -> {
         if (this.plugin.getConfig().getBoolean("ml.flag-only", true)) {
            this.plugin.getLogger().warning("[OverWatch ML] FLAG: " + player.getName() + " for " + category + ": " + reason);
            if (this.plugin.getMiningTelemetry() != null) {
               this.plugin.getMiningTelemetry().recordAlert("ml-flag", player.getName(), player.getLocation(), category + ": " + reason);
            }
         } else {
            this.plugin.getLogger().warning("[OverWatch ML] PUNISHING " + player.getName() + " for " + category + ": " + reason);
            this.plugin.getPunishmentManager().setPunishmentLevel(player, 3);
         }
      });
   }

   public void shutdown() {
      this.mlExecutor.shutdown();

      try {
         this.mlExecutor.awaitTermination(5L, TimeUnit.SECONDS);
      } catch (InterruptedException var2) {
         this.plugin.getLogger().warning("ML Executor interrupted during shutdown.");
      }
   }
}
