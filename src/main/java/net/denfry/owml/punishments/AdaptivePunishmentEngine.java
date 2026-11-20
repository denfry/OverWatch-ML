package net.denfry.owml.punishments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class AdaptivePunishmentEngine {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, AdaptivePunishmentEngine.PlayerPunishmentHistory> playerHistories = new ConcurrentHashMap<>();
   private final Map<String, AdaptivePunishmentEngine.PunishmentEffectiveness> punishmentStats = new ConcurrentHashMap<>();
   private final Map<String, AdaptivePunishmentEngine.AdaptiveParameter> adaptiveParameters = new ConcurrentHashMap<>();
   private static final double LEARNING_RATE = 0.1;
   private static final double FORGETTING_FACTOR = 0.95;
   private static final int MIN_HISTORY_SIZE = 3;
   private static final int MAX_HISTORY_SIZE = 50;
   private final AtomicLong totalPunishments = new AtomicLong(0L);
   private final AtomicLong successfulPunishments = new AtomicLong(0L);

   public AdaptivePunishmentEngine() {
      this.initializeAdaptiveParameters();
      this.initializePunishmentStats();
      MessageManager.log("info", "Adaptive Punishment Engine initialized");
   }

   private void initializeAdaptiveParameters() {
      this.adaptiveParameters.put("escalation_factor", new AdaptivePunishmentEngine.AdaptiveParameter(1.0, 0.05, 0.5, 3.0));
      this.adaptiveParameters.put("forgiveness_factor", new AdaptivePunishmentEngine.AdaptiveParameter(0.8, 0.02, 0.1, 1.0));
      this.adaptiveParameters.put("context_sensitivity", new AdaptivePunishmentEngine.AdaptiveParameter(1.2, 0.03, 0.5, 2.0));
      this.adaptiveParameters.put("player_type_factor", new AdaptivePunishmentEngine.AdaptiveParameter(1.0, 0.01, 0.3, 2.0));
   }

   private void initializePunishmentStats() {
      String[] punishmentTypes = new String[]{
         "warning",
         "kick",
         "temp_ban_1h",
         "temp_ban_24h",
         "temp_ban_7d",
         "perm_ban",
         "mining_debuff",
         "inventory_lock",
         "speed_reduction",
         "fake_ore_veins",
         "paranoia_effects",
         "cursed_pickaxe"
      };

      for (String type : punishmentTypes) {
         this.punishmentStats.put(type, new AdaptivePunishmentEngine.PunishmentEffectiveness(type));
      }
   }

   public AdaptivePunishmentEngine.PunishmentRecommendation recommendPunishment(
      UUID playerId, String offenseType, double offenseSeverity, Map<String, Object> context
   ) {
      this.totalPunishments.incrementAndGet();
      AdaptivePunishmentEngine.PlayerPunishmentHistory history = this.playerHistories
         .computeIfAbsent(playerId, k -> new AdaptivePunishmentEngine.PlayerPunishmentHistory(playerId));
      AdaptivePunishmentEngine.PlayerBehaviorProfile profile = this.analyzePlayerProfile(history, context);
      double baseSeverity = this.calculateBaseSeverity(offenseType, offenseSeverity, profile);
      double adaptedSeverity = this.applyAdaptiveModifications(baseSeverity, history, profile, context);
      String punishmentType = this.selectOptimalPunishmentType(adaptedSeverity, history, profile);
      double intensity = this.calculatePunishmentIntensity(adaptedSeverity, punishmentType, profile);
      return new AdaptivePunishmentEngine.PunishmentRecommendation(punishmentType, adaptedSeverity, intensity, profile, context);
   }

   public void recordPunishmentOutcome(UUID playerId, String punishmentType, boolean wasEffective, Map<String, Object> outcomeContext) {
      AdaptivePunishmentEngine.PlayerPunishmentHistory history = this.playerHistories
         .computeIfAbsent(playerId, k -> new AdaptivePunishmentEngine.PlayerPunishmentHistory(playerId));
      history.addPunishment(punishmentType, wasEffective, outcomeContext);
      AdaptivePunishmentEngine.PunishmentEffectiveness stats = this.punishmentStats.get(punishmentType);
      if (stats != null) {
         stats.recordOutcome(wasEffective);
      }

      if (wasEffective) {
         this.successfulPunishments.incrementAndGet();
      }

      this.learnFromOutcome(playerId, punishmentType, wasEffective, outcomeContext);
   }

   private AdaptivePunishmentEngine.PlayerBehaviorProfile analyzePlayerProfile(
      AdaptivePunishmentEngine.PlayerPunishmentHistory history, Map<String, Object> context
   ) {
      AdaptivePunishmentEngine.PlayerBehaviorProfile profile = new AdaptivePunishmentEngine.PlayerBehaviorProfile();
      List<AdaptivePunishmentEngine.PunishmentRecord> recentPunishments = history.getRecentPunishments(10);
      if (recentPunishments.size() >= 3) {
         double deterrenceScore = this.calculateDeterrenceScore(recentPunishments);
         profile.setDeterrenceScore(deterrenceScore);
         double resistanceScore = this.calculateResistanceScore(recentPunishments);
         profile.setResistanceScore(resistanceScore);
         AdaptivePunishmentEngine.PlayerType playerType = this.classifyPlayerType(recentPunishments, context);
         profile.setPlayerType(playerType);
         double recidivismProb = this.calculateRecidivismProbability(recentPunishments);
         profile.setRecidivismProbability(recidivismProb);
      }

      this.analyzeContextFactors(profile, context);
      return profile;
   }

   private double calculateBaseSeverity(String offenseType, double offenseSeverity, AdaptivePunishmentEngine.PlayerBehaviorProfile profile) {
      double baseSeverity = this.getOffenseBaseSeverity(offenseType) * offenseSeverity;
      switch (profile.getPlayerType()) {
         case FIRST_TIME:
            baseSeverity *= 0.5;
            break;
         case CASUAL:
            baseSeverity *= 0.7;
         case REGULAR:
         default:
            break;
         case PERSISTENT:
            baseSeverity *= 1.3;
      }

      return Math.max(0.0, Math.min(1.0, baseSeverity));
   }

   private double applyAdaptiveModifications(
      double baseSeverity,
      AdaptivePunishmentEngine.PlayerPunishmentHistory history,
      AdaptivePunishmentEngine.PlayerBehaviorProfile profile,
      Map<String, Object> context
   ) {
      double modifiedSeverity = baseSeverity;
      if (profile.getRecidivismProbability() > 0.7) {
         AdaptivePunishmentEngine.AdaptiveParameter escalation = this.adaptiveParameters.get("escalation_factor");
         modifiedSeverity = baseSeverity * escalation.getValue();
      }

      if (profile.getDeterrenceScore() > 0.8) {
         AdaptivePunishmentEngine.AdaptiveParameter forgiveness = this.adaptiveParameters.get("forgiveness_factor");
         modifiedSeverity *= forgiveness.getValue();
      }

      AdaptivePunishmentEngine.AdaptiveParameter contextSensitivity = this.adaptiveParameters.get("context_sensitivity");
      double contextFactor = this.calculateContextFactor(context);
      modifiedSeverity *= 1.0 + contextFactor * (contextSensitivity.getValue() - 1.0);
      return Math.max(0.0, Math.min(1.0, modifiedSeverity));
   }

   private String selectOptimalPunishmentType(
      double severity, AdaptivePunishmentEngine.PlayerPunishmentHistory history, AdaptivePunishmentEngine.PlayerBehaviorProfile profile
   ) {
      List<String> candidateTypes = this.getCandidatePunishmentTypes(severity);
      if (candidateTypes.isEmpty()) {
         return "warning";
      } else {
         String bestType = candidateTypes.get(0);
         double bestScore = 0.0;

         for (String type : candidateTypes) {
            double effectiveness = this.predictPunishmentEffectiveness(type, profile, history);
            double resistance = this.predictResistance(type, profile);
            double score = effectiveness - resistance * 0.3;
            if (score > bestScore) {
               bestScore = score;
               bestType = type;
            }
         }

         return bestType;
      }
   }

   private double calculatePunishmentIntensity(double severity, String punishmentType, AdaptivePunishmentEngine.PlayerBehaviorProfile profile) {
      double baseIntensity = severity;
      if (profile.getResistanceScore() > 0.7) {
         baseIntensity = severity * 1.5;
      }

      AdaptivePunishmentEngine.PunishmentEffectiveness stats = this.punishmentStats.get(punishmentType);
      if (stats != null && stats.getTotalApplications() > 5) {
         double effectiveness = stats.getEffectivenessRate();
         if (effectiveness < 0.5) {
            baseIntensity *= 1.3;
         } else if (effectiveness > 0.8) {
            baseIntensity *= 0.9;
         }
      }

      return Math.max(0.1, Math.min(5.0, baseIntensity));
   }

   private void learnFromOutcome(UUID playerId, String punishmentType, boolean wasEffective, Map<String, Object> outcomeContext) {
      for (AdaptivePunishmentEngine.AdaptiveParameter param : this.adaptiveParameters.values()) {
         param.update(wasEffective ? 0.01 : -0.01);
      }

      AdaptivePunishmentEngine.PunishmentEffectiveness stats = this.punishmentStats.get(punishmentType);
      if (stats != null) {
         stats.updateEffectivenessModel(wasEffective, outcomeContext);
      }
   }

   private double getOffenseBaseSeverity(String offenseType) {
      String var2 = offenseType.toLowerCase();
      switch (var2) {
         case "minor_xray":
            return 0.3;
         case "moderate_xray":
            return 0.6;
         case "severe_xray":
            return 0.8;
         case "pattern_abuse":
            return 0.5;
         case "speed_hack":
            return 0.7;
         case "inventory_hack":
            return 0.9;
         default:
            return 0.4;
      }
   }

   private double calculateDeterrenceScore(List<AdaptivePunishmentEngine.PunishmentRecord> punishments) {
      if (punishments.isEmpty()) {
         return 1.0;
      } else {
         int effectiveCount = 0;

         for (AdaptivePunishmentEngine.PunishmentRecord record : punishments) {
            if (record.wasEffective) {
               effectiveCount++;
            }
         }

         return (double)effectiveCount / punishments.size();
      }
   }

   private double calculateResistanceScore(List<AdaptivePunishmentEngine.PunishmentRecord> punishments) {
      if (punishments.size() < 2) {
         return 0.0;
      } else {
         int returnCount = 0;

         for (int i = 1; i < punishments.size(); i++) {
            AdaptivePunishmentEngine.PunishmentRecord current = punishments.get(i);
            AdaptivePunishmentEngine.PunishmentRecord previous = punishments.get(i - 1);
            long timeDiff = current.timestamp - previous.timestamp;
            if (timeDiff < 86400000L) {
               returnCount++;
            }
         }

         return (double)returnCount / (punishments.size() - 1);
      }
   }

   private AdaptivePunishmentEngine.PlayerType classifyPlayerType(List<AdaptivePunishmentEngine.PunishmentRecord> punishments, Map<String, Object> context) {
      if (punishments.isEmpty()) {
         return AdaptivePunishmentEngine.PlayerType.FIRST_TIME;
      } else {
         long totalPlayTime = ((Number)context.getOrDefault("total_play_time", 3600000L)).longValue();
         int punishmentCount = punishments.size();
         double punishmentFrequency = punishmentCount / Math.max(1.0, totalPlayTime / 8.64E7);
         if (punishmentFrequency > 2.0) {
            return AdaptivePunishmentEngine.PlayerType.PERSISTENT;
         } else {
            return punishmentFrequency < 0.1 ? AdaptivePunishmentEngine.PlayerType.CASUAL : AdaptivePunishmentEngine.PlayerType.REGULAR;
         }
      }
   }

   private double calculateRecidivismProbability(List<AdaptivePunishmentEngine.PunishmentRecord> punishments) {
      if (punishments.size() < 2) {
         return 0.0;
      } else {
         int recidivismCount = 0;

         for (int i = 1; i < punishments.size(); i++) {
            if (!punishments.get(i - 1).wasEffective) {
               recidivismCount++;
            }
         }

         return (double)recidivismCount / (punishments.size() - 1);
      }
   }

   private void analyzeContextFactors(AdaptivePunishmentEngine.PlayerBehaviorProfile profile, Map<String, Object> context) {
      Integer hourOfDay = (Integer)context.get("hour_of_day");
      if (hourOfDay != null && (hourOfDay >= 22 || hourOfDay <= 6)) {
         profile.addContextFactor("night_time", 1.2);
      }

      Integer playerCount = (Integer)context.get("online_players");
      if (playerCount != null && playerCount > 100) {
         profile.addContextFactor("high_server_load", 1.1);
      }

      Boolean recentRestart = (Boolean)context.get("recent_restart");
      if (Boolean.TRUE.equals(recentRestart)) {
         profile.addContextFactor("post_restart", 0.9);
      }
   }

   private double calculateContextFactor(Map<String, Object> context) {
      double factor = 0.0;
      int factorCount = 0;
      Boolean isFirstOffense = (Boolean)context.get("first_offense");
      if (Boolean.TRUE.equals(isFirstOffense)) {
         factor += 0.8;
         factorCount++;
      }

      Boolean hasAppealHistory = (Boolean)context.get("has_appeal_history");
      if (Boolean.TRUE.equals(hasAppealHistory)) {
         factor += 0.9;
         factorCount++;
      }

      String offenseTime = (String)context.get("offense_time_category");
      if ("peak_hours".equals(offenseTime)) {
         factor++;
         factorCount++;
      }

      return factorCount > 0 ? factor / factorCount : 1.0;
   }

   private List<String> getCandidatePunishmentTypes(double severity) {
      List<String> candidates = new ArrayList<>();
      if (severity < 0.3) {
         candidates.addAll(Arrays.asList("warning", "mining_debuff"));
      } else if (severity < 0.6) {
         candidates.addAll(Arrays.asList("kick", "temp_ban_1h", "inventory_lock"));
      } else if (severity < 0.8) {
         candidates.addAll(Arrays.asList("temp_ban_24h", "speed_reduction", "fake_ore_veins"));
      } else {
         candidates.addAll(Arrays.asList("temp_ban_7d", "perm_ban", "paranoia_effects", "cursed_pickaxe"));
      }

      return candidates;
   }

   private double predictPunishmentEffectiveness(
      String punishmentType, AdaptivePunishmentEngine.PlayerBehaviorProfile profile, AdaptivePunishmentEngine.PlayerPunishmentHistory history
   ) {
      AdaptivePunishmentEngine.PunishmentEffectiveness stats = this.punishmentStats.get(punishmentType);
      if (stats == null) {
         return 0.5;
      } else {
         double baseEffectiveness = stats.getEffectivenessRate();
         if (profile.getPlayerType() == AdaptivePunishmentEngine.PlayerType.CASUAL && punishmentType.contains("ban")) {
            baseEffectiveness *= 0.8;
         }

         if (profile.getResistanceScore() > 0.7) {
            baseEffectiveness *= 0.9;
         }

         List<AdaptivePunishmentEngine.PunishmentRecord> playerRecords = history.getPunishmentsByType(punishmentType);
         if (!playerRecords.isEmpty()) {
            double playerSpecificEffectiveness = playerRecords.stream().mapToInt(r -> r.wasEffective ? 1 : 0).average().orElse(0.5);
            baseEffectiveness = baseEffectiveness * 0.7 + playerSpecificEffectiveness * 0.3;
         }

         return baseEffectiveness;
      }
   }

   private double predictResistance(String punishmentType, AdaptivePunishmentEngine.PlayerBehaviorProfile profile) {
      double baseResistance = profile.getResistanceScore();
      if (punishmentType.contains("debuff") || punishmentType.contains("speed")) {
         baseResistance *= 0.8;
      }

      if (punishmentType.contains("ban")) {
         baseResistance *= 1.2;
      }

      return Math.max(0.0, Math.min(1.0, baseResistance));
   }

   public Map<String, Object> getAdaptiveStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("totalPunishments", this.totalPunishments.get());
      stats.put("successfulPunishments", this.successfulPunishments.get());
      stats.put("successRate", this.totalPunishments.get() > 0L ? (double)this.successfulPunishments.get() / this.totalPunishments.get() : 0.0);
      stats.put("trackedPlayers", this.playerHistories.size());
      Map<String, Double> effectivenessStats = new HashMap<>();

      for (Entry<String, AdaptivePunishmentEngine.PunishmentEffectiveness> entry : this.punishmentStats.entrySet()) {
         effectivenessStats.put(entry.getKey(), entry.getValue().getEffectivenessRate());
      }

      stats.put("punishmentEffectiveness", effectivenessStats);
      Map<String, Double> paramStats = new HashMap<>();

      for (Entry<String, AdaptivePunishmentEngine.AdaptiveParameter> entry : this.adaptiveParameters.entrySet()) {
         paramStats.put(entry.getKey(), entry.getValue().getValue());
      }

      stats.put("adaptiveParameters", paramStats);
      return stats;
   }

   public static class AdaptiveParameter {
      private double currentValue;
      private final double learningRate;
      private final double minValue;
      private final double maxValue;

      public AdaptiveParameter(double initialValue, double learningRate, double minValue, double maxValue) {
         this.currentValue = initialValue;
         this.learningRate = learningRate;
         this.minValue = minValue;
         this.maxValue = maxValue;
      }

      public void update(double delta) {
         this.currentValue = this.currentValue + delta * this.learningRate;
         this.currentValue = Math.max(this.minValue, Math.min(this.maxValue, this.currentValue));
      }

      public double getValue() {
         return this.currentValue;
      }

      public void setValue(double value) {
         this.currentValue = Math.max(this.minValue, Math.min(this.maxValue, value));
      }
   }

   public static class PlayerBehaviorProfile {
      private double deterrenceScore = 0.5;
      private double resistanceScore = 0.0;
      private AdaptivePunishmentEngine.PlayerType playerType = AdaptivePunishmentEngine.PlayerType.REGULAR;
      private double recidivismProbability = 0.0;
      private final Map<String, Double> contextFactors = new ConcurrentHashMap<>();

      public double getDeterrenceScore() {
         return this.deterrenceScore;
      }

      public void setDeterrenceScore(double score) {
         this.deterrenceScore = score;
      }

      public double getResistanceScore() {
         return this.resistanceScore;
      }

      public void setResistanceScore(double score) {
         this.resistanceScore = score;
      }

      public AdaptivePunishmentEngine.PlayerType getPlayerType() {
         return this.playerType;
      }

      public void setPlayerType(AdaptivePunishmentEngine.PlayerType type) {
         this.playerType = type;
      }

      public double getRecidivismProbability() {
         return this.recidivismProbability;
      }

      public void setRecidivismProbability(double prob) {
         this.recidivismProbability = prob;
      }

      public void addContextFactor(String factor, double value) {
         this.contextFactors.put(factor, value);
      }

      public Map<String, Double> getContextFactors() {
         return new HashMap<>(this.contextFactors);
      }
   }

   public static class PlayerPunishmentHistory {
      private final UUID playerId;
      private final List<AdaptivePunishmentEngine.PunishmentRecord> punishmentRecords = new ArrayList<>();

      public PlayerPunishmentHistory(UUID playerId) {
         this.playerId = playerId;
      }

      public void addPunishment(String punishmentType, boolean wasEffective, Map<String, Object> context) {
         this.punishmentRecords.add(new AdaptivePunishmentEngine.PunishmentRecord(punishmentType, wasEffective, System.currentTimeMillis(), context));
         if (this.punishmentRecords.size() > 50) {
            this.punishmentRecords.remove(0);
         }
      }

      public List<AdaptivePunishmentEngine.PunishmentRecord> getRecentPunishments(int count) {
         int start = Math.max(0, this.punishmentRecords.size() - count);
         return new ArrayList<>(this.punishmentRecords.subList(start, this.punishmentRecords.size()));
      }

      public List<AdaptivePunishmentEngine.PunishmentRecord> getPunishmentsByType(String type) {
         List<AdaptivePunishmentEngine.PunishmentRecord> matching = new ArrayList<>();

         for (AdaptivePunishmentEngine.PunishmentRecord record : this.punishmentRecords) {
            if (record.punishmentType.equals(type)) {
               matching.add(record);
            }
         }

         return matching;
      }

      public int getTotalPunishments() {
         return this.punishmentRecords.size();
      }
   }

   public static enum PlayerType {
      FIRST_TIME,
      CASUAL,
      REGULAR,
      PERSISTENT;
   }

   public static class PunishmentEffectiveness {
      private final String punishmentType;
      private int totalApplications = 0;
      private int successfulApplications = 0;
      private double effectivenessRate = 0.5;
      private final Map<String, Double> contextFactors = new ConcurrentHashMap<>();

      public PunishmentEffectiveness(String punishmentType) {
         this.punishmentType = punishmentType;
      }

      public void recordOutcome(boolean wasEffective) {
         this.totalApplications++;
         if (wasEffective) {
            this.successfulApplications++;
         }

         double newRate = (double)this.successfulApplications / this.totalApplications;
         this.effectivenessRate = this.effectivenessRate * 0.95 + newRate * 0.050000000000000044;
      }

      public void updateEffectivenessModel(boolean wasEffective, Map<String, Object> context) {
         for (Entry<String, Object> entry : context.entrySet()) {
            String factor = entry.getKey();
            if (entry.getValue() instanceof Number) {
               double value = ((Number)entry.getValue()).doubleValue();
               double currentFactor = this.contextFactors.getOrDefault(factor, 1.0);
               double adjustment = wasEffective ? 1.01 : 0.99;
               this.contextFactors.put(factor, currentFactor * adjustment);
            }
         }
      }

      public double getEffectivenessRate() {
         return this.effectivenessRate;
      }

      public int getTotalApplications() {
         return this.totalApplications;
      }

      public int getSuccessfulApplications() {
         return this.successfulApplications;
      }
   }

   public static class PunishmentRecommendation {
      public final String punishmentType;
      public final double severity;
      public final double intensity;
      public final AdaptivePunishmentEngine.PlayerBehaviorProfile playerProfile;
      public final Map<String, Object> context;

      public PunishmentRecommendation(
         String punishmentType, double severity, double intensity, AdaptivePunishmentEngine.PlayerBehaviorProfile playerProfile, Map<String, Object> context
      ) {
         this.punishmentType = punishmentType;
         this.severity = severity;
         this.intensity = intensity;
         this.playerProfile = playerProfile;
         this.context = context;
      }

      public String getReasoning() {
         return String.format(
            "Type: %s, Severity: %.2f, Intensity: %.2f, Player Type: %s",
            this.punishmentType,
            this.severity,
            this.intensity,
            this.playerProfile.getPlayerType()
         );
      }
   }

   public static class PunishmentRecord {
      public final String punishmentType;
      public final boolean wasEffective;
      public final long timestamp;
      public final Map<String, Object> context;

      public PunishmentRecord(String punishmentType, boolean wasEffective, long timestamp, Map<String, Object> context) {
         this.punishmentType = punishmentType;
         this.wasEffective = wasEffective;
         this.timestamp = timestamp;
         this.context = context != null ? new HashMap<>(context) : new HashMap<>();
      }
   }
}
