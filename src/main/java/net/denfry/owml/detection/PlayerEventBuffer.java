package net.denfry.owml.detection;

import java.util.UUID;
import java.util.List;
import net.denfry.owml.utils.RingBuffer;

/**
 * Stores a stream of recent behavioral events for a single player.
 * Events are stored in a synchronized ring buffer for thread safety.
 */
public class PlayerEventBuffer {
    private final UUID playerId;
    private final RingBuffer<BehavioralEvent> events;

    public PlayerEventBuffer(UUID playerId, int capacity) {
        this.playerId = playerId;
        this.events = new RingBuffer<>(capacity);
    }

    /**
     * Adds an event to the buffer.
     * 
     * @param event The behavioral event.
     */
    public void addEvent(BehavioralEvent event) {
        events.add(event);
    }

    /**
     * Retrieves all events in the buffer.
     * 
     * @return List of events.
     */
    public List<BehavioralEvent> getEvents() {
        return events.getAll();
    }

    /**
     * Clears the event buffer.
     */
    public void clear() {
        events.clear();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Represents a single behavioral data point.
     */
    public record BehavioralEvent(long timestamp, String eventType, double value, Object context) {}
}
