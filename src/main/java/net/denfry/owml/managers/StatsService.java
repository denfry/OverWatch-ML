package net.denfry.owml.managers;

import net.denfry.owml.storage.IStatsRepository;
import org.bukkit.Material;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StatsService implements IStatsService {
    private final IStatsRepository repository;
    private final Map<UUID, Map<Material, Integer>> cumulativeStats = new ConcurrentHashMap<>();
    private final Set<Material> trackedOres;

    public StatsService(IStatsRepository repository, Set<Material> trackedOres) {
        this.repository = repository;
        this.trackedOres = Collections.unmodifiableSet(trackedOres);
    }

    @Override
    public void addOreMined(UUID playerId, Material ore) {
        if (!trackedOres.contains(ore)) {
            return;
        }
        cumulativeStats.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).merge(ore, 1, Integer::sum);
    }

    @Override
    public Map<Material, Integer> getOreStats(UUID playerId) {
        Map<Material, Integer> stats = cumulativeStats.get(playerId);
        return stats != null ? new HashMap<>(stats) : Collections.emptyMap();
    }

    @Override
    public int getTotalOresMined(UUID playerId) {
        Map<Material, Integer> stats = cumulativeStats.get(playerId);
        if (stats == null) return 0;
        return stats.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public Set<UUID> getAllPlayerIds() {
        return Collections.unmodifiableSet(cumulativeStats.keySet());
    }

    @Override
    public CompletableFuture<Void> save() {
        return repository.saveAll(cumulativeStats);
    }

    @Override
    public CompletableFuture<Void> load() {
        return repository.loadAll().thenAccept(loadedStats -> {
            cumulativeStats.clear();
            for (Map.Entry<UUID, Map<Material, Integer>> entry : loadedStats.entrySet()) {
                cumulativeStats.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
        });
    }
}
