package net.denfry.owml.detection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context information for detection analysis.
 * Contains all relevant data about the player's action being analyzed.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class DetectionContext {

    public enum Action {
        BLOCK_BREAK,
        BLOCK_PLACE,
        MOVEMENT,
        INVENTORY_CLICK,
        CHAT_MESSAGE,
        COMMAND_EXECUTE,
        JOIN,
        QUIT,
        WORLD_CHANGE
    }

    private final Player player;
    private final Location location;
    private final Location fromLocation;
    private final Material material;
    private final Action action;
    private final long timestamp;
    private final String additionalData;
    private final String sessionId;
    private final String world;
    private final long timeOfDay;

    // Behavioral metrics (optional)
    private final double miningSpeed;
    private final double oreDistributionUniformity;
    private final long sessionDuration;
    private final double idleTimeRatio;
    private final double movementEfficiency;
    private final double backtrackingRatio;

    private DetectionContext(Builder builder) {
        this.player = builder.player;
        this.location = builder.location;
        this.fromLocation = builder.fromLocation;
        this.material = builder.material;
        this.action = builder.action;
        this.timestamp = builder.timestamp;
        this.additionalData = builder.additionalData;
        this.sessionId = builder.sessionId;
        this.world = builder.world;
        this.timeOfDay = builder.timeOfDay;
        this.miningSpeed = builder.miningSpeed;
        this.oreDistributionUniformity = builder.oreDistributionUniformity;
        this.sessionDuration = builder.sessionDuration;
        this.idleTimeRatio = builder.idleTimeRatio;
        this.movementEfficiency = builder.movementEfficiency;
        this.backtrackingRatio = builder.backtrackingRatio;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    @NotNull
    public Player getPlayer() { return player; }

    @Nullable
    public Location getLocation() { return location; }

    @Nullable
    public Location getFromLocation() { return fromLocation; }

    @Nullable
    public Material getMaterial() { return material; }

    @NotNull
    public Action getAction() { return action; }

    public long getTimestamp() { return timestamp; }

    @Nullable
    public String getAdditionalData() { return additionalData; }

    @Nullable
    public String getSessionId() { return sessionId; }

    @Nullable
    public String getWorld() { return world; }

    public long getTimeOfDay() { return timeOfDay; }

    public double getMiningSpeed() { return miningSpeed; }

    public double getOreDistributionUniformity() { return oreDistributionUniformity; }

    public long getSessionDuration() { return sessionDuration; }

    public double getIdleTimeRatio() { return idleTimeRatio; }

    public double getMovementEfficiency() { return movementEfficiency; }

    public double getBacktrackingRatio() { return backtrackingRatio; }

    public static class Builder {
        private Player player;
        private Location location;
        private Location fromLocation;
        private Material material;
        private Action action;
        private long timestamp;
        private String additionalData;
        private String sessionId;
        private String world;
        private long timeOfDay;
        private double miningSpeed;
        private double oreDistributionUniformity;
        private long sessionDuration;
        private double idleTimeRatio;
        private double movementEfficiency;
        private double backtrackingRatio;

        public Builder player(@NotNull Player player) {
            this.player = player;
            return this;
        }

        public Builder location(@Nullable Location location) {
            this.location = location;
            return this;
        }

        public Builder fromLocation(@Nullable Location fromLocation) {
            this.fromLocation = fromLocation;
            return this;
        }

        public Builder material(@Nullable Material material) {
            this.material = material;
            return this;
        }

        public Builder action(@NotNull Action action) {
            this.action = action;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder additionalData(@Nullable String additionalData) {
            this.additionalData = additionalData;
            return this;
        }

        public Builder sessionId(@Nullable String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder world(@Nullable String world) {
            this.world = world;
            return this;
        }

        public Builder timeOfDay(long timeOfDay) {
            this.timeOfDay = timeOfDay;
            return this;
        }

        public Builder miningSpeed(double miningSpeed) {
            this.miningSpeed = miningSpeed;
            return this;
        }

        public Builder oreDistributionUniformity(double oreDistributionUniformity) {
            this.oreDistributionUniformity = oreDistributionUniformity;
            return this;
        }

        public Builder sessionDuration(long sessionDuration) {
            this.sessionDuration = sessionDuration;
            return this;
        }

        public Builder idleTimeRatio(double idleTimeRatio) {
            this.idleTimeRatio = idleTimeRatio;
            return this;
        }

        public Builder movementEfficiency(double movementEfficiency) {
            this.movementEfficiency = movementEfficiency;
            return this;
        }

        public Builder backtrackingRatio(double backtrackingRatio) {
            this.backtrackingRatio = backtrackingRatio;
            return this;
        }

        public DetectionContext build() {
            if (player == null) {
                throw new IllegalStateException("Player must be set");
            }
            if (action == null) {
                throw new IllegalStateException("Action must be set");
            }
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
            return new DetectionContext(this);
        }
    }
}
