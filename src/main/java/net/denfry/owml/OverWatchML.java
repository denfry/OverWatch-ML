/**
 * OverWatchML - Advanced Anti-Cheat Plugin for Minecraft
 * Powered by Machine Learning and Behavioral Analysis.
 */

package net.denfry.owml;

import java.io.File;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.commands.AntiXrayCommand;
import net.denfry.owml.commands.CommandHider;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.detection.AdvancedDetectionEngine;
import net.denfry.owml.gui.GuiListener;
import net.denfry.owml.gui.PlayerStatsGuiListener;
import net.denfry.owml.gui.StaffMenuGUI;
import net.denfry.owml.integrations.IntegrationManager;
import net.denfry.owml.listeners.BlockListener;
import net.denfry.owml.listeners.ChatListener;
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
import net.denfry.owml.monitoring.ActivityReportGenerator;
import net.denfry.owml.monitoring.RealtimeMetricsCollector;
import net.denfry.owml.plugins.PluginManager;
import net.denfry.owml.protocol.ProtocolHandler;
import net.denfry.owml.punishments.CompositePunishmentEngine;
import net.denfry.owml.punishments.TemporalPunishmentSystem;
import net.denfry.owml.punishments.handlers.Paranoia.ParanoiaHandler;
import net.denfry.owml.security.DataEncryption;
import net.denfry.owml.storage.StorageManager;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.ChatInputHandler;
import net.denfry.owml.utils.MaterialHelper;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.TextUtils;
import net.denfry.owml.utils.UpdateApplier;
import net.denfry.owml.utils.UpdateChecker;
import net.denfry.owml.utils.VersionHelper;
import net.denfry.owml.web.AdminPanel;

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
    private ProtocolHandler protocolHandler;
    private AdvancedDetectionEngine advancedDetectionEngine;
    private IntegrationManager integrationManager;
    private RealtimeMetricsCollector metricsCollector;
    private ActivityReportGenerator reportGenerator;
    private TemporalPunishmentSystem temporalPunishmentSystem;
    private CompositePunishmentEngine compositePunishmentEngine;
    private DataEncryption dataEncryption;
    private StorageManager storageManager;
    private PluginManager pluginManager;
    private AdminPanel adminPanel;
    private OverWatchContext context;
    private long startupTime;

    private net.denfry.owml.gui.modern.GUIManager guiManager;

    @Override
    public void onEnable() {
        startupTime = System.currentTimeMillis();
        try {
            instance = this;

            // Phase 1: Branding and Basic Environment
            MessageManager.logBrand();
            MessageManager.logSystemInfo();
            
            // Phase 2: Version compatibility initialization
            long phaseStart = System.currentTimeMillis();
            initializeVersionCompatibility();
            MessageManager.logStartup("Phase 2 (Environment) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 3: Configuration setup
            phaseStart = System.currentTimeMillis();
            initializeBasics();
            setupConfiguration();
            context = new OverWatchContext(this, configManager);
            context.loadAll();
            MessageManager.logStartup("Phase 3 (Config) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 4: Storage system
            phaseStart = System.currentTimeMillis();
            initializeStorage();
            MessageManager.logStartup("Phase 4 (Storage) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 5: Core managers
            phaseStart = System.currentTimeMillis();
            initializeManagers();
            initializeSecurity();
            MessageManager.logStartup("Phase 5 (Managers) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 6: Advanced systems & Integrations
            phaseStart = System.currentTimeMillis();
            initializeAdvancedSystems();
            initializeIntegrations();
            MessageManager.logStartup("Phase 6 (Systems) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 7: Event listeners & ML
            phaseStart = System.currentTimeMillis();
            registerEventListeners();
            initializeMLSystem();
            MessageManager.logStartup("Phase 7 (Events & ML) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Phase 8: Commands & GUI
            phaseStart = System.currentTimeMillis();
            setupCommands();
            configureAutoSave();
            this.guiManager = new net.denfry.owml.gui.modern.GUIManager(this);
            getServer().getPluginManager().registerEvents(guiManager, this);
            MessageManager.logStartup("Phase 8 (Commands & GUI) completed in {TIME}ms", "TIME", System.currentTimeMillis() - phaseStart);

            // Final Phase: Cleanup and start
            finalizeInitialization();
            loadLanguageFiles();
            scheduleBackgroundTasks();

            long totalTime = System.currentTimeMillis() - startupTime;
            MessageManager.logStartup("§aPlugin successfully enabled in §e{TIME}ms", "TIME", totalTime);
            MessageManager.logStartup("§aCurrent protection level: §f" + (mlManager != null && mlManager.isTrained() ? "§aFULL" : "§eBASIC (ML NOT TRAINED)"));

        } catch (Exception e) {
            MessageManager.logStartup("CRITICAL ERROR - Failed to enable plugin: {ERROR}", "ERROR", e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Phase 0: Version compatibility initialization.
     */
    private void initializeVersionCompatibility() {
        // Initialize version detection
        VersionHelper.initialize();

        // Initialize material helper for version compatibility
        MaterialHelper.initialize();

        // Initialize text utilities with appropriate provider
        TextUtils.initialize();

        MessageManager.logInit("Version Compatibility", "Initialized for Minecraft {VERSION}",
            "VERSION", VersionHelper.getServerVersion());
    }

    /**
     * Phase 1: Basic initialization - saves default config and sets up update applier.
     */
    private void initializeBasics() {
        saveDefaultConfig();
        updateApplier = new UpdateApplier(this);
        updateApplier.performPendingCleanup();
    }

    /**
     * Phase 2: Configuration setup - initializes config manager and sets up defaults.
     */
    private void setupConfiguration() {
        configManager = new ConfigManager(this);
        configManager.setupDefaultPunishmentOptions();
        configManager.setupDefaultAutoSaveConfigs();
        configManager.setupDefaultWorldDisablingConfig();
        configManager.initializeWebhookSettings();
    }

    /**
     * Phase 3: Update system initialization.
     */
    private void initializeUpdateSystem() {
        int resourceId = 122967;
        String modrinthId = getConfig().getString("modrinth-id", "ofKCm2tx");
        updateChecker = new UpdateChecker(this, resourceId, modrinthId);

        // Update checking will be scheduled later to avoid scheduler issues during initialization
        MessageManager.logInit("Update System", "Initialized - will be scheduled after startup");
    }

    /**
     * Phase 4: Metrics and external services setup.
     */
    private void setupMetricsAndServices() {
        appealManager = new AppealManager(this);

        int pluginId = 25174;
        Metrics metrics = new Metrics(this, pluginId);
        MessageManager.logInit("Metrics System", "bStats enabled with plugin ID: {ID}", "ID", pluginId);

        chatInputHandler = new ChatInputHandler(this);
        WebhookManager.initialize(this, getConfigManager());
    }

    /**
     * Phase 5: Storage system initialization.
     */
    private void initializeStorage() {
        net.denfry.owml.storage.StorageManager.initialize(this);
    }

    /**
     * Phase 6: Core managers initialization.
     */
    private void initializeManagers() {
        SuspiciousManager.initialize(this);
        StatsManager.initialize(this, configManager);

        staffAlertManager = new StaffAlertManager(this, configManager);
        decoyManager = new DecoyManager(this, configManager);
        punishmentManager = new PunishmentManager(configManager, this);

        StaffMenuGUI.setPlugin(this);
        paranoiaHandler = new ParanoiaHandler(this, punishmentManager, configManager);

        // Initialize advanced detection engine
        advancedDetectionEngine = new AdvancedDetectionEngine();

        // Initialize integration manager
        integrationManager = new IntegrationManager();

        // Initialize monitoring systems
        metricsCollector = new RealtimeMetricsCollector();
        reportGenerator = new ActivityReportGenerator();

        // Initialize punishment systems
        temporalPunishmentSystem = new TemporalPunishmentSystem();
        compositePunishmentEngine = new CompositePunishmentEngine();

        // Initialize security systems
        dataEncryption = new DataEncryption();

        // Initialize plugin manager
        pluginManager = new PluginManager();
    }

    /**
     * Phase 7: Security and encryption initialization.
     */
    private void initializeSecurity() {
        dataEncryption = new DataEncryption();

        MessageManager.logInit("Security Systems", "Initialized successfully");
    }

    /**
     * Phase 8: Advanced systems initialization.
     */
    private void initializeAdvancedSystems() {
        // Initialize temporal punishment system
        temporalPunishmentSystem = new TemporalPunishmentSystem();

        // Initialize composite punishment engine
        compositePunishmentEngine = new CompositePunishmentEngine();

        // Initialize metrics collector
        metricsCollector = new RealtimeMetricsCollector();

        // Initialize report generator
        reportGenerator = new ActivityReportGenerator();

        // Initialize plugin manager
        pluginManager = new PluginManager();

        MessageManager.logInit("Advanced Systems", "Initialized successfully");
    }

    /**
     * Phase 9: Integrations initialization.
     */
    private void initializeIntegrations() {
        integrationManager = new IntegrationManager();

        // Initialize admin panel if enabled
        initializeAdminPanel();

        MessageManager.logInit("Integration Systems", "Initialized successfully");
    }

    /**
     * Initialize the web admin panel if enabled in config
     */
    private void initializeAdminPanel() {
        boolean adminPanelEnabled = getConfig().getBoolean("admin-panel.enabled", false);

        if (!adminPanelEnabled) {
            MessageManager.logInit("Admin Panel", "Disabled in configuration");
            return;
        }

        try {
            int panelPort = getConfig().getInt("admin-panel.port", 8080);

            MessageManager.logInit("Admin Panel", "Starting on port {PORT}...", "PORT", String.valueOf(panelPort));
            MessageManager.log("warning", "==================================================");
            MessageManager.log("warning", "IMPORTANT: Admin panel may not work on most Minecraft hosting providers!");
            MessageManager.log("warning", "Most hosts do not allow opening additional ports beyond the Minecraft server port.");
            MessageManager.log("warning", "If you're on shared hosting, the admin panel will likely fail to start.");
            MessageManager.log("warning", "Admin panel works best on VPS/Dedicated servers where you control the firewall.");
            MessageManager.log("warning", "==================================================");

            AdminPanel.start(panelPort);

            if (AdminPanel.isEnabled()) {
                MessageManager.logInit("Admin Panel", "Successfully started on port {PORT}", "PORT", String.valueOf(panelPort));
                MessageManager.log("info", "Access admin panel at: http://your-server-ip:{PORT}", "PORT", String.valueOf(panelPort));
            } else {
                MessageManager.log("warning", "Admin panel failed to start - likely port binding issue or hosting restrictions");
            }

        } catch (Exception e) {
            MessageManager.log("error", "Failed to initialize admin panel: {ERROR}", "ERROR", e.getMessage());
            MessageManager.log("warning", "This is expected on most Minecraft hosting providers that restrict port access");
            e.printStackTrace();
        }
    }

    /**
     * Phase 10: Event listeners registration.
     */
    private void registerEventListeners() {
        // Chat listener for punishments
        getServer().getPluginManager().registerEvents(
                new ChatListener(punishmentManager, this),
                this
        );
        // Block listener for mining detection
        getServer().getPluginManager().registerEvents(
                new BlockListener(this, configManager, staffAlertManager, decoyManager, punishmentManager, paranoiaHandler, context),
                this
        );

        // ML v2 Listener
        getServer().getPluginManager().registerEvents(
                new net.denfry.owml.ml.v2.listeners.MLDataListener(this, context.getDetectionPipeline()),
                this
        );

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerStatsGuiListener(), this);
    }

    /**
     * Phase 8: Commands setup.
     */
    private void setupCommands() {
        if (configManager.isCommandHidingEnabled()) {
            setupCommandHiding();
        } else {
            getCommand("OverWatch").setExecutor(new AntiXrayCommand(this, updateChecker));
        }
    }

    /**
     * Sets up command hiding functionality.
     */
    private void setupCommandHiding() {
        CommandHider commandHider = new CommandHider(this);
        getServer().getPluginManager().registerEvents(commandHider, this);

        // Register command hider for all command aliases
        String[] commandNames = {"OverWatch", "owml", "OverWatchx"};
        for (String commandName : commandNames) {
            if (getCommand(commandName) != null) {
                getCommand(commandName).setExecutor(commandHider);
                getCommand(commandName).setTabCompleter(commandHider);
            }
        }

            MessageManager.logInit("Command Hiding", "Feature enabled - commands hidden from unauthorized players");
    }

    /**
     * Phase 9: Auto-save configuration.
     */
    private void configureAutoSave() {
        // Skip auto-save setup on Folia to avoid scheduler issues
        MessageManager.logInit("Auto-Save", "Disabled for Folia compatibility");
    }

    /**
     * Schedule background tasks after plugin is fully enabled
     */
    private void scheduleBackgroundTasks() {
        try {
            // Delay background tasks to ensure scheduler is fully ready (200 ticks = 10 seconds)
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    startAllBackgroundTasks();
                } catch (Exception e) {
                    MessageManager.logStartup("Failed to start background tasks: {ERROR}", "ERROR", e.getMessage());
                }
            }, 200L);
        } catch (UnsupportedOperationException e) {
            // If scheduler is not available even after delay, skip all background tasks for Folia compatibility
            MessageManager.logStartup("Scheduler unavailable - background tasks disabled for Folia compatibility");
        }
    }

    private void startAllBackgroundTasks() {
        // Setup auto-save tasks
        StatsManager.updateAutoSaveSettings();
        SuspiciousManager.updateAutoSaveSettings();
        punishmentManager.updateAutoSaveSettings();

        // Setup punishment cleanup tasks
        punishmentManager.startCleanupTask();
        punishmentManager.startDisabledLevelCheckTask();

        // Setup decoy cleanup task
        decoyManager.scheduleOreTrackerCleanup();

        // Setup paranoia task
        paranoiaHandler.startParanoiaTask();

        // Setup metrics collection
        metricsCollector.startCollectionTasks();

        // Setup temporal punishment cleanup
        temporalPunishmentSystem.startPunishmentCleanupTask();

        // Setup composite punishment cleanup
        compositePunishmentEngine.startCompositeCleanupTask();

        // Setup update checker if enabled
        if (getConfig().getBoolean("check-for-updates", true)) {
            String modrinthId = getConfig().getString("modrinth-id", "ofKCm2tx");
            updateChecker = new UpdateChecker(this, 122967, modrinthId);
            UpdateListener updateListener = new UpdateListener(this, updateChecker);
            getServer().getPluginManager().registerEvents(updateListener, this);
            updateListener.performInitialUpdateCheck();
        }

        MessageManager.logStartup("All background tasks started successfully");
    }

    /**
     * Phase 10: Final initialization steps and startup messages.
     */
    private void finalizeInitialization() {
        ensureCompleteConfig();

        boolean updatePending = updateApplier.checkForPendingUpdate();

        MessageManager.logStartup("Plugin successfully enabled and ready to operate");

        if (updatePending) {
            MessageManager.logStartup("Update pending - will be applied on next server shutdown");
        }
    }

    /**
     * Load language files from plugin data folder.
     */
    private void loadLanguageFiles() {
        // Load all available language files
        File dataFolder = getDataFolder();
        if (dataFolder.exists()) {
            File[] langFiles = dataFolder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
            if (langFiles != null) {
                for (File langFile : langFiles) {
                    String fileName = langFile.getName();
                    String languageCode = fileName.substring(9, fileName.length() - 11); // Remove "messages_" and ".properties"
                    MessageManager.loadLanguageFile(languageCode);
                }
            }
        }

        // Set language from config if available
        String configLanguage = getConfig().getString("language", "en");
        if (!configLanguage.equals("en")) {
            MessageManager.setLanguage(configLanguage);
        }
    }

    /**
     * Initialize the Machine Learning system
     */
    private void initializeMLSystem() {
        MLDataManager.initialize(this);
        MLConfig mlConfig = new MLConfig(this);

        mlManager = new ModernMLManager(this, configManager);

        if (mlConfig.isEnabled()) {
            MessageManager.logInit("ML System", "Detection system enabled");

            if (mlManager.isTrained()) {
                MessageManager.logInit("ML Model", "Successfully loaded with pre-trained data");
            } else {
                MessageManager.logInit("ML Model", "Not trained - use '/OverWatch ml train' to collect training data");
            }

            if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
                try {
                    protocolHandler = new ProtocolHandler(this, mlManager.getDataCollector(), configManager);
                    MessageManager.logInit("ProtocolLib", "Detected - advanced detection features enabled");
                } catch (Exception e) {
                    MessageManager.logInit("ProtocolLib", "Failed to initialize integration: {ERROR}", "ERROR", e.getMessage());
                    e.printStackTrace();
                }
            } else {
                MessageManager.logInit("ProtocolLib", "Not found - advanced protocol features disabled");
            }
        } else {
            MessageManager.logInit("ML System", "Disabled - use '/OverWatch ml enable' to activate");
        }
    }

    @Override
    public void saveConfig() {
        super.saveConfig();

        try {
            File configFile = new File(getDataFolder(), "config.yml");

            if (getConfigManager().isDebugEnabled()) {
                getLogger().info("Forcefully saving config to " + configFile.getAbsolutePath());
            }

            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                saveResource("config.yml", false);
            }
        } catch (Exception e) {
            getLogger().warning("Error ensuring config file exists: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        MessageManager.logStartup("Plugin shutdown initiated");

        try {
            // Save all data
            StatsManager.saveAllData();
            SuspiciousManager.saveAllData();

            // Shutdown ML manager
            if (mlManager != null) {
                mlManager.shutdown();
                MessageManager.logStartup("ML manager shut down successfully");
            }

            // Shutdown protocol handler
            if (protocolHandler != null) {
                protocolHandler.shutdown();
                MessageManager.logStartup("Protocol handler shut down successfully");
            }

            // Shutdown punishment manager
            if (punishmentManager != null) {
                punishmentManager.onDisable();
                MessageManager.logStartup("Punishment manager shut down successfully");
            }

            // Apply pending updates
            if (updateApplier != null && updateApplier.isUpdatePending()) {
                MessageManager.logStartup("Applying pending update during shutdown");
                updateApplier.applyUpdateOnShutdown();
                MessageManager.logStartup("Update applied successfully");
            }

            // Cleanup paranoia handler
            if (paranoiaHandler != null) {
                paranoiaHandler.cleanup();
                MessageManager.logStartup("Paranoia handler cleaned up successfully");
            }

            // Shutdown admin panel
            if (AdminPanel.isEnabled()) {
                AdminPanel.stop();
                MessageManager.logStartup("Admin panel shut down successfully");
            }

            // Shutdown storage system
            if (storageManager != null) {
                storageManager.shutdown();
                MessageManager.logStartup("Storage system shut down successfully");
            }

            // Shutdown async executors
            AsyncExecutor.shutdown();
            MessageManager.logStartup("Async executors shut down successfully");

            MessageManager.logStartup("Plugin shutdown completed successfully");

        } catch (Exception e) {
            MessageManager.logStartup("Error during shutdown: {ERROR}", "ERROR", e.getMessage());
            e.printStackTrace();
        }
    }
    private void ensureCompleteConfig() {
        boolean configUpdated = false;

        configUpdated |= ensureConfigPath("command-hiding.enabled", false);
        configUpdated |= ensureConfigPath("command-hiding.messages.error-line1", "§cUnknown or incomplete command, see below for error");
        configUpdated |= ensureConfigPath("command-hiding.messages.error-line2", "§c§n{command}§r§c§o<--[HERE]");
        configUpdated |= ensureConfigPath("check-for-updates", true);
        configUpdated |= ensureConfigPath("periodic-update-checks.enabled", true);
        configUpdated |= ensureConfigPath("periodic-update-checks.interval-hours", 24);

        if (configUpdated) {
            saveConfig();
            getLogger().info("Added missing configuration options to config.yml");
        }
    }

    private boolean ensureConfigPath(String path, Object defaultValue) {
        if (!getConfig().isSet(path)) {
            getConfig().set(path, defaultValue);
            return true;
        }
        return false;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public ModernMLManager getMLManager() {
        return mlManager;
    }

    public ChatInputHandler getChatInputHandler() {
        return chatInputHandler;
    }

    public WebhookManager getWebhookManager() {
        return WebhookManager.getInstance();
    }

    public AppealManager getAppealManager() {
        return appealManager;
    }

    public OverWatchContext getContext() {
        return context;
    }

    public static OverWatchML getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StaffAlertManager getStaffAlertManager() {
        return staffAlertManager;
    }

    public void setStaffAlertManager(StaffAlertManager staffAlertManager) {
        this.staffAlertManager = staffAlertManager;
    }

    public DecoyManager getDecoyManager() {
        return decoyManager;
    }

    public void setDecoyManager(DecoyManager decoyManager) {
        this.decoyManager = decoyManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public UpdateApplier getUpdateApplier() {
        return updateApplier;
    }

    public boolean isAutoUpdateEnabled() {
        return getConfig().getBoolean("auto-updates.enabled", true);
    }

    /**
     * Get the advanced detection engine instance.
     *
     * @return the advanced detection engine
     */
    public AdvancedDetectionEngine getAdvancedDetectionEngine() {
        return advancedDetectionEngine;
    }

    /**
     * Get the integration manager.
     *
     * @return the integration manager
     */
    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    /**
     * Get the metrics collector.
     *
     * @return the metrics collector
     */
    public RealtimeMetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    /**
     * Get the report generator.
     *
     * @return the report generator
     */
    public ActivityReportGenerator getReportGenerator() {
        return reportGenerator;
    }

    /**
     * Get the temporal punishment system.
     *
     * @return the temporal punishment system
     */
    public TemporalPunishmentSystem getTemporalPunishmentSystem() {
        return temporalPunishmentSystem;
    }

    /**
     * Get the composite punishment engine.
     *
     * @return the composite punishment engine
     */
    public CompositePunishmentEngine getCompositePunishmentEngine() {
        return compositePunishmentEngine;
    }

    /**
     * Get the data encryption system.
     *
     * @return the data encryption system
     */
    public DataEncryption getDataEncryption() {
        return dataEncryption;
    }

    /**
     * Get the plugin manager.
     *
     * @return the plugin manager
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Get the admin panel.
     *
     * @return the admin panel
     */
    public AdminPanel getAdminPanel() {
        return adminPanel;
    }

    /**
     * Get the suspicious manager.
     *
     * @return the suspicious manager
     */
    public SuspiciousManager getSuspiciousManager() {
        // SuspiciousManager is a static utility class, return null for now
        return null;
    }

    /**
     * Get the startup time of the plugin.
     *
     * @return startup time in milliseconds
     */
    public long getStartupTime() {
        return startupTime;
    }

    /**
     * Get suspicion level by player name.
     *
     * @param playerName the player's name
     * @return suspicion level
     */
    public int getSuspicionLevel(String playerName) {
        // Convert player name to UUID - simplified implementation
        // In real implementation, you'd look up the UUID
        return 0;
    }

    /**
     * Get all suspicious players.
     *
     * @return set of suspicious player UUIDs
     */
    public java.util.Set<java.util.UUID> getSuspiciousPlayers() {
        return SuspiciousManager.getSuspiciousPlayersStatic();
    }

    /**
     * Get the stats manager.
     *
     * @return the stats manager instance
     */
    public StatsManager getStatsManager() {
        // StatsManager is a static utility class, return a new instance for API compatibility
        return new StatsManager();
    }
}
