package net.denfry.owml.managers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ISuspiciousService {
    void addSuspicious(UUID playerId);
    void addSuspicionPoints(UUID playerId, int points);
    void removeSuspicionPoints(UUID playerId, int points);
    void setSuspicionLevel(UUID playerId, int level);
    int getSuspicionLevel(UUID playerId);
    boolean isPlayerSuspicious(UUID playerId);
    Set<UUID> getSuspiciousPlayers();
    CompletableFuture<Void> save();
    CompletableFuture<Void> load();
}
