package net.denfry.owml.storage;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

/**
 * Storage manager for abstracting data persistence operations.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class StorageManager {

    private static StorageManager instance;
    private final DataStorage storage;

    private StorageManager(OverWatchML plugin) {
        String storageType = plugin.getConfig().getString("storage.type", "yaml");

        storage = switch (storageType.toLowerCase()) {
            case "sqlite" -> new SQLiteStorage(plugin);
            case "yaml" -> new YamlStorage(plugin);
            default -> {
                MessageManager.log("warning", "Unknown storage type '{TYPE}', falling back to YAML",
                    "TYPE", storageType);
                yield new YamlStorage(plugin);
            }
        };

        MessageManager.log("info", "Initialized {TYPE} storage system", "TYPE", storageType.toUpperCase());
    }

    /**
     * Initialize storage manager
     */
    public static void initialize(OverWatchML plugin) {
        if (instance == null) {
            instance = new StorageManager(plugin);
        }
    }

    /**
     * Get storage manager instance
     */
    public static StorageManager getInstance() {
        return instance;
    }

    /**
     * Get the active storage implementation
     */
    public DataStorage getStorage() {
        return storage;
    }

    /**
     * Save all data
     */
    public void saveAll() {
        try {
            storage.saveAll();
            MessageManager.log("info", "All data saved successfully");
        } catch (Exception e) {
            MessageManager.log("error", "Failed to save data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Load all data
     */
    public void loadAll() {
        try {
            storage.loadAll();
            MessageManager.log("info", "All data loaded successfully");
        } catch (Exception e) {
            MessageManager.log("error", "Failed to load data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Shutdown storage
     */
    public void shutdown() {
        try {
            storage.shutdown();
            MessageManager.log("info", "Storage system shut down");
        } catch (Exception e) {
            MessageManager.log("error", "Error shutting down storage: {ERROR}", "ERROR", e.getMessage());
        }
    }
}
