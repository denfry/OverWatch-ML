package net.denfry.owml.ml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.detection.DetectionLevel;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.DetectionType;
import net.denfry.owml.detection.IDetectionEngine;
import net.denfry.owml.managers.SuspiciousManager;
import net.denfry.owml.ml.advanced.AdvancedFeatureEngineer;
import net.denfry.owml.ml.advanced.AutoencoderAnomalyDetector;
import net.denfry.owml.ml.advanced.EnsembleDetector;
import net.denfry.owml.ml.advanced.IsolationForest;
import net.denfry.owml.ml.impl.AsyncAnalysisManager;
import net.denfry.owml.ml.impl.PlayerDataCollectorAdapter;
import net.denfry.owml.ml.impl.ReasoningMLModelAdapter;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ModernMLManager implements Listener, IDetectionEngine {
   private final Logger logger;
   private final OverWatchML plugin;
   private final ConfigManager configManager;
   private final MLConfig mlConfig;
   private final MLExecutor executor;
   private final MLModel model;
   private final DataCollector dataCollector;
   private final AnalysisManager analysisManager;
   private final MLCache cache;
   private final MLMetrics metrics;
   private final PlayerDataCollector legacyDataCollector;
   private final OnlineLearningManager onlineLearningManager;
   private final PlayerClusteringEngine clusteringEngine;
   private final AutoTrainer autoTrainer;
   private final BotTrainingManager botTrainingManager;
   private final EnsembleDetector ensembleDetector;
   private final IsolationForest isolationForest;
   private final AutoencoderAnomalyDetector autoencoder;
   private final AdvancedFeatureEngineer featureEngineer;
   private final Map<UUID, Schedulers.Task> trainingTasks = new ConcurrentHashMap<>();
   private final Map<UUID, Long> trainingEndTimes = new ConcurrentHashMap<>();
   private final Queue<UUID> autoAnalysisQueue = new ConcurrentLinkedQueue<>();
   private final Set<UUID> playersUnderAnalysis = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private final Map<UUID, ReasoningMLModel.DetectionResult> detectionResults = new ConcurrentHashMap<>();
   private ScheduledFuture<?> cleanupTask;
   private ScheduledFuture<?> metricsTask;

   public ModernMLManager(OverWatchML plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.logger = plugin.getLogger();
      this.executor = new MLExecutor(plugin);
      this.cache = new MLCache();
      this.metrics = new MLMetrics();
      this.mlConfig = new MLConfig(plugin);
      this.legacyDataCollector = new PlayerDataCollector(this.mlConfig);
      ReasoningMLModel legacyModel = new ReasoningMLModel(plugin, this.mlConfig);
      this.model = new ReasoningMLModelAdapter(legacyModel);
      this.dataCollector = new PlayerDataCollectorAdapter(this.legacyDataCollector);
      this.analysisManager = new AsyncAnalysisManager(this.executor, this.model, this.dataCollector, this.cache, this.metrics);
      this.configureComponents(this.mlConfig);
      this.onlineLearningManager = new OnlineLearningManager();
      this.clusteringEngine = new PlayerClusteringEngine();
      this.autoTrainer = new AutoTrainer();
      this.botTrainingManager = new BotTrainingManager();
      this.ensembleDetector = new EnsembleDetector();
      this.isolationForest = new IsolationForest(100, 10, 0.6);
      this.autoencoder = null;
      this.featureEngineer = new AdvancedFeatureEngineer();
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      MLDataManager.initialize(plugin);
      this.configureComponents(this.mlConfig);
      this.startMaintenanceTasks();
      this.initializeModel();
      this.logger.info("Modern ML Manager initialized with async processing and caching");
   }

   private void configureComponents(MLConfig mlConfig) {
      this.cache
         .configureCache(
            mlConfig.getAnalysisCacheMaxSize(),
            Duration.ofMinutes(mlConfig.getAnalysisCacheExpiryMinutes()),
            mlConfig.getFeaturesCacheMaxSize(),
            Duration.ofMinutes(mlConfig.getFeaturesCacheExpiryMinutes()),
            mlConfig.getPlayerDataCacheMaxSize(),
            Duration.ofMinutes(mlConfig.getPlayerDataCacheExpiryMinutes())
         );
      if (mlConfig.getExecutorPoolSize() > 0) {
         this.logger.info("Custom executor pool size configured: " + mlConfig.getExecutorPoolSize());
      }

      this.logger
         .info(
            "ML Components configured: Cache sizes ["
               + mlConfig.getAnalysisCacheMaxSize()
               + ", "
               + mlConfig.getFeaturesCacheMaxSize()
               + ", "
               + mlConfig.getPlayerDataCacheMaxSize()
               + "]"
         );
   }

   private void initializeModel() {
      this.executor.<Boolean>submitMLTask(() -> {
         try {
            if (!this.model.train(Collections.emptyList())) {
               this.logger.warning("Failed to train ML model. Use '/owml train' to collect training data.");
            } else {
               this.logger.info("ML model trained successfully!");
            }

            return true;
         } catch (Exception var2) {
            this.logger.severe("Error initializing ML model: " + var2.getMessage());
            return false;
         }
      }).thenAccept(success -> {
         if (success) {
            this.startAutoAnalysis();
         }
      });
   }

   private void startMaintenanceTasks() {
      MLConfig mlConfig = new MLConfig(this.plugin);
      this.cleanupTask = this.executor
         .scheduleMaintenance(this::performMaintenance, mlConfig.getCleanupIntervalMinutes(), mlConfig.getCleanupIntervalMinutes(), TimeUnit.MINUTES);
      this.metricsTask = this.executor.scheduleMaintenance(() -> {
         this.logger.info("ML Metrics: " + this.metrics.getStats());
         this.logger.info("Cache Stats: " + this.cache.getStats());
      }, mlConfig.getMetricsReportIntervalMinutes(), mlConfig.getMetricsReportIntervalMinutes(), TimeUnit.MINUTES);
   }

   private void performMaintenance() {
      try {
         this.dataCollector.cleanupOldData();
         this.cache.clearAll();
         if (this.analysisManager instanceof AsyncAnalysisManager) {
            ((AsyncAnalysisManager)this.analysisManager).cleanupCompletedAnalyses();
         }

         long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1L);
         this.detectionResults.entrySet().removeIf(entry -> false);
      } catch (Exception var3) {
         this.logger.warning("Error during maintenance: " + var3.getMessage());
      }
   }

   @Override
   public CompletableFuture<DetectionResult> analyzePlayer(Player player) {
      return this.analysisManager.analyzeAsync(player.getUniqueId()).thenApply(this::convertToDetectionResult);
   }

   private DetectionResult convertToDetectionResult(ReasoningMLModel.DetectionResult mlResult) {
      double prob = mlResult.getProbability();
      DetectionLevel level;
      if (prob >= 0.9) {
         level = DetectionLevel.CRITICAL;
      } else if (prob >= 0.7) {
         level = DetectionLevel.HIGH;
      } else if (prob >= 0.5) {
         level = DetectionLevel.MEDIUM;
      } else if (prob >= 0.3) {
         level = DetectionLevel.LOW;
      } else {
         level = DetectionLevel.SAFE;
      }

      return new DetectionResult(level, prob, mlResult.getReasoningSteps(), DetectionType.PATTERN, Map.of("conclusion", mlResult.getConclusion()));
   }

   @Override
   public void stopAnalysis(UUID playerId) {
      this.analysisManager.stopAnalysis(playerId);
      this.playersUnderAnalysis.remove(playerId);
   }

   private void startAutoAnalysis() {
   }

   public void analyzePlayerAsync(UUID playerId, ModernMLManager.AnalysisCallback callback) {
      this.analysisManager.analyzeAsync(playerId).thenAccept(result -> this.executor.runOnMainThread(() -> {
         try {
            callback.onAnalysisComplete(playerId, result);
         } catch (Exception var5) {
            this.logger.warning("Error in analysis callback: " + var5.getMessage());
         }
      })).exceptionally(throwable -> {
         this.logger.warning("Asynchronous analysis failed for " + playerId + ": " + throwable.getMessage());
         this.executor.runOnMainThread(() -> callback.onAnalysisFailed(playerId, throwable));
         return null;
      });
   }

   public ReasoningMLModel.DetectionResult analyzePlayerSync(UUID playerId) {
      return this.analysisManager.analyzeSync(playerId);
   }

   public void startTraining(Player player, boolean isCheater) {
      if (this.isEnabled()) {
         UUID playerId = player.getUniqueId();
         this.stopTraining(playerId);
         this.dataCollector.startCollecting(player, isCheater);
         long endTime = System.currentTimeMillis() + this.getTrainingSessionDuration() * 1000L;
         this.trainingEndTimes.put(playerId, endTime);
         Schedulers.Task task = Schedulers.globalLater(() -> this.completeTraining(player, isCheater), this.getTrainingSessionDuration() * 20L);
         this.trainingTasks.put(playerId, task);
         this.notifyStaff("Started collecting training data for " + player.getName() + " as " + (isCheater ? "cheater" : "normal player"));
      }
   }

   private void completeTraining(Player player, boolean isCheater) {
      PlayerMiningData data = this.dataCollector.stopCollecting(player);
      this.trainingTasks.remove(player.getUniqueId());
      this.trainingEndTimes.remove(player.getUniqueId());
      if (data != null) {
         this.executor.submitSequentialTask(() -> {
            try {
               MLDataManager.saveTrainingData(data);
               boolean retrained = this.model.train(Collections.singleton(data));
               this.executor.runOnMainThread(() -> {
                  String status = retrained ? "successfully" : "failed";
                  this.notifyStaff("Training completed for " + player.getName() + ". Model retrained " + status);
               });
            } catch (Exception var4) {
               this.logger.severe("Error saving training data: " + var4.getMessage());
            }
         });
      }
   }

   public void stopTraining(UUID playerId) {
      Schedulers.Task task = this.trainingTasks.remove(playerId);
      if (task != null) {
         task.cancel();
      }

      this.trainingEndTimes.remove(playerId);
      this.dataCollector.stopCollecting(Bukkit.getPlayer(playerId));
   }

   public String getPerformanceStats() {
      return "ML Performance:\n" + this.metrics.getStats() + "\n" + this.cache.getStats();
   }

   public void shutdown() {
      this.logger.info("Shutting down Modern ML Manager...");
      this.trainingTasks.values().forEach(Schedulers.Task::cancel);
      this.trainingTasks.clear();
      if (this.cleanupTask != null) {
         this.cleanupTask.cancel(true);
      }

      if (this.metricsTask != null) {
         this.metricsTask.cancel(true);
      }

      this.executor.shutdown();
      this.model.dispose();
      this.cache.clearAll();
      this.logger.info("Modern ML Manager shut down");
   }

   public boolean isEnabled() {
      return this.mlConfig.isEnabled();
   }

   public boolean isTrained() {
      return this.model.isTrained();
   }

   public long getTrainingSessionDuration() {
      return 60L;
   }

   public Set<UUID> getPlayersUnderAnalysis() {
      return this.playersUnderAnalysis;
   }

   public Map<UUID, ReasoningMLModel.DetectionResult> getDetectionResults() {
      return this.detectionResults;
   }

   public Map<UUID, Boolean> getPlayersInTraining() {
      return new ConcurrentHashMap<>();
   }

   public double getDetectionScore(UUID playerId) {
      return this.analysisManager.getSuspicionScore(playerId);
   }

   @Override
   public boolean isUnderAnalysis(UUID playerId) {
      return this.playersUnderAnalysis.contains(playerId);
   }

   public long getRemainingAnalysisTime(UUID playerId) {
      return this.analysisManager.getRemainingAnalysisTime(playerId);
   }

   public long getRemainingTrainingTime(UUID playerId) {
      Long endTime = this.trainingEndTimes.get(playerId);
      if (endTime == null) {
         return -1L;
      } else {
         long remaining = endTime - System.currentTimeMillis();
         return Math.max(0L, remaining / 1000L);
      }
   }

   public void cancelTraining(Player player) {
      this.stopTraining(player.getUniqueId());
   }

   public void setEnabled(boolean enabled) {
      this.mlConfig.setEnabled(enabled);
   }

   public MLConfig getMLConfig() {
      return this.mlConfig;
   }

   public PlayerDataCollector getDataCollector() {
      return this.legacyDataCollector;
   }

   public PlayerClusteringEngine getClusteringEngine() {
      return this.clusteringEngine;
   }

   public ModernMLManager.MLStats getComprehensiveStats() {
      return new ModernMLManager.MLStats(
         this.onlineLearningManager.getStats(),
         this.clusteringEngine.getStats(),
         this.isTrained(),
         this.playersUnderAnalysis.size(),
         this.autoAnalysisQueue.size(),
         System.currentTimeMillis()
      );
   }

   @Override
   public void startAnalysis(Player player) {
      this.analysisManager.startAnalysis(player.getUniqueId(), this.getTrainingSessionDuration());
   }

   public List<String> getSimplifiedReport(UUID playerId) {
      List<String> report = new ArrayList<>();
      report.add("=== ML Analysis Report ===");
      report.add("Player: " + Bukkit.getOfflinePlayer(playerId).getName());
      report.add("Status: " + (this.isUnderAnalysis(playerId) ? "Under Analysis" : "Not Analyzing"));
      report.add("Suspicion Score: " + String.format("%.2f", this.getDetectionScore(playerId) * 100.0) + "%");
      report.add("Remaining Time: " + this.getRemainingAnalysisTime(playerId) + "s");
      report.add("Cache Hit Rate: " + String.format("%.1f%%", this.cache.getStats().analysisStats.hitRate() * 100.0));
      report.add("Total Predictions: " + this.metrics.getStats().totalPredictions);
      return report;
   }

   private void notifyStaff(String message) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission("owml.staff")) {
            player.sendMessage("§a[OverWatch-ML] §f" + message);
         }
      }
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.isEnabled()) {
         Player player = event.getPlayer();
         if (this.playersUnderAnalysis.contains(player.getUniqueId())) {
            this.dataCollector.addBlockBreak(player, event.getBlock().getType().name(), event.getBlock().getLocation().toString());
         }
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      if (this.isEnabled()) {
         Player player = event.getPlayer();
         if (this.playersUnderAnalysis.contains(player.getUniqueId())) {
            this.dataCollector
               .addMovement(
                  player,
                  event.getFrom().getX(),
                  event.getFrom().getY(),
                  event.getFrom().getZ(),
                  event.getTo().getX(),
                  event.getTo().getY(),
                  event.getTo().getZ()
               );
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();
      this.stopTraining(playerId);
      this.analysisManager.stopAnalysis(playerId);
      this.cache.invalidatePlayer(playerId);
   }

   public void setAutoAnalysisEnabled(boolean enabled) {
      this.mlConfig.setAutoAnalysisEnabled(enabled);
   }

   public int getAutoAnalysisQueueSize() {
      return this.autoAnalysisQueue.size();
   }

   public boolean hasExistingReport(UUID playerId, String playerName) {
      List<String> playerReports = MLDataManager.getPlayerReports(playerName);
      return !playerReports.isEmpty();
   }

   public boolean isPlayerInAnalysisQueue(UUID playerId) {
      return this.autoAnalysisQueue.contains(playerId);
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

   private void processAutoAnalysisQueue() {
      if (!this.autoAnalysisQueue.isEmpty()) {
         UUID playerId = this.autoAnalysisQueue.poll();
         Player player = Bukkit.getPlayer(playerId);
         if (player != null && player.isOnline()) {
            this.startAnalysis(player);
         }
      }
   }

   public BotTrainingManager getBotTrainingManager() {
      return this.botTrainingManager;
   }

   public void retrainModel() {
      if (this.isEnabled()) {
         this.executor.submitSequentialTask(() -> {
            try {
               MLDataManager.MLTrainingData trainingData = MLDataManager.loadTrainingData();
               new ArrayList();
               boolean retrained = this.model.train(Collections.emptyList());
               int totalSamples = trainingData.getNormalFeatures().size() + trainingData.getCheaterFeatures().size();
               this.executor.runOnMainThread(() -> {
                  if (retrained) {
                     this.notifyStaff("ML model retrained successfully with " + totalSamples + " samples!");
                  } else {
                     this.notifyStaff("ML model retraining failed or insufficient data (" + totalSamples + " samples)");
                  }
               });
            } catch (Exception var5) {
               this.logger.severe("Error during model retraining: " + var5.getMessage());
               this.executor.runOnMainThread(() -> this.notifyStaff("§cError retraining ML model: " + var5.getMessage()));
            }
         });
      }
   }

   public interface AnalysisCallback {
      void onAnalysisComplete(UUID var1, ReasoningMLModel.DetectionResult var2);

      void onAnalysisFailed(UUID var1, Throwable var2);
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
