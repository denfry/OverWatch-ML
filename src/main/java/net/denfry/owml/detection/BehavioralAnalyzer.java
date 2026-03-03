package net.denfry.owml.detection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Behavioral analyzer that detects suspicious patterns in player behavior.
 * Analyzes movement patterns, mining efficiency, and timing anomalies.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class BehavioralAnalyzer implements DetectionAnalyzer {

    private boolean enabled = true;

    @Override
    public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
        if (!enabled) {
            return DetectionResult.createSafe();
        }

        List<String> reasons = new ArrayList<>();
        double totalScore = 0.0;
        Map<String, Object> additionalData = new HashMap<>();

        // Analyze mining speed
        double miningSpeed = data.getAverageMiningSpeed();
        if (miningSpeed > 3.0) { // More than 3 blocks per second
            reasons.add("Excessive mining speed: " + String.format("%.1f", miningSpeed) + " blocks/sec");
            totalScore += 0.4;
            additionalData.put("miningSpeed", miningSpeed);
        }

        // Analyze mining efficiency
        double efficiency = data.getMiningEfficiency();
        if (efficiency > 50.0) { // More than 50 blocks per minute
            reasons.add("High mining efficiency: " + String.format("%.1f", efficiency) + " blocks/min");
            totalScore += 0.3;
            additionalData.put("miningEfficiency", efficiency);
        }

        // Analyze movement patterns
        double movementAnomaly = analyzeMovementPatterns(data);
        if (movementAnomaly > 0.7) {
            reasons.add("Unusual movement patterns detected");
            totalScore += movementAnomaly * 0.3;
            additionalData.put("movementAnomaly", movementAnomaly);
        }

        // Analyze timing patterns
        double timingAnomaly = analyzeTimingPatterns(data);
        if (timingAnomaly > 0.6) {
            reasons.add("Suspicious timing patterns in mining activity");
            totalScore += timingAnomaly * 0.2;
            additionalData.put("timingAnomaly", timingAnomaly);
        }

        // Analyze session behavior
        if (context.getAction() == DetectionContext.Action.BLOCK_BREAK) {
            double sessionAnomaly = analyzeSessionBehavior(data, context);
            if (sessionAnomaly > 0.5) {
                reasons.add("Abnormal session mining behavior");
                totalScore += sessionAnomaly * 0.25;
                additionalData.put("sessionAnomaly", sessionAnomaly);
            }
        }

        // Determine detection level
        DetectionLevel level = determineDetectionLevel(totalScore);

        return new DetectionResult(level, totalScore, reasons, DetectionType.BEHAVIORAL, additionalData);
    }

    /**
     * Analyze movement patterns for anomalies
     */
    private double analyzeMovementPatterns(PlayerDetectionData data) {
        Queue<Location> locations = data.getRecentLocations();
        if (locations.size() < 10) return 0.0;

        List<Location> locationList = new ArrayList<>(locations);
        double totalDistance = 0.0;
        int teleportCount = 0;

        for (int i = 1; i < locationList.size(); i++) {
            Location prev = locationList.get(i - 1);
            Location curr = locationList.get(i);

            double distance = prev.distance(curr);
            totalDistance += distance;

            // Detect teleports (distance > 10 blocks between ticks)
            if (distance > 10.0) {
                teleportCount++;
            }
        }

        double averageDistance = totalDistance / (locationList.size() - 1);

        // High teleport count or very consistent movement (bot-like) is suspicious
        double teleportRatio = (double) teleportCount / locationList.size();

        // Very consistent small movements might indicate automated behavior
        double consistencyScore = calculateMovementConsistency(locationList);

        return Math.max(teleportRatio * 0.8, consistencyScore * 0.6);
    }

    /**
     * Calculate movement consistency (lower values = more consistent = more suspicious)
     */
    private double calculateMovementConsistency(List<Location> locations) {
        if (locations.size() < 5) return 0.0;

        double[] distances = new double[locations.size() - 1];
        for (int i = 0; i < distances.length; i++) {
            distances[i] = locations.get(i).distance(locations.get(i + 1));
        }

        double mean = Arrays.stream(distances).average().orElse(0.0);
        double variance = Arrays.stream(distances)
                .map(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // Lower standard deviation = more consistent = more suspicious
        return Math.max(0.0, 1.0 - (stdDev / mean));
    }

    /**
     * Analyze timing patterns in mining activity
     */
    private double analyzeTimingPatterns(PlayerDetectionData data) {
        Queue<DetectionResult> recentDetections = data.getRecentDetections();
        if (recentDetections.size() < 5) return 0.0;

        // Look for patterns in detection timing
        List<DetectionResult> detections = new ArrayList<>(recentDetections);
        long[] timestamps = new long[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            // We don't have actual timestamps in DetectionResult, use index as proxy
            timestamps[i] = i * 1000L; // Assume 1 second intervals for analysis
        }

        // Calculate intervals between suspicious detections
        double averageInterval = 0.0;
        if (timestamps.length > 1) {
            long totalInterval = 0;
            for (int i = 1; i < timestamps.length; i++) {
                totalInterval += timestamps[i] - timestamps[i - 1];
            }
            averageInterval = (double) totalInterval / (timestamps.length - 1);
        }

        // Very regular intervals might indicate automated behavior
        double regularityScore = calculateTimingRegularity(timestamps);

        return regularityScore;
    }

    /**
     * Calculate timing regularity (1.0 = perfectly regular, 0.0 = random)
     */
    private double calculateTimingRegularity(long[] timestamps) {
        if (timestamps.length < 3) return 0.0;

        long[] intervals = new long[timestamps.length - 1];
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = timestamps[i + 1] - timestamps[i];
        }

        double mean = Arrays.stream(intervals).average().orElse(0.0);
        double variance = Arrays.stream(intervals)
                .mapToDouble(interval -> Math.pow(interval - mean, 2))
                .average()
                .orElse(0.0);

        double cv = Math.sqrt(variance) / mean; // Coefficient of variation

        // Lower CV = more regular timing = more suspicious
        return Math.max(0.0, 1.0 - cv);
    }

    /**
     * Analyze session behavior
     */
    private double analyzeSessionBehavior(PlayerDetectionData data, DetectionContext context) {
        // Check for sudden changes in behavior
        double currentEfficiency = data.getMiningEfficiency();
        double suspicionRatio = data.getSuspicionRatio();

        double anomalyScore = 0.0;

        // Sudden efficiency spikes
        if (currentEfficiency > 30.0) {
            anomalyScore += 0.3;
        }

        // High suspicion ratio
        if (suspicionRatio > 0.4) {
            anomalyScore += suspicionRatio * 0.4;
        }

        // Recent detection trend
        double trend = data.getRecentDetectionTrend();
        if (trend > 0.7) {
            anomalyScore += trend * 0.3;
        }

        return Math.min(1.0, anomalyScore);
    }

    /**
     * Determine detection level based on total score
     */
    private DetectionLevel determineDetectionLevel(double score) {
        if (score >= 0.8) return DetectionLevel.CRITICAL;
        if (score >= 0.6) return DetectionLevel.HIGH;
        if (score >= 0.4) return DetectionLevel.MEDIUM;
        if (score >= 0.2) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    @Override
    public DetectionType getDetectionType() {
        return DetectionType.BEHAVIORAL;
    }

    @Override
    public String getName() {
        return "Behavioral Analyzer";
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
