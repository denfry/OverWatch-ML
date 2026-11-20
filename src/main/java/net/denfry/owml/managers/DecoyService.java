package net.denfry.owml.managers;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.utils.BlockLocation;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class DecoyService implements IDecoyService {
   private final OverWatchML plugin;
   private final ConfigManager config;
   private final Map<BlockLocation, Material> decoyMap = new ConcurrentHashMap<>();
   private final Set<BlockLocation> playerPlacedOres = ConcurrentHashMap.newKeySet();
   private final Map<UUID, DecoyService.OreTracker> trackerMap = new ConcurrentHashMap<>();
   private final Map<BlockLocation, Material> originalTypes = new ConcurrentHashMap<>();
   private static final BlockFace[] FACES = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

   public DecoyService(OverWatchML plugin, ConfigManager config) {
      this.plugin = plugin;
      this.config = config;
   }

   @Override
   public void addPlayerPlacedOre(Location loc) {
      this.playerPlacedOres.add(BlockLocation.fromLocation(loc));
   }

   @Override
   public void removePlayerPlacedOre(Location loc) {
      this.playerPlacedOres.remove(BlockLocation.fromLocation(loc));
   }

   @Override
   public boolean isPlayerPlacedOre(Location loc) {
      return this.playerPlacedOres.contains(BlockLocation.fromLocation(loc));
   }

   @Override
   public void trackOreBreak(Player player, Block block, Material ore) {
      if (this.config.isDecoyEnabled()) {
         if (this.config.getNaturalOres().contains(ore)) {
            DecoyService.OreTracker tracker = this.trackerMap.computeIfAbsent(player.getUniqueId(), k -> new DecoyService.OreTracker());
            if (tracker.incrementAndCheck(this.config.getOreThreshold())) {
               this.placeDecoyAsync(player, block.getLocation(), ore);
            }
         }
      }
   }

   private void placeDecoyAsync(Player player, Location originalLoc, Material oreType) {
      Schedulers.at(originalLoc, () -> {
         if (player.isOnline()) {
            this.performDecoyPlacement(player, originalLoc, oreType);
         }
      });
   }

   private void performDecoyPlacement(Player player, Location originalLoc, Material oreType) {
      int radius = Math.max(2, this.config.getDecoySearchRadius());
      World world = originalLoc.getWorld();
      if (world != null) {
         Random random = new Random();

         for (int i = 0; i < 30; i++) {
            int x = originalLoc.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int y = originalLoc.getBlockY() + random.nextInt(radius * 2 + 1) - radius;
            int z = originalLoc.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            if (y > world.getMinHeight() && y < world.getMaxHeight() - 1) {
               Location candidate = new Location(world, x, y, z);
               if (this.isBuried(candidate)) {
                  this.placeDecoyAt(player, candidate, oreType);
                  return;
               }
            }
         }
      }
   }

   private static boolean canTouch(Location loc) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      } else {
         for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
               int cx = loc.getBlockX() + dx >> 4;
               int cz = loc.getBlockZ() + dz >> 4;
               if (!w.isChunkLoaded(cx, cz) || !Schedulers.isOwned(w, cx, cz)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private static boolean isReplaceable(Material m) {
      return switch (m) {
         case STONE, DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE, CALCITE, BLACKSTONE, NETHERRACK -> true;
         default -> false;
      };
   }

   private boolean isBuried(Location loc) {
      if (!canTouch(loc)) {
         return false;
      } else {
         BlockLocation key = BlockLocation.fromLocation(loc);
         if (!this.decoyMap.containsKey(key) && !this.playerPlacedOres.contains(key)) {
            Block block = loc.getBlock();
            if (!isReplaceable(block.getType())) {
               return false;
            } else {
               for (BlockFace face : FACES) {
                  Material adj = block.getRelative(face).getType();
                  if (adj.isAir() || !adj.isOccluding()) {
                     return false;
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      }
   }

   private void placeDecoyAt(Player player, Location loc, Material type) {
      Block block = loc.getBlock();
      Material original = block.getType();
      BlockLocation key = BlockLocation.fromLocation(loc);
      this.decoyMap.put(key, type);
      this.originalTypes.put(key, original);
      block.setType(type, false);
      if (this.config.isCachedDebugEnabled()) {
         this.plugin.getLogger().info("Decoy " + type + " для " + player.getName() + " в " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
      }

      this.scheduleRevert(player.getUniqueId(), loc, key, 0);
   }

   private void scheduleRevert(UUID owner, Location loc, BlockLocation key, int attempt) {
      long delay = Math.max(20L, this.config.getDecoyRevertDelay());
      Schedulers.atLater(
         loc,
         () -> {
            Material expected = this.decoyMap.get(key);
            if (expected == null) {
               this.originalTypes.remove(key);
            } else {
               Player p = this.plugin.getServer().getPlayer(owner);
               boolean far = p == null
                  || !p.isOnline()
                  || p.getWorld() != loc.getWorld()
                  || p.getLocation().distanceSquared(loc) > this.config.getMaxDecayDistance() * this.config.getMaxDecayDistance();
               if (!far && attempt < 10) {
                  this.scheduleRevert(owner, loc, key, attempt + 1);
               } else {
                  Block block = loc.getBlock();
                  Material original = this.originalTypes.remove(key);
                  this.decoyMap.remove(key);
                  if (original != null && block.getType() == expected) {
                     block.setType(original, false);
                  }
               }
            }
         },
         delay
      );
   }

   @Override
   public boolean isDecoy(Location loc) {
      return this.decoyMap.containsKey(BlockLocation.fromLocation(loc));
   }

   @Override
   public void removeDecoy(Location loc) {
      this.decoyMap.remove(BlockLocation.fromLocation(loc));
   }

   @Override
   public void cleanup() {
      this.decoyMap.clear();
      this.originalTypes.clear();
      this.playerPlacedOres.clear();
      this.trackerMap.clear();
   }

   private static class OreTracker {
      private int count = 0;
      private long lastReset = System.currentTimeMillis();

      public synchronized boolean incrementAndCheck(int threshold) {
         this.count++;
         if (this.count > threshold) {
            this.count = 0;
            return true;
         } else {
            return false;
         }
      }
   }
}
