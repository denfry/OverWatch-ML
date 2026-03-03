package net.denfry.owml.api.detectors;

import org.bukkit.entity.Player;
import net.denfry.owml.detection.DetectionAnalyzer;
import net.denfry.owml.detection.DetectionContext;
import net.denfry.owml.detection.DetectionLevel;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.DetectionType;
import net.denfry.owml.detection.PlayerDetectionData;

/**
 * Base class for custom detection analyzers.
 * Developers can extend this class to create custom detection algorithms.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public abstract class CustomDetector implements DetectionAnalyzer {

    private boolean enabled = true;
    private String detectorName;
    private String detectorDescription;
    private DetectorPriority priority = DetectorPriority.NORMAL;

    /**
     * Create a new custom detector
     *
     * @param name the detector name
     * @param description the detector description
     */
    protected CustomDetector(String name, String description) {
        this.detectorName = name;
        this.detectorDescription = description;
    }

    /**
     * Main detection method to be implemented by subclasses
     *
     * @param player the player to analyze
     * @param data the player's detection data
     * @param context the detection context
     * @return detection result
     */
    protected abstract DetectionResult performAnalysis(Player player, PlayerDetectionData data, DetectionContext context);

    /**
     * Optional: Called before analysis to prepare data
     */
    protected void preAnalysis(Player player, PlayerDetectionData data, DetectionContext context) {
        // Default: do nothing
    }

    /**
     * Optional: Called after analysis to clean up or log
     */
    protected void postAnalysis(Player player, DetectionResult result, DetectionContext context) {
        // Default: do nothing
    }

    /**
     * Optional: Validate that this detector can analyze the given context
     */
    protected boolean canAnalyze(DetectionContext context) {
        return true; // Can analyze by default
    }

    @Override
    public final DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
        if (!enabled || !canAnalyze(context)) {
            return DetectionResult.createSafe();
        }

        try {
            // Pre-analysis hook
            preAnalysis(player, data, context);

            // Perform the actual analysis
            DetectionResult result = performAnalysis(player, data, context);

            // Post-analysis hook
            postAnalysis(player, result, context);

            return result;

        } catch (Exception e) {
            // Log error and return safe result
            System.err.println("Error in custom detector " + detectorName + ": " + e.getMessage());
            e.printStackTrace();
            return DetectionResult.createSafe();
        }
    }

    // ===== CONFIGURATION METHODS =====

    /**
     * Set the detector priority
     */
    public void setPriority(DetectorPriority priority) {
        this.priority = priority;
    }

    /**
     * Get the detector priority
     */
    public DetectorPriority getPriority() {
        return priority;
    }

    /**
     * Set detector name
     */
    public void setName(String name) {
        this.detectorName = name;
    }

    /**
     * Set detector description
     */
    public void setDescription(String description) {
        this.detectorDescription = description;
    }

    // ===== DETECTION HELPER METHODS =====

    /**
     * Create a detection result with specified parameters
     */
    protected DetectionResult createResult(double confidence, String reason, String... reasons) {
        DetectionLevel level = determineLevel(confidence);
        return new DetectionResult(level, confidence, reasons.length > 0 ?
            java.util.Arrays.asList(reasons) : java.util.Collections.singletonList(reason),
            getDetectionType(), java.util.Collections.emptyMap());
    }

    /**
     * Create a high-confidence detection result
     */
    protected DetectionResult createHighConfidence(String reason) {
        return DetectionResult.createHighConfidence(DetectionLevel.HIGH, reason, getDetectionType());
    }

    /**
     * Create a critical detection result
     */
    protected DetectionResult createCritical(String reason) {
        return new DetectionResult(DetectionLevel.CRITICAL, 0.9, java.util.Collections.singletonList(reason),
                                 getDetectionType(), java.util.Collections.emptyMap());
    }

    /**
     * Determine detection level based on confidence
     */
    private DetectionLevel determineLevel(double confidence) {
        if (confidence >= 0.85) return DetectionLevel.CRITICAL;
        if (confidence >= 0.7) return DetectionLevel.HIGH;
        if (confidence >= 0.5) return DetectionLevel.MEDIUM;
        if (confidence >= 0.3) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    // ===== UTILITY METHODS =====

    /**
     * Calculate distance between two locations (simplified)
     */
    protected double calculateDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Check if player is moving suspiciously fast
     */
    protected boolean isMovingTooFast(Player player, double maxSpeed) {
        // Check player's current velocity
        org.bukkit.util.Vector velocity = player.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());

        // Account for flying/sprinting modifiers
        if (player.isFlying()) {
            maxSpeed *= 2.0; // Flying allows faster movement
        } else if (player.isSprinting()) {
            maxSpeed *= 1.3; // Sprinting increases speed
        }

        // Check if player is in liquid (water/lava) which slows movement
        if (player.getLocation().getBlock().isLiquid()) {
            maxSpeed *= 0.5; // Liquids slow movement
        }

        return horizontalSpeed > maxSpeed;
    }

    /**
     * Get player's mining efficiency
     */
    protected double getMiningEfficiency(PlayerDetectionData data) {
        return data.getMiningEfficiency();
    }

    /**
     * Get player's suspicion ratio
     */
    protected double getSuspicionRatio(PlayerDetectionData data) {
        return data.getSuspicionRatio();
    }

    // ===== INTERFACE IMPLEMENTATION =====

    @Override
    public String getName() {
        return detectorName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Get detector description
     */
    public String getDescription() {
        return detectorDescription;
    }

    /**
     * Get detector version (can be overridden)
     */
    public String getVersion() {
        return "1.0.0";
    }

    /**
     * Get detector author (can be overridden)
     */
    public String getAuthor() {
        return "Custom";
    }

    // ===== INTERFACE IMPLEMENTATION =====

    @Override
    public DetectionType getDetectionType() {
        // Default implementation - subclasses should override for specific types
        return DetectionType.CUSTOM;
    }

    // ===== DETECTOR PRIORITY =====

    public enum DetectorPriority {
        LOW(1, "Low Priority"),
        NORMAL(2, "Normal Priority"),
        HIGH(3, "High Priority"),
        CRITICAL(4, "Critical Priority");

        private final int value;
        private final String displayName;

        DetectorPriority(int value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }

        public int getValue() {
            return value;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
