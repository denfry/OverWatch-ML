package net.denfry.owml.api.managers;

import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all OverWatchML managers.
 * Provides common functionality for initialization, cleanup, and lifecycle management.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public interface Manager {

    /**
     * Initializes the manager.
     * This method is called when the plugin enables.
     */
    void initialize();

    /**
     * Shuts down the manager and cleans up resources.
     * This method is called when the plugin disables.
     */
    void shutdown();

    /**
     * Reloads the manager configuration.
     * Called when plugin configuration is reloaded.
     */
    void reload();

    /**
     * Gets the name of this manager for logging purposes.
     *
     * @return the manager name
     */
    @NotNull
    String getName();

    /**
     * Checks if this manager is currently enabled and operational.
     *
     * @return true if manager is enabled
     */
    boolean isEnabled();
}
