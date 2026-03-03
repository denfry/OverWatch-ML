package net.denfry.owml.storage;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.AsyncExecutor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class YamlStatsRepository implements IStatsRepository {
    private final File statsFile;
    private final OverWatchML plugin;

    public YamlStatsRepository(OverWatchML plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "mining_stats.yml");
    }

    @Override
    public CompletableFuture<Void> saveAll(Map<UUID, Map<Material, Integer>> stats) {
        // Deep copy of stats to avoid ConcurrentModificationException if stats is modified while saving
        Map<UUID, Map<Material, Integer>> statsCopy = new HashMap<>();
        for (Map.Entry<UUID, Map<Material, Integer>> entry : stats.entrySet()) {
            statsCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        return AsyncExecutor.submitIO(() -> {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, Map<Material, Integer>> entry : statsCopy.entrySet()) {
                String uuid = entry.getKey().toString();
                Map<Material, Integer> ores = entry.getValue();
                for (Map.Entry<Material, Integer> oreEntry : ores.entrySet()) {
                    String path = "stats." + uuid + "." + oreEntry.getKey().name();
                    config.set(path, oreEntry.getValue());
                }
            }
            try {
                config.save(statsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save mining stats", e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, Map<Material, Integer>>> loadAll() {
        return AsyncExecutor.submitIO(() -> {
            Map<UUID, Map<Material, Integer>> stats = new HashMap<>();
            if (!statsFile.exists()) {
                return stats;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
            ConfigurationSection statsSection = config.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String uuidString : statsSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        Map<Material, Integer> ores = new HashMap<>();
                        ConfigurationSection playerSection = statsSection.getConfigurationSection(uuidString);
                        if (playerSection != null) {
                            for (String oreName : playerSection.getKeys(false)) {
                                int count = playerSection.getInt(oreName);
                                Material ore = Material.getMaterial(oreName);
                                if (ore != null) {
                                    ores.put(ore, count);
                                }
                            }
                        }
                        stats.put(uuid, ores);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            return stats;
        });
    }
}
