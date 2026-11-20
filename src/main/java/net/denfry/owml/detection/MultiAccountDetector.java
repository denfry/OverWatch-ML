package net.denfry.owml.detection;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MultiAccountDetector implements DetectionAnalyzer {
   private boolean enabled = true;
   private final Map<String, Set<UUID>> ipToPlayers = new ConcurrentHashMap<>();
   private final Map<UUID, String> playerToIP = new ConcurrentHashMap<>();

   @Override
   public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
      if (!this.enabled) {
         return DetectionResult.createSafe();
      } else {
         try {
            InetAddress address = player.getAddress().getAddress();
            String ip = address.getHostAddress();
            UUID playerId = player.getUniqueId();
            this.playerToIP.put(playerId, ip);
            this.ipToPlayers.computeIfAbsent(ip, k -> new HashSet<>()).add(playerId);
            Set<UUID> playersFromSameIP = this.ipToPlayers.get(ip);
            if (playersFromSameIP != null && playersFromSameIP.size() > 1) {
               List<String> reasons = new ArrayList<>();
               double totalScore = 0.0;
               Map<String, Object> additionalData = new HashMap<>();
               int accountCount = playersFromSameIP.size();
               if (accountCount >= 3) {
                  reasons.add("Multiple accounts from same IP: " + accountCount + " accounts");
                  totalScore += Math.min(0.8, accountCount * 0.2);
                  additionalData.put("accountCount", accountCount);
               }

               double behaviorSimilarity = this.analyzeAccountSimilarity(playersFromSameIP);
               if (behaviorSimilarity > 0.7) {
                  reasons.add("Similar suspicious behavior across accounts: " + String.format("%.1f%%", behaviorSimilarity * 100.0));
                  totalScore += behaviorSimilarity * 0.4;
                  additionalData.put("behaviorSimilarity", behaviorSimilarity);
               }

               double timingSimilarity = this.analyzeTimingSimilarity(playersFromSameIP);
               if (timingSimilarity > 0.6) {
                  reasons.add("Synchronized account usage patterns detected");
                  totalScore += timingSimilarity * 0.3;
                  additionalData.put("timingSimilarity", timingSimilarity);
               }

               double coordinationScore = this.analyzeCoordination(playersFromSameIP);
               if (coordinationScore > 0.5) {
                  reasons.add("Coordinated suspicious activities detected");
                  totalScore += coordinationScore * 0.35;
                  additionalData.put("coordinationScore", coordinationScore);
               }

               DetectionLevel level = this.determineDetectionLevel(totalScore);
               additionalData.put("ip", ip);
               additionalData.put("accounts", this.getAccountNames(playersFromSameIP));
               return new DetectionResult(level, totalScore, reasons, DetectionType.MULTI_ACCOUNT, additionalData);
            } else {
               return DetectionResult.createSafe();
            }
         } catch (Exception var20) {
            return DetectionResult.createSafe();
         }
      }
   }

   private double analyzeAccountSimilarity(Set<UUID> playerIds) {
      if (playerIds.size() < 2) {
         return 0.0;
      } else {
         List<Double> suspicionLevels = new ArrayList<>();
         List<Double> miningRates = new ArrayList<>();

         for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
               double suspicionLevel = this.getPlayerSuspicionLevel(playerId);
               double miningRate = this.getPlayerMiningRate(playerId);
               suspicionLevels.add(suspicionLevel);
               miningRates.add(miningRate);
            }
         }

         if (suspicionLevels.size() < 2) {
            return 0.0;
         } else {
            double suspicionCV = this.calculateCoefficientOfVariation(suspicionLevels);
            double miningRateCV = this.calculateCoefficientOfVariation(miningRates);
            return 1.0 - (suspicionCV + miningRateCV) / 2.0;
         }
      }
   }

   private double analyzeTimingSimilarity(Set<UUID> playerIds) {
      long onlineCount = playerIds.stream().<Player>map(Bukkit::getPlayer).filter(Objects::nonNull).count();
      return onlineCount >= 2L ? Math.min(0.8, onlineCount * 0.3) : 0.0;
   }

   private double analyzeCoordination(Set<UUID> playerIds) {
      int suspiciousAccounts = 0;

      for (UUID playerId : playerIds) {
         double suspicionLevel = this.getPlayerSuspicionLevel(playerId);
         if (suspicionLevel > 0.3) {
            suspiciousAccounts++;
         }
      }

      double coordinationRatio = (double)suspiciousAccounts / playerIds.size();
      return Math.min(1.0, coordinationRatio * 1.5);
   }

   private double getPlayerSuspicionLevel(UUID playerId) {
      try {
         Class<?> suspiciousManagerClass = Class.forName("net.denfry.owml.managers.SuspiciousManager");
         Method getSuspicionMethod = suspiciousManagerClass.getMethod("getSuspicionLevel", UUID.class);
         Integer suspicionLevel = (Integer)getSuspicionMethod.invoke(null, playerId);
         return suspicionLevel != null ? suspicionLevel.doubleValue() / 100.0 : 0.0;
      } catch (Exception var5) {
         return 0.0;
      }
   }

   private double getPlayerMiningRate(UUID playerId) {
      try {
         Class<?> statsManagerClass = Class.forName("net.denfry.owml.managers.StatsManager");
         Method getMiningStatsMethod = statsManagerClass.getMethod("getPlayerMiningRate", UUID.class);
         Double miningRate = (Double)getMiningStatsMethod.invoke(null, playerId);
         return miningRate != null ? miningRate : 0.0;
      } catch (Exception var5) {
         return 0.0;
      }
   }

   private double calculateCoefficientOfVariation(List<Double> values) {
      if (values.isEmpty()) {
         return 0.0;
      } else {
         double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
         if (mean == 0.0) {
            return 0.0;
         } else {
            double variance = values.stream().mapToDouble(val -> Math.pow(val - mean, 2.0)).average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            return stdDev / mean;
         }
      }
   }

   private List<String> getAccountNames(Set<UUID> playerIds) {
      List<String> names = new ArrayList<>();

      for (UUID playerId : playerIds) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null) {
            names.add(player.getName());
         } else {
            names.add(playerId.toString().substring(0, 8) + "...");
         }
      }

      return names;
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
      return DetectionType.MULTI_ACCOUNT;
   }

   @Override
   public String getName() {
      return "Multi-Account Detector";
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
