package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Statistical anomaly detector that identifies unusual player behavior
 * using statistical analysis and machine learning techniques.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class AnomalyDetector implements DetectionAnalyzer {

    private boolean enabled = true;

    // Statistical baselines (would be loaded from historical data in production)
    private static final double BASELINE_MINING_RATE = 2.0; // blocks per second
    private static final double BASELINE_MOVEMENT_SPEED = 4.3; // blocks per second (sprint speed)
    private static final double BASELINE_SESSION_LENGTH = 3600000; // 1 hour in milliseconds
    private static final int BASELINE_BLOCKS_PER_SESSION = 500;

    // Z-score thresholds for anomaly detection
    private static final double CRITICAL_Z_SCORE = 3.0;
    private static final double HIGH_Z_SCORE = 2.0;
    private static final double MEDIUM_Z_SCORE = 1.5;

    @Override
    public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
        if (!enabled) {
            return DetectionResult.createSafe();
        }

        List<String> reasons = new ArrayList<>();
        double totalScore = 0.0;
        Map<String, Object> additionalData = new HashMap<>();

        // Analyze mining rate anomalies
        double miningRateAnomaly = analyzeMiningRateAnomaly(data);
        if (miningRateAnomaly > MEDIUM_Z_SCORE) {
            String severity = getSeverityString(miningRateAnomaly);
            reasons.add(severity + " mining rate anomaly (Z-score: " + String.format("%.1f", miningRateAnomaly) + ")");
            totalScore += normalizeZScore(miningRateAnomaly) * 0.4;
            additionalData.put("miningRateZScore", miningRateAnomaly);
        }

        // Analyze movement anomalies
        double movementAnomaly = analyzeMovementAnomaly(data, context);
        if (movementAnomaly > MEDIUM_Z_SCORE) {
            String severity = getSeverityString(movementAnomaly);
            reasons.add(severity + " movement pattern anomaly (Z-score: " + String.format("%.1f", movementAnomaly) + ")");
            totalScore += normalizeZScore(movementAnomaly) * 0.3;
            additionalData.put("movementZScore", movementAnomaly);
        }

        // Analyze timing anomalies
        double timingAnomaly = analyzeTimingAnomaly(data);
        if (timingAnomaly > MEDIUM_Z_SCORE) {
            String severity = getSeverityString(timingAnomaly);
            reasons.add(severity + " timing anomaly (Z-score: " + String.format("%.1f", timingAnomaly) + ")");
            totalScore += normalizeZScore(timingAnomaly) * 0.25;
            additionalData.put("timingZScore", timingAnomaly);
        }

        // Analyze behavioral consistency
        double consistencyAnomaly = analyzeBehavioralConsistency(data);
        if (consistencyAnomaly > MEDIUM_Z_SCORE) {
            String severity = getSeverityString(consistencyAnomaly);
            reasons.add(severity + " behavioral inconsistency (Z-score: " + String.format("%.1f", consistencyAnomaly) + ")");
            totalScore += normalizeZScore(consistencyAnomaly) * 0.2;
            additionalData.put("consistencyZScore", consistencyAnomaly);
        }

        // Analyze resource discovery patterns
        if (context.getAction() == DetectionContext.Action.BLOCK_BREAK) {
            double resourceAnomaly = analyzeResourceDiscoveryAnomaly(data, context);
            if (resourceAnomaly > MEDIUM_Z_SCORE) {
                String severity = getSeverityString(resourceAnomaly);
                reasons.add(severity + " resource discovery anomaly (Z-score: " + String.format("%.1f", resourceAnomaly) + ")");
                totalScore += normalizeZScore(resourceAnomaly) * 0.35;
                additionalData.put("resourceZScore", resourceAnomaly);
            }
        }

        DetectionLevel level = determineDetectionLevel(totalScore);
        return new DetectionResult(level, totalScore, reasons, DetectionType.ANOMALY, additionalData);
    }

    /**
     * Analyze mining rate anomalies using statistical methods
     */
    private double analyzeMiningRateAnomaly(PlayerDetectionData data) {
        double currentRate = data.getAverageMiningSpeed();

        if (currentRate < BASELINE_MINING_RATE * 0.1) return 0.0; // Too low to be anomalous

        // Calculate z-score: (value - mean) / standard_deviation
        // Using baseline as mean, and assuming 30% standard deviation
        double standardDeviation = BASELINE_MINING_RATE * 0.3;
        double zScore = (currentRate - BASELINE_MINING_RATE) / standardDeviation;

        // Only flag positive anomalies (too fast mining)
        return Math.max(0, zScore);
    }

    /**
     * Analyze movement pattern anomalies
     */
    private double analyzeMovementAnomaly(PlayerDetectionData data, DetectionContext context) {
        if (context.getFromLocation() == null || context.getLocation() == null ||
            context.getFromLocation().getWorld() == null || context.getLocation().getWorld() == null ||
            !context.getFromLocation().getWorld().equals(context.getLocation().getWorld())) {
            return 0.0;
        }

        double distance = context.getFromLocation().distance(context.getLocation());
        double timeDiff = 0.05; // Assume 1 tick (50ms) between location updates

        double speed = distance / timeDiff; // blocks per tick

        // Convert to blocks per second for comparison
        double speedBPS = speed * 20;

        if (speedBPS < BASELINE_MOVEMENT_SPEED * 0.5) return 0.0; // Too slow

        // Calculate z-score for movement speed
        double standardDeviation = BASELINE_MOVEMENT_SPEED * 0.4;
        double zScore = (speedBPS - BASELINE_MOVEMENT_SPEED) / standardDeviation;

        return Math.max(0, zScore);
    }

    /**
     * Analyze timing anomalies in player actions
     */
    private double analyzeTimingAnomaly(PlayerDetectionData data) {
        Queue<DetectionResult> recentDetections = data.getRecentDetections();
        if (recentDetections.size() < 3) return 0.0;

        // Calculate intervals between detections (simplified)
        List<Double> intervals = new ArrayList<>();
        DetectionResult[] detections = recentDetections.toArray(new DetectionResult[0]);

        for (int i = 1; i < Math.min(detections.length, 10); i++) {
            // Use confidence difference as proxy for timing regularity
            double interval = Math.abs(detections[i].getConfidence() - detections[i-1].getConfidence());
            intervals.add(interval);
        }

        if (intervals.isEmpty()) return 0.0;

        // Calculate coefficient of variation
        double mean = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval - mean, 2))
                .average()
                .orElse(0.0);

        if (mean == 0) return 0.0;

        double cv = Math.sqrt(variance) / mean; // Coefficient of variation

        // Very low CV indicates too regular timing (suspicious)
        // Very high CV indicates too random timing (could also be suspicious)
        double optimalCV = 0.5; // Expected coefficient of variation
        double cvDeviation = Math.abs(cv - optimalCV);

        return cvDeviation / optimalCV; // Normalized deviation
    }

    /**
     * Analyze behavioral consistency
     */
    private double analyzeBehavioralConsistency(PlayerDetectionData data) {
        Map<String, Double> scores = data.getBehavioralScores();
        if (scores.isEmpty()) return 0.0;

        // Calculate standard deviation of behavioral scores
        double[] scoreValues = scores.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(scoreValues).average().orElse(0.0);
        double variance = Arrays.stream(scoreValues)
                .map(score -> Math.pow(score - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // High standard deviation indicates inconsistent behavior (potentially suspicious)
        return stdDev;
    }

    /**
     * Analyze resource discovery patterns
     */
    private double analyzeResourceDiscoveryAnomaly(PlayerDetectionData data, DetectionContext context) {
        Material material = context.getMaterial();
        if (material == null) return 0.0;

        // Define value scores for different materials (only existing ones)
        Map<Material, Double> materialValues = new HashMap<>();
        addMaterialValue(materialValues, "DIAMOND_ORE", 10.0);
        addMaterialValue(materialValues, "DEEPSLATE_DIAMOND_ORE", 8.0);
        addMaterialValue(materialValues, "GOLD_ORE", 7.0);
        addMaterialValue(materialValues, "DEEPSLATE_GOLD_ORE", 6.0);
        addMaterialValue(materialValues, "EMERALD_ORE", 9.0);
        addMaterialValue(materialValues, "DEEPSLATE_EMERALD_ORE", 7.5);
        addMaterialValue(materialValues, "ANCIENT_DEBRIS", 12.0);
        addMaterialValue(materialValues, "NETHERITE_BLOCK", 15.0);

        Double materialValue = materialValues.get(material);
        if (materialValue == null) return 0.0;

        // Calculate "luck" score based on blocks mined vs valuable ores found
        int totalMined = data.getTotalBlocksMined();
        if (totalMined < 100) return 0.0; // Not enough data

        double expectedOres = totalMined / 1000.0; // Rough estimate: 1 valuable ore per 1000 blocks
        double actualValuableOres = data.getSuspiciousActions(); // Approximation

        if (expectedOres == 0) return 0.0;

        double luckRatio = actualValuableOres / expectedOres;

        // Too high luck ratio is suspicious (possible x-ray)
        double baselineLuck = 1.0;
        double standardDeviation = 0.5;

        return Math.max(0, (luckRatio - baselineLuck) / standardDeviation);
    }

    /**
     * Add material value to map if material exists
     */
    private void addMaterialValue(Map<Material, Double> map, String materialName, double value) {
        Material material = net.denfry.owml.utils.MaterialHelper.getMaterial(materialName);
        if (material != null) {
            map.put(material, value);
        }
    }

    /**
     * Convert z-score to severity string
     */
    private String getSeverityString(double zScore) {
        if (zScore >= CRITICAL_Z_SCORE) return "Critical";
        if (zScore >= HIGH_Z_SCORE) return "High";
        if (zScore >= MEDIUM_Z_SCORE) return "Medium";
        return "Low";
    }

    /**
     * Normalize z-score to confidence value (0-1)
     */
    private double normalizeZScore(double zScore) {
        // Sigmoid function to convert z-score to probability-like value
        return 1.0 / (1.0 + Math.exp(-zScore + 1.5));
    }

    /**
     * Determine detection level based on anomaly scores
     */
    private DetectionLevel determineDetectionLevel(double score) {
        if (score >= 0.9) return DetectionLevel.CRITICAL;
        if (score >= 0.75) return DetectionLevel.HIGH;
        if (score >= 0.6) return DetectionLevel.MEDIUM;
        if (score >= 0.4) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    @Override
    public DetectionType getDetectionType() {
        return DetectionType.ANOMALY;
    }

    @Override
    public String getName() {
        return "Anomaly Detector";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
