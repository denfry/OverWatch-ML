package net.denfry.owml.ml.v2.pipeline;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.v2.core.CheatCategory;
import net.denfry.owml.ml.v2.core.ServerContextNormalizer;
import net.denfry.owml.ml.v2.models.IncrementalIsolationForest;
import net.denfry.owml.ml.v2.models.OnlineAutoencoder;
import net.denfry.owml.ml.v2.profile.BehavioralProfile;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * TСЂС‘С…СѓСЂРѕРІРЅРµРІС‹Р№ РєРѕРЅРІРµР№РµСЂ (Pipeline) РґР»СЏ ML-Р°РЅР°Р»РёР·Р°
 */
public class DetectionPipeline {
    private final OverWatchML plugin;
    private final ConcurrentHashMap<UUID, BehavioralProfile> profiles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService mlExecutor = Executors.newScheduledThreadPool(2);
    
    public final ServerContextNormalizer serverContext = new ServerContextNormalizer();

    // System 1: XRAY (32 features)
    private final IncrementalIsolationForest xrayIF = new IncrementalIsolationForest(100, 256);
    private final OnlineAutoencoder xrayAE = new OnlineAutoencoder(32, 16, 0.001);
    private final List<double[]> xrayTrainingBatch = new CopyOnWriteArrayList<>();

    // System 2: COMBAT/MOVEMENT (40 features)
    private final IncrementalIsolationForest combatIF = new IncrementalIsolationForest(100, 256);
    private final OnlineAutoencoder combatAE = new OnlineAutoencoder(40, 20, 0.001);
    private final List<double[]> combatTrainingBatch = new CopyOnWriteArrayList<>();

    public DetectionPipeline(OverWatchML plugin) {
        this.plugin = plugin;
        
        // Initialize Cold Start with Synthetic Data
        xrayIF.updateModel(serverContext.generateSyntheticXrayData(200));
        combatIF.updateModel(serverContext.generateSyntheticCombatData(200));

        // Tier 3: Deep Profiler (runs every 5 mins per player via batch)
        mlExecutor.scheduleAtFixedRate(this::runDeepProfilerBatch, 5, 5, TimeUnit.MINUTES);
        
        // Online Learning: Incremental tree update & Autoencoder SGD
        mlExecutor.scheduleAtFixedRate(this::updateGlobalModels, 10, 10, TimeUnit.MINUTES);
    }

    public BehavioralProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, BehavioralProfile::new);
    }

    public void removeProfile(UUID uuid) {
        profiles.remove(uuid);
    }

    /**
     * TIER 1: INSTANT FILTER (Synchronous, <1ms)
     */
    public void processInstantFilter(Player player, CheatCategory category, int pointsAdded, String reason) {
        if (pointsAdded <= 0) return;
        
        BehavioralProfile profile = getProfile(player.getUniqueId());
        
        int currentPoints = (category == CheatCategory.XRAY) 
            ? profile.xraySuspicionScore.addAndGet(pointsAdded)
            : profile.combatSuspicionScore.addAndGet(pointsAdded);

        if (currentPoints >= 100) {
            triggerPunishment(player, category, "Tier 1 Instant Flag: " + reason);
            if (category == CheatCategory.XRAY) profile.xraySuspicionScore.set(0);
            else profile.combatSuspicionScore.set(0);
        }
    }

    /**
     * TIER 2: TEMPORAL ANOMALY ENGINE (Asynchronous, 5-20ms)
     */
    public void triggerTemporalAnalysis(Player player, CheatCategory category, double[] features) {
        UUID uuid = player.getUniqueId();
        
        CompletableFuture.runAsync(() -> {
            BehavioralProfile profile = getProfile(uuid);
            
            double isolationScore;
            double sequenceScore;
            
            if (category == CheatCategory.XRAY) {
                profile.addXrayEvent(features);
                isolationScore = xrayIF.getAnomalyScore(features);
                sequenceScore = evaluateSequenceAnomaly(profile.xrayEventWindow, profile.xrayWindowIndex);
                xrayTrainingBatch.add(features);
            } else {
                profile.addCombatEvent(features);
                isolationScore = combatIF.getAnomalyScore(features);
                sequenceScore = evaluateSequenceAnomaly(profile.combatEventWindow, profile.combatWindowIndex);
                combatTrainingBatch.add(features);
            }

            // Weighted ensemble
            double combinedScore = (isolationScore * 0.7) + (sequenceScore * 0.3);
            
            // Adapt threshold via Bayesian Trust
            double threshold = profile.getAdaptiveThreshold(0.85);

            if (combinedScore > threshold) {
                profile.updateTrust(true, combinedScore);
                flagTemporalAnomaly(player, category, combinedScore);
            } else {
                profile.updateTrust(false, combinedScore);
            }

        }, mlExecutor);
    }

    /**
     * LSTM-lite logic: Calculate variance and sequence divergence in recent events.
     */
    private double evaluateSequenceAnomaly(double[][] window, int currentIndex) {
        int count = 0;
        for (double[] event : window) if (event != null) count++;
        if (count < 10) return 0.0; // Need minimum events
        
        // Simplified sequence metric: average distance between consecutive feature states.
        // Aimbot/bot features often have extremely low variance between ticks.
        double totalDistance = 0;
        int validTransitions = 0;
        
        for (int i = 1; i < window.length; i++) {
            int curr = (currentIndex - i + window.length) % window.length;
            int prev = (currentIndex - i - 1 + window.length) % window.length;
            
            if (window[curr] != null && window[prev] != null) {
                totalDistance += net.denfry.owml.ml.v2.core.MathUtils.distance3D(window[curr], window[prev]);
                validTransitions++;
            }
        }
        
        if (validTransitions == 0) return 0.0;
        double avgDist = totalDistance / validTransitions;
        
        // Return inverse of variance (extreme consistency is anomaly)
        return Math.min(1.0, 1.0 / (1.0 + avgDist)); 
    }

    /**
     * TIER 3: DEEP BEHAVIORAL PROFILER
     */
    private void runDeepProfilerBatch() {
        for (BehavioralProfile profile : profiles.values()) {
            Player player = plugin.getServer().getPlayer(profile.playerId);
            if (player == null || !player.isOnline()) continue;

            // Extract aggregated feature vector from ring buffer means
            double[] aggXray = extractAggregatedFeatures(profile.xrayEventWindow, 32);
            if (aggXray != null) {
                double reconstructionError = xrayAE.getAnomalyScore(aggXray);
                if (reconstructionError > profile.getAdaptiveThreshold(0.92)) {
                    triggerPunishment(player, CheatCategory.XRAY, "Tier 3 Deep Profiler Flag");
                }
            }
            
            double[] aggCombat = extractAggregatedFeatures(profile.combatEventWindow, 40);
            if (aggCombat != null) {
                double reconstructionError = combatAE.getAnomalyScore(aggCombat);
                if (reconstructionError > profile.getAdaptiveThreshold(0.92)) {
                    triggerPunishment(player, CheatCategory.COMBAT_MOVEMENT, "Tier 3 Deep Profiler Flag");
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
                for (int i = 0; i < featureSize; i++) agg[i] += event[i];
            }
        }
        if (count == 0) return null;
        for (int i = 0; i < featureSize; i++) agg[i] /= count;
        return agg;
    }

    /**
     * Online Learning Updates
     */
    private void updateGlobalModels() {
        // Update Autoencoders (SGD) and Isolation Forests
        if (!xrayTrainingBatch.isEmpty()) {
            double[][] batch = xrayTrainingBatch.toArray(new double[0][]);
            xrayTrainingBatch.clear();
            xrayAE.trainBatch(batch);
            xrayIF.updateModel(batch);
        }
        
        if (!combatTrainingBatch.isEmpty()) {
            double[][] batch = combatTrainingBatch.toArray(new double[0][]);
            combatTrainingBatch.clear();
            combatAE.trainBatch(batch);
            combatIF.updateModel(batch);
        }
        
        plugin.getLogger().info("[OverWatch ML] Online models updated with new behavioral data.");
    }

    private void flagTemporalAnomaly(Player player, CheatCategory category, double score) {
        plugin.getLogger().warning("[OverWatch ML] Temporal anomaly detected for " + player.getName() + 
            " [" + category.name() + "] Score: " + String.format("%.2f", score));
        
        // Add to Tier 1 score to accelerate punishment
        processInstantFilter(player, category, 20, "Temporal Anomaly Aggregation");
    }

    private void triggerPunishment(Player player, CheatCategory category, String reason) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getConfig().getBoolean("ml.flag-only", true)) {
                plugin.getLogger().warning("[OverWatch ML] FLAG: " + player.getName() + " for " + category + ": " + reason);
            } else {
                plugin.getLogger().warning("[OverWatch ML] PUNISHING " + player.getName() + " for " + category + ": " + reason);
                plugin.getPunishmentManager().setPunishmentLevel(player, 3);
            }
        });
    }
    
    public void shutdown() {
        mlExecutor.shutdown();
        try {
            mlExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("ML Executor interrupted during shutdown.");
        }
    }
}
