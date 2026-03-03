package net.denfry.owml;

import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.DecoyService;
import net.denfry.owml.managers.IDecoyService;
import net.denfry.owml.managers.IStatsService;
import net.denfry.owml.managers.ISuspiciousService;
import net.denfry.owml.managers.StatsService;
import net.denfry.owml.managers.SuspiciousService;
import net.denfry.owml.ml.v2.pipeline.DetectionPipeline;
import net.denfry.owml.storage.YamlStatsRepository;
import net.denfry.owml.storage.YamlSuspiciousRepository;

public class OverWatchContext {
    private final OverWatchML plugin;
    private final IStatsService statsService;
    private final ISuspiciousService suspiciousService;
    private final IDecoyService decoyService;
    private final DetectionPipeline detectionPipeline;

    public OverWatchContext(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        
        // Stats
        this.statsService = new StatsService(
            new YamlStatsRepository(plugin),
            configManager.getNaturalOres()
        );
        
        // Suspicious
        this.suspiciousService = new SuspiciousService(
            new YamlSuspiciousRepository(plugin),
            plugin.getConfig().getInt("ml.autoAnalysis.suspiciousThreshold", 15)
        );

        // Decoy
        this.decoyService = new DecoyService(plugin, configManager);

        // ML v2 Pipeline
        this.detectionPipeline = new DetectionPipeline(plugin);
    }

    public void loadAll() {
        statsService.load();
        suspiciousService.load();
    }

    public void saveAll() {
        statsService.save();
        suspiciousService.save();
    }

    public void shutdown() {
        detectionPipeline.shutdown();
    }

    public IStatsService getStatsService() {
        return statsService;
    }

    public ISuspiciousService getSuspiciousService() {
        return suspiciousService;
    }

    public IDecoyService getDecoyService() {
        return decoyService;
    }

    public DetectionPipeline getDetectionPipeline() {
        return detectionPipeline;
    }
}
