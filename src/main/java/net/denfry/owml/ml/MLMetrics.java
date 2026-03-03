package net.denfry.owml.ml;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Метрики производительности ML системы
 */
public class MLMetrics {

    private final AtomicLong totalPredictions = new AtomicLong(0);
    private final AtomicLong successfulPredictions = new AtomicLong(0);
    private final AtomicLong failedPredictions = new AtomicLong(0);
    private final AtomicLong trainingSessions = new AtomicLong(0);
    private final AtomicLong totalAnalysisTimeMs = new AtomicLong(0);
    private final AtomicLong totalTrainingTimeMs = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Записывает результат предсказания
     */
    public void recordPrediction(ReasoningMLModel.DetectionResult result, long durationMs) {
        totalPredictions.incrementAndGet();
        totalAnalysisTimeMs.addAndGet(durationMs);

        if (result != null) {
            successfulPredictions.incrementAndGet();
        } else {
            failedPredictions.incrementAndGet();
        }
    }

    /**
     * Записывает успешное обучение
     */
    public void recordTraining(long durationMs) {
        trainingSessions.incrementAndGet();
        totalTrainingTimeMs.addAndGet(durationMs);
    }

    /**
     * Записывает попадание в кеш
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /**
     * Записывает промах кеша
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    /**
     * Получает статистику производительности
     */
    public MLStats getStats() {
        long totalPreds = totalPredictions.get();
        long totalTrain = trainingSessions.get();

        return new MLStats(
            totalPreds,
            successfulPredictions.get(),
            failedPredictions.get(),
            totalTrain,
            totalPreds > 0 ? (double) totalAnalysisTimeMs.get() / totalPreds : 0,
            totalTrain > 0 ? (double) totalTrainingTimeMs.get() / totalTrain : 0,
            getCacheHitRate()
        );
    }

    /**
     * Получает процент попаданий в кеш
     */
    private double getCacheHitRate() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Сбрасывает все метрики
     */
    public void reset() {
        totalPredictions.set(0);
        successfulPredictions.set(0);
        failedPredictions.set(0);
        trainingSessions.set(0);
        totalAnalysisTimeMs.set(0);
        totalTrainingTimeMs.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Класс для хранения статистики
     */
    public static class MLStats {
        public final long totalPredictions;
        public final long successfulPredictions;
        public final long failedPredictions;
        public final long trainingSessions;
        public final double avgPredictionTimeMs;
        public final double avgTrainingTimeMs;
        public final double cacheHitRate;

        public MLStats(long totalPredictions, long successfulPredictions, long failedPredictions,
                       long trainingSessions, double avgPredictionTimeMs, double avgTrainingTimeMs,
                       double cacheHitRate) {
            this.totalPredictions = totalPredictions;
            this.successfulPredictions = successfulPredictions;
            this.failedPredictions = failedPredictions;
            this.trainingSessions = trainingSessions;
            this.avgPredictionTimeMs = avgPredictionTimeMs;
            this.avgTrainingTimeMs = avgTrainingTimeMs;
            this.cacheHitRate = cacheHitRate;
        }

        @Override
        public String toString() {
            return String.format(
                "ML Stats: Predictions=%d (Success:%.1f%%), Training=%d, " +
                "Avg Prediction=%.2fms, Avg Training=%.2fms, Cache Hit=%.1f%%",
                totalPredictions,
                totalPredictions > 0 ? (double) successfulPredictions / totalPredictions * 100 : 0,
                trainingSessions,
                avgPredictionTimeMs,
                avgTrainingTimeMs,
                cacheHitRate * 100
            );
        }
    }
}
