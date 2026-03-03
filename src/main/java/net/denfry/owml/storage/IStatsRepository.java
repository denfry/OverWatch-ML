package net.denfry.owml.storage;

import org.bukkit.Material;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IStatsRepository {
    CompletableFuture<Void> saveAll(Map<UUID, Map<Material, Integer>> stats);
    CompletableFuture<Map<UUID, Map<Material, Integer>>> loadAll();
}
