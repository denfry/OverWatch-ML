package net.denfry.owml.detection;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * Manages all player behavioral profiles and event buffers.
 * Acts as the single source of truth for current player behavioral state.
 */
public class BehaviorProfileManager {
    private final Map<UUID, PlayerBehaviorProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerEventBuffer> eventBuffers = new ConcurrentHashMap<>();
    private static final int DEFAULT_BUFFER_SIZE = 1000;

    /**
     * Retrieves or creates a player behavioral profile.
     * 
     * @param playerId UUID of the player.
     * @return Behavioral profile.
     */
    public PlayerBehaviorProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, PlayerBehaviorProfile::new);
    }

    /**
     * Retrieves or creates a player event buffer.
     * 
     * @param playerId UUID of the player.
     * @return Event buffer.
     */
    public PlayerEventBuffer getEventBuffer(UUID playerId) {
        return eventBuffers.computeIfAbsent(playerId, id -> new PlayerEventBuffer(id, DEFAULT_BUFFER_SIZE));
    }

    /**
     * Removes all data associated with a player.
     * 
     * @param playerId UUID of the player.
     */
    public void removePlayer(UUID playerId) {
        profiles.remove(playerId);
        eventBuffers.remove(playerId);
    }

    /**
     * Clears all managed behavioral data.
     */
    public void clear() {
        profiles.clear();
        eventBuffers.clear();
    }
}
