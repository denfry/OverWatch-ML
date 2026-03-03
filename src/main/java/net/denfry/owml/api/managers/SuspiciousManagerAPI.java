package net.denfry.owml.api.managers;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * API interface for managing suspicious players.
 * Provides methods to check, modify, and manage player suspicion levels.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public interface SuspiciousManagerAPI extends Manager {

    /**
     * Checks if a player is currently considered suspicious.
     *
     * @param playerId the player's UUID
     * @return true if the player is suspicious
     */
    boolean isPlayerSuspicious(@NotNull UUID playerId);

    /**
     * Gets the suspicion level of a player.
     *
     * @param playerId the player's UUID
     * @return suspicion level (0-100), or -1 if player not found
     */
    int getSuspicionLevel(@NotNull UUID playerId);

    /**
     * Adds suspicion points to a player.
     *
     * @param playerId the player's UUID
     * @param points the number of suspicion points to add (positive number)
     */
    void addSuspicionPoints(@NotNull UUID playerId, int points);

    /**
     * Removes suspicion points from a player.
     *
     * @param playerId the player's UUID
     * @param points the number of suspicion points to remove (positive number)
     */
    void removeSuspicionPoints(@NotNull UUID playerId, int points);

    /**
     * Sets the suspicion level of a player.
     *
     * @param playerId the player's UUID
     * @param level the suspicion level (0-100)
     */
    void setSuspicionLevel(@NotNull UUID playerId, int level);

    /**
     * Clears all suspicion data for a player.
     *
     * @param playerId the player's UUID
     */
    void clearSuspicionData(@NotNull UUID playerId);

    /**
     * Gets all currently suspicious players.
     *
     * @return set of suspicious player UUIDs
     */
    @NotNull
    Set<UUID> getSuspiciousPlayers();

    /**
     * Gets the threshold above which players are considered suspicious.
     *
     * @return the suspicion threshold
     */
    int getSuspicionThreshold();

    /**
     * Sets the suspicion threshold.
     *
     * @param threshold the new threshold (1-100)
     */
    void setSuspicionThreshold(int threshold);
}
