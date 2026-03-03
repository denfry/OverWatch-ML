package net.denfry.owml.utils;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;
import java.util.UUID;

public record BlockLocation(UUID worldId, int x, int y, int z) {
    public static BlockLocation fromLocation(Location loc) {
        return new BlockLocation(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Location toLocation(org.bukkit.Server server) {
        World world = server.getWorld(worldId);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }
}
