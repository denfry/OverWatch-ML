package net.denfry.owml.managers;

import net.denfry.owml.storage.ISuspiciousRepository;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SuspiciousService implements ISuspiciousService {
    private final ISuspiciousRepository repository;
    private final Map<UUID, Integer> suspiciousCounts = new ConcurrentHashMap<>();
    private final int suspicionThreshold;

    public SuspiciousService(ISuspiciousRepository repository, int suspicionThreshold) {
        this.repository = repository;
        this.suspicionThreshold = suspicionThreshold;
    }

    @Override
    public void addSuspicious(UUID playerId) {
        suspiciousCounts.merge(playerId, 1, Integer::sum);
    }

    @Override
    public void addSuspicionPoints(UUID playerId, int points) {
        if (points <= 0) return;
        suspiciousCounts.merge(playerId, points, Integer::sum);
    }

    @Override
    public void removeSuspicionPoints(UUID playerId, int points) {
        if (points <= 0) return;
        suspiciousCounts.computeIfPresent(playerId, (id, current) -> Math.max(0, current - points));
    }

    @Override
    public void setSuspicionLevel(UUID playerId, int level) {
        suspiciousCounts.put(playerId, Math.max(0, level));
    }

    @Override
    public int getSuspicionLevel(UUID playerId) {
        return suspiciousCounts.getOrDefault(playerId, 0);
    }

    @Override
    public boolean isPlayerSuspicious(UUID playerId) {
        return getSuspicionLevel(playerId) >= suspicionThreshold;
    }

    @Override
    public Set<UUID> getSuspiciousPlayers() {
        return suspiciousCounts.entrySet().stream()
            .filter(entry -> entry.getValue() >= suspicionThreshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public CompletableFuture<Void> save() {
        return repository.saveAll(suspiciousCounts);
    }

    @Override
    public CompletableFuture<Void> load() {
        return repository.loadAll().thenAccept(loaded -> {
            suspiciousCounts.clear();
            suspiciousCounts.putAll(loaded);
        });
    }
}
