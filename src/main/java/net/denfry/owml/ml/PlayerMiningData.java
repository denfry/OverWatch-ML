package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.utils.MaterialHelper;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public class PlayerMiningData {
   private final String playerName;
   private final long sessionStartTime;
   private final boolean labeledAsCheater;
   private final List<PlayerMiningData.BlockBreakRecord> blockBreaks = new ArrayList<>();
   private final List<PlayerMiningData.BlockBreakRecord> oreBreaks = new ArrayList<>();
   private final Set<Material> valuableOreMaterials = ConcurrentHashMap.newKeySet();
   private final Set<Material> diamondOreMaterials = ConcurrentHashMap.newKeySet();
   private final Set<Material> netheriteOreMaterials = ConcurrentHashMap.newKeySet();
   private final List<PlayerMiningData.BlockBreakRecord> diamondOreBreaks = new ArrayList<>();
   private final List<PlayerMiningData.BlockBreakRecord> netheriteOreBreaks = new ArrayList<>();
   private final List<PlayerMiningData.OreVein> diamondVeins = new ArrayList<>();
   private final List<PlayerMiningData.OreVein> netheriteVeins = new ArrayList<>();
   private final int VEIN_PROXIMITY_THRESHOLD = 3;
   private final List<Double> diamondDistances = new ArrayList<>();
   private final List<Double> netheriteDistances = new ArrayList<>();
   private final List<Long> idleTimes = new ArrayList<>();
   private final Map<String, Double> features = new ConcurrentHashMap<>();
   private final List<PlayerMiningData.YLevelRecord> yLevelRecords = new ArrayList<>();
   private final List<Double> yLevelChanges = new ArrayList<>();
   private final List<Long> timeAtSameYLevel = new ArrayList<>();
   private final double Y_LEVEL_CHANGE_THRESHOLD = 1.0;
   private final Map<Material, Integer> oreCounts = new ConcurrentHashMap<>();
   private final Map<Material, List<PlayerMiningData.OreTimeWindow>> oreTimeWindows = new ConcurrentHashMap<>();
   private PlayerMiningData.OreVein currentDiamondVein = null;
   private PlayerMiningData.OreVein currentNetheriteVein = null;
   private Vector lastDiamondPosition = null;
   private Vector lastNetheritePosition = null;
   private Long lastBlockBreakTime = null;
   private int totalBlocksBroken = 0;
   private int totalOresMined = 0;
   private Double lastYLevel = null;
   private long lastYLevelChangeTime = 0L;
   private int totalYLevelChanges = 0;
   private double totalYLevelChangeAmount = 0.0;

   public PlayerMiningData(String playerName, boolean labeledAsCheater) {
      this.playerName = playerName;
      this.sessionStartTime = System.currentTimeMillis();
      this.labeledAsCheater = labeledAsCheater;
      this.initializeValuableOreMaterials();
      this.initializeDiamondAndNetheriteOreMaterials();

      for (Material oreMaterial : this.valuableOreMaterials) {
         this.oreTimeWindows.put(oreMaterial, new ArrayList<>());
      }
   }

   private void initializeValuableOreMaterials() {
      this.addIfExists("DIAMOND_ORE");
      this.addIfExists("DEEPSLATE_DIAMOND_ORE");
      this.addIfExists("ANCIENT_DEBRIS");
      this.addIfExists("EMERALD_ORE");
      this.addIfExists("DEEPSLATE_EMERALD_ORE");
      this.addIfExists("GOLD_ORE");
      this.addIfExists("DEEPSLATE_GOLD_ORE");
      this.addIfExists("IRON_ORE");
      this.addIfExists("DEEPSLATE_IRON_ORE");
      this.addIfExists("LAPIS_ORE");
      this.addIfExists("DEEPSLATE_LAPIS_ORE");
      this.addIfExists("REDSTONE_ORE");
      this.addIfExists("DEEPSLATE_REDSTONE_ORE");
      this.addIfExists("COPPER_ORE");
      this.addIfExists("DEEPSLATE_COPPER_ORE");
      this.addIfExists("NETHER_GOLD_ORE");
      this.addIfExists("NETHER_QUARTZ_ORE");
   }

   private void addIfExists(String materialName) {
      Material material = MaterialHelper.getMaterial(materialName);
      if (material != null) {
         this.valuableOreMaterials.add(material);
      }
   }

   private void initializeDiamondAndNetheriteOreMaterials() {
      this.addMaterialIfExists(this.diamondOreMaterials, "DIAMOND_ORE");
      this.addMaterialIfExists(this.diamondOreMaterials, "DEEPSLATE_DIAMOND_ORE");
      this.addMaterialIfExists(this.netheriteOreMaterials, "ANCIENT_DEBRIS");
   }

   private void addMaterialIfExists(Set<Material> set, String materialName) {
      Material material = MaterialHelper.getMaterial(materialName);
      if (material != null) {
         set.add(material);
      }
   }

   public void recordBlockBreak(Block block, Vector lookDirection) {
      long timestamp = System.currentTimeMillis();
      Vector blockPosition = block.getLocation().toVector();
      Material blockType = block.getType();
      if (this.lastBlockBreakTime != null) {
         long idleTime = timestamp - this.lastBlockBreakTime;
         if (idleTime > 0L && idleTime < 3600000L) {
            this.idleTimes.add(idleTime);
         }
      }

      this.lastBlockBreakTime = timestamp;
      PlayerMiningData.BlockBreakRecord record = new PlayerMiningData.BlockBreakRecord(timestamp, blockType, blockPosition);
      this.blockBreaks.add(record);
      this.totalBlocksBroken++;
      if (this.valuableOreMaterials.contains(blockType)) {
         this.recordOreFound(block);
      }

      if (this.diamondOreMaterials.contains(blockType)) {
         this.recordDiamondOreFound(record);
      }

      if (this.netheriteOreMaterials.contains(blockType)) {
         this.recordNetheriteOreFound(record);
      }
   }

   public void recordPlayerPosition(Vector position) {
      long timestamp = System.currentTimeMillis();
      double currentYLevel = position.getY();
      PlayerMiningData.YLevelRecord record = new PlayerMiningData.YLevelRecord(timestamp, currentYLevel, position);
      this.yLevelRecords.add(record);
      if (this.lastYLevel != null) {
         double yChange = Math.abs(currentYLevel - this.lastYLevel);
         if (yChange >= 1.0) {
            this.yLevelChanges.add(yChange);
            this.totalYLevelChanges++;
            this.totalYLevelChangeAmount += yChange;
            long timeAtLevel = timestamp - this.lastYLevelChangeTime;
            if (timeAtLevel > 0L && timeAtLevel < 3600000L) {
               this.timeAtSameYLevel.add(timeAtLevel);
            }

            this.lastYLevelChangeTime = timestamp;
         }
      } else {
         this.lastYLevelChangeTime = timestamp;
      }

      this.lastYLevel = currentYLevel;
   }

   public void recordOreFound(Block block) {
      long timestamp = System.currentTimeMillis();
      Material blockType = block.getType();
      PlayerMiningData.BlockBreakRecord record = new PlayerMiningData.BlockBreakRecord(timestamp, blockType, block.getLocation().toVector());
      this.oreBreaks.add(record);
      this.totalOresMined++;
      this.oreCounts.put(blockType, this.oreCounts.getOrDefault(blockType, 0) + 1);
      this.updateTimeWindows(blockType, timestamp);
   }

   private void recordDiamondOreFound(PlayerMiningData.BlockBreakRecord record) {
      this.diamondOreBreaks.add(record);
      if (this.lastDiamondPosition != null) {
         double distance = this.lastDiamondPosition.distance(record.position);
         this.diamondDistances.add(distance);
      }

      this.lastDiamondPosition = record.position;
      if (this.currentDiamondVein == null) {
         this.currentDiamondVein = new PlayerMiningData.OreVein();
         this.currentDiamondVein.addOre(record);
         this.diamondVeins.add(this.currentDiamondVein);
      } else {
         boolean belongsToCurrentVein = false;

         for (PlayerMiningData.BlockBreakRecord veinOre : this.currentDiamondVein.ores) {
            double distance = veinOre.position.distance(record.position);
            if (distance <= 3.0) {
               belongsToCurrentVein = true;
               break;
            }
         }

         if (belongsToCurrentVein) {
            this.currentDiamondVein.addOre(record);
         } else {
            this.currentDiamondVein = new PlayerMiningData.OreVein();
            this.currentDiamondVein.addOre(record);
            this.diamondVeins.add(this.currentDiamondVein);
         }
      }
   }

   private void recordNetheriteOreFound(PlayerMiningData.BlockBreakRecord record) {
      this.netheriteOreBreaks.add(record);
      if (this.lastNetheritePosition != null) {
         double distance = this.lastNetheritePosition.distance(record.position);
         this.netheriteDistances.add(distance);
      }

      this.lastNetheritePosition = record.position;
      if (this.currentNetheriteVein == null) {
         this.currentNetheriteVein = new PlayerMiningData.OreVein();
         this.currentNetheriteVein.addOre(record);
         this.netheriteVeins.add(this.currentNetheriteVein);
      } else {
         boolean belongsToCurrentVein = false;

         for (PlayerMiningData.BlockBreakRecord veinOre : this.currentNetheriteVein.ores) {
            double distance = veinOre.position.distance(record.position);
            if (distance <= 3.0) {
               belongsToCurrentVein = true;
               break;
            }
         }

         if (belongsToCurrentVein) {
            this.currentNetheriteVein.addOre(record);
         } else {
            this.currentNetheriteVein = new PlayerMiningData.OreVein();
            this.currentNetheriteVein.addOre(record);
            this.netheriteVeins.add(this.currentNetheriteVein);
         }
      }
   }

   private void updateTimeWindows(Material oreType, long timestamp) {
      List<PlayerMiningData.OreTimeWindow> windows = this.oreTimeWindows.get(oreType);
      if (windows.isEmpty()) {
         windows.add(new PlayerMiningData.OreTimeWindow(60000L));
         windows.add(new PlayerMiningData.OreTimeWindow(180000L));
         windows.add(new PlayerMiningData.OreTimeWindow(300000L));
         windows.add(new PlayerMiningData.OreTimeWindow(600000L));
      }

      for (PlayerMiningData.OreTimeWindow window : windows) {
         window.addOre(timestamp);
      }
   }

   private boolean isVeinInLinePattern(PlayerMiningData.OreVein vein) {
      if (vein.ores.size() < 3) {
         return false;
      } else {
         List<Vector> positions = new ArrayList<>();

         for (PlayerMiningData.BlockBreakRecord ore : vein.ores) {
            positions.add(ore.position);
         }

         if (positions.size() == 3) {
            double d12 = positions.get(0).distance(positions.get(1));
            double d23 = positions.get(1).distance(positions.get(2));
            double d13 = positions.get(0).distance(positions.get(2));
            return Math.abs(d13 - (d12 + d23)) < 0.5;
         } else {
            if (positions.size() > 3) {
               Vector prev = null;
               Vector current = null;
               Vector direction = null;
               double totalAngleDeviation = 0.0;
               int angleCount = 0;

               for (Vector pos : positions) {
                  if (prev == null) {
                     prev = pos;
                  } else if (current == null) {
                     current = pos;
                     direction = pos.clone().subtract(prev);
                     direction.normalize();
                  } else {
                     Vector newDirection = pos.clone().subtract(current);
                     newDirection.normalize();
                     double dot = direction.dot(newDirection);
                     dot = Math.max(-1.0, Math.min(1.0, dot));
                     double angle = Math.acos(dot);
                     double deviation = Math.abs(angle - Math.PI);
                     totalAngleDeviation += deviation;
                     angleCount++;
                     prev = current;
                     current = pos;
                     direction = newDirection;
                  }
               }

               if (angleCount > 0) {
                  double avgDeviation = totalAngleDeviation / angleCount;
                  return avgDeviation < 0.3;
               }
            }

            return false;
         }
      }
   }

   public void calculateDerivedFeatures() {
      long totalMiningTimeMs = System.currentTimeMillis() - this.sessionStartTime;
      this.features.put("total_mining_time_seconds", totalMiningTimeMs / 1000.0);
      this.features.put("total_blocks_broken", (double)this.totalBlocksBroken);
      this.features.put("total_ores_mined", (double)this.totalOresMined);

      for (Material oreMaterial : this.valuableOreMaterials) {
         String oreName = oreMaterial.toString().toLowerCase();
         this.features.put("ore_count_" + oreName, (double)this.oreCounts.getOrDefault(oreMaterial, 0).intValue());
      }

      for (Material oreMaterial : this.valuableOreMaterials) {
         if (this.oreTimeWindows.containsKey(oreMaterial)) {
            List<PlayerMiningData.OreTimeWindow> windows = this.oreTimeWindows.get(oreMaterial);
            String oreName = oreMaterial.toString().toLowerCase();

            for (PlayerMiningData.OreTimeWindow window : windows) {
               String windowName;
               if (window.windowSizeMs == 60000L) {
                  windowName = "1min";
               } else if (window.windowSizeMs == 180000L) {
                  windowName = "3min";
               } else if (window.windowSizeMs == 300000L) {
                  windowName = "5min";
               } else {
                  if (window.windowSizeMs != 600000L) {
                     continue;
                  }

                  windowName = "10min";
               }

               this.features.put("ore_rate_" + oreName + "_" + windowName, window.getMaxRate());
            }
         }
      }

      int totalDiamondVeins = this.diamondVeins.size();
      int totalDiamondOresInVeins = 0;
      int diamondVeinsInLinePattern = 0;

      for (PlayerMiningData.OreVein vein : this.diamondVeins) {
         totalDiamondOresInVeins += vein.ores.size();
         if (this.isVeinInLinePattern(vein)) {
            diamondVeinsInLinePattern++;
         }
      }

      double avgDiamondVeinSize = totalDiamondVeins > 0 ? (double)totalDiamondOresInVeins / totalDiamondVeins : 0.0;
      this.features.put("avg_diamond_vein_size", avgDiamondVeinSize);
      int totalNetheriteVeins = this.netheriteVeins.size();
      int totalNetheriteOresInVeins = 0;

      for (PlayerMiningData.OreVein veinx : this.netheriteVeins) {
         totalNetheriteOresInVeins += veinx.ores.size();
      }

      double avgAncientDebrisVeinSize = totalNetheriteVeins > 0 ? (double)totalNetheriteOresInVeins / totalNetheriteVeins : 0.0;
      this.features.put("avg_ancient_debris_vein_size", avgAncientDebrisVeinSize);
      double avgDiamondDistance = 0.0;
      if (!this.diamondDistances.isEmpty()) {
         double totalDiamondDistance = 0.0;

         for (double distance : this.diamondDistances) {
            totalDiamondDistance += distance;
         }

         avgDiamondDistance = totalDiamondDistance / this.diamondDistances.size();
      }

      this.features.put("avg_distance_between_diamonds", avgDiamondDistance);
      double avgNetheriteDistance = 0.0;
      if (!this.netheriteDistances.isEmpty()) {
         double totalNetheriteDistance = 0.0;

         for (double distance : this.netheriteDistances) {
            totalNetheriteDistance += distance;
         }

         avgNetheriteDistance = totalNetheriteDistance / this.netheriteDistances.size();
      }

      this.features.put("avg_distance_between_netherite", avgNetheriteDistance);
      double avgIdleTime = 0.0;
      if (!this.idleTimes.isEmpty()) {
         long totalIdleTime = 0L;

         for (long idleTime : this.idleTimes) {
            totalIdleTime += idleTime;
         }

         avgIdleTime = (double)totalIdleTime / this.idleTimes.size() / 1000.0;
      }

      this.features.put("avg_idle_time_seconds", avgIdleTime);
      double avgYLevelChange = 0.0;
      if (!this.yLevelChanges.isEmpty()) {
         double totalChanges = 0.0;

         for (double change : this.yLevelChanges) {
            totalChanges += change;
         }

         avgYLevelChange = totalChanges / this.yLevelChanges.size();
      }

      this.features.put("avg_y_level_change", avgYLevelChange);
      double avgTimeAtSameYLevel = 0.0;
      if (!this.timeAtSameYLevel.isEmpty()) {
         long totalTime = 0L;

         for (long time : this.timeAtSameYLevel) {
            totalTime += time;
         }

         avgTimeAtSameYLevel = (double)totalTime / this.timeAtSameYLevel.size() / 1000.0;
      }

      this.features.put("avg_time_at_same_y_level", avgTimeAtSameYLevel);
      double miningTimeMinutes = totalMiningTimeMs / 60000.0;
      double yLevelChangesPerMinute = this.totalYLevelChanges / miningTimeMinutes;
      this.features.put("y_level_changes_per_minute", yLevelChangesPerMinute);
      Map<Integer, Long> yLevelFrequency = new HashMap<>();
      long totalRecordedTime = 0L;

      for (int i = 1; i < this.yLevelRecords.size(); i++) {
         PlayerMiningData.YLevelRecord current = this.yLevelRecords.get(i);
         PlayerMiningData.YLevelRecord previous = this.yLevelRecords.get(i - 1);
         int yInt = (int)previous.yLevel;
         long timeSpent = current.timestamp - previous.timestamp;
         if (timeSpent > 0L && timeSpent < 3600000L) {
            yLevelFrequency.put(yInt, yLevelFrequency.getOrDefault(yInt, 0L) + timeSpent);
            totalRecordedTime += timeSpent;
         }
      }

      long maxTimeAtYLevel = 0L;
      int mostCommonYLevel = 0;

      for (Entry<Integer, Long> entry : yLevelFrequency.entrySet()) {
         if (entry.getValue() > maxTimeAtYLevel) {
            maxTimeAtYLevel = entry.getValue();
            mostCommonYLevel = entry.getKey();
         }
      }

      double percentTimeAtMostCommonYLevel = 0.0;
      if (totalRecordedTime > 0L) {
         percentTimeAtMostCommonYLevel = maxTimeAtYLevel * 100.0 / totalRecordedTime;
      }

      this.features.put("percent_time_at_most_common_y", percentTimeAtMostCommonYLevel);
      this.features.put("most_common_y_level", (double)mostCommonYLevel);
      List<Double> yChangesBeforeValuableOre = new ArrayList<>();
      List<Double> yChangesAfterValuableOre = new ArrayList<>();
      long TIME_WINDOW = 10000L;

      for (PlayerMiningData.BlockBreakRecord oreBreak : this.oreBreaks) {
         if (this.valuableOreMaterials.contains(oreBreak.material)) {
            List<PlayerMiningData.YLevelRecord> beforeRecords = new ArrayList<>();
            List<PlayerMiningData.YLevelRecord> afterRecords = new ArrayList<>();

            for (PlayerMiningData.YLevelRecord yRecord : this.yLevelRecords) {
               long timeDiff = yRecord.timestamp - oreBreak.timestamp;
               if (timeDiff < 0L && Math.abs(timeDiff) <= 10000L) {
                  beforeRecords.add(yRecord);
               } else if (timeDiff > 0L && timeDiff <= 10000L) {
                  afterRecords.add(yRecord);
               }
            }

            if (beforeRecords.size() >= 2) {
               double totalYChange = Math.abs(beforeRecords.get(0).yLevel - beforeRecords.get(beforeRecords.size() - 1).yLevel);
               yChangesBeforeValuableOre.add(totalYChange);
            }

            if (afterRecords.size() >= 2) {
               double totalYChange = Math.abs(afterRecords.get(0).yLevel - afterRecords.get(afterRecords.size() - 1).yLevel);
               yChangesAfterValuableOre.add(totalYChange);
            }
         }
      }

      double avgYChangeBeforeOre = 0.0;
      if (!yChangesBeforeValuableOre.isEmpty()) {
         double sum = 0.0;

         for (Double change : yChangesBeforeValuableOre) {
            sum += change;
         }

         avgYChangeBeforeOre = sum / yChangesBeforeValuableOre.size();
      }

      this.features.put("avg_y_change_before_ore", avgYChangeBeforeOre);
      double avgYChangeAfterOre = 0.0;
      if (!yChangesAfterValuableOre.isEmpty()) {
         double sum = 0.0;

         for (Double change : yChangesAfterValuableOre) {
            sum += change;
         }

         avgYChangeAfterOre = sum / yChangesAfterValuableOre.size();
      }

      this.features.put("avg_y_change_after_ore", avgYChangeAfterOre);
      double minY = Double.MAX_VALUE;
      double maxY = Double.MIN_VALUE;

      for (PlayerMiningData.YLevelRecord record : this.yLevelRecords) {
         minY = Math.min(minY, record.yLevel);
         maxY = Math.max(maxY, record.yLevel);
      }

      double yRange = minY != Double.MAX_VALUE && maxY != Double.MIN_VALUE ? maxY - minY : 0.0;
      this.features.put("y_level_range", yRange);
      double yLevelVariance = 0.0;
      if (this.yLevelRecords.size() > 1) {
         double sumY = 0.0;

         for (PlayerMiningData.YLevelRecord record : this.yLevelRecords) {
            sumY += record.yLevel;
         }

         double meanY = sumY / this.yLevelRecords.size();
         double sumSquaredDiff = 0.0;

         for (PlayerMiningData.YLevelRecord record : this.yLevelRecords) {
            double diff = record.yLevel - meanY;
            sumSquaredDiff += diff * diff;
         }

         yLevelVariance = sumSquaredDiff / this.yLevelRecords.size();
      }

      this.features.put("y_level_variance", yLevelVariance);
      this.features.put("y_level_std_dev", Math.sqrt(yLevelVariance));
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public boolean isLabeledAsCheater() {
      return this.labeledAsCheater;
   }

   public Map<String, Double> getFeatures() {
      return this.features;
   }

   public List<PlayerMiningData.BlockBreakRecord> getBlockBreaks() {
      return this.blockBreaks;
   }

   public List<PlayerMiningData.BlockBreakRecord> getOreBreaks() {
      return this.oreBreaks;
   }

   public long getTotalMiningTimeMs() {
      return System.currentTimeMillis() - this.sessionStartTime;
   }

   static class BlockBreakRecord {
      final long timestamp;
      final Material material;
      final Vector position;

      BlockBreakRecord(long timestamp, Material material, Vector position) {
         this.timestamp = timestamp;
         this.material = material;
         this.position = position;
      }
   }

   static class OreTimeWindow {
      final long windowSizeMs;
      final List<Long> oreTimestamps = new ArrayList<>();

      OreTimeWindow(long windowSizeMs) {
         this.windowSizeMs = windowSizeMs;
      }

      void addOre(long timestamp) {
         this.oreTimestamps.add(timestamp);
         long cutoffTime = timestamp - this.windowSizeMs;
         this.oreTimestamps.removeIf(t -> t < cutoffTime);
      }

      double getMaxRate() {
         if (this.oreTimestamps.isEmpty()) {
            return 0.0;
         } else {
            int count = this.oreTimestamps.size();
            return count * 60000.0 / this.windowSizeMs;
         }
      }
   }

   static class OreVein {
      final List<PlayerMiningData.BlockBreakRecord> ores = new ArrayList<>();

      void addOre(PlayerMiningData.BlockBreakRecord ore) {
         this.ores.add(ore);
      }

      int getSize() {
         return this.ores.size();
      }
   }

   static class YLevelRecord {
      final long timestamp;
      final double yLevel;
      final Vector position;

      YLevelRecord(long timestamp, double yLevel, Vector position) {
         this.timestamp = timestamp;
         this.yLevel = yLevel;
         this.position = position;
      }
   }
}
