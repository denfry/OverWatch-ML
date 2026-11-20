package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.denfry.owml.utils.Locs;
import net.denfry.owml.utils.MaterialHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AnomalyDetector implements DetectionAnalyzer {
   private boolean enabled = true;
   private static final double BASELINE_MINING_RATE = 2.0;
   private static final double BASELINE_MOVEMENT_SPEED = 4.3;
   private static final double BASELINE_SESSION_LENGTH = 3600000.0;
   private static final int BASELINE_BLOCKS_PER_SESSION = 500;
   private static final double CRITICAL_Z_SCORE = 3.0;
   private static final double HIGH_Z_SCORE = 2.0;
   private static final double MEDIUM_Z_SCORE = 1.5;

   @Override
   public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
      if (!this.enabled) {
         return DetectionResult.createSafe();
      } else {
         List<String> reasons = new ArrayList<>();
         double totalScore = 0.0;
         Map<String, Object> additionalData = new HashMap<>();
         double miningRateAnomaly = this.analyzeMiningRateAnomaly(data);
         if (miningRateAnomaly > 1.5) {
            String severity = this.getSeverityString(miningRateAnomaly);
            reasons.add(severity + " mining rate anomaly (Z-score: " + String.format("%.1f", miningRateAnomaly) + ")");
            totalScore += this.normalizeZScore(miningRateAnomaly) * 0.4;
            additionalData.put("miningRateZScore", miningRateAnomaly);
         }

         double movementAnomaly = this.analyzeMovementAnomaly(data, context);
         if (movementAnomaly > 1.5) {
            String severity = this.getSeverityString(movementAnomaly);
            reasons.add(severity + " movement pattern anomaly (Z-score: " + String.format("%.1f", movementAnomaly) + ")");
            totalScore += this.normalizeZScore(movementAnomaly) * 0.3;
            additionalData.put("movementZScore", movementAnomaly);
         }

         double timingAnomaly = this.analyzeTimingAnomaly(data);
         if (timingAnomaly > 1.5) {
            String severity = this.getSeverityString(timingAnomaly);
            reasons.add(severity + " timing anomaly (Z-score: " + String.format("%.1f", timingAnomaly) + ")");
            totalScore += this.normalizeZScore(timingAnomaly) * 0.25;
            additionalData.put("timingZScore", timingAnomaly);
         }

         double consistencyAnomaly = this.analyzeBehavioralConsistency(data);
         if (consistencyAnomaly > 1.5) {
            String severity = this.getSeverityString(consistencyAnomaly);
            reasons.add(severity + " behavioral inconsistency (Z-score: " + String.format("%.1f", consistencyAnomaly) + ")");
            totalScore += this.normalizeZScore(consistencyAnomaly) * 0.2;
            additionalData.put("consistencyZScore", consistencyAnomaly);
         }

         if (context.getAction() == DetectionContext.Action.BLOCK_BREAK) {
            double resourceAnomaly = this.analyzeResourceDiscoveryAnomaly(data, context);
            if (resourceAnomaly > 1.5) {
               String severity = this.getSeverityString(resourceAnomaly);
               reasons.add(severity + " resource discovery anomaly (Z-score: " + String.format("%.1f", resourceAnomaly) + ")");
               totalScore += this.normalizeZScore(resourceAnomaly) * 0.35;
               additionalData.put("resourceZScore", resourceAnomaly);
            }
         }

         DetectionLevel level = this.determineDetectionLevel(totalScore);
         return new DetectionResult(level, totalScore, reasons, DetectionType.ANOMALY, additionalData);
      }
   }

   private double analyzeMiningRateAnomaly(PlayerDetectionData data) {
      double currentRate = data.getAverageMiningSpeed();
      if (currentRate < 0.2) {
         return 0.0;
      } else {
         double standardDeviation = 0.6;
         double zScore = (currentRate - 2.0) / standardDeviation;
         return Math.max(0.0, zScore);
      }
   }

   private double analyzeMovementAnomaly(PlayerDetectionData data, DetectionContext context) {
      if (context.getFromLocation() != null
         && context.getLocation() != null
         && context.getFromLocation().getWorld() != null
         && context.getLocation().getWorld() != null
         && context.getFromLocation().getWorld().equals(context.getLocation().getWorld())) {
         double distance = Locs.distance(context.getFromLocation(), context.getLocation());
         double timeDiff = 0.05;
         double speed = distance / timeDiff;
         double speedBPS = speed * 20.0;
         if (speedBPS < 2.15) {
            return 0.0;
         } else {
            double standardDeviation = 1.72;
            double zScore = (speedBPS - 4.3) / standardDeviation;
            return Math.max(0.0, zScore);
         }
      } else {
         return 0.0;
      }
   }

   private double analyzeTimingAnomaly(PlayerDetectionData data) {
      Queue<DetectionResult> recentDetections = data.getRecentDetections();
      if (recentDetections.size() < 3) {
         return 0.0;
      } else {
         List<Double> intervals = new ArrayList<>();
         DetectionResult[] detections = recentDetections.toArray(new DetectionResult[0]);

         for (int i = 1; i < Math.min(detections.length, 10); i++) {
            double interval = Math.abs(detections[i].getConfidence() - detections[i - 1].getConfidence());
            intervals.add(interval);
         }

         if (intervals.isEmpty()) {
            return 0.0;
         } else {
            double mean = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = intervals.stream().mapToDouble(intervalx -> Math.pow(intervalx - mean, 2.0)).average().orElse(0.0);
            if (mean == 0.0) {
               return 0.0;
            } else {
               double cv = Math.sqrt(variance) / mean;
               double optimalCV = 0.5;
               double cvDeviation = Math.abs(cv - optimalCV);
               return cvDeviation / optimalCV;
            }
         }
      }
   }

   private double analyzeBehavioralConsistency(PlayerDetectionData data) {
      Map<String, Double> scores = data.getBehavioralScores();
      if (scores.isEmpty()) {
         return 0.0;
      } else {
         double[] scoreValues = scores.values().stream().mapToDouble(Double::doubleValue).toArray();
         double mean = Arrays.stream(scoreValues).average().orElse(0.0);
         double variance = Arrays.stream(scoreValues).map(score -> Math.pow(score - mean, 2.0)).average().orElse(0.0);
         return Math.sqrt(variance);
      }
   }

   private double analyzeResourceDiscoveryAnomaly(PlayerDetectionData data, DetectionContext context) {
      Material material = context.getMaterial();
      if (material == null) {
         return 0.0;
      } else {
         Map<Material, Double> materialValues = new HashMap<>();
         this.addMaterialValue(materialValues, "DIAMOND_ORE", 10.0);
         this.addMaterialValue(materialValues, "DEEPSLATE_DIAMOND_ORE", 8.0);
         this.addMaterialValue(materialValues, "GOLD_ORE", 7.0);
         this.addMaterialValue(materialValues, "DEEPSLATE_GOLD_ORE", 6.0);
         this.addMaterialValue(materialValues, "EMERALD_ORE", 9.0);
         this.addMaterialValue(materialValues, "DEEPSLATE_EMERALD_ORE", 7.5);
         this.addMaterialValue(materialValues, "ANCIENT_DEBRIS", 12.0);
         this.addMaterialValue(materialValues, "NETHERITE_BLOCK", 15.0);
         Double materialValue = materialValues.get(material);
         if (materialValue == null) {
            return 0.0;
         } else {
            int totalMined = data.getTotalBlocksMined();
            if (totalMined < 100) {
               return 0.0;
            } else {
               double expectedOres = totalMined / 1000.0;
               double actualValuableOres = data.getSuspiciousActions();
               if (expectedOres == 0.0) {
                  return 0.0;
               } else {
                  double luckRatio = actualValuableOres / expectedOres;
                  double baselineLuck = 1.0;
                  double standardDeviation = 0.5;
                  return Math.max(0.0, (luckRatio - baselineLuck) / standardDeviation);
               }
            }
         }
      }
   }

   private void addMaterialValue(Map<Material, Double> map, String materialName, double value) {
      Material material = MaterialHelper.getMaterial(materialName);
      if (material != null) {
         map.put(material, value);
      }
   }

   private String getSeverityString(double zScore) {
      if (zScore >= 3.0) {
         return "Critical";
      } else if (zScore >= 2.0) {
         return "High";
      } else {
         return zScore >= 1.5 ? "Medium" : "Low";
      }
   }

   private double normalizeZScore(double zScore) {
      return 1.0 / (1.0 + Math.exp(-zScore + 1.5));
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
      return DetectionType.ANOMALY;
   }

   @Override
   public String getName() {
      return "Anomaly Detector";
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
