package net.denfry.owml.ml;

import java.time.Duration;
import java.util.UUID;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Кеш для ML данных и результатов анализа
 */
public class MLCache {

    // Cache for player analysis results
    private final Cache<UUID, CacheEntry> analysisCache;

    // Cache for player features
    private final Cache<UUID, double[]> featuresCache;

    // Cache for player data
    private final Cache<UUID, PlayerMiningData> playerDataCache;

    public MLCache() {
        // Analysis results cache (short-lived)
        this.analysisCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();

        // Features cache (medium duration)
        this.featuresCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();

        // Player data cache (long-lived)
        this.playerDataCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    }

    /**
     * Кеширует результат анализа
     */
    public void putAnalysisResult(UUID playerId, ReasoningMLModel.DetectionResult result, double confidence) {
        analysisCache.put(playerId, new CacheEntry(result, confidence, System.currentTimeMillis()));
    }

    /**
     * Получает закешированный результат анализа
     */
    public CacheEntry getAnalysisResult(UUID playerId) {
        return analysisCache.getIfPresent(playerId);
    }

    /**
     * Кеширует признаки игрока
     */
    public void putFeatures(UUID playerId, double[] features) {
        featuresCache.put(playerId, features);
    }

    /**
     * Получает закешированные признаки
     */
    public double[] getFeatures(UUID playerId) {
        return featuresCache.getIfPresent(playerId);
    }

    /**
     * Кеширует данные игрока
     */
    public void putPlayerData(UUID playerId, PlayerMiningData data) {
        playerDataCache.put(playerId, data);
    }

    /**
     * Получает закешированные данные игрока
     */
    public PlayerMiningData getPlayerData(UUID playerId) {
        return playerDataCache.getIfPresent(playerId);
    }

    /**
     * Удаляет все данные игрока из кеша
     */
    public void invalidatePlayer(UUID playerId) {
        analysisCache.invalidate(playerId);
        featuresCache.invalidate(playerId);
        playerDataCache.invalidate(playerId);
    }

    /**
     * Очищает весь кеш
     */
    public void clearAll() {
        analysisCache.invalidateAll();
        featuresCache.invalidateAll();
        playerDataCache.invalidateAll();
    }

    /**
     * Получает статистику кеша
     */
    public CacheStats getStats() {
        return new CacheStats(
            analysisCache.stats(),
            featuresCache.stats(),
            playerDataCache.stats()
        );
    }

    /**
     * Настраивает параметры кеша
     */
    public void configureCache(int analysisMaxSize, Duration analysisExpiry,
                              int featuresMaxSize, Duration featuresExpiry,
                              int dataMaxSize, Duration dataExpiry) {
        // Note: In a real application, caches can be recreated with new parameters
        // But for simplicity, we leave it as is
    }

    /**
     * Запись в кеше с меткой времени
     */
    public static class CacheEntry {
        public final ReasoningMLModel.DetectionResult result;
        public final double confidence;
        public final long timestamp;

        public CacheEntry(ReasoningMLModel.DetectionResult result, double confidence, long timestamp) {
            this.result = result;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }

        public boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }

    /**
     * Статистика кеша
     */
    public static class CacheStats {
        public final com.github.benmanes.caffeine.cache.stats.CacheStats analysisStats;
        public final com.github.benmanes.caffeine.cache.stats.CacheStats featuresStats;
        public final com.github.benmanes.caffeine.cache.stats.CacheStats dataStats;

        public CacheStats(com.github.benmanes.caffeine.cache.stats.CacheStats analysisStats,
                         com.github.benmanes.caffeine.cache.stats.CacheStats featuresStats,
                         com.github.benmanes.caffeine.cache.stats.CacheStats dataStats) {
            this.analysisStats = analysisStats;
            this.featuresStats = featuresStats;
            this.dataStats = dataStats;
        }

        @Override
        public String toString() {
            return String.format(
                "Cache Stats:\n" +
                "  Analysis - Hits: %d, Misses: %d, Hit Rate: %.2f%%\n" +
                "  Features - Hits: %d, Misses: %d, Hit Rate: %.2f%%\n" +
                "  Data - Hits: %d, Misses: %d, Hit Rate: %.2f%%",
                analysisStats.hitCount(), analysisStats.missCount(),
                analysisStats.hitRate() * 100,
                featuresStats.hitCount(), featuresStats.missCount(),
                featuresStats.hitRate() * 100,
                dataStats.hitCount(), dataStats.missCount(),
                dataStats.hitRate() * 100
            );
        }
    }
}
