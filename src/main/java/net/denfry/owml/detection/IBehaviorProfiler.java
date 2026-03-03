package net.denfry.owml.detection;

import org.bukkit.entity.Player;
import java.util.UUID;

public interface IBehaviorProfiler {
    void recordAction(Player player, String actionType, Object context);
    PlayerDetectionData getProfile(UUID playerId);
    void clearProfile(UUID playerId);
}
