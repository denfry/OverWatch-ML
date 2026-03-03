package net.denfry.owml.managers;

import org.bukkit.Material;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IStatsService {
    void addOreMined(UUID playerId, Material ore);
    Map<Material, Integer> getOreStats(UUID playerId);
    int getTotalOresMined(UUID playerId);
    Set<UUID> getAllPlayerIds();
    CompletableFuture<Void> save();
    CompletableFuture<Void> load();
}
