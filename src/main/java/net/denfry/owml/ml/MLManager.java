package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.SuspiciousManager;
import net.denfry.owml.managers.WebhookManager;
import net.denfry.owml.ml.advanced.AdvancedFeatureEngineer;
import net.denfry.owml.ml.advanced.AutoencoderAnomalyDetector;
import net.denfry.owml.ml.advanced.EnsembleDetector;
import net.denfry.owml.ml.advanced.IsolationForest;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MLManager implements Listener {
   private final OverWatchML plugin;
   private final MLConfig mlConfig;
   private final PlayerDataCollector dataCollector;
   private final ReasoningMLModel model;
   private final ConfigManager configManager;
   private final WebhookManager webhookManager;
   private final OnlineLearningManager onlineLearningManager;
   private final PlayerClusteringEngine clusteringEngine;
   private final AutoTrainer autoTrainer;
   private final EnsembleDetector ensembleDetector;
   private final IsolationForest isolationForest;
   private final AutoencoderAnomalyDetector autoencoder;
   private final AdvancedFeatureEngineer featureEngineer;
   private final Map<UUID, Schedulers.Task> trainingTasks = new ConcurrentHashMap<>();
   private final Map<UUID, Schedulers.Task> analysisTasks = new ConcurrentHashMap<>();
   private final Map<UUID, Long> analysisEndTimes = new ConcurrentHashMap<>();
   private final Map<UUID, Long> trainingEndTimes = new ConcurrentHashMap<>();
   private final Queue<UUID> autoAnalysisQueue = new ConcurrentLinkedQueue<>();
   private final Set<UUID> playersUnderAnalysis = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private final Map<UUID, ReasoningMLModel.DetectionResult> detectionResults = new ConcurrentHashMap<>();

   public MLManager(OverWatchML plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.mlConfig = new MLConfig(plugin);
      this.dataCollector = new PlayerDataCollector(this.mlConfig);
      this.model = new ReasoningMLModel(plugin, this.mlConfig);
      this.webhookManager = WebhookManager.getInstance();
      this.onlineLearningManager = new OnlineLearningManager();
      this.clusteringEngine = new PlayerClusteringEngine();
      this.autoTrainer = new AutoTrainer();
      this.ensembleDetector = new EnsembleDetector();
      this.isolationForest = new IsolationForest(100, 10, 0.6);
      this.autoencoder = null;
      this.featureEngineer = new AdvancedFeatureEngineer();
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      MLDataManager.initialize(plugin);
      this.initializeAutoAnalysis();
      if (!this.model.train()) {
         plugin.getLogger().warning("Not enough training data. Use '/owml train <player> <cheater|normal>' to collect data.");
      } else {
         plugin.getLogger().info("ML model trained successfully!");
      }
   }

   public void startTraining(Player player, boolean isCheater) {
      if (this.mlConfig.isEnabled()) {
         UUID playerId = player.getUniqueId();
         if (this.trainingTasks.containsKey(playerId)) {
            this.trainingTasks.get(playerId).cancel();
            this.trainingTasks.remove(playerId);
            this.trainingEndTimes.remove(playerId);
            this.dataCollector.stopCollecting(player);
         }

         this.dataCollector.startCollecting(player, isCheater);
         long endTime = System.currentTimeMillis() + this.mlConfig.getTrainingSessionDuration() * 1000L;
         this.trainingEndTimes.put(playerId, endTime);
         Schedulers.Task task = Schedulers.globalLater(() -> {
            PlayerMiningData data = this.dataCollector.stopCollecting(player);
            this.trainingTasks.remove(playerId);
            this.trainingEndTimes.remove(playerId);
            if (data != null) {
               MLDataManager.saveTrainingData(data);
               String labelx = isCheater ? "cheater" : "normal player";

               for (Player staffx : Bukkit.getOnlinePlayers()) {
                  if (this.plugin.wantsAlerts(staffx)) {
                     staffx.sendMessage("§a[OverWatch-ML] §fTraining data collection complete for " + player.getName() + " as " + labelx + ".");
                     if (this.model.train()) {
                        staffx.sendMessage("§a[OverWatch-ML] §fML model retrained successfully!");
                     }
                  }
               }

               this.plugin.getLogger().info("Training data collection complete for " + player.getName() + " as " + (isCheater ? "cheater" : "normal player"));
            }
         }, this.mlConfig.getTrainingSessionDuration() * 20L);
         this.trainingTasks.put(playerId, task);
         String label = isCheater ? "cheater" : "normal player";

         for (Player staff : Bukkit.getOnlinePlayers()) {
            if (this.plugin.wantsAlerts(staff)) {
               staff.sendMessage(
                  "§a[OverWatch-ML] §fStarted collecting training data from "
                     + player.getName()
                     + " as "
                     + label
                     + " for "
                     + this.mlConfig.getTrainingSessionDuration()
                     + " seconds."
               );
            }
         }

         this.plugin
            .getLogger()
            .info(
               "Started collecting training data from "
                  + player.getName()
                  + " as "
                  + (isCheater ? "cheater" : "normal player")
                  + " for "
                  + this.mlConfig.getTrainingSessionDuration()
                  + " seconds."
            );
      }
   }

   public void startAnalysis(Player player) {
      if (this.mlConfig.isEnabled()) {
         if (!this.model.isTrained()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
               if (this.plugin.wantsAlerts(staff)) {
                  staff.sendMessage("§c[OverWatch-ML] §fML model is not trained yet. Use '/owml ml train' first.");
               }
            }
         } else {
            UUID playerId = player.getUniqueId();
            if (this.analysisTasks.containsKey(playerId)) {
               this.analysisTasks.get(playerId).cancel();
               this.analysisTasks.remove(playerId);
               this.analysisEndTimes.remove(playerId);
               this.dataCollector.stopCollecting(player);
            }

            this.dataCollector.startCollecting(player, false);
            this.playersUnderAnalysis.add(playerId);
            long endTime = System.currentTimeMillis() + this.mlConfig.getTrainingSessionDuration() * 1000L;
            this.analysisEndTimes.put(playerId, endTime);
            Schedulers.Task task = Schedulers.globalLater(() -> {
               PlayerMiningData data = this.dataCollector.stopCollecting(player);
               this.analysisTasks.remove(playerId);
               this.analysisEndTimes.remove(playerId);
               this.playersUnderAnalysis.remove(playerId);
               if (data != null) {
                  this.analyzePlayerData(player, data);
               }
            }, this.mlConfig.getTrainingSessionDuration() * 20L);
            this.analysisTasks.put(playerId, task);

            for (Player staffx : Bukkit.getOnlinePlayers()) {
               if (this.plugin.wantsAlerts(staffx) && !staffx.getUniqueId().equals(playerId)) {
                  staffx.sendMessage(
                     "§a[OverWatch-ML] §fStarted analyzing "
                        + player.getName()
                        + "'s mining behavior for "
                        + this.mlConfig.getTrainingSessionDuration()
                        + " seconds."
                  );
               }
            }

            this.plugin.getLogger().info("Started analyzing " + player.getName() + " for potential X-ray usage");
         }
      }
   }

   private void initializeAutoAnalysis() {
      if (!this.mlConfig.isAutoAnalysisEnabled()) {
         this.autoAnalysisQueue.clear();
      }
   }

   private void processAutoAnalysisQueue() {
      if (this.mlConfig.isAutoAnalysisEnabled() && this.model.isTrained()) {
         int maxPlayers = this.mlConfig.getMaxAutoAnalysisPlayers();
         int currentlyAnalyzing = this.playersUnderAnalysis.size();
         int availableSlots = Math.max(0, maxPlayers - currentlyAnalyzing);
         if (!this.autoAnalysisQueue.isEmpty()) {
            List<UUID> sortedQueue = new ArrayList<>(this.autoAnalysisQueue);
            Map<UUID, Integer> suspiciousCounts = SuspiciousManager.getSuspiciousCounts();
            sortedQueue.sort((id1, id2) -> Integer.compare(suspiciousCounts.getOrDefault(id2, 0), suspiciousCounts.getOrDefault(id1, 0)));
            this.autoAnalysisQueue.clear();
            this.autoAnalysisQueue.addAll(sortedQueue);
         }

         int processed = 0;

         while (!this.autoAnalysisQueue.isEmpty() && processed < availableSlots) {
            UUID playerId = this.autoAnalysisQueue.peek();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
               if (!this.hasExistingReport(playerId, player.getName())) {
                  this.autoAnalysisQueue.poll();
                  this.startAnalysis(player);
                  processed++;

                  for (Player staff : Bukkit.getOnlinePlayers()) {
                     if (this.plugin.wantsAlerts(staff)) {
                        int suspiciousCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
                        staff.sendMessage(
                           "§7[OverWatch-ML] §fAuto-analyzing player §e" + player.getName() + " §fdue to high suspicious count (§e" + suspiciousCount + "§f)"
                        );
                     }
                  }
               } else {
                  this.autoAnalysisQueue.poll();
                  this.plugin.getLogger().info("Skipping auto-analysis for " + player.getName() + " as they already have a report");
               }
            } else {
               this.autoAnalysisQueue.poll();
            }
         }
      }
   }

   public void queuePlayerForAnalysis(UUID playerId) {
      if (!this.autoAnalysisQueue.contains(playerId) && !this.playersUnderAnalysis.contains(playerId)) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null && player.isOnline()) {
            this.autoAnalysisQueue.add(playerId);
            int suspiciousCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
            this.plugin.getLogger().info("Added " + player.getName() + " to analysis queue with suspicious count: " + suspiciousCount);
            this.processAutoAnalysisQueue();
         }
      }
   }

   private void analyzePlayerData(Player player, PlayerMiningData data) {
      data.calculateDerivedFeatures();
      Map<String, Double> rawFeatures = data.getFeatures();
      List<Map<String, Double>> playerHistory = this.getPlayerHistory(player.getUniqueId());
      Map<String, Double> enhancedFeatures = this.featureEngineer.engineerFeatures(rawFeatures, playerHistory);
      double ensembleScore = this.ensembleDetector.predict(enhancedFeatures);
      double isolationScore = this.isolationForest.score(enhancedFeatures);
      double combinedScore = ensembleScore * 0.7 + isolationScore * 0.3;
      ReasoningMLModel.DetectionResult result;
      if (this.ensembleDetector.isTrained()) {
         result = this.createEnsembleResult(combinedScore, enhancedFeatures);
      } else {
         result = this.model.predict(data.getFeatures());
      }

      this.detectionResults.put(player.getUniqueId(), result);
      MLDataManager.savePlayerData(data);
      String reportPath = MLDataManager.saveDetectionReport(player.getName(), result, data);
      double cheatingProbability = result.getProbability();
      double cheatingPercentage = cheatingProbability * 100.0;
      StringBuilder keyFeaturesBuilder = new StringBuilder();
      List<String> steps = result.getReasoningSteps();

      for (int i = 0; i < Math.min(3, steps.size()); i++) {
         String step = steps.get(i);
         int endPos = step.indexOf(". ");
         if (endPos > 0) {
            keyFeaturesBuilder.append("• ").append(step, 0, endPos + 1).append("\n");
         } else {
            keyFeaturesBuilder.append("• ").append(step).append("\n");
         }
      }

      keyFeaturesBuilder.append("\nConclusion: ").append(result.getConclusion());
      boolean isSuspicious = cheatingProbability >= 0.65;
      boolean isHighlySuspicious = cheatingProbability >= this.mlConfig.getDetectionThreshold();
      String conclusion = result.getConclusion();
      this.webhookManager.sendMLAnalysisAlert(player, isSuspicious, cheatingPercentage, conclusion);
      String summary = String.format("§6Analysis for %s: %.1f%% chance of X-ray", player.getName(), cheatingPercentage);
      if (isHighlySuspicious) {
         summary = summary + " §c[HIGHLY SUSPICIOUS]";
         this.plugin
            .getLogger()
            .warning("Highly suspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");
         this.plugin.getLogger().info(result.getDetailedReport());
         this.plugin.getLogger().info("Full report saved to: " + reportPath);

         for (Player staff : Bukkit.getOnlinePlayers()) {
            if (this.plugin.wantsAlerts(staff)) {
               staff.sendMessage(
                  "§c[OverWatch-ML] §fHighly suspicious mining pattern detected for "
                     + player.getName()
                     + " ("
                     + String.format("%.1f", cheatingPercentage)
                     + "% confidence)"
               );
               staff.sendMessage("§7Key findings: " + result.getConclusion());
               staff.sendMessage("§7Use §f/owml ml report " + player.getName() + " §7to view analysis");
               staff.sendMessage("§7Or check the ML Analysis section in the Staff Control Panel");
            }
         }
      } else if (isSuspicious) {
         summary = summary + " §e[SUSPICIOUS]";
         this.plugin
            .getLogger()
            .warning("Suspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");
         this.plugin.getLogger().info(result.getDetailedReport());
         this.plugin.getLogger().info("Full report saved to: " + reportPath);

         for (Player staffx : Bukkit.getOnlinePlayers()) {
            if (this.plugin.wantsAlerts(staffx)) {
               staffx.sendMessage(
                  "§e[OverWatch-ML] §fSuspicious mining pattern detected for "
                     + player.getName()
                     + " ("
                     + String.format("%.1f", cheatingPercentage)
                     + "% confidence)"
               );
               staffx.sendMessage("§7Key findings: " + result.getConclusion());
               staffx.sendMessage("§7Use §f/owml ml report " + player.getName() + " §7to view analysis");
               staffx.sendMessage("§7Or check the ML Analysis section in the Staff Control Panel");
            }
         }
      } else {
         this.plugin.getLogger().info("Analysis completed for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% suspicion level)");
         this.plugin.getLogger().info("Report saved to: " + reportPath);
      }

      player.sendMessage(summary);
   }

   public String getDetectionReport(UUID playerId) {
      ReasoningMLModel.DetectionResult result = this.detectionResults.get(playerId);
      return result == null ? null : result.getDetailedReport();
   }

   public double getDetectionScore(UUID playerId) {
      ReasoningMLModel.DetectionResult result = this.detectionResults.get(playerId);
      return result == null ? -1.0 : result.getProbability();
   }

   public List<String> getSimplifiedReport(UUID playerId) {
      ReasoningMLModel.DetectionResult result = this.detectionResults.get(playerId);
      if (result == null) {
         return Collections.singletonList("§cNo analysis data available for this player");
      } else {
         List<String> report = new ArrayList<>();
         report.add("§6-------- X-Ray Detection Report --------");
         report.add(
            "§fSuspicion Score: "
               + String.format("§%c%.1f%%", Character.valueOf((char)(result.getProbability() > 0.7 ? 'c' : 'a')), result.getProbability() * 100.0)
         );
         report.add("§fConclusion: §7" + result.getConclusion());
         report.add("§6-------- Key Analysis Factors --------");
         List<String> steps = result.getReasoningSteps();

         for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            int endPos = step.indexOf(". ");
            if (endPos > 0) {
               report.add("§7" + step.substring(0, endPos + 1));
            } else {
               report.add("§7" + step);
            }
         }

         return report;
      }
   }

   public boolean isEnabled() {
      return this.mlConfig.isEnabled();
   }

   public void setEnabled(boolean enabled) {
      boolean wasEnabled = this.mlConfig.isEnabled();
      this.mlConfig.setEnabled(enabled);
      if (wasEnabled != enabled && !enabled) {
         this.autoAnalysisQueue.clear();
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      if (this.mlConfig.isEnabled()) {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         if ((this.playersUnderAnalysis.contains(playerId) || this.trainingTasks.containsKey(playerId))
            && !event.getFrom().toVector().equals(event.getTo().toVector())) {
            this.dataCollector.processPlayerMove(player);
         }
      }
   }

   @EventHandler
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      if (this.mlConfig.isEnabled()) {
         Player player = event.getPlayer();
         UUID playerId = player.getUniqueId();
         if (this.dataCollector.isCollectingData(playerId)) {
            PlayerProtocolData protocolData = this.dataCollector.getPlayerProtocolData(playerId);
            if (protocolData != null) {
               boolean isDigging = this.plugin.getProtocolHandler() != null && this.plugin.getProtocolHandler().isPlayerDigging(playerId);
               if (isDigging) {
                  protocolData.recordConcurrentAction("COMMAND_WHILE_DIGGING");
               }
            }
         }
      }
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.mlConfig.isEnabled()) {
         this.dataCollector.processBlockBreak(event);
         UUID playerId = event.getPlayer().getUniqueId();
         Map<String, Double> features = this.extractFeaturesForAutoLearning(playerId);
         if (!features.isEmpty()) {
            this.autoTrainer.addUnlabeledSample(playerId, features, 0.5);
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.trainingTasks.containsKey(playerId)) {
         this.trainingTasks.get(playerId).cancel();
         this.trainingTasks.remove(playerId);
         this.trainingEndTimes.remove(playerId);
         PlayerMiningData data = this.dataCollector.stopCollecting(player);
         if (data != null) {
            MLDataManager.saveTrainingData(data);
         }
      }

      if (this.analysisTasks.containsKey(playerId)) {
         this.analysisTasks.get(playerId).cancel();
         this.analysisTasks.remove(playerId);
         this.analysisEndTimes.remove(playerId);
         PlayerMiningData data = this.dataCollector.stopCollecting(player);
         if (data != null) {
            data.calculateDerivedFeatures();
            MLDataManager.savePlayerData(data);
         }

         this.playersUnderAnalysis.remove(playerId);
      }
   }

   public Set<UUID> getPlayersUnderAnalysis() {
      return Collections.unmodifiableSet(this.playersUnderAnalysis);
   }

   public Map<UUID, Boolean> getPlayersInTraining() {
      Map<UUID, Boolean> result = new HashMap<>();

      for (Entry<UUID, Schedulers.Task> entry : this.trainingTasks.entrySet()) {
         UUID playerId = entry.getKey();
         PlayerMiningData data = this.dataCollector.getPlayerData(playerId);
         if (data != null) {
            result.put(playerId, data.isLabeledAsCheater());
         }
      }

      return result;
   }

   public long getRemainingAnalysisTime(UUID playerId) {
      Long endTime = this.analysisEndTimes.get(playerId);
      if (endTime == null) {
         return -1L;
      } else {
         long remainingMs = endTime - System.currentTimeMillis();
         return remainingMs > 0L ? remainingMs / 1000L : 0L;
      }
   }

   public long getRemainingTrainingTime(UUID playerId) {
      Long endTime = this.trainingEndTimes.get(playerId);
      if (endTime == null) {
         return -1L;
      } else {
         long remainingMs = endTime - System.currentTimeMillis();
         return remainingMs > 0L ? remainingMs / 1000L : 0L;
      }
   }

   public void cancelTraining(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.trainingTasks.containsKey(playerId)) {
         this.trainingTasks.get(playerId).cancel();
         this.trainingTasks.remove(playerId);
         this.trainingEndTimes.remove(playerId);
         this.dataCollector.stopCollecting(player);

         for (Player staff : Bukkit.getOnlinePlayers()) {
            if (this.plugin.wantsAlerts(staff)) {
               staff.sendMessage("§c[OverWatch-ML] §fTraining data collection for " + player.getName() + " has been canceled.");
            }
         }

         this.plugin.getLogger().info("Training data collection for " + player.getName() + " has been canceled");
      }
   }

   public void setAutoAnalysisEnabled(boolean enabled) {
      boolean wasEnabled = this.mlConfig.isAutoAnalysisEnabled();
      this.mlConfig.setAutoAnalysisEnabled(enabled);
      if (wasEnabled != enabled && !enabled) {
         this.autoAnalysisQueue.clear();
      }
   }

   public MLConfig getMLConfig() {
      return this.mlConfig;
   }

   public boolean isPlayerInAnalysisQueue(UUID playerId) {
      return this.autoAnalysisQueue.contains(playerId);
   }

   public boolean hasExistingReport(UUID playerId, String playerName) {
      List<String> playerReports = MLDataManager.getPlayerReports(playerName);
      return !playerReports.isEmpty();
   }

   public int getAutoAnalysisQueueSize() {
      return this.autoAnalysisQueue.size();
   }

   public boolean isTrained() {
      return this.model.isTrained();
   }

   public PlayerDataCollector getDataCollector() {
      return this.dataCollector;
   }

   public OnlineLearningManager getOnlineLearningManager() {
      return this.onlineLearningManager;
   }

   public PlayerClusteringEngine getClusteringEngine() {
      return this.clusteringEngine;
   }

   public MLManager.MLStats getComprehensiveStats() {
      return new MLManager.MLStats(
         this.onlineLearningManager.getStats(),
         this.clusteringEngine.getStats(),
         this.model.isTrained(),
         this.playersUnderAnalysis.size(),
         this.autoAnalysisQueue.size(),
         System.currentTimeMillis()
      );
   }

   public Map<String, Object> getAdvancedMLStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("ensemble", this.ensembleDetector.getEnsembleStats());
      stats.put("isolationForest", this.isolationForest.getTrainingStats());
      if (this.autoencoder != null) {
         stats.put("autoencoder", this.autoencoder.getTrainingStats());
      }

      stats.put("featureEngineering", this.featureEngineer.getFeatureEngineeringStats());
      stats.put("autoTrainer", this.autoTrainer.getLearningStats());
      return stats;
   }

   private Map<String, Double> extractFeaturesForAutoLearning(UUID playerId) {
      Map<String, Double> features = new HashMap<>();

      try {
         PlayerMiningData miningData = this.dataCollector.getPlayerData(playerId);
         if (miningData == null) {
            return features;
         }

         Map<String, Double> miningFeatures = miningData.getFeatures();
         if (miningFeatures != null) {
            features.putAll(miningFeatures);
         }

         if (!features.containsKey("total_blocks")) {
            features.put("total_blocks", (double)miningData.getBlockBreaks().size());
         }

         if (!features.containsKey("total_ores")) {
            features.put("total_ores", (double)miningData.getOreBreaks().size());
         }

         if (!features.containsKey("session_time")) {
            features.put("session_time", (double)miningData.getTotalMiningTimeMs());
         }
      } catch (Exception var5) {
         this.plugin.getLogger().warning("Failed to extract features for auto-learning: " + var5.getMessage());
      }

      return features;
   }

   private List<Map<String, Double>> getPlayerHistory(UUID playerId) {
      List<Map<String, Double>> history = new ArrayList<>();
      Map<String, Object> cached = MemoryOptimizer.getCachedSample(playerId.toString());
      if (cached != null) {
         Map<String, Double> features = (Map<String, Double>)cached.get("features");
         if (features != null) {
            history.add(new HashMap<>(features));
         }
      }

      return history;
   }

   private ReasoningMLModel.DetectionResult createEnsembleResult(double score, Map<String, Double> features) {
      boolean isCheater = score >= 0.6;
      String conclusion = isCheater
         ? "Advanced ensemble detection indicates potential cheating behavior"
         : "Advanced ensemble detection shows normal player behavior";
      List<String> reasoningSteps = new ArrayList<>();
      reasoningSteps.add(String.format("Ensemble score: %.3f", score));

      for (Entry<String, Double> entry : features.entrySet()
         .stream()
         .sorted((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())))
         .limit(3L)
         .toList()) {
         reasoningSteps.add(String.format("%s: %.3f", entry.getKey(), entry.getValue()));
      }

      reasoningSteps.add("Analysis completed using advanced ML techniques");
      return new ReasoningMLModel.DetectionResult(score, conclusion, reasoningSteps);
   }

   public Map<String, Object> getAutoTrainerStats() {
      return this.autoTrainer.getLearningStats();
   }

   public void setAutoLearningEnabled(boolean enabled) {
      this.autoTrainer.setAutoGenerateEnabled(enabled);
   }

   public static class MLStats {
      public final OnlineLearningManager.LearningStats learningStats;
      public final PlayerClusteringEngine.ClusteringStats clusteringStats;
      public final boolean modelTrained;
      public final int activeAnalysis;
      public final int queuedAnalysis;
      public final long generatedAt;

      public MLStats(
         OnlineLearningManager.LearningStats learningStats,
         PlayerClusteringEngine.ClusteringStats clusteringStats,
         boolean modelTrained,
         int activeAnalysis,
         int queuedAnalysis,
         long generatedAt
      ) {
         this.learningStats = learningStats;
         this.clusteringStats = clusteringStats;
         this.modelTrained = modelTrained;
         this.activeAnalysis = activeAnalysis;
         this.queuedAnalysis = queuedAnalysis;
         this.generatedAt = generatedAt;
      }
   }
}
