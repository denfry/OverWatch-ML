package net.denfry.owml.ml;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.SuspiciousManager;
import net.denfry.owml.managers.WebhookManager;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.ml.advanced.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the ML components, training, and analysis with the reasoning-based model
 */
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

    // Advanced ML components
    private final EnsembleDetector ensembleDetector;
    private final IsolationForest isolationForest;
    private final AutoencoderAnomalyDetector autoencoder;
    private final AdvancedFeatureEngineer featureEngineer;


    private final Map<UUID, BukkitTask> trainingTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> analysisTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> analysisEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trainingEndTimes = new ConcurrentHashMap<>();


    private final Queue<UUID> autoAnalysisQueue = new LinkedList<>();


    private final Set<UUID> playersUnderAnalysis = Collections.newSetFromMap(new ConcurrentHashMap<>());


    private final Map<UUID, ReasoningMLModel.DetectionResult> detectionResults = new ConcurrentHashMap<>();

    public MLManager(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mlConfig = new MLConfig(plugin);
        this.dataCollector = new PlayerDataCollector(mlConfig);
        this.model = new ReasoningMLModel(plugin, mlConfig);
        this.webhookManager = WebhookManager.getInstance();
        this.onlineLearningManager = new OnlineLearningManager();
        this.clusteringEngine = new PlayerClusteringEngine();
        this.autoTrainer = new AutoTrainer();

        // Initialize advanced ML components
        this.ensembleDetector = new net.denfry.owml.ml.advanced.EnsembleDetector();
        this.isolationForest = new net.denfry.owml.ml.advanced.IsolationForest(100, 10, 0.6);
        this.autoencoder = null; // Will be initialized during training
        this.featureEngineer = new net.denfry.owml.ml.advanced.AdvancedFeatureEngineer();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);


        MLDataManager.initialize(plugin);

        initializeAutoAnalysis();


        if (!model.train()) {
            plugin.getLogger().warning("Not enough training data. Use '/owml train <player> <cheater|normal>' to collect data.");
        } else {
            plugin.getLogger().info("ML model trained successfully!");
        }
    }

    /**
     * Start collecting training data for a player
     *
     * @param player    The player to collect data from
     * @param isCheater Whether this player is labeled as a cheater
     */
    public void startTraining(Player player, boolean isCheater) {
        if (!mlConfig.isEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();


        if (trainingTasks.containsKey(playerId)) {
            trainingTasks.get(playerId).cancel();
            trainingTasks.remove(playerId);
            trainingEndTimes.remove(playerId);
            dataCollector.stopCollecting(player);
        }


        dataCollector.startCollecting(player, isCheater);


        long endTime = System.currentTimeMillis() + (mlConfig.getTrainingSessionDuration() * 1000L);
        trainingEndTimes.put(playerId, endTime);


        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PlayerMiningData data = dataCollector.stopCollecting(player);
            trainingTasks.remove(playerId);
            trainingEndTimes.remove(playerId);

            if (data != null) {

                MLDataManager.saveTrainingData(data);


                String label = isCheater ? "cheater" : "normal player";
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("owml.staff")) {
                        staff.sendMessage("В§a[OverWatch-ML] В§fTraining data collection complete for " + player.getName() + " as " + label + ".");


                        if (model.train()) {
                            staff.sendMessage("В§a[OverWatch-ML] В§fML model retrained successfully!");
                        }
                    }
                }


                plugin.getLogger().info("Training data collection complete for " + player.getName() + " as " + (isCheater ? "cheater" : "normal player"));
            }
        }, mlConfig.getTrainingSessionDuration() * 20L);

        trainingTasks.put(playerId, task);


        String label = isCheater ? "cheater" : "normal player";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("owml.staff")) {
                staff.sendMessage("В§a[OverWatch-ML] В§fStarted collecting training data from " + player.getName() + " as " + label + " for " + mlConfig.getTrainingSessionDuration() + " seconds.");
            }
        }


        plugin.getLogger().info("Started collecting training data from " + player.getName() + " as " + (isCheater ? "cheater" : "normal player") + " for " + mlConfig.getTrainingSessionDuration() + " seconds.");
    }

    /**
     * Start analyzing a player for potential X-ray hacking
     *
     * @param player The player to analyze
     */
    public void startAnalysis(Player player) {
        if (!mlConfig.isEnabled()) {
            return;
        }

        if (!model.isTrained()) {

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("owml.staff")) {
                    staff.sendMessage("В§c[OverWatch-ML] В§fML model is not trained yet. Use '/owml ml train' first.");
                }
            }
            return;
        }

        UUID playerId = player.getUniqueId();


        if (analysisTasks.containsKey(playerId)) {
            analysisTasks.get(playerId).cancel();
            analysisTasks.remove(playerId);
            analysisEndTimes.remove(playerId);
            dataCollector.stopCollecting(player);
        }


        dataCollector.startCollecting(player, false);
        playersUnderAnalysis.add(playerId);


        long endTime = System.currentTimeMillis() + (mlConfig.getTrainingSessionDuration() * 1000L);
        analysisEndTimes.put(playerId, endTime);


        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PlayerMiningData data = dataCollector.stopCollecting(player);
            analysisTasks.remove(playerId);
            analysisEndTimes.remove(playerId);
            playersUnderAnalysis.remove(playerId);

            if (data != null) {

                analyzePlayerData(player, data);
            }
        }, mlConfig.getTrainingSessionDuration() * 20L);

        analysisTasks.put(playerId, task);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("owml.staff") && !staff.getUniqueId().equals(playerId)) {
                staff.sendMessage("В§a[OverWatch-ML] В§fStarted analyzing " + player.getName() + "'s mining behavior for " + mlConfig.getTrainingSessionDuration() + " seconds.");
            }
        }


        plugin.getLogger().info("Started analyzing " + player.getName() + " for potential X-ray usage");
    }

    /**
     * Initialize the auto-analysis system
     */
    private void initializeAutoAnalysis() {


        if (!mlConfig.isAutoAnalysisEnabled()) {
            autoAnalysisQueue.clear();
        }
    }


    /**
     * Process the auto-analysis queue
     */
    private void processAutoAnalysisQueue() {
        if (!mlConfig.isAutoAnalysisEnabled() || !model.isTrained()) {
            return;
        }


        int maxPlayers = mlConfig.getMaxAutoAnalysisPlayers();
        int currentlyAnalyzing = playersUnderAnalysis.size();
        int availableSlots = Math.max(0, maxPlayers - currentlyAnalyzing);


        if (!autoAnalysisQueue.isEmpty()) {
            List<UUID> sortedQueue = new ArrayList<>(autoAnalysisQueue);
            Map<UUID, Integer> suspiciousCounts = SuspiciousManager.getSuspiciousCounts();


            sortedQueue.sort((id1, id2) -> Integer.compare(suspiciousCounts.getOrDefault(id2, 0), suspiciousCounts.getOrDefault(id1, 0)));


            autoAnalysisQueue.clear();
            autoAnalysisQueue.addAll(sortedQueue);
        }


        int processed = 0;
        while (!autoAnalysisQueue.isEmpty() && processed < availableSlots) {
            UUID playerId = autoAnalysisQueue.peek();
            Player player = Bukkit.getPlayer(playerId);

            if (player != null && player.isOnline()) {

                if (!hasExistingReport(playerId, player.getName())) {

                    autoAnalysisQueue.poll();

                    startAnalysis(player);
                    processed++;


                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("owml.staff")) {
                            int suspiciousCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
                            staff.sendMessage("В§7[OverWatch-ML] В§fAuto-analyzing player В§e" + player.getName() + " В§fdue to high suspicious count (В§e" + suspiciousCount + "В§f)");
                        }
                    }
                } else {

                    autoAnalysisQueue.poll();
                    plugin.getLogger().info("Skipping auto-analysis for " + player.getName() + " as they already have a report");
                }
            } else {

                autoAnalysisQueue.poll();
            }
        }
    }

    /**
     * Queue a player for analysis and process the queue
     *
     * @param playerId The player UUID to add to the queue
     */
    public void queuePlayerForAnalysis(UUID playerId) {

        if (!autoAnalysisQueue.contains(playerId) && !playersUnderAnalysis.contains(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {

                autoAnalysisQueue.add(playerId);


                int suspiciousCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
                plugin.getLogger().info("Added " + player.getName() + " to analysis queue with suspicious count: " + suspiciousCount);


                processAutoAnalysisQueue();
            }
        }
    }


    /**
     * Analyze player data and determine if they might be using X-ray
     *
     * @param player The player being analyzed
     * @param data   The collected mining data
     */
    private void analyzePlayerData(Player player, PlayerMiningData data) {

        data.calculateDerivedFeatures();

        // Get enhanced features using advanced feature engineering
        Map<String, Double> rawFeatures = data.getFeatures();
        List<Map<String, Double>> playerHistory = getPlayerHistory(player.getUniqueId());
        Map<String, Double> enhancedFeatures = featureEngineer.engineerFeatures(rawFeatures, playerHistory);

        // Use ensemble detection for improved accuracy
        double ensembleScore = ensembleDetector.predict(enhancedFeatures);
        double isolationScore = isolationForest.score(enhancedFeatures);

        // Combine scores (weighted average)
        double combinedScore = (ensembleScore * 0.7) + (isolationScore * 0.3);

        // Fallback to traditional model if advanced models aren't trained
        ReasoningMLModel.DetectionResult result;
        if (ensembleDetector.isTrained()) {
            // Create synthetic detection result from ensemble
            result = createEnsembleResult(combinedScore, enhancedFeatures);
        } else {
            // Use traditional model
            result = model.predict(data.getFeatures());
        }

        detectionResults.put(player.getUniqueId(), result);

        MLDataManager.savePlayerData(data);

        String reportPath = MLDataManager.saveDetectionReport(player.getName(), result, data);


        double cheatingProbability = result.getProbability();


        double cheatingPercentage = cheatingProbability * 100;


        StringBuilder keyFeaturesBuilder = new StringBuilder();
        List<String> steps = result.getReasoningSteps();
        for (int i = 0; i < Math.min(3, steps.size()); i++) {
            String step = steps.get(i);

            int endPos = step.indexOf(". ");
            if (endPos > 0) {
                keyFeaturesBuilder.append("вЂў ").append(step, 0, endPos + 1).append("\n");
            } else {
                keyFeaturesBuilder.append("вЂў ").append(step).append("\n");
            }
        }


        keyFeaturesBuilder.append("\nConclusion: ").append(result.getConclusion());


        boolean isSuspicious = cheatingProbability >= 0.65;
        boolean isHighlySuspicious = cheatingProbability >= mlConfig.getDetectionThreshold();


        String conclusion = result.getConclusion();


        webhookManager.sendMLAnalysisAlert(player, isSuspicious, cheatingPercentage, conclusion);


        String summary = String.format("В§6Analysis for %s: %.1f%% chance of X-ray", player.getName(), cheatingPercentage);


        if (isHighlySuspicious) {
            summary += " В§c[HIGHLY SUSPICIOUS]";


            plugin.getLogger().warning("Highly suspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");
            plugin.getLogger().info(result.getDetailedReport());
            plugin.getLogger().info("Full report saved to: " + reportPath);


            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("owml.staff")) {
                    staff.sendMessage("В§c[OverWatch-ML] В§fHighly suspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");


                    staff.sendMessage("В§7Key findings: " + result.getConclusion());


                    staff.sendMessage("В§7Use В§f/owml ml report " + player.getName() + " В§7to view analysis");
                    staff.sendMessage("В§7Or check the ML Analysis section in the Staff Control Panel");
                }
            }
        } else if (isSuspicious) {
            summary += " В§e[SUSPICIOUS]";


            plugin.getLogger().warning("Suspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");
            plugin.getLogger().info(result.getDetailedReport());
            plugin.getLogger().info("Full report saved to: " + reportPath);


            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("owml.staff")) {
                    staff.sendMessage("В§e[OverWatch-ML] В§fSuspicious mining pattern detected for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% confidence)");


                    staff.sendMessage("В§7Key findings: " + result.getConclusion());


                    staff.sendMessage("В§7Use В§f/owml ml report " + player.getName() + " В§7to view analysis");
                    staff.sendMessage("В§7Or check the ML Analysis section in the Staff Control Panel");
                }
            }
        } else {

            plugin.getLogger().info("Analysis completed for " + player.getName() + " (" + String.format("%.1f", cheatingPercentage) + "% suspicion level)");
            plugin.getLogger().info("Report saved to: " + reportPath);
        }


        player.sendMessage(summary);
    }

    /**
     * Get the detailed detection report for a player
     *
     * @param playerId The player's UUID
     * @return The detection report or null if not analyzed
     */
    public String getDetectionReport(UUID playerId) {
        ReasoningMLModel.DetectionResult result = detectionResults.get(playerId);
        if (result == null) {
            return null;
        }
        return result.getDetailedReport();
    }

    /**
     * Get the current detection score for a player
     *
     * @param playerId The player's UUID
     * @return The detection score (0.0 to 1.0) or -1 if not analyzed
     */
    public double getDetectionScore(UUID playerId) {
        ReasoningMLModel.DetectionResult result = detectionResults.get(playerId);
        if (result == null) {
            return -1.0;
        }
        return result.getProbability();
    }

    /**
     * Generate a simplified report for staff to view in-game
     *
     * @param playerId The player's UUID
     * @return A simplified version of the detection report
     */
    public List<String> getSimplifiedReport(UUID playerId) {
        ReasoningMLModel.DetectionResult result = detectionResults.get(playerId);
        if (result == null) {
            return Collections.singletonList("В§cNo analysis data available for this player");
        }

        List<String> report = new ArrayList<>();
        report.add("В§6-------- X-Ray Detection Report --------");
        report.add("В§fSuspicion Score: " + String.format("В§%c%.1f%%", result.getProbability() > 0.7 ? 'c' : 'a', result.getProbability() * 100));
        report.add("В§fConclusion: В§7" + result.getConclusion());
        report.add("В§6-------- Key Analysis Factors --------");


        List<String> steps = result.getReasoningSteps();
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);

            int endPos = step.indexOf(". ");
            if (endPos > 0) {
                report.add("В§7" + step.substring(0, endPos + 1));
            } else {
                report.add("В§7" + step);
            }
        }

        return report;
    }

    /**
     * Check if the ML system is enabled
     *
     * @return True if enabled
     */
    public boolean isEnabled() {
        return mlConfig.isEnabled();
    }

    /**
     * Enable or disable the ML system
     *
     * @param enabled Whether the system should be enabled
     */
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = mlConfig.isEnabled();
        mlConfig.setEnabled(enabled);


        if (wasEnabled != enabled) {
            if (!enabled) {

                autoAnalysisQueue.clear();
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!mlConfig.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();


        if (playersUnderAnalysis.contains(playerId) || trainingTasks.containsKey(playerId)) {

            if (!event.getFrom().toVector().equals(event.getTo().toVector())) {

                dataCollector.processPlayerMove(player);
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!mlConfig.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();


        if (!dataCollector.isCollectingData(playerId)) {
            return;
        }


        PlayerProtocolData protocolData = dataCollector.getPlayerProtocolData(playerId);
        if (protocolData == null) {
            return;
        }


        boolean isDigging = plugin.getProtocolHandler().isPlayerDigging(playerId);


        if (isDigging) {
            protocolData.recordConcurrentAction("COMMAND_WHILE_DIGGING");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!mlConfig.isEnabled()) {
            return;
        }

        dataCollector.processBlockBreak(event);

        // Send data to AutoTrainer for pattern learning
        UUID playerId = event.getPlayer().getUniqueId();
        Map<String, Double> features = extractFeaturesForAutoLearning(playerId);
        if (!features.isEmpty()) {
            autoTrainer.addUnlabeledSample(playerId, features, 0.5); // Medium confidence for unlabeled data
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();


        if (trainingTasks.containsKey(playerId)) {
            trainingTasks.get(playerId).cancel();
            trainingTasks.remove(playerId);
            trainingEndTimes.remove(playerId);

            PlayerMiningData data = dataCollector.stopCollecting(player);
            if (data != null) {

                MLDataManager.saveTrainingData(data);
            }
        }

        if (analysisTasks.containsKey(playerId)) {
            analysisTasks.get(playerId).cancel();
            analysisTasks.remove(playerId);
            analysisEndTimes.remove(playerId);

            PlayerMiningData data = dataCollector.stopCollecting(player);
            if (data != null) {
                data.calculateDerivedFeatures();
                MLDataManager.savePlayerData(data);
            }

            playersUnderAnalysis.remove(playerId);
        }
    }

    /**
     * Get all players currently under analysis
     *
     * @return A set of player UUIDs under analysis
     */
    public Set<UUID> getPlayersUnderAnalysis() {
        return Collections.unmodifiableSet(playersUnderAnalysis);
    }

    /**
     * Get all players currently in training with their label (true for cheater, false for normal)
     *
     * @return A map of player UUIDs to their training label
     */
    public Map<UUID, Boolean> getPlayersInTraining() {
        Map<UUID, Boolean> result = new HashMap<>();

        for (Map.Entry<UUID, BukkitTask> entry : trainingTasks.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerMiningData data = dataCollector.getPlayerData(playerId);

            if (data != null) {
                result.put(playerId, data.isLabeledAsCheater());
            }
        }

        return result;
    }

    /**
     * Get the remaining time for analysis in seconds
     *
     * @param playerId The player's UUID
     * @return The remaining time in seconds, or -1 if not analyzing
     */
    public long getRemainingAnalysisTime(UUID playerId) {
        Long endTime = analysisEndTimes.get(playerId);
        if (endTime == null) {
            return -1;
        }

        long remainingMs = endTime - System.currentTimeMillis();
        return remainingMs > 0 ? remainingMs / 1000 : 0;
    }

    /**
     * Get the remaining time for training in seconds
     *
     * @param playerId The player's UUID
     * @return The remaining time in seconds, or -1 if not training
     */
    public long getRemainingTrainingTime(UUID playerId) {
        Long endTime = trainingEndTimes.get(playerId);
        if (endTime == null) {
            return -1;
        }

        long remainingMs = endTime - System.currentTimeMillis();
        return remainingMs > 0 ? remainingMs / 1000 : 0;
    }

    /**
     * Cancel training for a player
     *
     * @param player The player to cancel training for
     */
    public void cancelTraining(Player player) {
        UUID playerId = player.getUniqueId();

        if (trainingTasks.containsKey(playerId)) {
            trainingTasks.get(playerId).cancel();
            trainingTasks.remove(playerId);
            trainingEndTimes.remove(playerId);
            dataCollector.stopCollecting(player);


            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("owml.staff")) {
                    staff.sendMessage("В§c[OverWatch-ML] В§fTraining data collection for " + player.getName() + " has been canceled.");
                }
            }


            plugin.getLogger().info("Training data collection for " + player.getName() + " has been canceled");
        }
    }

    /**
     * Enable or disable auto-analysis only
     *
     * @param enabled Whether auto-analysis should be enabled
     */
    public void setAutoAnalysisEnabled(boolean enabled) {
        boolean wasEnabled = mlConfig.isAutoAnalysisEnabled();
        mlConfig.setAutoAnalysisEnabled(enabled);


        if (wasEnabled != enabled) {
            if (!enabled) {

                autoAnalysisQueue.clear();
            }
        }
    }

    /**
     * Get the ML config instance
     *
     * @return The ML config
     */
    public MLConfig getMLConfig() {
        return mlConfig;
    }

    /**
     * Check if a player is already in the analysis queue
     *
     * @param playerId The player's UUID
     * @return True if player is in queue, false otherwise
     */
    public boolean isPlayerInAnalysisQueue(UUID playerId) {
        return autoAnalysisQueue.contains(playerId);
    }

    /**
     * Check if a player already has an analysis report
     *
     * @param playerId   The player's UUID
     * @param playerName The player's name
     * @return True if the player already has a report, false otherwise
     */
    public boolean hasExistingReport(UUID playerId, String playerName) {

        List<String> playerReports = MLDataManager.getPlayerReports(playerName);
        return !playerReports.isEmpty();
    }

    /**
     * Get the number of players in the auto-analysis queue
     *
     * @return Queue size
     */
    public int getAutoAnalysisQueueSize() {
        return autoAnalysisQueue.size();
    }

    /**
     * Check if the ML model has been trained
     *
     * @return True if the model has been trained
     */
    public boolean isTrained() {
        return model.isTrained();
    }

    /**
     * Get the player data collector
     *
     * @return The player data collector
     */
    public PlayerDataCollector getDataCollector() {
        return dataCollector;
    }

    /**
     * Get the online learning manager
     *
     * @return The online learning manager
     */
    public OnlineLearningManager getOnlineLearningManager() {
        return onlineLearningManager;
    }

    /**
     * Get the player clustering engine
     *
     * @return The player clustering engine
     */
    public PlayerClusteringEngine getClusteringEngine() {
        return clusteringEngine;
    }

    /**
     * Get comprehensive ML statistics
     *
     * @return ML statistics
     */
    public MLStats getComprehensiveStats() {
        return new MLStats(
            onlineLearningManager.getStats(),
            clusteringEngine.getStats(),
            model.isTrained(),
            playersUnderAnalysis.size(),
            autoAnalysisQueue.size(),
            System.currentTimeMillis()
        );
    }

    /**
     * Get advanced ML components statistics
     */
    public Map<String, Object> getAdvancedMLStats() {
        Map<String, Object> stats = new HashMap<>();

        // Ensemble detector stats
        stats.put("ensemble", ensembleDetector.getEnsembleStats());

        // Isolation Forest stats
        stats.put("isolationForest", isolationForest.getTrainingStats());

        // Autoencoder stats
        if (autoencoder != null) {
            stats.put("autoencoder", autoencoder.getTrainingStats());
        }

        // Feature engineering stats
        stats.put("featureEngineering", featureEngineer.getFeatureEngineeringStats());

        // Auto-trainer stats
        stats.put("autoTrainer", autoTrainer.getLearningStats());

        return stats;
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
     * Extract features from player data for auto-learning
     */
    private Map<String, Double> extractFeaturesForAutoLearning(UUID playerId) {
        Map<String, Double> features = new HashMap<>();

        try {
            // Get player mining data
            PlayerMiningData miningData = dataCollector.getPlayerData(playerId);
            if (miningData == null) return features;

            // Get calculated features from mining data
            Map<String, Double> miningFeatures = miningData.getFeatures();
            if (miningFeatures != null) {
                features.putAll(miningFeatures);
            }

            // Add some basic features if not already present
            if (!features.containsKey("total_blocks")) {
                features.put("total_blocks", (double) miningData.getBlockBreaks().size());
            }
            if (!features.containsKey("total_ores")) {
                features.put("total_ores", (double) miningData.getOreBreaks().size());
            }
            if (!features.containsKey("session_time")) {
                features.put("session_time", (double) miningData.getTotalMiningTimeMs());
            }

        } catch (Exception e) {
            // If feature extraction fails, return empty map
            plugin.getLogger().warning("Failed to extract features for auto-learning: " + e.getMessage());
        }

        return features;
    }

    /**
     * Get player behavior history for feature engineering
     */
    private List<Map<String, Double>> getPlayerHistory(UUID playerId) {
        // Simplified: return last few sessions
        // In full implementation, this would query historical data
        List<Map<String, Double>> history = new ArrayList<>();

        // Get cached player data if available
        Map<String, Object> cached = MemoryOptimizer.getCachedSample(playerId.toString());
        if (cached != null) {
            @SuppressWarnings("unchecked")
            Map<String, Double> features = (Map<String, Double>) cached.get("features");
            if (features != null) {
                history.add(new HashMap<>(features));
            }
        }

        return history;
    }

    /**
     * Create detection result from ensemble prediction
     */
    private ReasoningMLModel.DetectionResult createEnsembleResult(double score, Map<String, Double> features) {
        boolean isCheater = score >= 0.6;
        String conclusion = isCheater ?
            "Advanced ensemble detection indicates potential cheating behavior" :
            "Advanced ensemble detection shows normal player behavior";

        List<String> reasoningSteps = new ArrayList<>();
        reasoningSteps.add(String.format("Ensemble score: %.3f", score));

        // Add top contributing features
        List<Map.Entry<String, Double>> topFeatures = features.entrySet().stream()
            .sorted((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())))
            .limit(3)
            .toList();

        for (Map.Entry<String, Double> entry : topFeatures) {
            reasoningSteps.add(String.format("%s: %.3f", entry.getKey(), entry.getValue()));
        }

        reasoningSteps.add("Analysis completed using advanced ML techniques");

        return new ReasoningMLModel.DetectionResult(score, conclusion, reasoningSteps);
    }

    /**
     * Get auto-trainer statistics
     */
    public Map<String, Object> getAutoTrainerStats() {
        return autoTrainer.getLearningStats();
    }

    /**
     * Enable/disable automatic learning
     */
    public void setAutoLearningEnabled(boolean enabled) {
        autoTrainer.setAutoGenerateEnabled(enabled);
    }
}
