package net.denfry.owml.detection;

import org.bukkit.entity.Player;

/**
 * Base interface for detection analyzers.
 * Each analyzer implements specific detection algorithms.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public interface DetectionAnalyzer {

    /**
     * Analyze player behavior for suspicious activity
     *
     * @param player the player to analyze
     * @param data the player's detection data
     * @param context the detection context
     * @return detection result
     */
    DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context);

    /**
     * Get the type of detection this analyzer performs
     */
    DetectionType getDetectionType();

    /**
     * Get the name of this analyzer
     */
    String getName();

    /**
     * Check if this analyzer is enabled
     */
    boolean isEnabled();

    /**
     * Enable or disable this analyzer
     */
    void setEnabled(boolean enabled);
}
