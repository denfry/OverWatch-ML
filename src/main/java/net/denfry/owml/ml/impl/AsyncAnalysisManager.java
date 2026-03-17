package net.denfry.owml.ml.impl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import net.denfry.owml.ml.AnalysisManager;
import net.denfry.owml.ml.DataCollector;
import net.denfry.owml.ml.MLCache;
import net.denfry.owml.ml.MLExecutor;
import net.denfry.owml.ml.MLMetrics;
import net.denfry.owml.ml.MLModel;
import net.denfry.owml.ml.PlayerMiningData;
import net.denfry.owml.ml.ReasoningMLModel;

/**
 * Асинхронный менеджер анализа игроков с кешированием и метриками
 */
public class AsyncAnalysisManager implements AnalysisManager {

    private final Logger logger;
    private final MLExecutor executor;
    private final MLModel model;
    private final DataCollector dataCollector;
    private final MLCache cache;
    private final MLMetrics metrics;

    // Track active analyses
    private final Map<UUID, Long> activeAnalyses = new ConcurrentHashMap<>();
    private final Map<UUID, ReasoningMLModel.DetectionResult> lastResults = new ConcurrentHashMap<>();

    public AsyncAnalysisManager(MLExecutor executor, MLModel model,
                               DataCollector dataCollector, MLCache cache, MLMetrics metrics) {
        this.executor = executor;
        this.model = model;
        this.dataCollector = dataCollector;
        this.cache = cache;
        this.metrics = metrics;
        this.logger = Bukkit.getLogger();
    }

    @Override
    public CompletableFuture<ReasoningMLModel.DetectionResult> analyzeAsync(UUID playerId) {
        // Check cache first
        MLCache.CacheEntry cachedResult = cache.getAnalysisResult(playerId);
        if (cachedResult != null && !cachedResult.isExpired(TimeUnit.MINUTES.toMillis(2))) {
            metrics.recordCacheHit();
            return CompletableFuture.completedFuture(cachedResult.result);
        }

        metrics.recordCacheMiss();

        // Perform analysis asynchronously
        return executor.submitMLTask((Callable<ReasoningMLModel.DetectionResult>) () -> {
            long startTime = System.currentTimeMillis();

            try {
                PlayerMiningData data = dataCollector.getCurrentData(playerId);
                if (data == null) {
                    return new ReasoningMLModel.DetectionResult(0.0, "No mining data available", new java.util.ArrayList<>());
                }

                ReasoningMLModel.DetectionResult result = model.analyze(data);

                long duration = System.currentTimeMillis() - startTime;
                metrics.recordPrediction(result, duration);

                // Cache the result
                cache.putAnalysisResult(playerId, result, 0.8); // Assumed confidence

                // Save last result
                lastResults.put(playerId, result);

                return result;

            } catch (Exception e) {
                logger.warning("Failed to analyze player " + playerId + ": " + e.getMessage());
                metrics.recordPrediction(null, System.currentTimeMillis() - startTime);

                // Return last known result or NORMAL
                return lastResults.getOrDefault(playerId, new ReasoningMLModel.DetectionResult(0.0, "No previous result available", new java.util.ArrayList<>()));
            }
        });
    }

    @Override
    public ReasoningMLModel.DetectionResult analyzeSync(UUID playerId) {
        try {
            return analyzeAsync(playerId).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("Sync analysis failed for player " + playerId + ": " + e.getMessage());
            return new ReasoningMLModel.DetectionResult(0.0, "Analysis failed", new java.util.ArrayList<>());
        }
    }

    @Override
    public double getSuspicionScore(UUID playerId) {
        // Return the probability from the last detection result
        ReasoningMLModel.DetectionResult lastResult = lastResults.get(playerId);
        if (lastResult == null) {
            return 0.0;
        }

        return lastResult.getProbability();
    }

    @Override
    public boolean isUnderAnalysis(UUID playerId) {
        Long endTime = activeAnalyses.get(playerId);
        if (endTime == null) {
            return false;
        }

        if (System.currentTimeMillis() > endTime) {
            activeAnalyses.remove(playerId);
            return false;
        }

        return true;
    }

    @Override
    public void startAnalysis(UUID playerId, long durationInSeconds) {
        long endTime = System.currentTimeMillis() + (durationInSeconds * 1000L);
        activeAnalyses.put(playerId, endTime);

        // Automatically stop analysis after specified time
        executor.runTaskLater(() -> stopAnalysis(playerId), durationInSeconds * 20L);
    }

    @Override
    public void stopAnalysis(UUID playerId) {
        activeAnalyses.remove(playerId);
        cache.invalidatePlayer(playerId); // Clear cache for this player
    }

    @Override
    public long getRemainingAnalysisTime(UUID playerId) {
        Long endTime = activeAnalyses.get(playerId);
        if (endTime == null) {
            return -1;
        }

        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    /**
     * Очищает завершенные анализы
     */
    public void cleanupCompletedAnalyses() {
        long currentTime = System.currentTimeMillis();
        activeAnalyses.entrySet().removeIf(entry -> entry.getValue() <= currentTime);
    }
}
