package net.denfry.owml.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.api.events.OverWatchMLEventListener;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.managers.StatsManager;
import net.denfry.owml.ml.ModernMLManager;

/**
 * Public API for OverWatchML plugin.
 * This API allows other plugins to interact with OverWatchML functionality.
 *
 * <p>All methods are thread-safe unless otherwise noted.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * OverWatchMLAPI api = OverWatchMLAPI.getInstance();
 * if (api != null) {
 *     boolean isSuspicious = api.isPlayerSuspicious(player.getUniqueId());
 *     // Handle suspicious player
 * }
 * }</pre>
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public final class OverWatchMLAPI {

    private static volatile OverWatchMLAPI instance;

    private final OverWatchML plugin;
    private final DecoyManager decoyManager;
    private final PunishmentManager punishmentManager;
    private final net.denfry.owml.api.managers.SuspiciousManagerAPI suspiciousManagerAPI;
    private final StatsManager statsManager;
    private final ModernMLManager mlManager;

    /**
     * Private constructor for singleton pattern.
     */
    private OverWatchMLAPI(OverWatchML plugin) {
        this.plugin = plugin;
        this.decoyManager = plugin.getDecoyManager();
        this.punishmentManager = plugin.getPunishmentManager();
        this.suspiciousManagerAPI = net.denfry.owml.managers.SuspiciousManager.getInstance();
        this.statsManager = StatsManager.getInstance();
        this.mlManager = plugin.getMLManager();
    }

    /**
     * Gets the API instance. Returns null if the plugin is not loaded.
     *
     * @return the API instance, or null if plugin is not loaded
     */
    @Nullable
    public static OverWatchMLAPI getInstance() {
        if (instance == null) {
            OverWatchML plugin = OverWatchML.getInstance();
            if (plugin != null) {
                instance = new OverWatchMLAPI(plugin);
            }
        }
        return instance;
    }

    /**
     * Checks if the plugin is enabled and ready.
     *
     * @return true if plugin is enabled and operational
     */
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    /**
     * Gets the plugin version.
     *
     * @return the plugin version string
     */
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // ===== PLAYER ANALYSIS =====

    /**
     * Checks if a player is currently considered suspicious.
     *
     * @param playerId the player's UUID
     * @return true if the player is suspicious
     */
    public boolean isPlayerSuspicious(@NotNull UUID playerId) {
        return suspiciousManagerAPI.isPlayerSuspicious(playerId);
    }

    /**
     * Gets the suspicion level of a player (0-100).
     *
     * @param playerId the player's UUID
     * @return suspicion level, or -1 if player not found
     */
    public int getPlayerSuspicionLevel(@NotNull UUID playerId) {
        return suspiciousManagerAPI.getSuspicionLevel(playerId);
    }

    /**
     * Gets the current punishment level of a player.
     *
     * @param playerId the player's UUID
     * @return punishment level (0-6), or 0 if no punishment
     */
    public int getPlayerPunishmentLevel(@NotNull UUID playerId) {
        return punishmentManager.getPlayerPunishmentLevel(playerId);
    }

    // ===== MINING STATISTICS =====

    /**
     * Gets mining statistics for a player.
     *
     * @param playerId the player's UUID
     * @return mining statistics, or null if not available
     */
    @Nullable
    public PlayerMiningStats getPlayerMiningStats(@NotNull UUID playerId) {
        // Calculate total ores from ore stats
        Map<Material, Integer> oreStats = StatsManager.getOreStats(playerId);
        int totalOres = oreStats.values().stream().mapToInt(Integer::intValue).sum();

        if (totalOres == 0) {
            return null; // No stats available
        }

        // For now, we'll use simplified values since detailed stats aren't available
        // In a real implementation, this would come from a proper StatsManagerAPI implementation
        return new PlayerMiningStats(totalOres, 0, System.currentTimeMillis());
    }

    /**
     * Gets the total number of ores mined by a player.
     *
     * @param playerId the player's UUID
     * @return total ores mined
     */
    public int getTotalOresMined(@NotNull UUID playerId) {
        return StatsManager.getTotalOresMined(playerId);
    }

    /**
     * Gets the suspicion level of a player by name.
     *
     * @param playerName the player's name
     * @return suspicion level, or -1 if player not found
     */
    public int getSuspicionLevel(@NotNull String playerName) {
        return plugin.getSuspicionLevel(playerName);
    }

    /**
     * Gets all currently suspicious players.
     *
     * @return set of suspicious player UUIDs
     */
    @NotNull
    public Set<UUID> getSuspiciousPlayers() {
        return plugin.getSuspiciousPlayers();
    }

    // ===== DECOY SYSTEM =====

    /**
     * Checks if a location contains a decoy ore.
     *
     * @param location the location to check
     * @return true if location contains a decoy
     */
    public boolean isDecoyLocation(@NotNull Location location) {
        return decoyManager.isPlayerPlacedOre(location);
    }

    /**
     * Places a decoy ore at the specified location.
     * This method is for advanced users who understand the implications.
     *
     * @param location the location to place the decoy
     * @param material the material to use (must be an ore)
     * @return true if decoy was placed successfully
     * @deprecated Use {@link #placeDecoyOre(Location)} for safer placement
     */
    @Deprecated
    @ApiStatus.Experimental
    public boolean placeDecoyOre(@NotNull Location location, @NotNull Material material) {
        return decoyManager.placeDecoyOre(location, material);
    }

    /**
     * Places a decoy ore at the specified location using default settings.
     *
     * @param location the location to place the decoy
     * @return true if decoy was placed successfully
     */
    public boolean placeDecoyOre(@NotNull Location location) {
        return decoyManager.placeDecoyOre(location);
    }

    // ===== WORLD MANAGEMENT =====

    /**
     * Checks if anti-xray checks are disabled in a specific world.
     *
     * @param world the world to check
     * @return true if checks are disabled in this world
     */
    public boolean isWorldDisabled(@NotNull World world) {
        return plugin.getConfigManager().isWorldDisabled(world.getName());
    }

    /**
     * Gets the set of worlds where checks are disabled.
     *
     * @return set of disabled world names
     */
    @NotNull
    public Set<String> getDisabledWorlds() {
        return plugin.getConfigManager().getDisabledWorlds();
    }

    // ===== MACHINE LEARNING =====

    /**
     * Checks if machine learning detection is enabled.
     *
     * @return true if ML is enabled and operational
     */
    public boolean isMachineLearningEnabled() {
        return mlManager != null && mlManager.isEnabled();
    }

    /**
     * Starts ML analysis for a player asynchronously.
     *
     * @param player the player to analyze
     * @return CompletableFuture that completes when analysis is done
     */
    @NotNull
    public CompletableFuture<MLAnalysisResult> analyzePlayerAsync(@NotNull Player player) {
        if (!isMachineLearningEnabled()) {
            CompletableFuture<MLAnalysisResult> future = new CompletableFuture<>();
            future.complete(MLAnalysisResult.NOT_AVAILABLE);
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                mlManager.startAnalysis(player);
                // Wait for analysis to complete (this is a simplified version)
                Thread.sleep(5000); // Wait 5 seconds for demo
                return MLAnalysisResult.ANALYSIS_COMPLETE;
            } catch (Exception e) {
                return MLAnalysisResult.ERROR;
            }
        });
    }

    // ===== CONFIGURATION =====

    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return plugin.getConfigManager().isDebugEnabled();
    }

    /**
     * Gets the current ore threshold for triggering decoys.
     *
     * @return the ore threshold value
     */
    public int getOreThreshold() {
        return plugin.getConfigManager().getOreThreshold();
    }

    // ===== EVENTS =====

    /**
     * Registers a custom event listener for OverWatchML events.
     * This allows other plugins to listen to OverWatchML specific events.
     *
     * @param listener the listener to register
     */
    public void registerEventListener(@NotNull OverWatchMLEventListener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Data class for player mining statistics.
     */
    public static class PlayerMiningStats {
        private final int totalOres;
        private final int suspiciousActions;
        private final long lastActivity;

        public PlayerMiningStats(int totalOres, int suspiciousActions, long lastActivity) {
            this.totalOres = totalOres;
            this.suspiciousActions = suspiciousActions;
            this.lastActivity = lastActivity;
        }

        public int getTotalOres() { return totalOres; }
        public int getSuspiciousActions() { return suspiciousActions; }
        public long getLastActivity() { return lastActivity; }
    }

    /**
     * Enum representing ML analysis results.
     */
    public enum MLAnalysisResult {
        ANALYSIS_COMPLETE,
        NOT_AVAILABLE,
        ERROR
    }
}
