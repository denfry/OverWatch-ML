package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Path prediction analyzer that detects when players are mining in ways
 * that suggest they know ore locations without exploration.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class PathPredictor implements DetectionAnalyzer {

    private boolean enabled = true;

    // Valuable ores that players might be targeting
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

        Material material = context.getMaterial();
        if (material == null || !VALUABLE_ORES.contains(material)) {
            return DetectionResult.createSafe();
        }

        Location location = context.getLocation();
        if (location == null) return DetectionResult.createSafe();

        List<String> reasons = new ArrayList<>();
        double totalScore = 0.0;
        Map<String, Object> additionalData = new HashMap<>();

        // Analyze mining path efficiency
        double efficiencyScore = analyzeMiningEfficiency(data, location);
        if (efficiencyScore > 0.8) {
            reasons.add("Unusually efficient ore discovery path: " +
                String.format("%.1f%%", efficiencyScore * 100));
            totalScore += efficiencyScore * 0.4;
            additionalData.put("efficiencyScore", efficiencyScore);
        }

        // Analyze directional consistency
        double directionScore = analyzeDirectionalConsistency(data);
        if (directionScore > 0.7) {
            reasons.add("Highly directional mining pattern detected");
            totalScore += directionScore * 0.3;
            additionalData.put("directionScore", directionScore);
        }

        // Analyze exploration vs exploitation ratio
        double explorationRatio = analyzeExplorationRatio(data);
        if (explorationRatio < 0.3) {
            reasons.add("Low exploration ratio: " + String.format("%.1f%%", explorationRatio * 100));
            totalScore += (1.0 - explorationRatio) * 0.35;
            additionalData.put("explorationRatio", explorationRatio);
        }

        // Analyze ore clustering patterns
        double clusteringScore = analyzeOreClustering(data, material);
        if (clusteringScore > 0.75) {
            reasons.add("Suspicious ore clustering pattern");
            totalScore += clusteringScore * 0.4;
            additionalData.put("clusteringScore", clusteringScore);
        }

        DetectionLevel level = determineDetectionLevel(totalScore);
        return new DetectionResult(level, totalScore, reasons, DetectionType.PATH_PREDICTION, additionalData);
    }

    /**
     * Analyze mining path efficiency
     */
    private double analyzeMiningEfficiency(PlayerDetectionData data, Location currentLocation) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 5) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);
        locations.add(currentLocation);

        // Calculate path straightness (how direct the path to ores is)
        double straightness = calculatePathStraightness(locations);

        // Calculate ore discovery rate vs blocks mined
        int valuableOres = countValuableOres(locations);
        int totalBlocks = locations.size();

        double discoveryRate = totalBlocks > 0 ? (double) valuableOres / totalBlocks : 0.0;

        // High straightness + high discovery rate = very suspicious
        return (straightness * 0.6) + (discoveryRate * 2.0);
    }

    /**
     * Calculate how straight/direct a mining path is
     */
    private double calculatePathStraightness(List<Location> locations) {
        if (locations.size() < 3) return 0.0;

        Location start = locations.get(0);
        Location end = locations.get(locations.size() - 1);

        double directDistance = start.distance(end);
        double actualDistance = 0.0;

        for (int i = 1; i < locations.size(); i++) {
            actualDistance += locations.get(i - 1).distance(locations.get(i));
        }

        if (actualDistance == 0) return 0.0;

        // Perfect straightness = 1.0, very winding = 0.0
        double straightness = directDistance / actualDistance;

        // Values > 1.0 indicate backtracking (suspicious)
        return Math.min(2.0, straightness);
    }

    /**
     * Count valuable ores in location history
     */
    private int countValuableOres(List<Location> locations) {
        // In a real implementation, you'd check actual block types
        // For now, simulate based on position
        return (int) locations.stream()
                .filter(loc -> isLikelyOreLocation(loc))
                .count();
    }

    /**
     * Check if location is likely to contain valuable ore (simplified)
     */
    private boolean isLikelyOreLocation(Location loc) {
        // Simple simulation: certain Y levels are more likely to have ores
        int y = loc.getBlockY();
        return (y >= 5 && y <= 20) || (y >= -50 && y <= -10);
    }

    /**
     * Analyze directional consistency in mining
     */
    private double analyzeDirectionalConsistency(PlayerDetectionData data) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 10) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);

        // Calculate movement vectors
        List<double[]> vectors = new ArrayList<>();
        for (int i = 1; i < locations.size(); i++) {
            Location prev = locations.get(i - 1);
            Location curr = locations.get(i);
            double[] vector = {
                curr.getX() - prev.getX(),
                curr.getY() - prev.getY(),
                curr.getZ() - prev.getZ()
            };
            vectors.add(vector);
        }

        if (vectors.size() < 2) return 0.0;

        // Calculate average direction
        double[] avgVector = new double[3];
        for (double[] vector : vectors) {
            for (int i = 0; i < 3; i++) {
                avgVector[i] += vector[i];
            }
        }
        for (int i = 0; i < 3; i++) {
            avgVector[i] /= vectors.size();
        }

        // Calculate consistency (how much vectors align with average direction)
        double totalAlignment = 0.0;
        for (double[] vector : vectors) {
            double alignment = calculateVectorAlignment(avgVector, vector);
            totalAlignment += alignment;
        }

        return totalAlignment / vectors.size();
    }

    /**
     * Calculate alignment between two vectors (dot product normalized)
     */
    private double calculateVectorAlignment(double[] v1, double[] v2) {
        double dotProduct = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
        double mag1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
        double mag2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]);

        if (mag1 == 0 || mag2 == 0) return 0.0;

        return dotProduct / (mag1 * mag2);
    }

    /**
     * Analyze exploration vs exploitation ratio
     */
    private double analyzeExplorationRatio(PlayerDetectionData data) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 20) return 0.5; // Neutral

        List<Location> locations = new ArrayList<>(recentLocations);

        // Calculate unique areas explored
        Set<String> uniqueChunks = new HashSet<>();
        Set<String> uniqueAreas = new HashSet<>();

        for (Location loc : locations) {
            String chunkKey = loc.getBlockX() / 16 + "," + loc.getBlockZ() / 16;
            uniqueChunks.add(chunkKey);

            String areaKey = loc.getBlockX() / 5 + "," + loc.getBlockY() / 5 + "," + loc.getBlockZ() / 5;
            uniqueAreas.add(areaKey);
        }

        // Exploration ratio = unique areas / total locations
        return Math.min(1.0, (double) uniqueAreas.size() / locations.size());
    }

    /**
     * Analyze ore clustering patterns
     */
    private double analyzeOreClustering(PlayerDetectionData data, Material targetMaterial) {
        Queue<Location> recentLocations = data.getRecentLocations();
        if (recentLocations.size() < 10) return 0.0;

        List<Location> locations = new ArrayList<>(recentLocations);

        // Count ore locations within clusters
        int clusterCount = 0;
        int totalOres = 0;

        for (Location loc : locations) {
            if (isLikelyOreLocation(loc)) {
                totalOres++;

                // Check if there are other ores nearby
                int nearbyOres = countNearbyOres(loc, locations);
                if (nearbyOres >= 3) {
                    clusterCount++;
                }
            }
        }

        if (totalOres == 0) return 0.0;

        // High clustering ratio is suspicious
        double clusteringRatio = (double) clusterCount / totalOres;
        return Math.min(1.0, clusteringRatio * 2.0);
    }

    /**
     * Count ores near a specific location
     */
    private int countNearbyOres(Location center, List<Location> allLocations) {
        return (int) allLocations.stream()
                .filter(loc -> isLikelyOreLocation(loc))
                .filter(loc -> loc.distance(center) <= 5.0)
                .count();
    }

    /**
     * Determine detection level based on path analysis
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
        return DetectionType.PATH_PREDICTION;
    }

    @Override
    public String getName() {
        return "Path Prediction Analyzer";
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
