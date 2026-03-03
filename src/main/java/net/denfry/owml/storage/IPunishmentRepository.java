package net.denfry.owml.storage;

import net.denfry.owml.punishments.PunishmentData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IPunishmentRepository {
    CompletableFuture<Void> saveAll(Map<UUID, PunishmentData> data);
    CompletableFuture<Map<UUID, PunishmentData>> loadAll();
}
