package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;

public class ReasoningMLModel {
   private final OverWatchML plugin;
   private final MLConfig config;
   private final Map<String, ReasoningMLModel.ReferenceStats> referenceStats = new ConcurrentHashMap<>();
   private final double suspicionThreshold = 0.7;
   private boolean trained = false;

   public ReasoningMLModel(OverWatchML plugin, MLConfig config) {
      this.plugin = plugin;
      this.config = config;
   }

   public boolean train() {
      MLDataManager.MLTrainingData trainingData = MLDataManager.loadTrainingData();
      if (!trainingData.hasEnoughData()) {
         this.plugin.getLogger().warning("Not enough training data to train the model");
         return false;
      } else {
         List<Map<String, Double>> normalFeatures = trainingData.getNormalFeatures();
         List<Map<String, Double>> cheaterFeatures = trainingData.getCheaterFeatures();
         Set<String> allFeatures = new HashSet<>();
         normalFeatures.forEach(map -> allFeatures.addAll(map.keySet()));
         cheaterFeatures.forEach(map -> allFeatures.addAll(map.keySet()));

         for (String feature : allFeatures) {
            this.calculateReferenceStats(feature, normalFeatures, cheaterFeatures);
         }

         this.plugin.getLogger().info("Trained reference statistics for " + this.referenceStats.size() + " features");
         this.trained = true;
         return true;
      }
   }

   private void calculateReferenceStats(String feature, List<Map<String, Double>> normalFeatures, List<Map<String, Double>> cheaterFeatures) {
      double normalSum = 0.0;
      int normalCount = 0;

      for (Map<String, Double> player : normalFeatures) {
         if (player.containsKey(feature)) {
            normalSum += player.get(feature);
            normalCount++;
         }
      }

      double normalMean = normalCount > 0 ? normalSum / normalCount : 0.0;
      double normalSumSqDiff = 0.0;

      for (Map<String, Double> playerx : normalFeatures) {
         if (playerx.containsKey(feature)) {
            double diff = playerx.get(feature) - normalMean;
            normalSumSqDiff += diff * diff;
         }
      }

      double normalStdDev = normalCount > 1 ? Math.sqrt(normalSumSqDiff / (normalCount - 1)) : 1.0;
      normalStdDev = Math.max(1.0E-5, normalStdDev);
      double cheaterSum = 0.0;
      int cheaterCount = 0;

      for (Map<String, Double> playerxx : cheaterFeatures) {
         if (playerxx.containsKey(feature)) {
            cheaterSum += playerxx.get(feature);
            cheaterCount++;
         }
      }

      double cheaterMean = cheaterCount > 0 ? cheaterSum / cheaterCount : 0.0;
      double cheaterSumSqDiff = 0.0;

      for (Map<String, Double> playerxxx : cheaterFeatures) {
         if (playerxxx.containsKey(feature)) {
            double diff = playerxxx.get(feature) - cheaterMean;
            cheaterSumSqDiff += diff * diff;
         }
      }

      double cheaterStdDev = cheaterCount > 1 ? Math.sqrt(cheaterSumSqDiff / (cheaterCount - 1)) : 1.0;
      cheaterStdDev = Math.max(1.0E-5, cheaterStdDev);
      this.referenceStats.put(feature, new ReasoningMLModel.ReferenceStats(normalMean, normalStdDev, cheaterMean, cheaterStdDev));
   }

   public ReasoningMLModel.DetectionResult predict(Map<String, Double> features) {
      if (this.trained && !this.referenceStats.isEmpty()) {
         List<String> reasoningSteps = new ArrayList<>();
         ReasoningMLModel.MiningStyle miningStyle = this.identifyMiningStyle(features);
         reasoningSteps.add("Step 0: Mining Style Identification - " + this.describeMiningStyle(miningStyle, features));
         ReasoningMLModel.AnalysisResult blocksResult = this.analyzeBlocksBroken(features, miningStyle);
         reasoningSteps.add("Step 1: " + blocksResult.reason);
         ReasoningMLModel.AnalysisResult oreMiningResult = this.analyzeOreMiningRatio(features, miningStyle);
         reasoningSteps.add("Step 2: " + oreMiningResult.reason);
         ReasoningMLModel.AnalysisResult focusedMiningResult = this.analyzeFocusedMining(features, miningStyle);
         reasoningSteps.add("Step 3: " + focusedMiningResult.reason);
         ReasoningMLModel.AnalysisResult oreRateResult = this.analyzeOreFindingRates(features, miningStyle);
         reasoningSteps.add("Step 4: " + oreRateResult.reason);
         ReasoningMLModel.AnalysisResult patternResult = this.analyzeMiningPatterns(features, miningStyle);
         reasoningSteps.add("Step 5: " + patternResult.reason);
         ReasoningMLModel.AnalysisResult behaviorResult = this.analyzeBehaviorPatterns(features, miningStyle);
         reasoningSteps.add("Step 6: " + behaviorResult.reason);
         double[] weights = this.getWeightsByMiningStyle(miningStyle);
         double[] scores = new double[]{
            blocksResult.suspicionScore,
            oreMiningResult.suspicionScore,
            focusedMiningResult.suspicionScore,
            oreRateResult.suspicionScore,
            patternResult.suspicionScore,
            behaviorResult.suspicionScore
         };
         double finalScore = 0.0;

         for (int i = 0; i < weights.length; i++) {
            finalScore += weights[i] * scores[i];
         }

         double efficiencyOverride = this.calculateOreEfficiencyOverride(features);
         if (efficiencyOverride > 0.0) {
            finalScore = Math.max(finalScore, efficiencyOverride);
         }

         StringBuilder conclusion = new StringBuilder();
         if (finalScore > 0.7) {
            conclusion.append("Player behavior indicates likely X-ray cheating. ");
         } else if (finalScore > 0.65) {
            conclusion.append("Player behavior appears suspicious. ");
         } else {
            conclusion.append("Player behavior appears normal. ");
            if (miningStyle == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
               conclusion.append("Detected as a legitimate branch miner. ");
            } else if (miningStyle == ReasoningMLModel.MiningStyle.CAVE_MINER) {
               conclusion.append("Detected as a legitimate cave explorer. ");
            }
         }

         double maxScore = 0.0;
         int maxIndex = 0;

         for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
               maxScore = scores[i];
               maxIndex = i;
            }
         }

         if (maxScore > 0.7) {
            conclusion.append("Most suspicious aspect: ");
            switch (maxIndex) {
               case 0:
                  conclusion.append("block breaking rate");
                  break;
               case 1:
                  conclusion.append("ore mining ratio");
                  break;
               case 2:
                  conclusion.append("focused ore targeting");
                  break;
               case 3:
                  conclusion.append("ore finding rates");
                  break;
               case 4:
                  conclusion.append("mining patterns");
                  break;
               case 5:
                  conclusion.append("behavior patterns");
            }
         }

         return new ReasoningMLModel.DetectionResult(finalScore, conclusion.toString(), reasoningSteps);
      } else {
         return new ReasoningMLModel.DetectionResult(0.5, "Model not trained", new ArrayList<>());
      }
   }

   private double calculateOreEfficiencyOverride(Map<String, Double> features) {
      Double totalBlocks = features.get("total_blocks_broken");
      if (totalBlocks != null && !(totalBlocks < 100.0)) {
         double diamonds = features.getOrDefault("ore_count_diamond_ore", 0.0) + features.getOrDefault("ore_count_deepslate_diamond_ore", 0.0);
         double debris = features.getOrDefault("ore_count_ancient_debris", 0.0);
         double diamondsPerHundred = diamonds / totalBlocks * 100.0;
         double debrisPerHundred = debris / totalBlocks * 100.0;
         double overrideScore = 0.0;
         if (diamondsPerHundred > 4.0) {
            overrideScore = Math.max(overrideScore, Math.min(0.95, 0.5 + (diamondsPerHundred - 4.0) * 0.05));
         }

         if (debrisPerHundred > 1.5) {
            overrideScore = Math.max(overrideScore, Math.min(0.95, 0.55 + (debrisPerHundred - 1.5) * 0.08));
         }

         return overrideScore;
      } else {
         return 0.0;
      }
   }

   private double[] getWeightsByMiningStyle(ReasoningMLModel.MiningStyle style) {
      switch (style) {
         case UNKNOWN:
         case XRAY_CHEATER:
         default:
            return new double[]{0.15, 0.2, 0.15, 0.2, 0.15, 0.15};
         case BRANCH_MINER:
            return new double[]{0.05, 0.2, 0.15, 0.25, 0.25, 0.1};
         case CAVE_MINER:
            return new double[]{0.1, 0.15, 0.25, 0.25, 0.15, 0.1};
      }
   }

   private ReasoningMLModel.MiningStyle identifyMiningStyle(Map<String, Double> features) {
      boolean likelyBranchMiner = this.isBranchMiner(features);
      boolean likelyCaveMiner = this.isCaveMiner(features);
      boolean likelyXray = false;
      if (features.containsKey("total_ores_mined") && features.containsKey("total_blocks_broken")) {
         double oreRatio = features.get("total_ores_mined") / features.get("total_blocks_broken") * 100.0;
         String mostMinedOre = this.getMostMinedOre(features);
         boolean focusedOnValuableOre = mostMinedOre != null && (mostMinedOre.contains("diamond") || mostMinedOre.contains("ancient_debris"));
         double suspiciousDiamondRate = 0.0;

         for (String key : features.keySet()) {
            if (key.startsWith("ore_rate_") && key.contains("diamond")) {
               ReasoningMLModel.ReferenceStats stats = this.referenceStats.getOrDefault(key, null);
               if (stats != null) {
                  suspiciousDiamondRate = Math.max(suspiciousDiamondRate, stats.calculateSuspicionScore(features.get(key)));
               }
            }
         }

         likelyXray = oreRatio > 10.0 && focusedOnValuableOre && suspiciousDiamondRate > 0.55;
      }

      if (likelyXray) {
         return ReasoningMLModel.MiningStyle.XRAY_CHEATER;
      } else if (likelyBranchMiner) {
         return ReasoningMLModel.MiningStyle.BRANCH_MINER;
      } else {
         return likelyCaveMiner ? ReasoningMLModel.MiningStyle.CAVE_MINER : ReasoningMLModel.MiningStyle.UNKNOWN;
      }
   }

   private boolean isBranchMiner(Map<String, Double> features) {
      boolean consistentYLevel = features.getOrDefault("percent_time_at_most_common_y", 0.0) > 80.0;
      boolean lowYVariance = features.getOrDefault("y_level_variance", 999.0) < 20.0;
      boolean fewYChanges = features.getOrDefault("y_level_changes_per_minute", 999.0) < 5.0;
      boolean smallYRange = features.getOrDefault("y_level_range", 999.0) < 10.0;
      boolean manyBlocksBroken = false;
      if (features.containsKey("total_blocks_broken")) {
         double blocksBroken = features.get("total_blocks_broken");
         double sessionDuration = features.getOrDefault("sessionDuration", 600.0);
         double blocksPerMinute = blocksBroken / (sessionDuration / 60.0);
         manyBlocksBroken = blocksPerMinute > 40.0;
      }

      return consistentYLevel && (lowYVariance || smallYRange) && fewYChanges;
   }

   private boolean isCaveMiner(Map<String, Double> features) {
      boolean highYVariance = features.getOrDefault("y_level_variance", 0.0) > 50.0;
      boolean manyYChanges = features.getOrDefault("y_level_changes_per_minute", 0.0) > 10.0;
      boolean variedYLevels = features.getOrDefault("percent_time_at_most_common_y", 100.0) < 50.0;
      boolean largeYRange = features.getOrDefault("y_level_range", 0.0) > 20.0;
      return (highYVariance || largeYRange) && (manyYChanges || variedYLevels);
   }

   private String describeMiningStyle(ReasoningMLModel.MiningStyle style, Map<String, Double> features) {
      StringBuilder description = new StringBuilder();
      switch (style) {
         case UNKNOWN:
         default:
            description.append("Mining style could not be clearly identified. ");
            description.append("Will analyze individual behaviors to determine legitimacy.");
            break;
         case BRANCH_MINER:
            description.append("Player identified as a branch miner. ");
            if (features.containsKey("most_common_y_level")) {
               description.append("Mining primarily at Y=").append((int)features.get("most_common_y_level").doubleValue()).append(". ");
            }

            description.append("Shows consistent Y-level (")
               .append(String.format("%.1f", features.getOrDefault("percent_time_at_most_common_y", 0.0)))
               .append("% at one level), few vertical movements, and systematic horizontal tunneling.");
            break;
         case CAVE_MINER:
            description.append("Player identified as a cave explorer. ");
            description.append("Shows variable Y-levels with frequent elevation changes (")
               .append(String.format("%.1f", features.getOrDefault("y_level_changes_per_minute", 0.0)))
               .append(" per minute) and explores a vertical range of approximately ")
               .append((int)features.getOrDefault("y_level_range", 0.0).doubleValue())
               .append(" blocks.");
            break;
         case XRAY_CHEATER:
            description.append("Player shows strong indicators of X-ray cheating. ");
            String mostMinedOre = this.getMostMinedOre(features);
            if (mostMinedOre != null) {
               description.append("Focused primarily on ").append(mostMinedOre).append(". ");
            }

            description.append("Movement patterns and ore discovery rates are highly suspicious.");
      }

      return description.toString();
   }

   private String getMostMinedOre(Map<String, Double> features) {
      String mostMinedOre = null;
      double mostMinedAmount = 0.0;

      for (Entry<String, Double> entry : features.entrySet()) {
         if (entry.getKey().startsWith("ore_count_")) {
            String oreType = entry.getKey().substring("ore_count_".length());
            double amount = entry.getValue();
            if (amount > mostMinedAmount) {
               mostMinedAmount = amount;
               mostMinedOre = oreType;
            }
         }
      }

      return mostMinedOre;
   }

   private ReasoningMLModel.AnalysisResult analyzeBlocksBroken(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      if (!features.containsKey("total_blocks_broken")) {
         return new ReasoningMLModel.AnalysisResult(0.5, "No data on blocks broken");
      } else {
         double blocksBroken = features.get("total_blocks_broken");
         double suspicionScore = 0.0;
         ReasoningMLModel.ReferenceStats stats = this.referenceStats
            .getOrDefault("total_blocks_broken", new ReasoningMLModel.ReferenceStats(800.0, 200.0, 1200.0, 300.0));
         suspicionScore = stats.calculateSuspicionScore(blocksBroken);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            suspicionScore = Math.max(0.0, suspicionScore - 0.15);
            if (blocksBroken > stats.normalMean && blocksBroken < stats.normalMean * 2.0) {
               suspicionScore = Math.max(0.0, suspicionScore - 0.05);
            }
         }

         StringBuilder reason = new StringBuilder();
         reason.append("Player broke ").append((int)blocksBroken).append(" blocks, which ");
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            reason.append("is expected for branch mining but ");
         }

         reason.append(stats.getComparisonDescription(blocksBroken)).append(". ");
         double sessionDuration = features.getOrDefault("sessionDuration", 600.0);
         double blocksPerMinute = blocksBroken / (sessionDuration / 60.0);
         reason.append("Mining rate: ").append(String.format("%.1f", blocksPerMinute)).append(" blocks per minute.");
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && suspicionScore < 0.3) {
            reason.append(" This rate is consistent with efficient branch mining.");
         }

         return new ReasoningMLModel.AnalysisResult(suspicionScore, reason.toString());
      }
   }

   private ReasoningMLModel.AnalysisResult analyzeOreMiningRatio(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      if (features.containsKey("total_blocks_broken") && features.containsKey("total_ores_mined")) {
         double totalBlocks = features.get("total_blocks_broken");
         double totalOres = features.get("total_ores_mined");
         if (totalBlocks == 0.0) {
            return new ReasoningMLModel.AnalysisResult(0.5, "No blocks broken, cannot calculate ore ratio");
         } else {
            double oreRatio = totalOres / totalBlocks * 100.0;
            ReasoningMLModel.ReferenceStats oreRatioStats = new ReasoningMLModel.ReferenceStats(5.0, 2.0, 20.0, 10.0);
            if (this.referenceStats.containsKey("ore_ratio")) {
               oreRatioStats = this.referenceStats.get("ore_ratio");
            } else {
               double normalRatio = 0.0;
               double cheaterRatio = 0.0;
               int normalCount = 0;
               int cheaterCount = 0;
               if (this.referenceStats.containsKey("total_blocks_broken") && this.referenceStats.containsKey("total_ores_mined")) {
                  ReasoningMLModel.ReferenceStats blocksStats = this.referenceStats.get("total_blocks_broken");
                  ReasoningMLModel.ReferenceStats oresStats = this.referenceStats.get("total_ores_mined");
                  normalRatio = oresStats.normalMean / blocksStats.normalMean * 100.0;
                  cheaterRatio = oresStats.cheaterMean / blocksStats.cheaterMean * 100.0;
                  oreRatioStats = new ReasoningMLModel.ReferenceStats(normalRatio, normalRatio * 0.4, cheaterRatio, cheaterRatio * 0.5);
               }
            }

            double suspicionScore = oreRatioStats.calculateSuspicionScore(oreRatio);
            if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
               if (oreRatio < 8.0) {
                  suspicionScore = Math.max(0.0, suspicionScore - 0.12);
               }
            } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER && oreRatio < 12.0) {
               suspicionScore = Math.max(0.0, suspicionScore - 0.12);
            }

            StringBuilder reason = new StringBuilder();
            reason.append(String.format("%.1f%%", oreRatio))
               .append(" of blocks broken were ores (")
               .append((int)totalOres)
               .append(" ores out of ")
               .append((int)totalBlocks)
               .append(" blocks), which ");
            if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
               reason.append("for branch mining ");
            } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER) {
               reason.append("for cave exploration ");
            }

            reason.append(oreRatioStats.getComparisonDescription(oreRatio)).append(".");
            if (style == ReasoningMLModel.MiningStyle.CAVE_MINER && oreRatio > oreRatioStats.normalMean) {
               reason.append(" Note: Cave miners typically find more exposed ores than branch miners.");
            }

            return new ReasoningMLModel.AnalysisResult(suspicionScore, reason.toString());
         }
      } else {
         return new ReasoningMLModel.AnalysisResult(0.5, "Insufficient data for ore mining ratio analysis");
      }
   }

   private ReasoningMLModel.AnalysisResult analyzeFocusedMining(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      if (!features.containsKey("total_ores_mined")) {
         return new ReasoningMLModel.AnalysisResult(0.5, "No data on ores mined");
      } else {
         double totalOres = features.get("total_ores_mined");
         if (totalOres == 0.0) {
            return new ReasoningMLModel.AnalysisResult(0.0, "No ores mined");
         } else {
            String mostMinedOre = null;
            double mostMinedAmount = 0.0;
            Map<String, Double> oreCounts = new HashMap<>();

            for (Entry<String, Double> entry : features.entrySet()) {
               if (entry.getKey().startsWith("ore_count_")) {
                  String oreType = entry.getKey().substring("ore_count_".length());
                  double amount = entry.getValue();
                  oreCounts.put(oreType, amount);
                  if (amount > mostMinedAmount) {
                     mostMinedAmount = amount;
                     mostMinedOre = oreType;
                  }
               }
            }

            if (mostMinedOre == null) {
               return new ReasoningMLModel.AnalysisResult(0.5, "No specific ore type data found");
            } else {
               double focusRatio = mostMinedAmount / totalOres * 100.0;
               ReasoningMLModel.ReferenceStats focusStats = new ReasoningMLModel.ReferenceStats(60.0, 15.0, 90.0, 10.0);
               double suspicionScore = focusStats.calculateSuspicionScore(focusRatio);
               boolean isValuableOre = false;
               if (mostMinedOre.contains("diamond") || mostMinedOre.contains("ancient_debris") || mostMinedOre.contains("emerald")) {
                  isValuableOre = true;
                  if (style != ReasoningMLModel.MiningStyle.BRANCH_MINER) {
                     suspicionScore = Math.max(suspicionScore, suspicionScore * 1.3);
                  }
               }

               if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && focusRatio > 70.0 && focusRatio < 95.0 && features.containsKey("most_common_y_level")) {
                  double yLevel = features.get("most_common_y_level");
                  if (yLevel >= -59.0 && yLevel <= -50.0) {
                     suspicionScore = Math.max(0.0, suspicionScore - 0.15);
                  }
               }

               StringBuilder reason = new StringBuilder();
               reason.append("Player's mining is ")
                  .append(String.format("%.1f%%", focusRatio))
                  .append(" focused on ")
                  .append(mostMinedOre)
                  .append(" (")
                  .append((int)mostMinedAmount)
                  .append(" out of ")
                  .append((int)totalOres)
                  .append(" total ores), which ");
               reason.append(focusStats.getComparisonDescription(focusRatio)).append(".");
               if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && isValuableOre) {
                  reason.append(" For branch miners at diamond level, high focus on diamonds is expected. ");
                  if (features.containsKey("most_common_y_level")) {
                     double yLevel = features.get("most_common_y_level");
                     reason.append("Player is mining at Y=").append((int)yLevel).append(". ");
                     if (yLevel >= -59.0 && yLevel <= -50.0) {
                        reason.append("This is optimal diamond mining level.");
                     }
                  }
               } else if (isValuableOre && focusRatio > 80.0 && style != ReasoningMLModel.MiningStyle.BRANCH_MINER) {
                  reason.append(" Highly suspicious focus on valuable ore type.");
               }

               return new ReasoningMLModel.AnalysisResult(suspicionScore, reason.toString());
            }
         }
      }
   }

   private ReasoningMLModel.AnalysisResult analyzeOreFindingRates(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      Map<String, Double> oreRates = new HashMap<>();

      for (Entry<String, Double> entry : features.entrySet()) {
         if (entry.getKey().startsWith("ore_rate_")) {
            oreRates.put(entry.getKey(), entry.getValue());
         }
      }

      if (oreRates.isEmpty()) {
         return new ReasoningMLModel.AnalysisResult(0.5, "No ore rate data available");
      } else {
         double maxSuspicionScore = 0.0;
         String mostSuspiciousRate = null;
         StringBuilder reason = new StringBuilder("Analysis of ore finding rates: ");

         for (Entry<String, Double> rateEntry : oreRates.entrySet()) {
            String rateKey = rateEntry.getKey();
            double rateValue = rateEntry.getValue();
            ReasoningMLModel.ReferenceStats rateStats = this.referenceStats.getOrDefault(rateKey, null);
            if (rateStats == null) {
               String[] parts = rateKey.split("_");
               if (parts.length >= 4) {
                  String oreType = parts[2];
                  if (oreType.contains("diamond")) {
                     rateStats = new ReasoningMLModel.ReferenceStats(3.0, 2.0, 15.0, 7.0);
                  } else if (oreType.contains("ancient_debris")) {
                     rateStats = new ReasoningMLModel.ReferenceStats(1.0, 0.5, 5.0, 2.0);
                  } else {
                     rateStats = new ReasoningMLModel.ReferenceStats(8.0, 4.0, 20.0, 10.0);
                  }
               } else {
                  rateStats = new ReasoningMLModel.ReferenceStats(5.0, 3.0, 15.0, 7.0);
               }
            }

            double suspicionScore = rateStats.calculateSuspicionScore(rateValue);
            if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && rateKey.contains("diamond")) {
               if (rateValue > rateStats.normalMean && rateValue < rateStats.cheaterMean * 0.7) {
                  suspicionScore = Math.max(0.0, suspicionScore - 0.1);
               }
            } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER && rateKey.contains("1min") && rateValue > rateStats.normalMean * 1.5) {
               suspicionScore = Math.max(0.0, suspicionScore - 0.1);
            }

            if (suspicionScore > maxSuspicionScore) {
               maxSuspicionScore = suspicionScore;
               mostSuspiciousRate = rateKey;
            }

            if (rateKey.contains("diamond") || rateKey.contains("ancient_debris")) {
               reason.append("\n- ").append(rateKey).append(": ").append(String.format("%.1f", rateValue)).append(" ores per timeframe, which ");
               if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && rateKey.contains("diamond")) {
                  reason.append("for branch mining ");
               } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER && rateKey.contains("1min")) {
                  reason.append("for cave exploration (short-term spike) ");
               }

               reason.append(rateStats.getComparisonDescription(rateValue));
            }
         }

         if (mostSuspiciousRate != null) {
            double mostSuspiciousValue = oreRates.get(mostSuspiciousRate);
            reason.append("\nMost notable: ").append(mostSuspiciousRate).append(" at ").append(String.format("%.1f", mostSuspiciousValue));
            if (maxSuspicionScore > 0.7) {
               reason.append(" is highly unusual compared to normal players");
               if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
                  reason.append(", even accounting for branch mining techniques");
               } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER) {
                  reason.append(", even accounting for cave exploration advantages");
               }

               reason.append(".");
            } else {
               reason.append(" appears within reasonable limits");
               if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
                  reason.append(" for branch mining");
               } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER) {
                  reason.append(" for cave exploration");
               }

               reason.append(".");
            }
         }

         return new ReasoningMLModel.AnalysisResult(maxSuspicionScore, reason.toString());
      }
   }

   private ReasoningMLModel.AnalysisResult analyzeMiningPatterns(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      StringBuilder reason = new StringBuilder("Mining pattern analysis: ");
      double overallSuspicionScore = 0.0;
      int analyzedPatterns = 0;
      if (features.containsKey("avg_diamond_vein_size")) {
         double veinSize = features.get("avg_diamond_vein_size");
         ReasoningMLModel.ReferenceStats veinStats = this.referenceStats
            .getOrDefault("avg_diamond_vein_size", new ReasoningMLModel.ReferenceStats(2.8, 0.7, 4.5, 1.0));
         double veinSuspicion = veinStats.calculateSuspicionScore(veinSize);
         overallSuspicionScore += veinSuspicion;
         analyzedPatterns++;
         reason.append("\n- Average diamond vein size: ")
            .append(String.format("%.2f", veinSize))
            .append(", which ")
            .append(veinStats.getComparisonDescription(veinSize));
      }

      if (features.containsKey("avg_distance_between_diamonds")) {
         double distance = features.get("avg_distance_between_diamonds");
         ReasoningMLModel.ReferenceStats distanceStats = this.referenceStats
            .getOrDefault("avg_distance_between_diamonds", new ReasoningMLModel.ReferenceStats(15.0, 7.0, 3.5, 1.5));
         double distanceSuspicion = distanceStats.calculateSuspicionScore(distance);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && distance > 5.0 && distance < distanceStats.normalMean) {
            distanceSuspicion = Math.max(0.0, distanceSuspicion - 0.12);
         }

         overallSuspicionScore += distanceSuspicion;
         analyzedPatterns++;
         reason.append("\n- Average distance between diamonds: ").append(String.format("%.2f", distance)).append(" blocks, which ");
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && distance > 5.0) {
            reason.append("for branch mining ");
         }

         reason.append(distanceStats.getComparisonDescription(distance));
         if (distance < 5.0) {
            reason.append(" This is suspiciously close - diamonds are normally far apart.");
         }
      }

      if (features.containsKey("avg_y_change_after_ore")) {
         double yChangeAfterOre = features.get("avg_y_change_after_ore");
         ReasoningMLModel.ReferenceStats yChangeStats = this.referenceStats
            .getOrDefault("avg_y_change_after_ore", new ReasoningMLModel.ReferenceStats(8.0, 3.0, 4.0, 2.0));
         double yChangeSuspicion = yChangeStats.calculateSuspicionScore(yChangeAfterOre);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            yChangeSuspicion = Math.max(0.0, yChangeSuspicion - 0.2);
         }

         overallSuspicionScore += yChangeSuspicion;
         analyzedPatterns++;
         reason.append("\n- Average Y-level change after finding ore: ").append(String.format("%.2f", yChangeAfterOre)).append(" blocks, which ");
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            reason.append("is expected for branch mining but ");
         }

         reason.append(yChangeStats.getComparisonDescription(yChangeAfterOre));
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER && yChangeAfterOre < 1.0) {
            reason.append(" Branch miners typically maintain their Y-level while mining.");
         }
      }

      if (analyzedPatterns == 0) {
         return new ReasoningMLModel.AnalysisResult(0.5, "Insufficient data for mining pattern analysis");
      } else {
         double finalScore = overallSuspicionScore / analyzedPatterns;
         return new ReasoningMLModel.AnalysisResult(finalScore, reason.toString());
      }
   }

   private ReasoningMLModel.AnalysisResult analyzeBehaviorPatterns(Map<String, Double> features, ReasoningMLModel.MiningStyle style) {
      StringBuilder reason = new StringBuilder("Behavior pattern analysis: ");
      double overallSuspicionScore = 0.0;
      int analyzedPatterns = 0;
      if (features.containsKey("y_level_variance")) {
         double variance = features.get("y_level_variance");
         ReasoningMLModel.ReferenceStats varianceStats = this.referenceStats
            .getOrDefault("y_level_variance", new ReasoningMLModel.ReferenceStats(120.0, 40.0, 60.0, 30.0));
         double varianceSuspicion = varianceStats.calculateSuspicionScore(variance);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            varianceSuspicion = Math.max(0.0, varianceSuspicion - 0.25);
         }

         overallSuspicionScore += varianceSuspicion;
         analyzedPatterns++;
         reason.append("\n- Y-level variance: ").append(String.format("%.2f", variance));
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            reason.append(", which is normal for branch mining (they stay at one level)");
         } else {
            reason.append(", which ").append(varianceStats.getComparisonDescription(variance));
         }
      }

      if (features.containsKey("percent_time_at_most_common_y")) {
         double percentTimeAtY = features.get("percent_time_at_most_common_y");
         ReasoningMLModel.ReferenceStats timeAtYStats = this.referenceStats
            .getOrDefault("percent_time_at_most_common_y", new ReasoningMLModel.ReferenceStats(20.0, 10.0, 60.0, 20.0));
         double timeAtYSuspicion = timeAtYStats.calculateSuspicionScore(percentTimeAtY);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            timeAtYSuspicion = Math.max(0.0, timeAtYSuspicion - 0.2);
         }

         overallSuspicionScore += timeAtYSuspicion;
         analyzedPatterns++;
         reason.append("\n- Percent time at most common Y-level: ").append(String.format("%.2f%%", percentTimeAtY));
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            reason.append(", which is normal for branch mining (they stay at one level)");
         } else {
            reason.append(", which ").append(timeAtYStats.getComparisonDescription(percentTimeAtY));
         }

         if (features.containsKey("most_common_y_level")) {
            double mostCommonY = features.get("most_common_y_level");
            reason.append(" (Most common Y-level: ").append((int)mostCommonY).append(")");
            if (mostCommonY >= -59.0 && mostCommonY <= -50.0) {
               if (style != ReasoningMLModel.MiningStyle.BRANCH_MINER) {
                  reason.append(" - This is within the diamond-rich Y level range, which increases suspicion.");
                  overallSuspicionScore += 0.1;
               } else {
                  reason.append(" - This is within optimal diamond mining range, which is expected for branch miners.");
               }
            }
         }
      }

      if (features.containsKey("y_level_changes_per_minute")) {
         double changesPerMinute = features.get("y_level_changes_per_minute");
         ReasoningMLModel.ReferenceStats changesStats = this.referenceStats
            .getOrDefault("y_level_changes_per_minute", new ReasoningMLModel.ReferenceStats(15.0, 7.0, 35.0, 15.0));
         double changesSuspicion = changesStats.calculateSuspicionScore(changesPerMinute);
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            if (changesPerMinute < 5.0) {
               changesSuspicion = 0.0;
            }
         } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER
            && changesPerMinute > changesStats.normalMean
            && changesPerMinute < changesStats.cheaterMean) {
            changesSuspicion = Math.max(0.0, changesSuspicion - 0.3);
         }

         overallSuspicionScore += changesSuspicion;
         analyzedPatterns++;
         reason.append("\n- Y-level changes per minute: ").append(String.format("%.2f", changesPerMinute));
         if (style == ReasoningMLModel.MiningStyle.BRANCH_MINER) {
            reason.append(", which is ");
            if (changesPerMinute < 5.0) {
               reason.append("normal for branch mining (minimal vertical movement)");
            } else {
               reason.append("unusual for branch mining (should have minimal vertical movement)");
            }
         } else if (style == ReasoningMLModel.MiningStyle.CAVE_MINER) {
            reason.append(", which is appropriate for cave exploration");
         } else {
            reason.append(", which ").append(changesStats.getComparisonDescription(changesPerMinute));
         }
      }

      if (analyzedPatterns == 0) {
         return new ReasoningMLModel.AnalysisResult(0.5, "Insufficient data for behavior pattern analysis");
      } else {
         double finalScore = overallSuspicionScore / analyzedPatterns;
         return new ReasoningMLModel.AnalysisResult(finalScore, reason.toString());
      }
   }

   public boolean isTrained() {
      return this.trained;
   }

   private static class AnalysisResult {
      double suspicionScore;
      String reason;

      public AnalysisResult(double suspicionScore, String reason) {
         this.suspicionScore = suspicionScore;
         this.reason = reason;
      }
   }

   public static class DetectionResult {
      private final double probability;
      private final String conclusion;
      private final List<String> reasoningSteps;

      public DetectionResult(double probability, String conclusion, List<String> reasoningSteps) {
         this.probability = probability;
         this.conclusion = conclusion;
         this.reasoningSteps = reasoningSteps;
      }

      public double getProbability() {
         return this.probability;
      }

      public String getConclusion() {
         return this.conclusion;
      }

      public List<String> getReasoningSteps() {
         return this.reasoningSteps;
      }

      public String getDetailedReport() {
         StringBuilder report = new StringBuilder();
         report.append("X-Ray Detection Report\n");
         report.append("---------------------\n");
         report.append("Suspicion Score: ").append(String.format("%.2f", this.probability * 100.0)).append("%\n");
         report.append("Conclusion: ").append(this.conclusion).append("\n\n");
         report.append("Detailed Reasoning:\n");

         for (String step : this.reasoningSteps) {
            report.append(step).append("\n\n");
         }

         return report.toString();
      }
   }

   private static enum MiningStyle {
      UNKNOWN,
      BRANCH_MINER,
      CAVE_MINER,
      XRAY_CHEATER;
   }

   private static class ReferenceStats {
      double normalMean;
      double normalStdDev;
      double cheaterMean;
      double cheaterStdDev;

      public ReferenceStats(double normalMean, double normalStdDev, double cheaterMean, double cheaterStdDev) {
         this.normalMean = normalMean;
         this.normalStdDev = normalStdDev;
         this.cheaterMean = cheaterMean;
         this.cheaterStdDev = cheaterStdDev;
      }

      public double calculateSuspicionScore(double value) {
         double normalZScore = Math.abs((value - this.normalMean) / this.normalStdDev);
         double cheaterZScore = Math.abs((value - this.cheaterMean) / this.cheaterStdDev);
         return normalZScore > cheaterZScore ? 1.0 / (1.0 + Math.exp(-(normalZScore - cheaterZScore))) : 0.0;
      }

      public String getComparisonDescription(double value) {
         double normalDist = Math.abs(value - this.normalMean);
         double cheaterDist = Math.abs(value - this.cheaterMean);
         if (normalDist < cheaterDist) {
            double ratio = cheaterDist / (normalDist > 0.0 ? normalDist : 1.0);
            if (ratio > 5.0) {
               return "strongly resembles normal player behavior";
            } else {
               return ratio > 2.0 ? "resembles normal player behavior" : "somewhat resembles normal player behavior";
            }
         } else {
            double ratio = normalDist / (cheaterDist > 0.0 ? cheaterDist : 1.0);
            if (ratio > 5.0) {
               return "strongly resembles cheater behavior";
            } else {
               return ratio > 2.0 ? "resembles cheater behavior" : "somewhat resembles cheater behavior";
            }
         }
      }
   }
}
