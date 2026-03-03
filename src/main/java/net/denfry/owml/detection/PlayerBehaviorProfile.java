package net.denfry.owml.detection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level profile of a player's behavior.
 * This acts as the centralized object for all calculated metrics and detection scores.
 */
public class PlayerBehaviorProfile {
    private final UUID playerId;
    private final Map<String, Double> metrics = new ConcurrentHashMap<>();
    private final Map<String, Double> detectionScores = new ConcurrentHashMap<>();
    private long lastAnalysisTime;

    public PlayerBehaviorProfile(UUID playerId) {
        this.playerId = playerId;
        this.lastAnalysisTime = System.currentTimeMillis();
    }

    /**
     * Updates a behavioral metric.
     * 
     * @param key   Metric key.
     * @param value Metric value.
     */
    public void setMetric(String key, double value) {
        metrics.put(key, value);
    }

    /**
     * Retrieves a behavioral metric.
     * 
     * @param key Metric key.
     * @return Metric value or 0.0 if not found.
     */
    public double getMetric(String key) {
        return metrics.getOrDefault(key, 0.0);
    }

    /**
     * Sets a detection score for a specific category.
     * 
     * @param category Cheat category.
     * @param score    Score (0.0 to 1.0).
     */
    public void setDetectionScore(String category, double score) {
        detectionScores.put(category, score);
    }

    /**
     * Retrieves a detection score.
     * 
     * @param category Cheat category.
     * @return Score or 0.0 if not found.
     */
    public double getDetectionScore(String category) {
        return detectionScores.getOrDefault(category, 0.0);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public Map<String, Double> getDetectionScores() {
        return detectionScores;
    }

    public long getLastAnalysisTime() {
        return lastAnalysisTime;
    }

    public void updateAnalysisTime() {
        this.lastAnalysisTime = System.currentTimeMillis();
    }
}
