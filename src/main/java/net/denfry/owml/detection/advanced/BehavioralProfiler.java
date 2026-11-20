package net.denfry.owml.detection.advanced;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class BehavioralProfiler {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, BehavioralProfiler.PlayerBehavioralProfile> profiles = new ConcurrentHashMap<>();
   private final Map<String, BehavioralProfiler.BehavioralBaseline> baselines = new ConcurrentHashMap<>();
   private final BehavioralProfiler.RiskAssessmentEngine riskEngine = new BehavioralProfiler.RiskAssessmentEngine();

   public BehavioralProfiler() {
      this.initializeBaselines();
      MessageManager.log("info", "Behavioral Profiler initialized with advanced analysis capabilities");
   }

   public void updateProfile(UUID playerId, BehavioralProfiler.BehavioralData data) {
      BehavioralProfiler.PlayerBehavioralProfile profile = this.profiles
         .computeIfAbsent(playerId, k -> new BehavioralProfiler.PlayerBehavioralProfile(playerId));
      profile.update(data);
      profile.updateLastActivity();
   }

   public BehavioralProfiler.BehavioralAnalysisResult analyzeBehavior(UUID playerId) {
      BehavioralProfiler.PlayerBehavioralProfile profile = this.profiles.get(playerId);
      if (profile != null && profile.hasEnoughData()) {
         List<BehavioralProfiler.Anomaly> anomalies = new ArrayList<>();
         double riskScore = 0.0;
         riskScore += this.analyzeMiningBehavior(profile, anomalies);
         riskScore += this.analyzeMovementBehavior(profile, anomalies);
         riskScore += this.analyzeInteractionBehavior(profile, anomalies);
         riskScore += this.analyzeTemporalBehavior(profile, anomalies);
         riskScore += this.analyzeSocialBehavior(profile, anomalies);
         riskScore = Math.min(1.0, riskScore);
         String riskLevel = this.getRiskLevel(riskScore);
         return new BehavioralProfiler.BehavioralAnalysisResult(riskScore, anomalies, riskLevel);
      } else {
         return new BehavioralProfiler.BehavioralAnalysisResult(0.0, Collections.emptyList(), "insufficient_data");
      }
   }

   private double analyzeMiningBehavior(BehavioralProfiler.PlayerBehavioralProfile profile, List<BehavioralProfiler.Anomaly> anomalies) {
      double riskScore = 0.0;
      double avgMiningSpeed = profile.getAverageMiningSpeed();
      BehavioralProfiler.BehavioralBaseline speedBaseline = this.baselines.get("mining_speed");
      if (speedBaseline != null) {
         double deviation = Math.abs(avgMiningSpeed - speedBaseline.mean) / speedBaseline.stdDev;
         if (deviation > 3.0) {
            riskScore += 0.3;
            anomalies.add(new BehavioralProfiler.Anomaly("mining_speed", deviation, "unusual_mining_speed"));
         }
      }

      double patternConsistency = profile.getMiningPatternConsistency();
      if (patternConsistency < 0.3) {
         riskScore += 0.2;
         anomalies.add(new BehavioralProfiler.Anomaly("pattern_consistency", patternConsistency, "inconsistent_mining"));
      }

      double rareOreRatio = profile.getRareOreRatio();
      if (rareOreRatio > 0.8) {
         riskScore += 0.4;
         anomalies.add(new BehavioralProfiler.Anomaly("rare_ore_ratio", rareOreRatio, "excessive_rare_ore_mining"));
      }

      return riskScore;
   }

   private double analyzeMovementBehavior(BehavioralProfiler.PlayerBehavioralProfile profile, List<BehavioralProfiler.Anomaly> anomalies) {
      double riskScore = 0.0;
      double avgSpeed = profile.getAverageMovementSpeed();
      BehavioralProfiler.BehavioralBaseline speedBaseline = this.baselines.get("movement_speed");
      if (speedBaseline != null) {
         double deviation = Math.abs(avgSpeed - speedBaseline.mean) / speedBaseline.stdDev;
         if (deviation > 2.5) {
            riskScore += 0.2;
            anomalies.add(new BehavioralProfiler.Anomaly("movement_speed", deviation, "unusual_movement_speed"));
         }
      }

      int teleportCount = profile.getTeleportCount();
      if (teleportCount > 10) {
         riskScore += 0.15;
         anomalies.add(new BehavioralProfiler.Anomaly("teleport_frequency", teleportCount, "frequent_teleportation"));
      }

      double pathEfficiency = profile.getPathEfficiency();
      if (pathEfficiency > 0.9) {
         riskScore += 0.1;
         anomalies.add(new BehavioralProfiler.Anomaly("path_efficiency", pathEfficiency, "suspiciously_efficient_paths"));
      }

      return riskScore;
   }

   private double analyzeInteractionBehavior(BehavioralProfiler.PlayerBehavioralProfile profile, List<BehavioralProfiler.Anomaly> anomalies) {
      double riskScore = 0.0;
      Map<String, Integer> commandUsage = profile.getCommandUsage();
      int suspiciousCommands = 0;

      for (Entry<String, Integer> entry : commandUsage.entrySet()) {
         String command = entry.getKey();
         int count = entry.getValue();
         if (command.contains("gamemode") && count > 5) {
            suspiciousCommands++;
         }

         if (command.contains("give") && count > 3) {
            suspiciousCommands++;
         }

         if (command.contains("op") && count > 0) {
            suspiciousCommands += 2;
         }
      }

      if (suspiciousCommands > 0) {
         riskScore += Math.min(0.3, suspiciousCommands * 0.1);
         anomalies.add(new BehavioralProfiler.Anomaly("command_usage", suspiciousCommands, "suspicious_command_usage"));
      }

      int inventoryChanges = profile.getInventoryChanges();
      if (inventoryChanges > 50) {
         riskScore += 0.1;
         anomalies.add(new BehavioralProfiler.Anomaly("inventory_changes", inventoryChanges, "excessive_inventory_manipulation"));
      }

      return riskScore;
   }

   private double analyzeTemporalBehavior(BehavioralProfiler.PlayerBehavioralProfile profile, List<BehavioralProfiler.Anomaly> anomalies) {
      double riskScore = 0.0;
      long avgSessionTime = profile.getAverageSessionTime();
      if (avgSessionTime > 28800000L) {
         riskScore += 0.1;
         anomalies.add(new BehavioralProfiler.Anomaly("session_duration", avgSessionTime, "extended_sessions"));
      }

      Map<Integer, Integer> hourlyActivity = profile.getHourlyActivity();
      double activityVariance = this.calculateVariance(hourlyActivity.values());
      BehavioralProfiler.BehavioralBaseline activityBaseline = this.baselines.get("activity_variance");
      if (activityBaseline != null) {
         double deviation = Math.abs(activityVariance - activityBaseline.mean) / activityBaseline.stdDev;
         if (deviation > 2.0) {
            riskScore += 0.15;
            anomalies.add(new BehavioralProfiler.Anomaly("activity_pattern", deviation, "unusual_activity_pattern"));
         }
      }

      double idleRatio = profile.getIdleTimeRatio();
      if (idleRatio > 0.7) {
         riskScore += 0.1;
         anomalies.add(new BehavioralProfiler.Anomaly("idle_ratio", idleRatio, "excessive_idle_time"));
      }

      return riskScore;
   }

   private double analyzeSocialBehavior(BehavioralProfiler.PlayerBehavioralProfile profile, List<BehavioralProfiler.Anomaly> anomalies) {
      double riskScore = 0.0;
      int messageCount = profile.getMessageCount();
      int spamMessages = profile.getSpamMessageCount();
      if (spamMessages > messageCount * 0.3) {
         riskScore += 0.1;
         anomalies.add(new BehavioralProfiler.Anomaly("chat_spam", spamMessages, "excessive_spam"));
      }

      Set<UUID> associations = profile.getPlayerAssociations();
      if (associations.size() > 20) {
         riskScore += 0.05;
         anomalies.add(new BehavioralProfiler.Anomaly("social_connections", associations.size(), "large_social_network"));
      }

      return riskScore;
   }

   private void initializeBaselines() {
      this.baselines.put("mining_speed", new BehavioralProfiler.BehavioralBaseline(2.5, 0.8));
      this.baselines.put("movement_speed", new BehavioralProfiler.BehavioralBaseline(4.5, 1.2));
      this.baselines.put("activity_variance", new BehavioralProfiler.BehavioralBaseline(150.0, 50.0));
      MessageManager.log("info", "Behavioral baselines initialized with {COUNT} metrics", "COUNT", String.valueOf(this.baselines.size()));
   }

   public void updateBaselines(Map<String, Double> normalBehaviorData) {
      for (Entry<String, Double> entry : normalBehaviorData.entrySet()) {
         String metric = entry.getKey();
         double value = entry.getValue();
         BehavioralProfiler.BehavioralBaseline baseline = this.baselines.computeIfAbsent(metric, k -> new BehavioralProfiler.BehavioralBaseline(value, 1.0));
         baseline.update(value);
      }
   }

   private String getRiskLevel(double score) {
      if (score >= 0.8) {
         return "critical";
      } else if (score >= 0.6) {
         return "high";
      } else if (score >= 0.4) {
         return "medium";
      } else {
         return score >= 0.2 ? "low" : "normal";
      }
   }

   private double calculateVariance(Collection<Integer> values) {
      if (values.isEmpty()) {
         return 0.0;
      } else {
         double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
         return values.stream().mapToDouble(v -> Math.pow(v.intValue() - mean, 2.0)).average().orElse(0.0);
      }
   }

   public Map<String, Object> getProfilingStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("profilesTracked", this.profiles.size());
      stats.put("baselinesLearned", this.baselines.size());
      long activeProfiles = this.profiles.values().stream().mapToLong(p -> System.currentTimeMillis() - p.getLastActivity() < 86400000L ? 1L : 0L).sum();
      stats.put("activeProfiles", activeProfiles);
      return stats;
   }

   public Map<String, Double> getBehavioralFingerprint(UUID playerId) {
      BehavioralProfiler.PlayerBehavioralProfile profile = this.profiles.get(playerId);
      return profile == null ? new HashMap<>() : new HashMap<>(profile.aggregatedMetrics);
   }

   public double calculateSimilarity(UUID playerId1, UUID playerId2) {
      Map<String, Double> fingerprint1 = this.getBehavioralFingerprint(playerId1);
      Map<String, Double> fingerprint2 = this.getBehavioralFingerprint(playerId2);
      return !fingerprint1.isEmpty() && !fingerprint2.isEmpty() ? this.calculateCosineSimilarity(fingerprint1, fingerprint2) : 0.0;
   }

   private double calculateCosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
      Set<String> allKeys = new HashSet<>();
      allKeys.addAll(vector1.keySet());
      allKeys.addAll(vector2.keySet());
      double dotProduct = 0.0;
      double norm1 = 0.0;
      double norm2 = 0.0;

      for (String key : allKeys) {
         double val1 = vector1.getOrDefault(key, 0.0);
         double val2 = vector2.getOrDefault(key, 0.0);
         dotProduct += val1 * val2;
         norm1 += val1 * val1;
         norm2 += val2 * val2;
      }

      return norm1 != 0.0 && norm2 != 0.0 ? dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)) : 0.0;
   }

   public static class Anomaly {
      public final String metric;
      public final double severity;
      public final String type;

      public Anomaly(String metric, double severity, String type) {
         this.metric = metric;
         this.severity = severity;
         this.type = type;
      }
   }

   public static class BehavioralAnalysisResult {
      public final double riskScore;
      public final List<BehavioralProfiler.Anomaly> anomalies;
      public final String riskLevel;

      public BehavioralAnalysisResult(double riskScore, List<BehavioralProfiler.Anomaly> anomalies, String riskLevel) {
         this.riskScore = riskScore;
         this.anomalies = anomalies;
         this.riskLevel = riskLevel;
      }
   }

   private static class BehavioralBaseline {
      private double sum = 0.0;
      private double sumSquares = 0.0;
      private int count = 0;
      public final double mean = this.getMean();
      public final double stdDev = this.getStdDev();

      public BehavioralBaseline(double initialValue, double initialStdDev) {
         this.update(initialValue);
      }

      public void update(double value) {
         this.sum += value;
         this.sumSquares += value * value;
         this.count++;
      }

      public double getMean() {
         return this.count > 0 ? this.sum / this.count : 0.0;
      }

      public double getStdDev() {
         if (this.count <= 1) {
            return 1.0;
         } else {
            double mean = this.getMean();
            return Math.sqrt(this.sumSquares / this.count - mean * mean);
         }
      }
   }

   public static class BehavioralData {
      public final long timestamp = System.currentTimeMillis();
      public final String actionType;
      public final Map<String, Double> metrics;
      public final Map<String, Object> context;

      public BehavioralData(String actionType, Map<String, Double> metrics, Map<String, Object> context) {
         this.actionType = actionType;
         this.metrics = (Map<String, Double>)(metrics != null ? metrics : new HashMap<>());
         this.context = (Map<String, Object>)(context != null ? context : new HashMap<>());
      }
   }

   private static class PlayerBehavioralProfile {
      private final UUID playerId;
      private final List<BehavioralProfiler.BehavioralData> behaviorHistory = new ArrayList<>();
      private final Map<String, Double> aggregatedMetrics = new ConcurrentHashMap<>();
      private final Map<String, Integer> commandUsage = new ConcurrentHashMap<>();
      private final Map<Integer, Integer> hourlyActivity = new ConcurrentHashMap<>();
      private final Set<UUID> associations = ConcurrentHashMap.newKeySet();
      private long lastActivity = System.currentTimeMillis();
      private int teleportCount = 0;
      private int inventoryChanges = 0;
      private int messageCount = 0;
      private int spamMessageCount = 0;

      public PlayerBehavioralProfile(UUID playerId) {
         this.playerId = playerId;
      }

      public void update(BehavioralProfiler.BehavioralData data) {
         this.behaviorHistory.add(data);
         if (this.behaviorHistory.size() > 1000) {
            this.behaviorHistory.remove(0);
         }

         for (Entry<String, Double> entry : data.metrics.entrySet()) {
            String metric = entry.getKey();
            double value = entry.getValue();
            double current = this.aggregatedMetrics.getOrDefault(metric, 0.0);
            int count = this.getMetricCount(metric) + 1;
            this.aggregatedMetrics.put(metric, (current * (count - 1) + value) / count);
         }

         this.updateSpecificMetrics(data);
      }

      private void updateSpecificMetrics(BehavioralProfiler.BehavioralData data) {
         String actionType = data.actionType;
         switch (actionType) {
            case "command":
               String command = (String)data.context.getOrDefault("command", "");
               this.commandUsage.put(command, this.commandUsage.getOrDefault(command, 0) + 1);
               break;
            case "teleport":
               this.teleportCount++;
               break;
            case "inventory_change":
               this.inventoryChanges++;
               break;
            case "chat_message":
               this.messageCount++;
               String message = (String)data.context.getOrDefault("message", "");
               if (this.isSpamMessage(message)) {
                  this.spamMessageCount++;
               }
               break;
            case "player_interaction":
               UUID otherPlayer = (UUID)data.context.get("other_player");
               if (otherPlayer != null) {
                  this.associations.add(otherPlayer);
               }
         }

         int hour = (int)(data.timestamp / 3600000L % 24L);
         this.hourlyActivity.put(hour, this.hourlyActivity.getOrDefault(hour, 0) + 1);
      }

      private boolean isSpamMessage(String message) {
         if (message != null && message.length() >= 10) {
            String lower = message.toLowerCase();
            return lower.contains("spam") || lower.matches(".*(.)\\1{10,}.*") || message.length() > 200;
         } else {
            return false;
         }
      }

      private int getMetricCount(String metric) {
         return (int)this.behaviorHistory.stream().filter(data -> data.metrics.containsKey(metric)).count();
      }

      public boolean hasEnoughData() {
         return this.behaviorHistory.size() >= 50;
      }

      public double getAverageMiningSpeed() {
         return this.aggregatedMetrics.getOrDefault("mining_speed", 0.0);
      }

      public double getMiningPatternConsistency() {
         return this.aggregatedMetrics.getOrDefault("pattern_consistency", 1.0);
      }

      public double getRareOreRatio() {
         return this.aggregatedMetrics.getOrDefault("rare_ore_ratio", 0.0);
      }

      public double getAverageMovementSpeed() {
         return this.aggregatedMetrics.getOrDefault("movement_speed", 0.0);
      }

      public double getPathEfficiency() {
         return this.aggregatedMetrics.getOrDefault("path_efficiency", 0.0);
      }

      public long getAverageSessionTime() {
         return this.aggregatedMetrics.getOrDefault("session_time", 0.0).longValue();
      }

      public double getIdleTimeRatio() {
         return this.aggregatedMetrics.getOrDefault("idle_ratio", 0.0);
      }

      public Map<String, Integer> getCommandUsage() {
         return new HashMap<>(this.commandUsage);
      }

      public Map<Integer, Integer> getHourlyActivity() {
         return new HashMap<>(this.hourlyActivity);
      }

      public Set<UUID> getPlayerAssociations() {
         return new HashSet<>(this.associations);
      }

      public int getTeleportCount() {
         return this.teleportCount;
      }

      public int getInventoryChanges() {
         return this.inventoryChanges;
      }

      public int getMessageCount() {
         return this.messageCount;
      }

      public int getSpamMessageCount() {
         return this.spamMessageCount;
      }

      public long getLastActivity() {
         return this.lastActivity;
      }

      public void updateLastActivity() {
         this.lastActivity = System.currentTimeMillis();
      }
   }

   private static class RiskAssessmentEngine {
   }
}
