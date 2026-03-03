package net.denfry.owml.storage;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.AsyncExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class YamlSuspiciousRepository implements ISuspiciousRepository {
    private final File file;
    private final OverWatchML plugin;

    public YamlSuspiciousRepository(OverWatchML plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "suspicious_counts.yml");
    }

    @Override
    public CompletableFuture<Void> saveAll(Map<UUID, Integer> counts) {
        Map<UUID, Integer> copy = new HashMap<>(counts);
        return AsyncExecutor.submitIO(() -> {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, Integer> entry : copy.entrySet()) {
                config.set("players." + entry.getKey().toString(), entry.getValue());
            }
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save suspicious counts", e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, Integer>> loadAll() {
        return AsyncExecutor.submitIO(() -> {
            Map<UUID, Integer> counts = new HashMap<>();
            if (!file.exists()) return counts;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("players");
            if (section != null) {
                for (String uuidString : section.getKeys(false)) {
                    try {
                        counts.put(UUID.fromString(uuidString), section.getInt(uuidString));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            return counts;
        });
    }
}
