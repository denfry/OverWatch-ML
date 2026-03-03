package net.denfry.owml.ml.impl;

import java.util.UUID;
import org.bukkit.entity.Player;
import net.denfry.owml.ml.DataCollector;
import net.denfry.owml.ml.PlayerMiningData;

/**
 * Адаптер для существующего PlayerDataCollector
 */
public class PlayerDataCollectorAdapter implements DataCollector {

    private final net.denfry.owml.ml.PlayerDataCollector collector;

    public PlayerDataCollectorAdapter(net.denfry.owml.ml.PlayerDataCollector collector) {
        this.collector = collector;
    }

    @Override
    public void startCollecting(Player player, boolean isCheater) {
        collector.startCollecting(player, isCheater);
    }

    @Override
    public PlayerMiningData stopCollecting(Player player) {
        return collector.stopCollecting(player);
    }

    @Override
    public boolean isCollecting(UUID playerId) {
        return collector.isCollectingData(playerId);
    }

    @Override
    public void addBlockBreak(Player player, String blockType, String location) {
        // This method may not be implemented in the original collector
        // Can add logic if needed
    }

    @Override
    public void addMovement(Player player, double fromX, double fromY, double fromZ,
                           double toX, double toY, double toZ) {
        // This method may not be implemented in the original collector
        // Can add logic if needed
    }

    @Override
    public PlayerMiningData getCurrentData(UUID playerId) {
        return collector.getPlayerData(playerId);
    }

    @Override
    public void cleanupOldData() {
        // PlayerDataCollector doesn't have cleanup functionality
        // This is a no-op implementation for interface compatibility
    }
}
