package net.denfry.owml.ml;

import org.bukkit.configuration.file.FileConfiguration;
import net.denfry.owml.OverWatchML;

/**
 * Configuration for the machine learning component
 */
public class MLConfig {
    private final OverWatchML plugin;

    private boolean enabled = true;

    private int trainingSessionDuration = 10 * 60;

    private double detectionThreshold = 0.75;

    private int positionUpdateInterval = 5;

    private boolean autoAnalysisEnabled = true;
    private int suspiciousThreshold = 5;
    private int maxAutoAnalysisPlayers = 5;

    // Performance tuning
    private int executorPoolSize = 0; // 0 = auto-detect
    private int analysisTimeoutSeconds = 30;
    private int maxConcurrentAnalyses = 10;

    // Cache configuration
    private int analysisCacheMaxSize = 1000;
    private int featuresCacheMaxSize = 500;
    private int playerDataCacheMaxSize = 200;
    private int analysisCacheExpiryMinutes = 5;
    private int featuresCacheExpiryMinutes = 10;
    private int playerDataCacheExpiryMinutes = 30;

    // Maintenance
    private int cleanupIntervalMinutes = 5;
    private int metricsReportIntervalMinutes = 15;
    private int maxTrainingQueueSize = 1000;
    private int maxAnalysisQueueSize = 500;

    public MLConfig(OverWatchML plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load configuration from config.yml
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains("ml.enabled")) {
            config.set("ml.enabled", enabled);
        }
        if (!config.contains("ml.trainingSessionDuration")) {
            config.set("ml.trainingSessionDuration", trainingSessionDuration);
        }
        if (!config.contains("ml.detectionThreshold")) {
            config.set("ml.detectionThreshold", detectionThreshold);
        }
        if (!config.contains("ml.positionUpdateInterval")) {
            config.set("ml.positionUpdateInterval", positionUpdateInterval);
        }

        if (!config.contains("ml.autoAnalysis.enabled")) {
            config.set("ml.autoAnalysis.enabled", autoAnalysisEnabled);
        }
        if (!config.contains("ml.autoAnalysis.suspiciousThreshold")) {
            config.set("ml.autoAnalysis.suspiciousThreshold", suspiciousThreshold);
        }
        if (!config.contains("ml.autoAnalysis.maxPlayers")) {
            config.set("ml.autoAnalysis.maxPlayers", maxAutoAnalysisPlayers);
        }

        // Performance tuning
        if (!config.contains("ml.performance.executorPoolSize")) {
            config.set("ml.performance.executorPoolSize", executorPoolSize);
        }
        if (!config.contains("ml.performance.analysisTimeoutSeconds")) {
            config.set("ml.performance.analysisTimeoutSeconds", analysisTimeoutSeconds);
        }
        if (!config.contains("ml.performance.maxConcurrentAnalyses")) {
            config.set("ml.performance.maxConcurrentAnalyses", maxConcurrentAnalyses);
        }

        // Cache configuration
        if (!config.contains("ml.cache.analysis.maxSize")) {
            config.set("ml.cache.analysis.maxSize", analysisCacheMaxSize);
        }
        if (!config.contains("ml.cache.features.maxSize")) {
            config.set("ml.cache.features.maxSize", featuresCacheMaxSize);
        }
        if (!config.contains("ml.cache.playerData.maxSize")) {
            config.set("ml.cache.playerData.maxSize", playerDataCacheMaxSize);
        }
        if (!config.contains("ml.cache.analysis.expiryMinutes")) {
            config.set("ml.cache.analysis.expiryMinutes", analysisCacheExpiryMinutes);
        }
        if (!config.contains("ml.cache.features.expiryMinutes")) {
            config.set("ml.cache.features.expiryMinutes", featuresCacheExpiryMinutes);
        }
        if (!config.contains("ml.cache.playerData.expiryMinutes")) {
            config.set("ml.cache.playerData.expiryMinutes", playerDataCacheExpiryMinutes);
        }

        // Maintenance
        if (!config.contains("ml.maintenance.cleanupIntervalMinutes")) {
            config.set("ml.maintenance.cleanupIntervalMinutes", cleanupIntervalMinutes);
        }
        if (!config.contains("ml.maintenance.metricsReportIntervalMinutes")) {
            config.set("ml.maintenance.metricsReportIntervalMinutes", metricsReportIntervalMinutes);
        }
        if (!config.contains("ml.maintenance.maxTrainingQueueSize")) {
            config.set("ml.maintenance.maxTrainingQueueSize", maxTrainingQueueSize);
        }
        if (!config.contains("ml.maintenance.maxAnalysisQueueSize")) {
            config.set("ml.maintenance.maxAnalysisQueueSize", maxAnalysisQueueSize);
        }

        plugin.saveConfig();

        enabled = config.getBoolean("ml.enabled");
        trainingSessionDuration = config.getInt("ml.trainingSessionDuration");
        detectionThreshold = config.getDouble("ml.detectionThreshold");
        positionUpdateInterval = config.getInt("ml.positionUpdateInterval");

        autoAnalysisEnabled = config.getBoolean("ml.autoAnalysis.enabled");
        suspiciousThreshold = config.getInt("ml.autoAnalysis.suspiciousThreshold");
        maxAutoAnalysisPlayers = config.getInt("ml.autoAnalysis.maxPlayers");

        // Performance tuning
        executorPoolSize = config.getInt("ml.performance.executorPoolSize");
        analysisTimeoutSeconds = config.getInt("ml.performance.analysisTimeoutSeconds");
        maxConcurrentAnalyses = config.getInt("ml.performance.maxConcurrentAnalyses");

        // Cache configuration
        analysisCacheMaxSize = config.getInt("ml.cache.analysis.maxSize");
        featuresCacheMaxSize = config.getInt("ml.cache.features.maxSize");
        playerDataCacheMaxSize = config.getInt("ml.cache.playerData.maxSize");
        analysisCacheExpiryMinutes = config.getInt("ml.cache.analysis.expiryMinutes");
        featuresCacheExpiryMinutes = config.getInt("ml.cache.features.expiryMinutes");
        playerDataCacheExpiryMinutes = config.getInt("ml.cache.playerData.expiryMinutes");

        // Maintenance
        cleanupIntervalMinutes = config.getInt("ml.maintenance.cleanupIntervalMinutes");
        metricsReportIntervalMinutes = config.getInt("ml.maintenance.metricsReportIntervalMinutes");
        maxTrainingQueueSize = config.getInt("ml.maintenance.maxTrainingQueueSize");
        maxAnalysisQueueSize = config.getInt("ml.maintenance.maxAnalysisQueueSize");
    }

    /**
     * Save configuration to config.yml
     */
    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();

        config.set("ml.enabled", enabled);
        config.set("ml.trainingSessionDuration", trainingSessionDuration);
        config.set("ml.detectionThreshold", detectionThreshold);
        config.set("ml.positionUpdateInterval", positionUpdateInterval);

        config.set("ml.autoAnalysis.enabled", autoAnalysisEnabled);
        config.set("ml.autoAnalysis.suspiciousThreshold", suspiciousThreshold);
        config.set("ml.autoAnalysis.maxPlayers", maxAutoAnalysisPlayers);

        // Performance tuning
        config.set("ml.performance.executorPoolSize", executorPoolSize);
        config.set("ml.performance.analysisTimeoutSeconds", analysisTimeoutSeconds);
        config.set("ml.performance.maxConcurrentAnalyses", maxConcurrentAnalyses);

        // Cache configuration
        config.set("ml.cache.analysis.maxSize", analysisCacheMaxSize);
        config.set("ml.cache.features.maxSize", featuresCacheMaxSize);
        config.set("ml.cache.playerData.maxSize", playerDataCacheMaxSize);
        config.set("ml.cache.analysis.expiryMinutes", analysisCacheExpiryMinutes);
        config.set("ml.cache.features.expiryMinutes", featuresCacheExpiryMinutes);
        config.set("ml.cache.playerData.expiryMinutes", playerDataCacheExpiryMinutes);

        // Maintenance
        config.set("ml.maintenance.cleanupIntervalMinutes", cleanupIntervalMinutes);
        config.set("ml.maintenance.metricsReportIntervalMinutes", metricsReportIntervalMinutes);
        config.set("ml.maintenance.maxTrainingQueueSize", maxTrainingQueueSize);
        config.set("ml.maintenance.maxAnalysisQueueSize", maxAnalysisQueueSize);

        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveConfig();
    }

    public int getTrainingSessionDuration() {
        return trainingSessionDuration;
    }

    public double getDetectionThreshold() {
        return detectionThreshold;
    }

    public void setDetectionThreshold(double detectionThreshold) {
        this.detectionThreshold = detectionThreshold;
        saveConfig();
    }

    public int getPositionUpdateInterval() {
        return positionUpdateInterval;
    }

    public boolean isAutoAnalysisEnabled() {
        return autoAnalysisEnabled;
    }

    public void setAutoAnalysisEnabled(boolean autoAnalysisEnabled) {
        this.autoAnalysisEnabled = autoAnalysisEnabled;
        saveConfig();
    }

    public int getSuspiciousThreshold() {
        return suspiciousThreshold;
    }

    public void setSuspiciousThreshold(int suspiciousThreshold) {
        this.suspiciousThreshold = suspiciousThreshold;
        saveConfig();
    }

    public int getMaxAutoAnalysisPlayers() {
        return maxAutoAnalysisPlayers;
    }

    public void setMaxAutoAnalysisPlayers(int maxAutoAnalysisPlayers) {
        this.maxAutoAnalysisPlayers = maxAutoAnalysisPlayers;
        saveConfig();
    }

    // Performance tuning getters/setters
    public int getExecutorPoolSize() {
        return executorPoolSize;
    }

    public void setExecutorPoolSize(int executorPoolSize) {
        this.executorPoolSize = executorPoolSize;
        saveConfig();
    }

    public int getAnalysisTimeoutSeconds() {
        return analysisTimeoutSeconds;
    }

    public void setAnalysisTimeoutSeconds(int analysisTimeoutSeconds) {
        this.analysisTimeoutSeconds = analysisTimeoutSeconds;
        saveConfig();
    }

    public int getMaxConcurrentAnalyses() {
        return maxConcurrentAnalyses;
    }

    public void setMaxConcurrentAnalyses(int maxConcurrentAnalyses) {
        this.maxConcurrentAnalyses = maxConcurrentAnalyses;
        saveConfig();
    }

    // Cache configuration getters/setters
    public int getAnalysisCacheMaxSize() {
        return analysisCacheMaxSize;
    }

    public void setAnalysisCacheMaxSize(int analysisCacheMaxSize) {
        this.analysisCacheMaxSize = analysisCacheMaxSize;
        saveConfig();
    }

    public int getFeaturesCacheMaxSize() {
        return featuresCacheMaxSize;
    }

    public void setFeaturesCacheMaxSize(int featuresCacheMaxSize) {
        this.featuresCacheMaxSize = featuresCacheMaxSize;
        saveConfig();
    }

    public int getPlayerDataCacheMaxSize() {
        return playerDataCacheMaxSize;
    }

    public void setPlayerDataCacheMaxSize(int playerDataCacheMaxSize) {
        this.playerDataCacheMaxSize = playerDataCacheMaxSize;
        saveConfig();
    }

    public int getAnalysisCacheExpiryMinutes() {
        return analysisCacheExpiryMinutes;
    }

    public void setAnalysisCacheExpiryMinutes(int analysisCacheExpiryMinutes) {
        this.analysisCacheExpiryMinutes = analysisCacheExpiryMinutes;
        saveConfig();
    }

    public int getFeaturesCacheExpiryMinutes() {
        return featuresCacheExpiryMinutes;
    }

    public void setFeaturesCacheExpiryMinutes(int featuresCacheExpiryMinutes) {
        this.featuresCacheExpiryMinutes = featuresCacheExpiryMinutes;
        saveConfig();
    }

    public int getPlayerDataCacheExpiryMinutes() {
        return playerDataCacheExpiryMinutes;
    }

    public void setPlayerDataCacheExpiryMinutes(int playerDataCacheExpiryMinutes) {
        this.playerDataCacheExpiryMinutes = playerDataCacheExpiryMinutes;
        saveConfig();
    }

    // Maintenance getters/setters
    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }

    public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        saveConfig();
    }

    public int getMetricsReportIntervalMinutes() {
        return metricsReportIntervalMinutes;
    }

    public void setMetricsReportIntervalMinutes(int metricsReportIntervalMinutes) {
        this.metricsReportIntervalMinutes = metricsReportIntervalMinutes;
        saveConfig();
    }

    public int getMaxTrainingQueueSize() {
        return maxTrainingQueueSize;
    }

    public void setMaxTrainingQueueSize(int maxTrainingQueueSize) {
        this.maxTrainingQueueSize = maxTrainingQueueSize;
        saveConfig();
    }

    public int getMaxAnalysisQueueSize() {
        return maxAnalysisQueueSize;
    }

    public void setMaxAnalysisQueueSize(int maxAnalysisQueueSize) {
        this.maxAnalysisQueueSize = maxAnalysisQueueSize;
        saveConfig();
    }
}
