package net.denfry.owml.managers;

import net.denfry.owml.punishments.PunishmentData;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IPunishmentService {
    PunishmentData getPlayerData(UUID playerId);
    void setPunishmentLevel(Player player, int level);
    void checkAndPunish(Player player);
    void removePunishment(Player player);
    
    CompletableFuture<Void> save();
    CompletableFuture<Void> load();
}
