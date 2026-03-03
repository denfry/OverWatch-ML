package net.denfry.owml.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

/**
 * YAML file-based data storage implementation.
 * Maintains backward compatibility with existing YAML storage.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class YamlStorage implements DataStorage {

    private final OverWatchML plugin;
    private final File dataFolder;
    private final Map<String, FileConfiguration> configCache = new ConcurrentHashMap<>();
    private boolean initialized = false;

    public YamlStorage(OverWatchML plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
    }

    @Override
    public void initialize() throws StorageException {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            initialized = true;
            MessageManager.log("info", "YAML storage initialized in: {PATH}", "PATH", dataFolder.getAbsolutePath());
        } catch (Exception e) {
            throw new StorageException("Failed to initialize YAML storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        configCache.clear();
        initialized = false;
        MessageManager.log("info", "YAML storage shut down");
    }

    @Override
    public boolean isConnected() {
        return initialized && dataFolder.exists();
    }

    @Override
    public void savePlayerStats(@NotNull UUID playerId, @NotNull Map<String, Object> stats) {
        File playerFile = getPlayerFile(playerId, "stats.yml");
        FileConfiguration config = getConfig(playerFile);

        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }

        saveConfig(playerFile, config);
    }

    @Override
    @Nullable
    public Map<String, Object> loadPlayerStats(@NotNull UUID playerId) {
        File playerFile = getPlayerFile(playerId, "stats.yml");
        if (!playerFile.exists()) {
            return null;
        }

        FileConfiguration config = getConfig(playerFile);
        Map<String, Object> stats = new HashMap<>();

        for (String key : config.getKeys(true)) {
            stats.put(key, config.get(key));
        }

        return stats;
    }

    @Override
    public void saveSuspiciousData(@NotNull UUID playerId, int suspicionLevel, long lastActivity) {
        File playerFile = getPlayerFile(playerId, "suspicious.yml");
        FileConfiguration config = getConfig(playerFile);

        config.set("suspicionLevel", suspicionLevel);
        config.set("lastActivity", lastActivity);
        config.set("totalAlerts", config.getInt("totalAlerts", 0) + 1);
        config.set("firstDetected", config.getLong("firstDetected", System.currentTimeMillis()));

        saveConfig(playerFile, config);
    }

    @Override
    @Nullable
    public SuspiciousData loadSuspiciousData(@NotNull UUID playerId) {
        File playerFile = getPlayerFile(playerId, "suspicious.yml");
        if (!playerFile.exists()) {
            return null;
        }

        FileConfiguration config = getConfig(playerFile);
        return new SuspiciousData(
            config.getInt("suspicionLevel", 0),
            config.getLong("lastActivity", 0),
            config.getInt("totalAlerts", 0),
            config.getLong("firstDetected", 0)
        );
    }

    @Override
    @NotNull
    public Map<UUID, SuspiciousData> getAllSuspiciousPlayers() {
        Map<UUID, SuspiciousData> suspiciousPlayers = new HashMap<>();

        try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith("suspicious.yml"))
                 .forEach(path -> {
                     try {
                         String fileName = path.getFileName().toString();
                         String uuidString = fileName.substring(0, fileName.length() - 14); // Remove "_suspicious.yml"
                         UUID playerId = UUID.fromString(uuidString);

                         FileConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
                         SuspiciousData data = new SuspiciousData(
                             config.getInt("suspicionLevel", 0),
                             config.getLong("lastActivity", 0),
                             config.getInt("totalAlerts", 0),
                             config.getLong("firstDetected", 0)
                         );

                         suspiciousPlayers.put(playerId, data);
                     } catch (Exception e) {
                         MessageManager.log("warning", "Failed to load suspicious data for file: {FILE}", "FILE", path.toString());
                     }
                 });
        } catch (IOException e) {
            MessageManager.log("error", "Failed to scan suspicious player files: {ERROR}", "ERROR", e.getMessage());
        }

        return suspiciousPlayers;
    }

    @Override
    public void savePunishmentData(@NotNull UUID playerId, @NotNull Map<String, Object> punishmentData) {
        File playerFile = getPlayerFile(playerId, "punishment.yml");
        FileConfiguration config = getConfig(playerFile);

        for (Map.Entry<String, Object> entry : punishmentData.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }

        saveConfig(playerFile, config);
    }

    @Override
    @Nullable
    public Map<String, Object> loadPunishmentData(@NotNull UUID playerId) {
        File playerFile = getPlayerFile(playerId, "punishment.yml");
        if (!playerFile.exists()) {
            return null;
        }

        FileConfiguration config = getConfig(playerFile);
        Map<String, Object> data = new HashMap<>();

        for (String key : config.getKeys(true)) {
            data.put(key, config.get(key));
        }

        return data;
    }

    @Override
    public void deletePlayerData(@NotNull UUID playerId) {
        // Delete all player-related files
        String[] fileTypes = {"stats.yml", "suspicious.yml", "punishment.yml"};
        for (String fileType : fileTypes) {
            File playerFile = getPlayerFile(playerId, fileType);
            if (playerFile.exists()) {
                if (playerFile.delete()) {
                    MessageManager.log("info", "Deleted player data file: {FILE}", "FILE", playerFile.getName());
                } else {
                    MessageManager.log("warning", "Failed to delete player data file: {FILE}", "FILE", playerFile.getName());
                }
            }
        }

        // Remove from cache
        configCache.remove(getCacheKey(playerId, "stats.yml"));
        configCache.remove(getCacheKey(playerId, "suspicious.yml"));
        configCache.remove(getCacheKey(playerId, "punishment.yml"));
    }

    @Override
    @NotNull
    public StorageStats getStats() {
        long totalFiles = 0;
        long suspiciousFiles = 0;

        try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            totalFiles = paths.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            MessageManager.log("warning", "Failed to count storage files: {ERROR}", "ERROR", e.getMessage());
        }

        // Count suspicious files
        try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            suspiciousFiles = paths.filter(Files::isRegularFile)
                                 .filter(path -> path.toString().contains("suspicious.yml"))
                                 .count();
        } catch (IOException e) {
            MessageManager.log("warning", "Failed to count suspicious files: {ERROR}", "ERROR", e.getMessage());
        }

        long storageSize = calculateStorageSize();

        return new StorageStats(
            totalFiles / 3, // Approximate players (3 files per player)
            suspiciousFiles,
            totalFiles,
            storageSize,
            5.0, // Estimated query time for YAML
            "YAML"
        );
    }

    @Override
    public void performMaintenance() {
        // Clean up empty files and optimize storage
        try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> {
                     try {
                         return Files.size(path) == 0;
                     } catch (IOException e) {
                         return false;
                     }
                 })
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                         MessageManager.log("info", "Cleaned up empty file: {FILE}", "FILE", path.getFileName().toString());
                     } catch (IOException e) {
                         MessageManager.log("warning", "Failed to delete empty file: {FILE}", "FILE", path.getFileName().toString());
                     }
                 });
        } catch (IOException e) {
            MessageManager.log("error", "Failed to perform maintenance: {ERROR}", "ERROR", e.getMessage());
        }

        // Clear cache to free memory
        configCache.clear();
        MessageManager.log("info", "YAML storage maintenance completed");
    }

    /**
     * Get player data file.
     */
    private File getPlayerFile(UUID playerId, String fileType) {
        return new File(dataFolder, playerId.toString() + "_" + fileType);
    }

    /**
     * Get cached configuration or load from file.
     */
    private FileConfiguration getConfig(File file) {
        String cacheKey = getCacheKey(file);
        return configCache.computeIfAbsent(cacheKey, key -> YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Save configuration to file.
     */
    private void saveConfig(File file, FileConfiguration config) {
        try {
            config.save(file);
        } catch (IOException e) {
            MessageManager.log("error", "Failed to save config to {FILE}: {ERROR}", "FILE", file.getName(), "ERROR", e.getMessage());
        }
    }

    /**
     * Get cache key for file.
     */
    private String getCacheKey(File file) {
        return file.getAbsolutePath();
    }

    /**
     * Get cache key for player file.
     */
    private String getCacheKey(UUID playerId, String fileType) {
        return playerId.toString() + "_" + fileType;
    }

    /**
     * Calculate total storage size.
     */
    private long calculateStorageSize() {
        try (Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            return paths.filter(Files::isRegularFile)
                       .mapToLong(path -> {
                           try {
                               return Files.size(path);
                           } catch (IOException e) {
                               return 0L;
                           }
                       })
                       .sum();
        } catch (IOException e) {
            MessageManager.log("warning", "Failed to calculate storage size: {ERROR}", "ERROR", e.getMessage());
            return 0L;
        }
    }
}
