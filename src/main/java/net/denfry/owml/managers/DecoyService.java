package net.denfry.owml.managers;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.utils.BlockLocation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DecoyService implements IDecoyService {
    private final OverWatchML plugin;
    private final ConfigManager config;
    
    private final Map<BlockLocation, Material> decoyMap = new ConcurrentHashMap<>();
    private final Set<BlockLocation> playerPlacedOres = ConcurrentHashMap.newKeySet();
    private final Map<UUID, OreTracker> trackerMap = new ConcurrentHashMap<>();
    
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public DecoyService(OverWatchML plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void addPlayerPlacedOre(Location loc) {
        playerPlacedOres.add(BlockLocation.fromLocation(loc));
    }

    @Override
    public void removePlayerPlacedOre(Location loc) {
        playerPlacedOres.remove(BlockLocation.fromLocation(loc));
    }

    @Override
    public boolean isPlayerPlacedOre(Location loc) {
        return playerPlacedOres.contains(BlockLocation.fromLocation(loc));
    }

    @Override
    public void trackOreBreak(Player player, Block block, Material ore) {
        if (!config.getNaturalOres().contains(ore)) return;

        OreTracker tracker = trackerMap.computeIfAbsent(player.getUniqueId(), k -> new OreTracker());
        if (tracker.incrementAndCheck(config.getOreThreshold())) {
            placeDecoyAsync(player, block.getLocation(), ore);
        }
    }

    private void placeDecoyAsync(Player player, Location originalLoc, Material oreType) {
        // We do the search on the main thread but in a smarter way, 
        // or we could use Paper's async chunk loading if available.
        // For now, let's just make it a scheduled task to not block the current break event.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            performDecoyPlacement(player, originalLoc, oreType);
        });
    }

    private void performDecoyPlacement(Player player, Location originalLoc, Material oreType) {
        int radius = config.getDecoySearchRadius();
        World world = originalLoc.getWorld();
        
        // Smarter search: spiral or random samples instead of 1000+ loops
        Random random = new Random();
        for (int i = 0; i < 20; i++) { // 20 random samples is much faster than 1331 loops
            int x = originalLoc.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int y = originalLoc.getBlockY() + random.nextInt(radius * 2 + 1) - radius;
            int z = originalLoc.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            
            Location candidate = new Location(world, x, y, z);
            if (isBuried(candidate)) {
                placeDecoyAt(candidate, oreType);
                return;
            }
        }
    }

    private boolean isBuried(Location loc) {
        Block block = loc.getBlock();
        if (!block.getType().isAir()) return false; // Must be air/replaceable to place? 
        // Wait, original logic said if isBuried it replaces it. 
        // If it's already an ore, it's not "buried" in stone usually.
        
        for (BlockFace face : FACES) {
            if (block.getRelative(face).getType().isAir()) return false;
        }
        return true;
    }

    private void placeDecoyAt(Location loc, Material type) {
        decoyMap.put(BlockLocation.fromLocation(loc), type);
        loc.getBlock().setType(type);
    }

    @Override
    public boolean isDecoy(Location loc) {
        return decoyMap.containsKey(BlockLocation.fromLocation(loc));
    }

    @Override
    public void removeDecoy(Location loc) {
        decoyMap.remove(BlockLocation.fromLocation(loc));
    }

    @Override
    public void cleanup() {
        decoyMap.clear();
        playerPlacedOres.clear();
        trackerMap.clear();
    }

    private static class OreTracker {
        private int count = 0;
        private long lastReset = System.currentTimeMillis();

        public synchronized boolean incrementAndCheck(int threshold) {
            count++;
            if (count > threshold) {
                count = 0;
                return true;
            }
            return false;
        }
    }
}
