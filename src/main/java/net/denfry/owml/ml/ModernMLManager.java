package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.ml.advanced.AdvancedFeatureEngineer;
import net.denfry.owml.ml.advanced.AutoencoderAnomalyDetector;
import net.denfry.owml.ml.advanced.EnsembleDetector;
import net.denfry.owml.ml.advanced.IsolationForest;
import net.denfry.owml.ml.impl.AsyncAnalysisManager;
import net.denfry.owml.ml.impl.PlayerDataCollectorAdapter;
import net.denfry.owml.ml.impl.ReasoningMLModelAdapter;

import net.denfry.owml.detection.IDetectionEngine;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.DetectionLevel;
import net.denfry.owml.detection.DetectionType;
import java.util.concurrent.CompletableFuture;

/**
 * Modern ML manager with best practices:
 * - Asynchronous processing
 * - Caching
 * - Performance monitoring
 * - Graceful degradation
 * - Modular architecture
 */
public class ModernMLManager implements Listener, IDetectionEngine {

    private final Logger logger;
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final MLConfig mlConfig;

    // Core ML components with interfaces
    private final MLExecutor executor;
    private final MLModel model;
    private final DataCollector dataCollector;
    private final AnalysisManager analysisManager;
    private final MLCache cache;
    private final MLMetrics metrics;

    // Legacy components for backward compatibility
    private final PlayerDataCollector legacyDataCollector;

    // Advanced ML components
    private final OnlineLearningManager onlineLearningManager;
    private final PlayerClusteringEngine clusteringEngine;
    private final AutoTrainer autoTrainer;
    private final BotTrainingManager botTrainingManager;
    private final EnsembleDetector ensembleDetector;
    private final IsolationForest isolationForest;
    private final AutoencoderAnomalyDetector autoencoder;
    private final AdvancedFeatureEngineer featureEngineer;

    // Legacy compatibility fields
    private final Map<UUID, BukkitTask> trainingTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trainingEndTimes = new ConcurrentHashMap<>();
    private final Queue<UUID> autoAnalysisQueue = new LinkedList<>();
    private final Set<UUID> playersUnderAnalysis = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, ReasoningMLModel.DetectionResult> detectionResults = new ConcurrentHashMap<>();

    // Maintenance tasks
    private ScheduledFuture<?> cleanupTask;
    private ScheduledFuture<?> metricsTask;

    public ModernMLManager(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();

        // Initialize modern components
        this.executor = new MLExecutor(plugin);
        this.cache = new MLCache();
        this.metrics = new MLMetrics();

        // Initialize legacy components
        this.mlConfig = new MLConfig(plugin);
        this.legacyDataCollector = new PlayerDataCollector(this.mlConfig);
        ReasoningMLModel legacyModel = new ReasoningMLModel(plugin, this.mlConfig);

        // Wrap legacy components with adapters
        this.model = new ReasoningMLModelAdapter(legacyModel);
        this.dataCollector = new PlayerDataCollectorAdapter(this.legacyDataCollector);
        this.analysisManager = new AsyncAnalysisManager(executor, model, dataCollector, cache, metrics);

        // Configure components based on ML config
        configureComponents(this.mlConfig);

        // Initialize advanced components
        this.onlineLearningManager = new OnlineLearningManager();
        this.clusteringEngine = new PlayerClusteringEngine();
        this.autoTrainer = new AutoTrainer();
        this.botTrainingManager = new BotTrainingManager();
        this.ensembleDetector = new EnsembleDetector();
        this.isolationForest = new IsolationForest(100, 10, 0.6);
        this.autoencoder = null; // Initialized during training
        this.featureEngineer = new AdvancedFeatureEngineer();

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Initialize data management
        MLDataManager.initialize(plugin);

        // Configure components based on ML config
        configureComponents(this.mlConfig);

        // Start maintenance tasks
        startMaintenanceTasks();

        // Initial model training
        initializeModel();

        logger.info("Modern ML Manager initialized with async processing and caching");
    }

    /**
     * Configure ML components based on ML config
     */
    private void configureComponents(MLConfig mlConfig) {
        // Configure cache
        cache.configureCache(
            mlConfig.getAnalysisCacheMaxSize(),
            java.time.Duration.ofMinutes(mlConfig.getAnalysisCacheExpiryMinutes()),
            mlConfig.getFeaturesCacheMaxSize(),
            java.time.Duration.ofMinutes(mlConfig.getFeaturesCacheExpiryMinutes()),
            mlConfig.getPlayerDataCacheMaxSize(),
            java.time.Duration.ofMinutes(mlConfig.getPlayerDataCacheExpiryMinutes())
        );

        // Configure executor if custom pool size specified
        if (mlConfig.getExecutorPoolSize() > 0) {
            // Note: In a real implementation, you might recreate the executor
            logger.info("Custom executor pool size configured: " + mlConfig.getExecutorPoolSize());
        }

        logger.info("ML Components configured: Cache sizes [" +
            mlConfig.getAnalysisCacheMaxSize() + ", " +
            mlConfig.getFeaturesCacheMaxSize() + ", " +
            mlConfig.getPlayerDataCacheMaxSize() + "]");
    }

    /**
     * Initialize model with graceful degradation
     */
    private void initializeModel() {
        executor.submitMLTask(() -> {
            try {
                if (!model.train(Collections.emptyList())) {
                    logger.warning("Failed to train ML model. Use '/owml train' to collect training data.");
                } else {
                    logger.info("ML model trained successfully!");
                }
                return true;
            } catch (Exception e) {
                logger.severe("Error initializing ML model: " + e.getMessage());
                // Graceful degradation - continue working without model
                return false;
            }
        }).thenAccept(success -> {
            if (success) {
                startAutoAnalysis();
            }
        });
    }

    /**
     * Start system maintenance tasks
     */
    private void startMaintenanceTasks() {
        MLConfig mlConfig = new MLConfig(plugin);

        // Clean up stale data
        cleanupTask = executor.scheduleMaintenance(this::performMaintenance,
            mlConfig.getCleanupIntervalMinutes(), mlConfig.getCleanupIntervalMinutes(), TimeUnit.MINUTES);

        // Report metrics
        metricsTask = executor.scheduleMaintenance(() -> {
            logger.info("ML Metrics: " + metrics.getStats());
            logger.info("Cache Stats: " + cache.getStats());
        }, mlConfig.getMetricsReportIntervalMinutes(), mlConfig.getMetricsReportIntervalMinutes(), TimeUnit.MINUTES);
    }

    /**
     * Perform system maintenance
     */
    private void performMaintenance() {
        try {
            // Clean up old data
            dataCollector.cleanupOldData();
            cache.clearAll();

            // Clean up completed analyses
            if (analysisManager instanceof AsyncAnalysisManager) {
                ((AsyncAnalysisManager) analysisManager).cleanupCompletedAnalyses();
            }

            // Clean up stale results
            long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
            detectionResults.entrySet().removeIf(entry -> {
                // Can add logic to check last update time
                return false; // Keep all for now
            });

        } catch (Exception e) {
            logger.warning("Error during maintenance: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<DetectionResult> analyzePlayer(Player player) {
        return analysisManager.analyzeAsync(player.getUniqueId())
            .thenApply(this::convertToDetectionResult);
    }

    private DetectionResult convertToDetectionResult(ReasoningMLModel.DetectionResult mlResult) {
        DetectionLevel level;
        double prob = mlResult.getProbability();
        
        if (prob >= 0.9) level = DetectionLevel.CRITICAL;
        else if (prob >= 0.7) level = DetectionLevel.HIGH;
        else if (prob >= 0.5) level = DetectionLevel.MEDIUM;
        else if (prob >= 0.3) level = DetectionLevel.LOW;
        else level = DetectionLevel.SAFE;
        
        return new DetectionResult(
            level,
            prob,
            mlResult.getReasoningSteps(),
            DetectionType.PATTERN,
            Map.of("conclusion", mlResult.getConclusion())
        );
    }

    @Override
    public void stopAnalysis(UUID playerId) {
        analysisManager.stopAnalysis(playerId);
        playersUnderAnalysis.remove(playerId);
    }

    /**
     * Start automatic analysis
     */
    private void startAutoAnalysis() {
        // Implement automatic player analysis
        // Can add logic for periodic analysis of suspicious players
    }

    /**
     * Asynchronous player analysis
     */
    public void analyzePlayerAsync(UUID playerId, AnalysisCallback callback) {
        analysisManager.analyzeAsync(playerId)
            .thenAccept(result -> {
                executor.runOnMainThread(() -> {
                    try {
                        callback.onAnalysisComplete(playerId, result);
                    } catch (Exception e) {
                        logger.warning("Error in analysis callback: " + e.getMessage());
                    }
                });
            })
            .exceptionally(throwable -> {
                logger.warning("Asynchronous analysis failed for " + playerId + ": " + throwable.getMessage());
                executor.runOnMainThread(() -> {
                    callback.onAnalysisFailed(playerId, throwable);
                });
                return null;
            });
    }

    /**
     * Synchronous analysis (critical cases only)
     */
    public ReasoningMLModel.DetectionResult analyzePlayerSync(UUID playerId) {
        return analysisManager.analyzeSync(playerId);
    }

    /**
     * Start collecting training data
     */
    public void startTraining(Player player, boolean isCheater) {
        if (!isEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Stop existing data collection
        stopTraining(playerId);

        // Start new collection
        dataCollector.startCollecting(player, isCheater);

        long endTime = System.currentTimeMillis() + (getTrainingSessionDuration() * 1000L);
        trainingEndTimes.put(playerId, endTime);

        // Schedule collection completion
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            completeTraining(player, isCheater);
        }, getTrainingSessionDuration() * 20L);

        trainingTasks.put(playerId, task);

        notifyStaff("Started collecting training data for " + player.getName() +
                   " as " + (isCheater ? "cheater" : "normal player"));
    }

    /**
     * Complete training data collection
     */
    private void completeTraining(Player player, boolean isCheater) {
        PlayerMiningData data = dataCollector.stopCollecting(player);
        trainingTasks.remove(player.getUniqueId());
        trainingEndTimes.remove(player.getUniqueId());

        if (data != null) {
            // Async saving and retraining
            executor.submitSequentialTask(() -> {
                try {
                    MLDataManager.saveTrainingData(data);
                    boolean retrained = model.train(Collections.singleton(data));

                    executor.runOnMainThread(() -> {
                        String status = retrained ? "successfully" : "failed";
                        notifyStaff("Training completed for " + player.getName() +
                                   ". Model retrained " + status);
                    });
                } catch (Exception e) {
                    logger.severe("Error saving training data: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Stop collecting data
     */
    public void stopTraining(UUID playerId) {
        BukkitTask task = trainingTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        trainingEndTimes.remove(playerId);
        dataCollector.stopCollecting(Bukkit.getPlayer(playerId));
    }

    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return "ML Performance:\n" + metrics.getStats() + "\n" + cache.getStats();
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        logger.info("Shutting down Modern ML Manager...");

        // Cancel all tasks
        trainingTasks.values().forEach(BukkitTask::cancel);
        trainingTasks.clear();

        // Cancel maintenance tasks
        if (cleanupTask != null) cleanupTask.cancel(true);
        if (metricsTask != null) metricsTask.cancel(true);

        // Shutdown executor
        executor.shutdown();

        // Clean up resources
        model.dispose();
        cache.clearAll();

        logger.info("Modern ML Manager shut down");
    }

    // Legacy compatibility methods
    public boolean isEnabled() { return mlConfig.isEnabled(); }
    public boolean isTrained() { return model.isTrained(); }
    public long getTrainingSessionDuration() { return 60; } // 60 seconds default
    public Set<UUID> getPlayersUnderAnalysis() { return playersUnderAnalysis; }
    public Map<UUID, ReasoningMLModel.DetectionResult> getDetectionResults() { return detectionResults; }
    public Map<UUID, Boolean> getPlayersInTraining() {
        // Return empty map for compatibility - can be extended later
        return new ConcurrentHashMap<>();
    }
    public double getDetectionScore(UUID playerId) {
        return analysisManager.getSuspicionScore(playerId);
    }
    public boolean isUnderAnalysis(UUID playerId) {
        return playersUnderAnalysis.contains(playerId);
    }
    public long getRemainingAnalysisTime(UUID playerId) {
        return analysisManager.getRemainingAnalysisTime(playerId);
    }
    public long getRemainingTrainingTime(UUID playerId) {
        Long endTime = trainingEndTimes.get(playerId);
        if (endTime == null) return -1;
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    public void cancelTraining(Player player) {
        stopTraining(player.getUniqueId());
    }
    public void setEnabled(boolean enabled) {
        mlConfig.setEnabled(enabled);
    }
    public MLConfig getMLConfig() {
        return mlConfig;
    }
    public PlayerDataCollector getDataCollector() {
        return legacyDataCollector;
    }

    public PlayerClusteringEngine getClusteringEngine() {
        return clusteringEngine;
    }

    public MLStats getComprehensiveStats() {
        return new MLStats(
            onlineLearningManager.getStats(),
            clusteringEngine.getStats(),
            isTrained(),
            playersUnderAnalysis.size(),
            autoAnalysisQueue.size(),
            System.currentTimeMillis()
        );
    }

    // Additional methods for command compatibility
    public void startAnalysis(Player player) {
        analysisManager.startAnalysis(player.getUniqueId(), getTrainingSessionDuration());
    }

    public List<String> getSimplifiedReport(UUID playerId) {
        List<String> report = new ArrayList<>();
        report.add("=== ML Analysis Report ===");
        report.add("Player: " + Bukkit.getOfflinePlayer(playerId).getName());
        report.add("Status: " + (isUnderAnalysis(playerId) ? "Under Analysis" : "Not Analyzing"));
        report.add("Suspicion Score: " + String.format("%.2f", getDetectionScore(playerId) * 100) + "%");
        report.add("Remaining Time: " + getRemainingAnalysisTime(playerId) + "s");
        report.add("Cache Hit Rate: " + String.format("%.1f%%", cache.getStats().analysisStats.hitRate() * 100));
        report.add("Total Predictions: " + metrics.getStats().totalPredictions);
        return report;
    }

    /**
     * Notify staff
     */
    private void notifyStaff(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("owml.staff")) {
                player.sendMessage("В§a[OverWatch-ML] В§f" + message);
            }
        }
    }

    // Event handlers
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (playersUnderAnalysis.contains(player.getUniqueId())) {
            // Add mining data
            dataCollector.addBlockBreak(player,
                event.getBlock().getType().name(),
                event.getBlock().getLocation().toString());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (playersUnderAnalysis.contains(player.getUniqueId())) {
            // Add movement data
            dataCollector.addMovement(player,
                event.getFrom().getX(), event.getFrom().getY(), event.getFrom().getZ(),
                event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Stop all active processes for player
        stopTraining(playerId);
        analysisManager.stopAnalysis(playerId);

        // Clear cache
        cache.invalidatePlayer(playerId);
    }

    /**
     * Enable or disable auto-analysis
     */
    public void setAutoAnalysisEnabled(boolean enabled) {
        mlConfig.setAutoAnalysisEnabled(enabled);
    }

    /**
     * Get the number of players in the auto-analysis queue
     */
    public int getAutoAnalysisQueueSize() {
        return autoAnalysisQueue.size();
    }

    public boolean hasExistingReport(UUID playerId, String playerName) {
        List<String> playerReports = MLDataManager.getPlayerReports(playerName);
        return !playerReports.isEmpty();
    }

    public boolean isPlayerInAnalysisQueue(UUID playerId) {
        return autoAnalysisQueue.contains(playerId);
    }

    public void queuePlayerForAnalysis(UUID playerId) {
        if (!autoAnalysisQueue.contains(playerId) && !playersUnderAnalysis.contains(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                autoAnalysisQueue.add(playerId);

                int suspiciousCount = net.denfry.owml.managers.SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
                plugin.getLogger().info("Added " + player.getName() + " to analysis queue with suspicious count: " + suspiciousCount);

                // Basic queue processing - could be enhanced with sorting and limits
                processAutoAnalysisQueue();
            }
        }
    }

    private void processAutoAnalysisQueue() {
        // Simplified version - just start analysis for the next player in queue
        if (!autoAnalysisQueue.isEmpty()) {
            UUID playerId = autoAnalysisQueue.poll();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                startAnalysis(player);
            }
        }
    }

    /**
     * Get bot training manager
     */
    public BotTrainingManager getBotTrainingManager() {
        return botTrainingManager;
    }

    /**
     * Manually retrain the ML model with available training data
     */
    public void retrainModel() {
        if (!isEnabled()) {
            return;
        }

        executor.submitSequentialTask(() -> {
            try {
                // Load all available training data
                MLDataManager.MLTrainingData trainingData = MLDataManager.loadTrainingData();
                if (trainingData.hasEnoughData()) {
                    // Convert feature maps to training samples for the model
                    List<Map<String, Double>> allFeatures = new ArrayList<>();
                    allFeatures.addAll(trainingData.getNormalFeatures());
                    allFeatures.addAll(trainingData.getCheaterFeatures());

                    // For now, just notify that retraining would happen
                    // The actual model retraining happens through the normal training pipeline
                    int totalSamples = allFeatures.size();

                    executor.runOnMainThread(() -> {
                        notifyStaff("Training data updated. Available " + totalSamples +
                                  " samples (" + trainingData.getNormalFeatures().size() + " normal, " +
                                  trainingData.getCheaterFeatures().size() + " cheater)");
                        notifyStaff("Next model training will occur automatically in the next training cycle");
                    });
                } else {
                    executor.runOnMainThread(() -> {
                        notifyStaff("Insufficient training data for model retraining");
                    });
                }
            } catch (Exception e) {
                logger.severe("Error during model data refresh: " + e.getMessage());
                executor.runOnMainThread(() -> {
                    notifyStaff("Error updating training data: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Comprehensive ML statistics
     */
    public static class MLStats {
        public final OnlineLearningManager.LearningStats learningStats;
        public final PlayerClusteringEngine.ClusteringStats clusteringStats;
        public final boolean modelTrained;
        public final int activeAnalysis;
        public final int queuedAnalysis;
        public final long generatedAt;

        public MLStats(OnlineLearningManager.LearningStats learningStats,
                      PlayerClusteringEngine.ClusteringStats clusteringStats,
                      boolean modelTrained, int activeAnalysis, int queuedAnalysis, long generatedAt) {
            this.learningStats = learningStats;
            this.clusteringStats = clusteringStats;
            this.modelTrained = modelTrained;
            this.activeAnalysis = activeAnalysis;
            this.queuedAnalysis = queuedAnalysis;
            this.generatedAt = generatedAt;
        }
    }

    /**
     * Analysis callback interface
     */
    public interface AnalysisCallback {
        void onAnalysisComplete(UUID playerId, ReasoningMLModel.DetectionResult result);
        void onAnalysisFailed(UUID playerId, Throwable error);
    }
}
