package net.denfry.owml.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ISuspiciousRepository {
    CompletableFuture<Void> saveAll(Map<UUID, Integer> counts);
    CompletableFuture<Map<UUID, Integer>> loadAll();
}
