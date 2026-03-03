package net.denfry.owml.detection;

import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IDetectionEngine {
    CompletableFuture<DetectionResult> analyzePlayer(Player player);
    boolean isUnderAnalysis(UUID playerId);
    void startAnalysis(Player player);
    void stopAnalysis(UUID playerId);
}
