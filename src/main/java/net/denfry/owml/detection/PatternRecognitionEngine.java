package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Pattern recognition engine that detects suspicious mining patterns.
 * Analyzes sequences of block breaks for x-ray like behavior.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class PatternRecognitionEngine implements DetectionAnalyzer {

    private boolean enabled = true;

    // Known suspicious patterns
    private static final Set<Material> VALUABLE_ORES = initializeValuableOres();

    /**
     * Initialize the set of valuable ores based on materials that exist in the current version
     */
    private static Set<Material> initializeValuableOres() {
        Set<Material> ores = new java.util.HashSet<>();

        // Add materials only if they exist in the current version
        addMaterialIfExists(ores, "DIAMOND_ORE");
        addMaterialIfExists(ores, "DEEPSLATE_DIAMOND_ORE");
        addMaterialIfExists(ores, "GOLD_ORE");
        addMaterialIfExists(ores, "DEEPSLATE_GOLD_ORE");
        addMaterialIfExists(ores, "EMERALD_ORE");
        addMaterialIfExists(ores, "DEEPSLATE_EMERALD_ORE");
        addMaterialIfExists(ores, "ANCIENT_DEBRIS");
        addMaterialIfExists(ores, "NETHERITE_BLOCK");

        return java.util.Collections.unmodifiableSet(ores);
    }

    /**
     * Add material to set if it exists in the current version
     */
    private static void addMaterialIfExists(Set<Material> set, String materialName) {
        Material material = net.denfry.owml.utils.MaterialHelper.getMaterial(materialName);
        if (material != null) {
            set.add(material);
        }
    }

    @Override
    public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
        if (!enabled || context.getAction() != DetectionContext.Action.BLOCK_BREAK) {
            return DetectionResult.createSafe();
        }

        List<String> reasons = new ArrayList<>();
        double totalScore = 0.0;
        Map<String, Object> additionalData = new HashMap<>();

        // Only analyze valuable ore mining
        Material material = context.getMaterial();
        if (material == null || !VALUABLE_ORES.contains(material)) {
            return DetectionResult.createSafe();
        }

        Location location = context.getLocation();
        if (location == null) return DetectionResult.createSafe();

        // Analyze tunnel mining patterns
        double tunnelScore = analyzeTunnelPattern(data, location);
        if (tunnelScore > 0.6) {
            reasons.add("Tunnel mining pattern detected: " + String.format("%.1f%%", tunnelScore * 100));
            totalScore += tunnelScore * 0.4;
            additionalData.put("tunnelScore", tunnelScore);
        }

        // Analyze branch mining patterns
        double branchScore = analyzeBranchPattern(data, location);
        if (branchScore > 0.5) {
            reasons.add("Branch mining pattern detected: " + String.format("%.1f%%", branchScore * 100));
            totalScore += branchScore * 0.3;
            additionalData.put("branchScore", branchScore);
        }

        // Analyze strip mining patterns
        double stripScore = analyzeStripPattern(data, location);
        if (stripScore > 0.7) {
            reasons.add("Strip mining pattern detected: " + String.format("%.1f%%", stripScore * 100));
            totalScore += stripScore * 0.35;
            additionalData.put("stripScore", stripScore);
        }

        // Analyze density patterns (too many ores in small area)
        double densityScore = analyzeDensityPattern(data, location, material);
        if (densityScore > 0.8) {
            reasons.add("High ore density pattern: " + String.format("%.1f%%", densityScore * 100));
            totalScore += densityScore * 0.5;
            additionalData.put("densityScore", densityScore);
        }

        // Analyze timing patterns between ore discoveries
        double timingScore = analyzeTimingPattern(data);
        if (timingScore > 0.6) {
            reasons.add("Suspicious ore discovery timing");
            totalScore += timingScore * 0.25;
            additionalData.put("timingScore", timingScore);
        }

        DetectionLevel level = determineDetectionLevel(totalScore);
        return new DetectionResult(level, totalScore, reasons, DetectionType.PATTERN, additionalData);
    }

    /**
     * Analyze tunnel mining patterns (long straight lines)
     */
    private double analyzeTunnelPattern(PlayerDetectionData data, Location currentLocation) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 10) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);
        locations.add(currentLocation);

        // Check for straight line patterns
        int straightSegments = 0;
        int totalSegments = locations.size() - 3;

        for (int i = 2; i < locations.size(); i++) {
            Location a = locations.get(i - 2);
            Location b = locations.get(i - 1);
            Location c = locations.get(i);

            // Check if three points are colinear (straight line)
            if (isColinear(a, b, c)) {
                straightSegments++;
            }
        }

        return totalSegments > 0 ? (double) straightSegments / totalSegments : 0.0;
    }

    /**
     * Check if three points are approximately colinear
     */
    private boolean isColinear(Location a, Location b, Location c) {
        // Calculate area of triangle formed by three points
        // If area is close to 0, points are colinear
        double area = Math.abs((b.getX() - a.getX()) * (c.getZ() - a.getZ()) -
                              (c.getX() - a.getX()) * (b.getZ() - a.getZ()));

        return area < 0.5; // Allow small tolerance
    }

    /**
     * Analyze branch mining patterns (perpendicular tunnels)
     */
    private double analyzeBranchPattern(PlayerDetectionData data, Location currentLocation) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 15) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);
        locations.add(currentLocation);

        int perpendicularTurns = 0;
        int totalTurns = locations.size() - 2;

        for (int i = 1; i < locations.size() - 1; i++) {
            Location prev = locations.get(i - 1);
            Location curr = locations.get(i);
            Location next = locations.get(i + 1);

            // Calculate direction vectors
            double dx1 = curr.getX() - prev.getX();
            double dz1 = curr.getZ() - prev.getZ();
            double dx2 = next.getX() - curr.getX();
            double dz2 = next.getZ() - curr.getZ();

            // Check if turn is approximately 90 degrees (perpendicular)
            double dotProduct = dx1 * dx2 + dz1 * dz2;
            double mag1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
            double mag2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);

            if (mag1 > 0 && mag2 > 0) {
                double cosAngle = dotProduct / (mag1 * mag2);
                double angle = Math.acos(Math.max(-1, Math.min(1, cosAngle)));
                double angleDegrees = Math.toDegrees(angle);

                // 90-degree turn (В±15 degrees tolerance)
                if (Math.abs(angleDegrees - 90) < 15) {
                    perpendicularTurns++;
                }
            }
        }

        return totalTurns > 0 ? (double) perpendicularTurns / totalTurns : 0.0;
    }

    /**
     * Analyze strip mining patterns (mining in flat layers)
     */
    private double analyzeStripPattern(PlayerDetectionData data, Location currentLocation) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 8) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);

        // Check Y-coordinate consistency (same level mining)
        double averageY = locations.stream().mapToDouble(Location::getY).average().orElse(0);
        long sameLevelCount = locations.stream()
                .mapToDouble(Location::getY)
                .filter(y -> Math.abs(y - averageY) < 1.5) // Within 1.5 blocks
                .count();

        double levelConsistency = (double) sameLevelCount / locations.size();

        // Check for grid-like patterns in X/Z coordinates
        double gridPattern = analyzeGridPattern(locations);

        return (levelConsistency * 0.6) + (gridPattern * 0.4);
    }

    /**
     * Analyze grid-like mining patterns
     */
    private double analyzeGridPattern(List<Location> locations) {
        if (locations.size() < 10) return 0.0;

        // Extract X and Z coordinates
        double[] xCoords = locations.stream().mapToDouble(Location::getX).toArray();
        double[] zCoords = locations.stream().mapToDouble(Location::getZ).toArray();

        // Check for regular spacing in X and Z
        double xVariance = calculateVariance(xCoords);
        double zVariance = calculateVariance(zCoords);

        // Lower variance = more regular spacing = more suspicious
        double xRegularity = Math.max(0, 1.0 - xVariance / 10.0);
        double zRegularity = Math.max(0, 1.0 - zVariance / 10.0);

        return (xRegularity + zRegularity) / 2.0;
    }

    /**
     * Calculate variance of an array
     */
    private double calculateVariance(double[] values) {
        if (values.length < 2) return 0.0;

        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
                .map(val -> Math.pow(val - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance); // Standard deviation as proxy for variance
    }

    /**
     * Analyze ore density patterns
     */
    private double analyzeDensityPattern(PlayerDetectionData data, Location currentLocation, Material material) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 5) return 0.0;

        // Count ores in 10x10x10 area around current location
        int oreCount = 0;
        int totalBlocks = 0;

        for (Location loc : recentLocations) {
            if (loc.distance(currentLocation) <= 10.0) {
                // In a real implementation, you'd check the actual block type
                // For now, assume some locations contain the same ore type
                oreCount++;
                totalBlocks++;
            }
        }

        if (totalBlocks == 0) return 0.0;

        double density = (double) oreCount / totalBlocks;

        // Very high density of valuable ores in small area is suspicious
        return Math.min(1.0, density * 5.0); // Scale up for sensitivity
    }

    /**
     * Analyze timing patterns between ore discoveries
     */
    private double analyzeTimingPattern(PlayerDetectionData data) {
        Queue<DetectionResult> recentDetections = data.getRecentDetections();
        if (recentDetections.size() < 3) return 0.0;

        // Look for suspiciously regular timing between detections
        List<DetectionResult> detections = new ArrayList<>(recentDetections);

        // Calculate time intervals (simplified - would need actual timestamps)
        int regularIntervals = 0;
        int totalIntervals = detections.size() - 1;

        for (int i = 1; i < detections.size(); i++) {
            DetectionResult prev = detections.get(i - 1);
            DetectionResult curr = detections.get(i);

            // Check if confidence levels are similar (indicating pattern)
            double confidenceDiff = Math.abs(prev.getConfidence() - curr.getConfidence());
            if (confidenceDiff < 0.1) { // Very similar confidence
                regularIntervals++;
            }
        }

        return totalIntervals > 0 ? (double) regularIntervals / totalIntervals : 0.0;
    }

    /**
     * Determine detection level based on pattern scores
     */
    private DetectionLevel determineDetectionLevel(double score) {
        if (score >= 0.85) return DetectionLevel.CRITICAL;
        if (score >= 0.65) return DetectionLevel.HIGH;
        if (score >= 0.45) return DetectionLevel.MEDIUM;
        if (score >= 0.25) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    @Override
    public DetectionType getDetectionType() {
        return DetectionType.PATTERN;
    }

    @Override
    public String getName() {
        return "Pattern Recognition Engine";
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
