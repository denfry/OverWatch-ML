package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.denfry.owml.utils.Locs;
import net.denfry.owml.utils.MaterialHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PathPredictor implements DetectionAnalyzer {
   private boolean enabled = true;
   private static final Set<Material> VALUABLE_ORES = initializeValuableOres();

   private static Set<Material> initializeValuableOres() {
      Set<Material> ores = new HashSet<>();
      addMaterialIfExists(ores, "DIAMOND_ORE");
      addMaterialIfExists(ores, "DEEPSLATE_DIAMOND_ORE");
      addMaterialIfExists(ores, "GOLD_ORE");
      addMaterialIfExists(ores, "DEEPSLATE_GOLD_ORE");
      addMaterialIfExists(ores, "EMERALD_ORE");
      addMaterialIfExists(ores, "DEEPSLATE_EMERALD_ORE");
      addMaterialIfExists(ores, "ANCIENT_DEBRIS");
      return Collections.unmodifiableSet(ores);
   }

   private static void addMaterialIfExists(Set<Material> set, String materialName) {
      Material material = MaterialHelper.getMaterial(materialName);
      if (material != null) {
         set.add(material);
      }
   }

   @Override
   public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
      if (this.enabled && context.getAction() == DetectionContext.Action.BLOCK_BREAK) {
         Material material = context.getMaterial();
         if (material != null && VALUABLE_ORES.contains(material)) {
            Location location = context.getLocation();
            if (location == null) {
               return DetectionResult.createSafe();
            } else {
               List<String> reasons = new ArrayList<>();
               double totalScore = 0.0;
               Map<String, Object> additionalData = new HashMap<>();
               double efficiencyScore = this.analyzeMiningEfficiency(data, location);
               if (efficiencyScore > 0.8) {
                  reasons.add("Unusually efficient ore discovery path: " + String.format("%.1f%%", efficiencyScore * 100.0));
                  totalScore += efficiencyScore * 0.4;
                  additionalData.put("efficiencyScore", efficiencyScore);
               }

               double directionScore = this.analyzeDirectionalConsistency(data);
               if (directionScore > 0.7) {
                  reasons.add("Highly directional mining pattern detected");
                  totalScore += directionScore * 0.3;
                  additionalData.put("directionScore", directionScore);
               }

               double explorationRatio = this.analyzeExplorationRatio(data);
               if (explorationRatio < 0.3) {
                  reasons.add("Low exploration ratio: " + String.format("%.1f%%", explorationRatio * 100.0));
                  totalScore += (1.0 - explorationRatio) * 0.35;
                  additionalData.put("explorationRatio", explorationRatio);
               }

               double clusteringScore = this.analyzeOreClustering(data, material);
               if (clusteringScore > 0.75) {
                  reasons.add("Suspicious ore clustering pattern");
                  totalScore += clusteringScore * 0.4;
                  additionalData.put("clusteringScore", clusteringScore);
               }

               DetectionLevel level = this.determineDetectionLevel(totalScore);
               return new DetectionResult(level, totalScore, reasons, DetectionType.PATH_PREDICTION, additionalData);
            }
         } else {
            return DetectionResult.createSafe();
         }
      } else {
         return DetectionResult.createSafe();
      }
   }

   private double analyzeMiningEfficiency(PlayerDetectionData data, Location currentLocation) {
      Queue<Location> recentLocations = data.getRecentLocations();
      if (recentLocations.size() < 5) {
         return 0.0;
      } else {
         List<Location> locations = new ArrayList<>(recentLocations);
         locations.add(currentLocation);
         double straightness = this.calculatePathStraightness(locations);
         int valuableOres = this.countValuableOres(locations);
         int totalBlocks = locations.size();
         double discoveryRate = totalBlocks > 0 ? (double)valuableOres / totalBlocks : 0.0;
         return straightness * 0.6 + discoveryRate * 2.0;
      }
   }

   private double calculatePathStraightness(List<Location> locations) {
      if (locations.size() < 3) {
         return 0.0;
      } else {
         Location start = locations.get(0);
         Location end = locations.get(locations.size() - 1);
         if (start.getWorld() != null && end.getWorld() != null && start.getWorld().equals(end.getWorld())) {
            double directDistance = Locs.distance(start, end);
            double actualDistance = 0.0;

            for (int i = 1; i < locations.size(); i++) {
               Location prev = locations.get(i - 1);
               Location curr = locations.get(i);
               if (prev.getWorld() != null && curr.getWorld() != null && prev.getWorld().equals(curr.getWorld())) {
                  actualDistance += Locs.distance(prev, curr);
               }
            }

            if (actualDistance == 0.0) {
               return 0.0;
            } else {
               double straightness = directDistance / actualDistance;
               return Math.min(2.0, straightness);
            }
         } else {
            return 0.0;
         }
      }
   }

   private int countValuableOres(List<Location> locations) {
      return (int)locations.stream().filter(loc -> this.isLikelyOreLocation(loc)).count();
   }

   private boolean isLikelyOreLocation(Location loc) {
      int y = loc.getBlockY();
      return y >= 5 && y <= 20 || y >= -50 && y <= -10;
   }

   private double analyzeDirectionalConsistency(PlayerDetectionData data) {
      Queue<Location> recentLocations = data.getRecentLocations();
      if (recentLocations.size() < 10) {
         return 0.0;
      } else {
         List<Location> locations = new ArrayList<>(recentLocations);
         List<double[]> vectors = new ArrayList<>();

         for (int i = 1; i < locations.size(); i++) {
            Location prev = locations.get(i - 1);
            Location curr = locations.get(i);
            double[] vector = new double[]{curr.getX() - prev.getX(), curr.getY() - prev.getY(), curr.getZ() - prev.getZ()};
            vectors.add(vector);
         }

         if (vectors.size() < 2) {
            return 0.0;
         } else {
            double[] avgVector = new double[3];

            for (double[] vector : vectors) {
               for (int i = 0; i < 3; i++) {
                  avgVector[i] += vector[i];
               }
            }

            for (int i = 0; i < 3; i++) {
               avgVector[i] /= vectors.size();
            }

            double totalAlignment = 0.0;

            for (double[] vector : vectors) {
               double alignment = this.calculateVectorAlignment(avgVector, vector);
               totalAlignment += alignment;
            }

            return totalAlignment / vectors.size();
         }
      }
   }

   private double calculateVectorAlignment(double[] v1, double[] v2) {
      double dotProduct = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
      double mag1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
      double mag2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]);
      return mag1 != 0.0 && mag2 != 0.0 ? dotProduct / (mag1 * mag2) : 0.0;
   }

   private double analyzeExplorationRatio(PlayerDetectionData data) {
      Queue<Location> recentLocations = data.getRecentLocations();
      if (recentLocations.size() < 20) {
         return 0.5;
      } else {
         List<Location> locations = new ArrayList<>(recentLocations);
         Set<String> uniqueChunks = new HashSet<>();
         Set<String> uniqueAreas = new HashSet<>();

         for (Location loc : locations) {
            String chunkKey = loc.getBlockX() / 16 + "," + loc.getBlockZ() / 16;
            uniqueChunks.add(chunkKey);
            String areaKey = loc.getBlockX() / 5 + "," + loc.getBlockY() / 5 + "," + loc.getBlockZ() / 5;
            uniqueAreas.add(areaKey);
         }

         return Math.min(1.0, (double)uniqueAreas.size() / locations.size());
      }
   }

   private double analyzeOreClustering(PlayerDetectionData data, Material targetMaterial) {
      Queue<Location> recentLocations = data.getRecentLocations();
      if (recentLocations.size() < 10) {
         return 0.0;
      } else {
         List<Location> locations = new ArrayList<>(recentLocations);
         int clusterCount = 0;
         int totalOres = 0;

         for (Location loc : locations) {
            if (this.isLikelyOreLocation(loc)) {
               totalOres++;
               int nearbyOres = this.countNearbyOres(loc, locations);
               if (nearbyOres >= 3) {
                  clusterCount++;
               }
            }
         }

         if (totalOres == 0) {
            return 0.0;
         } else {
            double clusteringRatio = (double)clusterCount / totalOres;
            return Math.min(1.0, clusteringRatio * 2.0);
         }
      }
   }

   private int countNearbyOres(Location center, List<Location> allLocations) {
      return center != null && center.getWorld() != null
         ? (int)allLocations.stream()
            .filter(loc -> this.isLikelyOreLocation(loc))
            .filter(loc -> loc != null && loc.getWorld() != null && loc.getWorld().equals(center.getWorld()) && loc.distance(center) <= 5.0)
            .count()
         : 0;
   }

   private DetectionLevel determineDetectionLevel(double score) {
      if (score >= 0.9) {
         return DetectionLevel.CRITICAL;
      } else if (score >= 0.75) {
         return DetectionLevel.HIGH;
      } else if (score >= 0.6) {
         return DetectionLevel.MEDIUM;
      } else {
         return score >= 0.4 ? DetectionLevel.LOW : DetectionLevel.SAFE;
      }
   }

   @Override
   public DetectionType getDetectionType() {
      return DetectionType.PATH_PREDICTION;
   }

   @Override
   public String getName() {
      return "Path Prediction Analyzer";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
