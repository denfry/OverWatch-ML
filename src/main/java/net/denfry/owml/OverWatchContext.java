package net.denfry.owml;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.detection.BehaviorProfileManager;
import net.denfry.owml.detection.DetectionOrchestrator;
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
   private final BehaviorProfileManager profileManager;
   private final DetectionOrchestrator detectionOrchestrator;

   public OverWatchContext(OverWatchML plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.statsService = new StatsService(new YamlStatsRepository(plugin), configManager.getNaturalOres());
      this.suspiciousService = new SuspiciousService(new YamlSuspiciousRepository(plugin), plugin.getConfig().getInt("ml.autoAnalysis.suspiciousThreshold", 15));
      this.decoyService = new DecoyService(plugin, configManager);
      this.detectionPipeline = new DetectionPipeline(plugin);
      this.profileManager = new BehaviorProfileManager();
      this.detectionOrchestrator = new DetectionOrchestrator(plugin, this.profileManager, new ArrayList<>());
   }

   public void loadAll() {
      this.statsService.load();
      this.suspiciousService.load();
   }

   public CompletableFuture<Void> saveAll() {
      return CompletableFuture.allOf(this.statsService.save(), this.suspiciousService.save());
   }

   public void shutdown() {
      this.detectionPipeline.shutdown();
      this.detectionOrchestrator.shutdown();
   }

   public IStatsService getStatsService() {
      return this.statsService;
   }

   public ISuspiciousService getSuspiciousService() {
      return this.suspiciousService;
   }

   public IDecoyService getDecoyService() {
      return this.decoyService;
   }

   public DetectionPipeline getDetectionPipeline() {
      return this.detectionPipeline;
   }

   public BehaviorProfileManager getProfileManager() {
      return this.profileManager;
   }

   public DetectionOrchestrator getDetectionOrchestrator() {
      return this.detectionOrchestrator;
   }
}
