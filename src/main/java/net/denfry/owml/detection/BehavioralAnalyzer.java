package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.denfry.owml.utils.Locs;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BehavioralAnalyzer implements DetectionAnalyzer {
   private boolean enabled = true;

   @Override
   public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
      if (!this.enabled) {
         return DetectionResult.createSafe();
      } else {
         List<String> reasons = new ArrayList<>();
         double totalScore = 0.0;
         Map<String, Object> additionalData = new HashMap<>();
         double miningSpeed = data.getAverageMiningSpeed();
         if (miningSpeed > 3.0) {
            reasons.add("Excessive mining speed: " + String.format("%.1f", miningSpeed) + " blocks/sec");
            totalScore += 0.4;
            additionalData.put("miningSpeed", miningSpeed);
         }

         double efficiency = data.getMiningEfficiency();
         if (efficiency > 50.0) {
            reasons.add("High mining efficiency: " + String.format("%.1f", efficiency) + " blocks/min");
            totalScore += 0.3;
            additionalData.put("miningEfficiency", efficiency);
         }

         double movementAnomaly = this.analyzeMovementPatterns(data);
         if (movementAnomaly > 0.7) {
            reasons.add("Unusual movement patterns detected");
            totalScore += movementAnomaly * 0.3;
            additionalData.put("movementAnomaly", movementAnomaly);
         }

         double timingAnomaly = this.analyzeTimingPatterns(data);
         if (timingAnomaly > 0.6) {
            reasons.add("Suspicious timing patterns in mining activity");
            totalScore += timingAnomaly * 0.2;
            additionalData.put("timingAnomaly", timingAnomaly);
         }

         if (context.getAction() == DetectionContext.Action.BLOCK_BREAK) {
            double sessionAnomaly = this.analyzeSessionBehavior(data, context);
            if (sessionAnomaly > 0.5) {
               reasons.add("Abnormal session mining behavior");
               totalScore += sessionAnomaly * 0.25;
               additionalData.put("sessionAnomaly", sessionAnomaly);
            }
         }

         DetectionLevel level = this.determineDetectionLevel(totalScore);
         return new DetectionResult(level, totalScore, reasons, DetectionType.BEHAVIORAL, additionalData);
      }
   }

   private double analyzeMovementPatterns(PlayerDetectionData data) {
      Queue<Location> locations = data.getRecentLocations();
      if (locations.size() < 10) {
         return 0.0;
      } else {
         List<Location> locationList = new ArrayList<>(locations);
         double totalDistance = 0.0;
         int teleportCount = 0;

         for (int i = 1; i < locationList.size(); i++) {
            Location prev = locationList.get(i - 1);
            Location curr = locationList.get(i);
            if (prev.getWorld() != null && curr.getWorld() != null && prev.getWorld().equals(curr.getWorld())) {
               double distance = Locs.distance(prev, curr);
               totalDistance += distance;
               if (distance > 10.0) {
                  teleportCount++;
               }
            }
         }

         double averageDistance = totalDistance / (locationList.size() - 1);
         double teleportRatio = (double)teleportCount / locationList.size();
         double consistencyScore = this.calculateMovementConsistency(locationList);
         return Math.max(teleportRatio * 0.8, consistencyScore * 0.6);
      }
   }

   private double calculateMovementConsistency(List<Location> locations) {
      if (locations.size() < 5) {
         return 0.0;
      } else {
         double[] distances = new double[locations.size() - 1];

         for (int i = 0; i < distances.length; i++) {
            distances[i] = Locs.distance(locations.get(i), locations.get(i + 1));
         }

         double mean = Arrays.stream(distances).average().orElse(0.0);
         double variance = Arrays.stream(distances).map(d -> Math.pow(d - mean, 2.0)).average().orElse(0.0);
         double stdDev = Math.sqrt(variance);
         return Math.max(0.0, 1.0 - stdDev / mean);
      }
   }

   private double analyzeTimingPatterns(PlayerDetectionData data) {
      Queue<DetectionResult> recentDetections = data.getRecentDetections();
      if (recentDetections.size() < 5) {
         return 0.0;
      } else {
         List<DetectionResult> detections = new ArrayList<>(recentDetections);
         long[] timestamps = new long[detections.size()];

         for (int i = 0; i < detections.size(); i++) {
            timestamps[i] = i * 1000L;
         }

         double averageInterval = 0.0;
         if (timestamps.length > 1) {
            long totalInterval = 0L;

            for (int i = 1; i < timestamps.length; i++) {
               totalInterval += timestamps[i] - timestamps[i - 1];
            }

            averageInterval = (double)totalInterval / (timestamps.length - 1);
         }

         return this.calculateTimingRegularity(timestamps);
      }
   }

   private double calculateTimingRegularity(long[] timestamps) {
      if (timestamps.length < 3) {
         return 0.0;
      } else {
         long[] intervals = new long[timestamps.length - 1];

         for (int i = 0; i < intervals.length; i++) {
            intervals[i] = timestamps[i + 1] - timestamps[i];
         }

         double mean = Arrays.stream(intervals).average().orElse(0.0);
         double variance = Arrays.stream(intervals).mapToDouble(interval -> Math.pow(interval - mean, 2.0)).average().orElse(0.0);
         double cv = Math.sqrt(variance) / mean;
         return Math.max(0.0, 1.0 - cv);
      }
   }

   private double analyzeSessionBehavior(PlayerDetectionData data, DetectionContext context) {
      double currentEfficiency = data.getMiningEfficiency();
      double suspicionRatio = data.getSuspicionRatio();
      double anomalyScore = 0.0;
      if (currentEfficiency > 30.0) {
         anomalyScore += 0.3;
      }

      if (suspicionRatio > 0.4) {
         anomalyScore += suspicionRatio * 0.4;
      }

      double trend = data.getRecentDetectionTrend();
      if (trend > 0.7) {
         anomalyScore += trend * 0.3;
      }

      return Math.min(1.0, anomalyScore);
   }

   private DetectionLevel determineDetectionLevel(double score) {
      if (score >= 0.8) {
         return DetectionLevel.CRITICAL;
      } else if (score >= 0.6) {
         return DetectionLevel.HIGH;
      } else if (score >= 0.4) {
         return DetectionLevel.MEDIUM;
      } else {
         return score >= 0.2 ? DetectionLevel.LOW : DetectionLevel.SAFE;
      }
   }

   @Override
   public DetectionType getDetectionType() {
      return DetectionType.BEHAVIORAL;
   }

   @Override
   public String getName() {
      return "Behavioral Analyzer";
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
