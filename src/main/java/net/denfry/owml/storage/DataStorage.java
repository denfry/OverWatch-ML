package net.denfry.owml.storage;

import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for data storage implementations.
 * Supports different storage backends like YAML files, SQLite, MySQL, etc.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public interface DataStorage {

    /**
     * Initialize the storage connection.
     *
     * @throws StorageException if initialization fails
     */
    void initialize() throws StorageException;

    /**
     * Shutdown the storage connection and cleanup resources.
     */
    void shutdown();

    /**
     * Check if storage is connected and operational.
     *
     * @return true if storage is ready
     */
    boolean isConnected();

    /**
     * Save player statistics.
     *
     * @param playerId the player's UUID
     * @param stats the statistics map
     */
    void savePlayerStats(@NotNull UUID playerId, @NotNull Map<String, Object> stats);

    /**
     * Load player statistics.
     *
     * @param playerId the player's UUID
     * @return statistics map, or null if not found
     */
    @Nullable
    Map<String, Object> loadPlayerStats(@NotNull UUID playerId);

    /**
     * Save suspicious player data.
     *
     * @param playerId the player's UUID
     * @param suspicionLevel the suspicion level
     * @param lastActivity timestamp of last activity
     */
    void saveSuspiciousData(@NotNull UUID playerId, int suspicionLevel, long lastActivity);

    /**
     * Load suspicious player data.
     *
     * @param playerId the player's UUID
     * @return suspicion data, or null if not found
     */
    @Nullable
    SuspiciousData loadSuspiciousData(@NotNull UUID playerId);

    /**
     * Get all suspicious players.
     *
     * @return set of player UUIDs with suspicion data
     */
    @NotNull
    Map<UUID, SuspiciousData> getAllSuspiciousPlayers();

    /**
     * Save punishment data.
     *
     * @param playerId the player's UUID
     * @param punishmentData the punishment data
     */
    void savePunishmentData(@NotNull UUID playerId, @NotNull Map<String, Object> punishmentData);

    /**
     * Load punishment data.
     *
     * @param playerId the player's UUID
     * @return punishment data, or null if not found
     */
    @Nullable
    Map<String, Object> loadPunishmentData(@NotNull UUID playerId);

    /**
     * Delete player data.
     *
     * @param playerId the player's UUID
     */
    void deletePlayerData(@NotNull UUID playerId);

    /**
     * Get storage statistics.
     *
     * @return storage statistics
     */
    @NotNull
    StorageStats getStats();

    /**
     * Perform maintenance operations (cleanup, optimization, etc.)
     */
    void performMaintenance();

    /**
     * Save all data to storage.
     */
    default void saveAll() {
        // Default implementation does nothing
    }

    /**
     * Load all data from storage.
     */
    default void loadAll() {
        // Default implementation does nothing
    }

    /**
     * Data class for suspicious player information.
     */
    class SuspiciousData {
        public final int suspicionLevel;
        public final long lastActivity;
        public final int totalAlerts;
        public final long firstDetected;

        public SuspiciousData(int suspicionLevel, long lastActivity, int totalAlerts, long firstDetected) {
            this.suspicionLevel = suspicionLevel;
            this.lastActivity = lastActivity;
            this.totalAlerts = totalAlerts;
            this.firstDetected = firstDetected;
        }
    }

    /**
     * Data class for storage statistics.
     */
    class StorageStats {
        public final long totalPlayers;
        public final long suspiciousPlayers;
        public final long totalRecords;
        public final long storageSizeBytes;
        public final double avgQueryTimeMs;
        public final String storageType;

        public StorageStats(long totalPlayers, long suspiciousPlayers, long totalRecords,
                          long storageSizeBytes, double avgQueryTimeMs, String storageType) {
            this.totalPlayers = totalPlayers;
            this.suspiciousPlayers = suspiciousPlayers;
            this.totalRecords = totalRecords;
            this.storageSizeBytes = storageSizeBytes;
            this.avgQueryTimeMs = avgQueryTimeMs;
            this.storageType = storageType;
        }

        @Override
        public String toString() {
            return String.format("%s: %d players, %d suspicious, %d records, %.1fms avg query",
                storageType, totalPlayers, suspiciousPlayers, totalRecords, avgQueryTimeMs);
        }
    }
}
