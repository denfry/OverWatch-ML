package net.denfry.owml;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.commands.AntiXrayCommand;
import net.denfry.owml.commands.CommandHider;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.detection.AdvancedDetectionEngine;
import net.denfry.owml.gui.modern.GUIManager;
import net.denfry.owml.integrations.IntegrationManager;
import org.bstats.bukkit.Metrics;
import net.denfry.owml.listeners.BlockListener;
import net.denfry.owml.listeners.ChatListener;
import net.denfry.owml.listeners.CheatListener;
import net.denfry.owml.listeners.UpdateListener;
import net.denfry.owml.managers.AppealManager;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.managers.StatsManager;
import net.denfry.owml.managers.SuspiciousManager;
import net.denfry.owml.managers.WebhookManager;
import net.denfry.owml.ml.MLConfig;
import net.denfry.owml.ml.MLDataManager;
import net.denfry.owml.ml.ModernMLManager;
import net.denfry.owml.ml.v2.listeners.MLDataListener;
import net.denfry.owml.monitoring.ActivityReportGenerator;
import net.denfry.owml.monitoring.MiningTelemetry;
import net.denfry.owml.monitoring.RealtimeMetricsCollector;
import net.denfry.owml.plugins.PluginManager;
import net.denfry.owml.protocol.PacketMonitor;
import net.denfry.owml.protocol.PacketMonitors;
import net.denfry.owml.punishments.CompositePunishmentEngine;
import net.denfry.owml.punishments.TemporalPunishmentSystem;
import net.denfry.owml.punishments.handlers.Paranoia.ParanoiaHandler;
import net.denfry.owml.security.DataEncryption;
import net.denfry.owml.storage.StorageManager;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.ChatInputHandler;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.Schedulers;
import net.denfry.owml.utils.UpdateApplier;
import net.denfry.owml.utils.UpdateChecker;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class OverWatchML extends JavaPlugin {
   private static OverWatchML instance;
   private ConfigManager configManager;
   private StaffAlertManager staffAlertManager;
   private DecoyManager decoyManager;
   private PunishmentManager punishmentManager;
   private AppealManager appealManager;
   private UpdateChecker updateChecker;
   private UpdateApplier updateApplier;
   private ParanoiaHandler paranoiaHandler;
   private ChatInputHandler chatInputHandler;
   private ModernMLManager mlManager;
   private PacketMonitor protocolHandler;
   private AdvancedDetectionEngine advancedDetectionEngine;
   private IntegrationManager integrationManager;
   private RealtimeMetricsCollector metricsCollector;
   private ActivityReportGenerator reportGenerator;
   private TemporalPunishmentSystem temporalPunishmentSystem;
   private CompositePunishmentEngine compositePunishmentEngine;
   private MiningTelemetry miningTelemetry;
   private DataEncryption dataEncryption;
   private StorageManager storageManager;
   private PluginManager pluginManager;
   private OverWatchContext context;
   private long startupTime;
   private GUIManager guiManager;

   public void onEnable() {
      this.startupTime = System.currentTimeMillis();

      try {
         instance = this;
         Schedulers.init(this);
         MessageManager.logBrand();
         MessageManager.logSystemInfo();
         long phaseStart = System.currentTimeMillis();
         this.initializeBasics();
         this.setupConfiguration();
         this.setupMetricsAndServices();
         this.context = new OverWatchContext(this, this.configManager);
         this.context.loadAll();
         MessageManager.logStartup("Phase 2 (Config) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         phaseStart = System.currentTimeMillis();
         this.initializeStorage();
         MessageManager.logStartup("Phase 3 (Storage) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         phaseStart = System.currentTimeMillis();
         this.initializeManagers();
         MessageManager.logStartup("Phase 4 (Managers) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         phaseStart = System.currentTimeMillis();
         MessageManager.logStartup("Phase 5 (Systems) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         phaseStart = System.currentTimeMillis();
         this.registerEventListeners();
         this.initializeMLSystem();
         MessageManager.logStartup("Phase 6 (Events & ML) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         phaseStart = System.currentTimeMillis();
         this.setupCommands();
         this.configureAutoSave();
         this.guiManager = new GUIManager(this);
         this.getServer().getPluginManager().registerEvents(this.guiManager, this);
         MessageManager.logStartup("Phase 7 (Commands & GUI) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);
         this.finalizeInitialization();
         this.loadLanguageFiles();
         this.scheduleBackgroundTasks();
         long totalTime = System.currentTimeMillis() - this.startupTime;
         MessageManager.logStartup("§aPlugin successfully enabled in §e{TIME}ms", "TIME", totalTime);
         MessageManager.logStartup("§aTarget Version: 1.21+ (Modern API Only)");
      } catch (Exception var5) {
         MessageManager.logStartup("CRITICAL ERROR - Failed to enable plugin: {ERROR}", "ERROR", var5.getMessage());
         var5.printStackTrace();
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   private void initializeBasics() {
      this.saveDefaultConfig();
      this.updateApplier = new UpdateApplier(this);
      this.updateApplier.performPendingCleanup();
   }

   private void setupConfiguration() {
      this.configManager = new ConfigManager(this);
      this.configManager.setupDefaultPunishmentOptions();
      this.configManager.setupDefaultAutoSaveConfigs();
      this.configManager.setupDefaultWorldDisablingConfig();
      this.configManager.initializeWebhookSettings();
   }

   private void initializeUpdateSystem() {
      int resourceId = 122967;
      String modrinthId = this.getConfig().getString("modrinth-id", "ofKCm2tx");
      this.updateChecker = new UpdateChecker(this, resourceId, modrinthId);
      MessageManager.logInit("Update System", "Initialized - will be scheduled after startup");
   }

   private void setupMetricsAndServices() {
      this.appealManager = new AppealManager(this);
      int pluginId = 29877;
      new Metrics(this, pluginId);
      MessageManager.logInit("Metrics System", "bStats enabled with plugin ID: {ID}", "ID", pluginId);
      this.chatInputHandler = new ChatInputHandler(this);
      WebhookManager.initialize(this, this.getConfigManager());
   }

   private void initializeStorage() {
      StorageManager.initialize(this);
   }

   private void initializeManagers() {
      SuspiciousManager.initialize(this);
      StatsManager.initialize(this, this.configManager);
      this.staffAlertManager = new StaffAlertManager(this, this.configManager);
      this.decoyManager = new DecoyManager(this, this.configManager);
      this.punishmentManager = new PunishmentManager(this.configManager, this);
      this.paranoiaHandler = new ParanoiaHandler(this, this.punishmentManager, this.configManager);
      this.advancedDetectionEngine = new AdvancedDetectionEngine();
      this.integrationManager = new IntegrationManager();
      this.metricsCollector = new RealtimeMetricsCollector();
      this.reportGenerator = new ActivityReportGenerator();
      this.temporalPunishmentSystem = new TemporalPunishmentSystem();
      this.compositePunishmentEngine = new CompositePunishmentEngine();
      this.dataEncryption = new DataEncryption();
      this.pluginManager = new PluginManager();
   }

   private void registerEventListeners() {
      this.getServer().getPluginManager().registerEvents(new ChatListener(this.punishmentManager, this), this);
      this.getServer()
         .getPluginManager()
         .registerEvents(
            new BlockListener(this, this.configManager, this.staffAlertManager, this.decoyManager, this.punishmentManager, this.paranoiaHandler, this.context),
            this
         );
      this.getServer().getPluginManager().registerEvents(new MLDataListener(this, this.context.getDetectionPipeline()), this);
      this.getServer().getPluginManager().registerEvents(new CheatListener(this, this.context), this);
      if (this.getConfig().getBoolean("telemetry.enabled", true)) {
         Set<Material> ores = EnumSet.noneOf(Material.class);

         for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isBlock() && (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS)) {
               ores.add(m);
            }
         }

         this.miningTelemetry = new MiningTelemetry(this, ores);
         this.getServer().getPluginManager().registerEvents(this.miningTelemetry, this);
         this.miningTelemetry.start(this.getConfig().getInt("telemetry.flush-minutes", 10));
      }

      // Automatic x-ray signal: veins reached by digging per 100 stone (see XrayRatioTracker). Alerts staff only.
      if (this.getConfig().getBoolean("xray-heuristic.enabled", true)) {
         net.denfry.owml.detection.xray.XrayRatioListener xray = new net.denfry.owml.detection.xray.XrayRatioListener(
            this, this.staffAlertManager, this.getConfig().getConfigurationSection("xray-heuristic"));
         this.getServer().getPluginManager().registerEvents(xray, this);
         Schedulers.asyncTimer(xray::cleanup, 20L * 600L, 20L * 600L);
      }
   }

   private void setupCommands() {
      if (this.configManager.isCommandHidingEnabled()) {
         this.setupCommandHiding();
      } else {
         AntiXrayCommand antiXrayCommand = new AntiXrayCommand(this, this.updateChecker);
         this.getCommand("OverWatch").setExecutor(antiXrayCommand);
         this.getCommand("OverWatch").setTabCompleter(antiXrayCommand);
      }
   }

   private void setupCommandHiding() {
      CommandHider commandHider = new CommandHider(this);
      this.getServer().getPluginManager().registerEvents(commandHider, this);
      String[] commandNames = new String[]{"OverWatch", "owml", "OverWatchx"};

      for (String commandName : commandNames) {
         if (this.getCommand(commandName) != null) {
            this.getCommand(commandName).setExecutor(commandHider);
            this.getCommand(commandName).setTabCompleter(commandHider);
         }
      }

      MessageManager.logInit("Command Hiding", "Feature enabled - commands hidden from unauthorized players");
   }

   private void configureAutoSave() {
      MessageManager.logInit("Auto-Save", "Disabled for Folia compatibility");
   }

   private void scheduleBackgroundTasks() {
      Schedulers.globalLater(() -> {
         try {
            this.startAllBackgroundTasks();
         } catch (Exception var2) {
            MessageManager.logStartup("Failed to start background tasks: {ERROR}", "ERROR", var2.getMessage());
            var2.printStackTrace();
         }
      }, 200L);
   }

   private void startAllBackgroundTasks() {
      if (this.getConfig().getBoolean("stats.autoSave.enabled", true)) {
         long minutes = Math.max(1L, this.getConfig().getLong("stats.autoSave.intervalMinutes", 10L));
         long ticks = minutes * 60L * 20L;
         Schedulers.asyncTimer(() -> {
            if (this.context != null) {
               this.context.saveAll();
            }
         }, ticks, ticks);
      }

      StatsManager.updateAutoSaveSettings();
      SuspiciousManager.updateAutoSaveSettings();
      this.punishmentManager.updateAutoSaveSettings();
      this.punishmentManager.startCleanupTask();
      this.punishmentManager.startDisabledLevelCheckTask();
      this.decoyManager.scheduleOreTrackerCleanup();
      this.paranoiaHandler.startParanoiaTask();
      this.metricsCollector.startCollectionTasks();
      this.temporalPunishmentSystem.startPunishmentCleanupTask();
      this.compositePunishmentEngine.startCompositeCleanupTask();
      if (this.getConfig().getBoolean("check-for-updates", true)) {
         String modrinthId = this.getConfig().getString("modrinth-id", "ofKCm2tx");
         this.updateChecker = new UpdateChecker(this, 122967, modrinthId);
         UpdateListener updateListener = new UpdateListener(this, this.updateChecker);
         this.getServer().getPluginManager().registerEvents(updateListener, this);
         updateListener.performInitialUpdateCheck();
      }

      MessageManager.logStartup("All background tasks started successfully");
   }

   private void finalizeInitialization() {
      this.ensureCompleteConfig();
      boolean updatePending = this.updateApplier.checkForPendingUpdate();
      MessageManager.logStartup("Plugin successfully enabled and ready to operate");
      if (updatePending) {
         MessageManager.logStartup("Update pending - will be applied on next server shutdown");
      }
   }

   private void loadLanguageFiles() {
      File dataFolder = this.getDataFolder();
      if (dataFolder.exists()) {
         File[] langFiles = dataFolder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
         if (langFiles != null) {
            for (File langFile : langFiles) {
               String fileName = langFile.getName();
               String languageCode = fileName.substring(9, fileName.length() - 11);
               MessageManager.loadLanguageFile(languageCode);
            }
         }
      }

      String configLanguage = this.getConfig().getString("language", "en");
      if (!configLanguage.equals("en")) {
         MessageManager.setLanguage(configLanguage);
      }
   }

   private void initializeMLSystem() {
      MLDataManager.initialize(this);
      MLConfig mlConfig = new MLConfig(this);
      this.mlManager = new ModernMLManager(this, this.configManager);
      if (mlConfig.isEnabled()) {
         MessageManager.logInit("ML System", "Detection system enabled");
         if (this.mlManager.isTrained()) {
            MessageManager.logInit("ML Model", "Successfully loaded with pre-trained data");
         } else {
            MessageManager.logInit("ML Model", "Not trained - use '/OverWatch ml train' to collect training data");
         }

         this.protocolHandler = PacketMonitors.create(this, this.mlManager.getDataCollector(), this.configManager);
      } else {
         MessageManager.logInit("ML System", "Disabled - use '/OverWatch ml enable' to activate");
      }
   }

   public void saveConfig() {
      super.saveConfig();

      try {
         File configFile = new File(this.getDataFolder(), "config.yml");
         if (this.getConfigManager().isDebugEnabled()) {
            this.getLogger().info("Forcefully saving config to " + configFile.getAbsolutePath());
         }

         if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            this.saveResource("config.yml", false);
         }
      } catch (Exception var2) {
         this.getLogger().warning("Error ensuring config file exists: " + var2.getMessage());
         var2.printStackTrace();
      }
   }

   public void onDisable() {
      MessageManager.logStartup("Plugin shutdown initiated");
      if (this.miningTelemetry != null) {
         try {
            this.miningTelemetry.flush(true);
         } catch (RuntimeException var4) {
         }
      }

      try {
         if (this.context != null) {
            try {
               this.context.saveAll().get(15L, TimeUnit.SECONDS);
            } catch (Exception var2) {
               this.getLogger().warning("Не дождались сохранения данных: " + var2);
            }

            this.context.shutdown();
            MessageManager.logStartup("OverWatch context shut down successfully");
         }

         if (this.mlManager != null) {
            this.mlManager.shutdown();
            MessageManager.logStartup("ML manager shut down successfully");
         }

         if (this.protocolHandler != null) {
            this.protocolHandler.shutdown();
            MessageManager.logStartup("Protocol handler shut down successfully");
         }

         if (this.punishmentManager != null) {
            this.punishmentManager.onDisable();
            MessageManager.logStartup("Punishment manager shut down successfully");
         }

         if (this.updateApplier != null && this.updateApplier.isUpdatePending()) {
            MessageManager.logStartup("Applying pending update during shutdown");
            this.updateApplier.applyUpdateOnShutdown();
            MessageManager.logStartup("Update applied successfully");
         }

         if (this.paranoiaHandler != null) {
            this.paranoiaHandler.cleanup();
            MessageManager.logStartup("Paranoia handler cleaned up successfully");
         }

         if (this.storageManager != null) {
            this.storageManager.shutdown();
            MessageManager.logStartup("Storage system shut down successfully");
         }

         Schedulers.cancelAll();
         AsyncExecutor.shutdown();
         MessageManager.logStartup("Async executors shut down successfully");
         MessageManager.logStartup("Plugin shutdown completed successfully");
      } catch (Exception var3) {
         MessageManager.logStartup("Error during shutdown: {ERROR}", "ERROR", var3.getMessage());
         var3.printStackTrace();
      }
   }

   private void ensureCompleteConfig() {
      boolean configUpdated = false;
      configUpdated |= this.ensureConfigPath("command-hiding.enabled", false);
      configUpdated |= this.ensureConfigPath("command-hiding.messages.error-line1", "§cUnknown or incomplete command, see below for error");
      configUpdated |= this.ensureConfigPath("command-hiding.messages.error-line2", "§c§n{command}§r§c§o<--[HERE]");
      configUpdated |= this.ensureConfigPath("check-for-updates", false);
      configUpdated |= this.ensureConfigPath("periodic-update-checks.enabled", false);
      configUpdated |= this.ensureConfigPath("periodic-update-checks.interval-hours", 24);
      if (configUpdated) {
         this.saveConfig();
         this.getLogger().info("Added missing configuration options to config.yml");
      }
   }

   private boolean ensureConfigPath(String path, Object defaultValue) {
      if (!this.getConfig().isSet(path)) {
         this.getConfig().set(path, defaultValue);
         return true;
      } else {
         return false;
      }
   }

   public PacketMonitor getProtocolHandler() {
      return this.protocolHandler;
   }

   public ModernMLManager getMLManager() {
      return this.mlManager;
   }

   public ChatInputHandler getChatInputHandler() {
      return this.chatInputHandler;
   }

   public WebhookManager getWebhookManager() {
      return WebhookManager.getInstance();
   }

   public AppealManager getAppealManager() {
      return this.appealManager;
   }

   public OverWatchContext getContext() {
      return this.context;
   }

   public static OverWatchML getInstance() {
      return instance;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public boolean wantsAlerts(Player staff) {
      return staff.hasPermission("owml.staff") && (this.staffAlertManager == null || !this.staffAlertManager.areAlertsDisabled(staff));
   }

   public StaffAlertManager getStaffAlertManager() {
      return this.staffAlertManager;
   }

   public void setStaffAlertManager(StaffAlertManager staffAlertManager) {
      this.staffAlertManager = staffAlertManager;
   }

   public DecoyManager getDecoyManager() {
      return this.decoyManager;
   }

   public void setDecoyManager(DecoyManager decoyManager) {
      this.decoyManager = decoyManager;
   }

   public PunishmentManager getPunishmentManager() {
      return this.punishmentManager;
   }

   public UpdateChecker getUpdateChecker() {
      return this.updateChecker;
   }

   public UpdateApplier getUpdateApplier() {
      return this.updateApplier;
   }

   public boolean isAutoUpdateEnabled() {
      return this.getConfig().getBoolean("auto-updates.enabled", false);
   }

   public AdvancedDetectionEngine getAdvancedDetectionEngine() {
      return this.advancedDetectionEngine;
   }

   public IntegrationManager getIntegrationManager() {
      return this.integrationManager;
   }

   public RealtimeMetricsCollector getMetricsCollector() {
      return this.metricsCollector;
   }

   public ActivityReportGenerator getReportGenerator() {
      return this.reportGenerator;
   }

   public TemporalPunishmentSystem getTemporalPunishmentSystem() {
      return this.temporalPunishmentSystem;
   }

   public MiningTelemetry getMiningTelemetry() {
      return this.miningTelemetry;
   }

   public CompositePunishmentEngine getCompositePunishmentEngine() {
      return this.compositePunishmentEngine;
   }

   public DataEncryption getDataEncryption() {
      return this.dataEncryption;
   }

   public PluginManager getPluginManager() {
      return this.pluginManager;
   }

   public SuspiciousManager getSuspiciousManager() {
      return null;
   }

   public long getStartupTime() {
      return this.startupTime;
   }

   public int getSuspicionLevel(String playerName) {
      return 0;
   }

   public Set<UUID> getSuspiciousPlayers() {
      return SuspiciousManager.getSuspiciousPlayersStatic();
   }

   public StatsManager getStatsManager() {
      return new StatsManager();
   }
}
