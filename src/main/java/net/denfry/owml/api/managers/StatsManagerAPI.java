package net.denfry.owml.api.managers;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * API interface for managing player mining statistics.
 * Provides methods to track and retrieve mining data for analysis.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public interface StatsManagerAPI extends Manager {

    /**
     * Records that a player mined a specific ore.
     *
     * @param playerId the player's UUID
     * @param material the material that was mined
     */
    void recordOreMined(@NotNull UUID playerId, @NotNull Material material);

    /**
     * Gets the total number of ores mined by a player.
     *
     * @param playerId the player's UUID
     * @return total ores mined, or 0 if no data
     */
    int getTotalOresMined(@NotNull UUID playerId);

    /**
     * Gets mining statistics for a specific ore type.
     *
     * @param playerId the player's UUID
     * @param material the ore material
     * @return number of this ore mined, or 0 if no data
     */
    int getOreCount(@NotNull UUID playerId, @NotNull Material material);

    /**
     * Gets all ore statistics for a player.
     *
     * @param playerId the player's UUID
     * @return map of material to count, or empty map if no data
     */
    @NotNull
    Map<Material, Integer> getAllOreStats(@NotNull UUID playerId);

    /**
     * Gets the timestamp of the last mining activity for a player.
     *
     * @param playerId the player's UUID
     * @return timestamp in milliseconds, or 0 if no activity
     */
    long getLastMiningActivity(@NotNull UUID playerId);

    /**
     * Clears all statistics for a player.
     *
     * @param playerId the player's UUID
     */
    void clearPlayerStats(@NotNull UUID playerId);

    /**
     * Gets the mining rate (ores per minute) for a player in the last time window.
     *
     * @param playerId the player's UUID
     * @param timeWindowMinutes the time window in minutes
     * @return mining rate, or 0 if no data
     */
    double getMiningRate(@NotNull UUID playerId, int timeWindowMinutes);

    /**
     * Checks if a player has exceeded the mining threshold recently.
     *
     * @param playerId the player's UUID
     * @param threshold the threshold to check against
     * @param timeWindowTicks the time window in ticks
     * @return true if threshold exceeded
     */
    boolean hasExceededThreshold(@NotNull UUID playerId, int threshold, long timeWindowTicks);

    /**
     * Gets detailed mining statistics for a player.
     *
     * @param playerId the player's UUID
     * @return detailed statistics, or null if no data
     */
    @Nullable
    DetailedMiningStats getDetailedStats(@NotNull UUID playerId);

    /**
     * Data class for detailed mining statistics.
     */
    class DetailedMiningStats {
        private final int totalOres;
        private final int uniqueOreTypes;
        private final long firstMiningActivity;
        private final long lastMiningActivity;
        private final double averageMiningRate;
        private final Material mostMinedOre;
        private final Map<Material, Integer> oreBreakdown;

        public DetailedMiningStats(int totalOres, int uniqueOreTypes, long firstMiningActivity,
                                 long lastMiningActivity, double averageMiningRate,
                                 @Nullable Material mostMinedOre, @NotNull Map<Material, Integer> oreBreakdown) {
            this.totalOres = totalOres;
            this.uniqueOreTypes = uniqueOreTypes;
            this.firstMiningActivity = firstMiningActivity;
            this.lastMiningActivity = lastMiningActivity;
            this.averageMiningRate = averageMiningRate;
            this.mostMinedOre = mostMinedOre;
            this.oreBreakdown = oreBreakdown;
        }

        public int getTotalOres() { return totalOres; }
        public int getUniqueOreTypes() { return uniqueOreTypes; }
        public long getFirstMiningActivity() { return firstMiningActivity; }
        public long getLastMiningActivity() { return lastMiningActivity; }
        public double getAverageMiningRate() { return averageMiningRate; }
        @Nullable public Material getMostMinedOre() { return mostMinedOre; }
        @NotNull public Map<Material, Integer> getOreBreakdown() { return oreBreakdown; }
    }
}
