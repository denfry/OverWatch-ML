package net.denfry.owml.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.utils.LocationUtils;
import net.denfry.owml.utils.Locs;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class DecoyManager {
   private final JavaPlugin plugin;
   private final ConfigManager configManager;
   private final ConcurrentMap<Location, Material> decoyMap = new ConcurrentHashMap<>();
   private final ConcurrentMap<UUID, DecoyManager.OreTracker> trackerMap = new ConcurrentHashMap<>();
   private final Set<DecoyManager.LocationWrapper> playerPlacedOre = ConcurrentHashMap.newKeySet();
   private final ConcurrentMap<Location, Set<Location>> veinMap = new ConcurrentHashMap<>();
   private final ConcurrentMap<Location, Map<Location, Material>> originalBlockTypes = new ConcurrentHashMap<>();
   private final ConcurrentMap<Location, UUID> decoyOwners = new ConcurrentHashMap<>();
   private final BlockFace[] ADJACENT_FACES = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
   private final Set<Material> replaceableBlocks = ConcurrentHashMap.newKeySet();

   public DecoyManager(JavaPlugin plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.initializeReplaceableBlocks();
   }

   private void initializeReplaceableBlocks() {
      String[] baseBlocks = new String[]{"STONE", "COBBLESTONE", "DIRT", "GRAVEL", "GRANITE", "DIORITE", "ANDESITE"};
      String[] versionSpecificBlocks = new String[]{"DEEPSLATE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK", "BLACKSTONE"};

      for (String blockName : baseBlocks) {
         try {
            Material material = Material.valueOf(blockName);
            if (material != null && material.isBlock()) {
               this.replaceableBlocks.add(material);
            }
         } catch (IllegalArgumentException var9) {
         }
      }

      for (String blockName : versionSpecificBlocks) {
         try {
            Material material = Material.valueOf(blockName);
            if (material != null && material.isBlock()) {
               this.replaceableBlocks.add(material);
            }
         } catch (IllegalArgumentException var8) {
         }
      }
   }

   public void scheduleOreTrackerCleanup() {
      long cleanupInterval = Math.max(1200L, this.configManager.getTimeWindowTicks() / 2L);
      Schedulers.globalTimer(() -> {
         long currentTime = System.currentTimeMillis();
         Iterator<Entry<UUID, DecoyManager.OreTracker>> it = this.trackerMap.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, DecoyManager.OreTracker> entry = it.next();
            DecoyManager.OreTracker tracker = entry.getValue();
            if (tracker.isExpired(currentTime, this.configManager.getTimeWindowTicks())) {
               it.remove();
            }
         }
      }, cleanupInterval, cleanupInterval);
   }

   public Map<Location, Set<Location>> getAllDecoyVeins() {
      return new HashMap<>(this.veinMap);
   }

   public void addPlayerPlacedOre(Location loc) {
      this.playerPlacedOre.add(new DecoyManager.LocationWrapper(loc));
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Added player-placed ore at " + LocationUtils.formatLocation(loc));
      }
   }

   public boolean isPlayerPlacedOre(Location loc) {
      boolean result = this.playerPlacedOre.contains(new DecoyManager.LocationWrapper(loc));
      if (this.configManager.isDebugEnabled() && result) {
         this.plugin.getLogger().info("Found player-placed ore at " + LocationUtils.formatLocation(loc));
      }

      return result;
   }

   public void removePlayerPlacedOre(Location loc) {
      boolean removed = this.playerPlacedOre.remove(new DecoyManager.LocationWrapper(loc));
      if (this.configManager.isDebugEnabled() && removed) {
         this.plugin.getLogger().info("Removed player-placed ore at " + LocationUtils.formatLocation(loc));
      }
   }

   public boolean isDecoy(Location loc) {
      if (!this.decoyMap.containsKey(loc)) {
         return false;
      } else {
         Material expectedType = this.decoyMap.get(loc);
         if (expectedType == null) {
            return false;
         } else if (!canTouch(loc)) {
            return true;
         } else {
            Block block = loc.getBlock();
            if (block.getType() != expectedType) {
               this.removeDecoy(loc);
               return false;
            } else {
               return true;
            }
         }
      }
   }

   public void removeDecoy(Location loc) {
      Material expectedMaterial = this.decoyMap.remove(loc);
      this.decoyOwners.remove(loc);

      for (Entry<Location, Set<Location>> entry : new HashMap<>(this.veinMap).entrySet()) {
         if (entry.getValue().contains(loc)) {
            entry.getValue().remove(loc);
            if (entry.getValue().isEmpty()) {
               this.veinMap.remove(entry.getKey());
               this.originalBlockTypes.remove(entry.getKey());
            }
            break;
         }
      }

      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Removed decoy ore at " + LocationUtils.formatLocation(loc));
      }
   }

   public void trackOreBreak(Player player, Block block, Material ore) {
      if (this.configManager.getNaturalOres().contains(ore)) {
         UUID playerUUID = player.getUniqueId();
         DecoyManager.OreTracker tracker = this.trackerMap.computeIfAbsent(playerUUID, k -> new DecoyManager.OreTracker());
         tracker.increment(System.currentTimeMillis());
         if (tracker.getCount() > this.configManager.getOreThreshold() && this.configManager.isDecoyEnabled()) {
            this.placeDecoy(player, block, ore);
            tracker.reset();
         }
      }
   }

   private void placeDecoy(Player player, Block originalBlock, Material oreType) {
      Location initialCandidateLoc = this.calculateDecoyLocation(player, originalBlock.getLocation());
      int attempts = 0;
      int maxAttempts = 5;

      while (attempts < maxAttempts) {
         attempts++;
         if (initialCandidateLoc != null && this.isBuried(initialCandidateLoc) && this.placeDecoyVeinAt(player, initialCandidateLoc, oreType)) {
            return;
         }

         if (initialCandidateLoc != null) {
            Random random = new Random();
            initialCandidateLoc = initialCandidateLoc.clone().add(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
         }
      }

      Location originalLoc = originalBlock.getLocation();
      int radius = this.configManager.getDecoySearchRadius();

      for (int yOffset = -radius; yOffset <= radius; yOffset++) {
         for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
               if (xOffset != 0 || yOffset != 0 || zOffset != 0) {
                  Location candidateLoc = originalLoc.clone().add(xOffset, yOffset, zOffset);
                  if (candidateLoc != null
                     && candidateLoc.getWorld() != null
                     && candidateLoc.getWorld().equals(originalLoc.getWorld())
                     && this.isBuried(candidateLoc)
                     && this.placeDecoyVeinAt(player, candidateLoc, oreType)) {
                     return;
                  }
               }
            }
         }
      }

      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Failed to place decoy ore vein for " + player.getName() + " near " + LocationUtils.formatLocation(originalLoc));
      }
   }

   private static boolean canTouch(Location loc) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      } else {
         int cx = loc.getBlockX() >> 4;
         int cz = loc.getBlockZ() >> 4;
         return w.isChunkLoaded(cx, cz) && Schedulers.isOwned(w, cx, cz);
      }
   }

   private boolean placeDecoyVeinAt(Player player, Location startLoc, Material oreType) {
      int veinSize = this.getVeinSizeForOre(oreType);
      Set<Location> veinBlocks = this.generateVein(startLoc, oreType, veinSize);
      if (!veinBlocks.isEmpty() && veinBlocks.size() >= Math.max(2, veinSize / 2)) {
         Map<Location, Material> originalTypes = new ConcurrentHashMap<>();
         UUID playerUUID = player.getUniqueId();
         int actualBlocksPlaced = 0;

         for (Location loc : veinBlocks) {
            if (canTouch(loc)) {
               Block block = loc.getBlock();
               if (this.replaceableBlocks.contains(block.getType())
                  && !this.playerPlacedOre.contains(new DecoyManager.LocationWrapper(loc))
                  && !this.decoyMap.containsKey(loc)) {
                  originalTypes.put(loc, block.getType());
                  block.setType(oreType);
                  this.decoyMap.put(loc, oreType);
                  this.decoyOwners.put(loc, playerUUID);
                  actualBlocksPlaced++;
               }
            }
         }

         if (actualBlocksPlaced == 0) {
            return false;
         } else {
            Set<Location> placedBlocks = ConcurrentHashMap.newKeySet();

            for (Location locx : veinBlocks) {
               if (this.decoyMap.containsKey(locx)) {
                  placedBlocks.add(locx);
               }
            }

            this.veinMap.put(startLoc, placedBlocks);
            this.originalBlockTypes.put(startLoc, originalTypes);
            if (this.configManager.isDebugEnabled()) {
               this.plugin
                  .getLogger()
                  .info(
                     "Decoy ore vein placed for "
                        + player.getName()
                        + " at "
                        + LocationUtils.formatLocation(startLoc)
                        + " with "
                        + actualBlocksPlaced
                        + " blocks (originally planned "
                        + veinBlocks.size()
                        + ")"
                  );
            }

            this.scheduleDecoyVeinRevert(player, startLoc);
            return true;
         }
      } else {
         return false;
      }
   }

   private int getVeinSizeForOre(Material oreType) {
      Random random = new Random();
      if (oreType == Material.DIAMOND_ORE || oreType == Material.DEEPSLATE_DIAMOND_ORE) {
         return 3 + random.nextInt(3);
      } else if (oreType == Material.EMERALD_ORE || oreType == Material.DEEPSLATE_EMERALD_ORE) {
         return 1 + random.nextInt(2);
      } else if (oreType == Material.IRON_ORE
         || oreType == Material.DEEPSLATE_IRON_ORE
         || oreType == Material.GOLD_ORE
         || oreType == Material.DEEPSLATE_GOLD_ORE) {
         return 4 + random.nextInt(4);
      } else if (oreType == Material.COAL_ORE || oreType == Material.DEEPSLATE_COAL_ORE) {
         return 5 + random.nextInt(11);
      } else {
         return oreType != Material.REDSTONE_ORE
               && oreType != Material.DEEPSLATE_REDSTONE_ORE
               && oreType != Material.LAPIS_ORE
               && oreType != Material.DEEPSLATE_LAPIS_ORE
            ? 3 + random.nextInt(3)
            : 4 + random.nextInt(5);
      }
   }

   private Set<Location> generateVein(Location startLoc, Material oreType, int size) {
      Set<Location> veinBlocks = new HashSet<>();
      Set<Location> candidates = new HashSet<>();
      if (!canTouch(startLoc)) {
         return veinBlocks;
      } else {
         Block startBlock = startLoc.getBlock();
         if (this.replaceableBlocks.contains(startBlock.getType())
            && !this.playerPlacedOre.contains(new DecoyManager.LocationWrapper(startLoc))
            && this.isBuried(startLoc)) {
            veinBlocks.add(startLoc);

            for (BlockFace face : this.ADJACENT_FACES) {
               candidates.add(startBlock.getRelative(face).getLocation());
            }

            Random random = new Random();
            int attempts = 0;
            int maxAttempts = size * 5;

            while (veinBlocks.size() < size && !candidates.isEmpty() && attempts < maxAttempts) {
               attempts++;
               List<Location> candidateList = new ArrayList<>(candidates);
               Location nextLoc = candidateList.get(random.nextInt(candidateList.size()));
               candidates.remove(nextLoc);
               if (canTouch(nextLoc)) {
                  Block block = nextLoc.getBlock();
                  if (this.replaceableBlocks.contains(block.getType())
                     && !this.playerPlacedOre.contains(new DecoyManager.LocationWrapper(nextLoc))
                     && !this.decoyMap.containsKey(nextLoc)
                     && this.isBuried(nextLoc)) {
                     veinBlocks.add(nextLoc);

                     for (BlockFace face : this.ADJACENT_FACES) {
                        Location adjLoc = block.getRelative(face).getLocation();
                        if (!veinBlocks.contains(adjLoc)) {
                           candidates.add(adjLoc);
                        }
                     }
                  }
               }
            }

            return (Set<Location>)(veinBlocks.size() < Math.max(2, size / 2) ? new HashSet<>() : veinBlocks);
         } else {
            return veinBlocks;
         }
      }
   }

   private void scheduleDecoyVeinRevert(Player player, Location primaryLoc) {
      Schedulers.atLater(
         primaryLoc,
         () -> {
            Set<Location> veinBlocks = this.veinMap.get(primaryLoc);
            Map<Location, Material> originalTypes = this.originalBlockTypes.get(primaryLoc);
            if (veinBlocks != null && originalTypes != null) {
               boolean shouldRevert = !player.isOnline()
                  || player.getWorld() != primaryLoc.getWorld()
                  || Locs.distance(player.getLocation(), primaryLoc) > this.configManager.getMaxDecayDistance();
               if (shouldRevert) {
                  for (Location loc : veinBlocks) {
                     Material originalType = originalTypes.get(loc);
                     if (originalType != null) {
                        Schedulers.runAt(loc, () -> {
                           Block block = loc.getBlock();
                           Material expected = this.decoyMap.get(loc);
                           if (expected != null && block.getType() == expected) {
                              block.setType(originalType);
                           }

                           this.decoyMap.remove(loc);
                           this.decoyOwners.remove(loc);
                        });
                     }
                  }

                  this.veinMap.remove(primaryLoc);
                  this.originalBlockTypes.remove(primaryLoc);
                  if (this.configManager.isDebugEnabled()) {
                     this.plugin
                        .getLogger()
                        .info(
                           "Reverted decoy ore vein at "
                              + LocationUtils.formatLocation(primaryLoc)
                              + (player.isOnline() ? " due to player being too far away" : " because player went offline")
                        );
                  }
               }
            }
         },
         this.configManager.getDecoyRevertDelay()
      );
   }

   private Location calculateDecoyLocation(Player player, Location original) {
      Location eyeLoc = player.getEyeLocation();
      Vector viewDir = eyeLoc.getDirection().normalize();
      Vector decoyDir = viewDir.clone().multiply(-1);
      Vector perpendicular = decoyDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
      decoyDir.add(perpendicular.multiply(this.configManager.getDecoyFieldOffset()));
      return original.clone().add(decoyDir.multiply(this.configManager.getDecoyDistance()));
   }

   private boolean isBuried(Location loc) {
      if (canTouch(loc)
         && canTouch(loc.clone().add(1.0, 0.0, 1.0))
         && canTouch(loc.clone().add(-1.0, 0.0, -1.0))
         && canTouch(loc.clone().add(1.0, 0.0, -1.0))
         && canTouch(loc.clone().add(-1.0, 0.0, 1.0))) {
         Block block = loc.getBlock();
         int buriedCount = 0;
         int exposedCount = 0;
         BlockFace[] criticalFaces = new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

         for (BlockFace face : criticalFaces) {
            Block adjacentBlock = block.getRelative(face);
            Material adj = adjacentBlock.getType();
            if (adj == Material.AIR || adj == Material.WATER || adj == Material.LAVA || adj == Material.CAVE_AIR || this.isTransparent(adj)) {
               if (++exposedCount >= 2) {
                  return false;
               }
            }
         }

         for (BlockFace facex : BlockFace.values()) {
            if (facex != BlockFace.SELF) {
               Block adjacentBlock = block.getRelative(facex);
               Material adj = adjacentBlock.getType();
               if (adj != Material.AIR && adj != Material.WATER && adj != Material.LAVA && adj != Material.CAVE_AIR && !this.isTransparent(adj)) {
                  buriedCount++;
               }
            }
         }

         int buriedThreshold = this.configManager.getBuriedThreshold();
         return buriedCount >= buriedThreshold;
      } else {
         return false;
      }
   }

   private boolean isTransparent(Material material) {
      return material.isTransparent()
         || material == Material.GLASS
         || material == Material.TINTED_GLASS
         || material.name().contains("GLASS_PANE")
         || material.name().contains("LEAVES")
         || material == Material.ICE
         || material == Material.SLIME_BLOCK;
   }

   public void validateDecoys() {
      for (Entry<Location, Material> entry : this.decoyMap.entrySet()) {
         Location loc = entry.getKey();
         Material expectedType = entry.getValue();
         if (loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            Schedulers.at(loc, () -> {
               if (loc.getBlock().getType() != expectedType && this.decoyMap.remove(loc, expectedType)) {
                  this.decoyOwners.remove(loc);
                  this.pruneEmptyVeins();
               }
            });
         }
      }
   }

   private void pruneEmptyVeins() {
      for (Entry<Location, Set<Location>> entry : this.veinMap.entrySet()) {
         boolean validVein = false;

         for (Location blockLoc : entry.getValue()) {
            if (this.decoyMap.containsKey(blockLoc)) {
               validVein = true;
               break;
            }
         }

         if (!validVein) {
            this.veinMap.remove(entry.getKey());
            this.originalBlockTypes.remove(entry.getKey());
            if (this.configManager.isDebugEnabled()) {
               this.plugin.getLogger().info("Removed invalid vein at " + LocationUtils.formatLocation(entry.getKey()));
            }
         }
      }
   }

   private void scheduleDecoyValidation() {
      Schedulers.globalTimer(this::validateDecoys, 12000L, 12000L);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Scheduled decoy validation every 10 minutes");
      }
   }

   public boolean placeDecoyOre(Location location, Material material) {
      if (location == null || material == null) {
         return false;
      } else if (!canTouch(location)) {
         return false;
      } else {
         Block block = location.getBlock();
         if (!this.replaceableBlocks.contains(block.getType())) {
            return false;
         } else {
            Map<Location, Material> originals = this.originalBlockTypes.computeIfAbsent(location, k -> new ConcurrentHashMap<>());
            originals.put(location, block.getType());
            block.setType(material);
            this.decoyMap.put(location, material);
            this.playerPlacedOre.add(new DecoyManager.LocationWrapper(location));
            return true;
         }
      }
   }

   public boolean placeDecoyOre(Location location) {
      return this.placeDecoyOre(location, Material.DIAMOND_ORE);
   }

   private static class LocationWrapper {
      private final String world;
      private final int x;
      private final int y;
      private final int z;

      public LocationWrapper(Location loc) {
         this.world = loc.getWorld().getName();
         this.x = loc.getBlockX();
         this.y = loc.getBlockY();
         this.z = loc.getBlockZ();
      }

      @Override
      public boolean equals(Object obj) {
         return !(obj instanceof DecoyManager.LocationWrapper other)
            ? false
            : this.world.equals(other.world) && this.x == other.x && this.y == other.y && this.z == other.z;
      }

      @Override
      public int hashCode() {
         return this.world.hashCode() ^ this.x ^ this.y << 8 ^ this.z << 16;
      }
   }

   private static class OreTracker {
      private int count = 0;
      private long lastBreakTime = 0L;

      public void increment(long currentTime) {
         this.count++;
         this.lastBreakTime = currentTime;
      }

      public int getCount() {
         return this.count;
      }

      public void reset() {
         this.count = 0;
      }

      public boolean isExpired(long currentTime, long timeWindowTicks) {
         long timeWindowMs = timeWindowTicks * 50L;
         return currentTime - this.lastBreakTime > timeWindowMs;
      }
   }
}
