package net.denfry.owml.plugins;

import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.DetectionContext;
import org.bukkit.entity.Player;

/**
 * Base interface for OverWatchML plugins.
 * Plugins can extend OverWatch functionality with custom detectors,
 * commands, and features.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public interface OverWatchPlugin {

    /**
     * Initialize the plugin
     *
     * @param context the plugin context
     */
    void initialize(PluginManager.PluginContext context);

    /**
     * Get plugin name
     */
    String getName();

    /**
     * Get plugin version
     */
    String getVersion();

    /**
     * Get plugin description
     */
    String getDescription();

    /**
     * Get plugin author
     */
    String getAuthor();

    /**
     * Check if plugin is enabled
     */
    boolean isEnabled();

    /**
     * Enable the plugin
     */
    void enable();

    /**
     * Disable the plugin
     */
    void disable();

    /**
     * Called when the plugin should perform detection analysis
     * (Optional - only for detector plugins)
     *
     * @param player the player to analyze
     * @param context the detection context
     * @return detection result, or null if no detection
     */
    default DetectionResult analyze(Player player, DetectionContext context) {
        return null; // No detection by default
    }

    /**
     * Called when a detection result is available
     * (Optional - for plugins that want to react to detections)
     *
     * @param player the player
     * @param result the detection result
     * @param context the detection context
     */
    default void onDetection(Player player, DetectionResult result, DetectionContext context) {
        // Default: do nothing
    }

    /**
     * Get plugin configuration (optional)
     * Plugins can return their configuration schema here
     */
    default String getConfigurationSchema() {
        return null; // No configuration by default
    }
}
