package net.denfry.owml.web;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.post;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.api.OverWatchMLAPI;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.PerformanceMonitor;
import spark.Spark;

/**
 * Web-based administrative panel for OverWatch-ML.
 * Provides REST API and web interface for plugin management.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class AdminPanel {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static boolean enabled = false;
    private static int port = 8080;

    /**
     * Start the admin panel web server.
     */
    public static void start(int serverPort) {
        if (enabled) {
            MessageManager.logDashboard("START_ATTEMPT", "Panel already running on port {PORT}", "PORT", String.valueOf(port));
            return;
        }

        port = serverPort;
        MessageManager.logDashboard("STARTING", "Initializing admin panel on port {PORT}", "PORT", String.valueOf(port));
        enabled = true;

        try {
            Spark.port(port);
            MessageManager.logDashboard("CONFIG", "Setting server port to {PORT}", "PORT", String.valueOf(port));

            // Enable CORS for web interface
            enableCORS();
            MessageManager.logDashboard("CONFIG", "CORS enabled for web interface");

            // Setup routes
            setupRoutes();
            MessageManager.logDashboard("CONFIG", "API routes configured");

            // Setup static file serving
            setupStaticFiles();
            MessageManager.logDashboard("CONFIG", "Static file serving configured");

            MessageManager.logDashboard("STARTED", "Admin panel successfully started and listening on port {PORT}", "PORT", String.valueOf(port));

        } catch (Exception e) {
            MessageManager.logDashboard("START_FAILED", "Failed to start admin panel: {ERROR}", "ERROR", e.getMessage());
            enabled = false;
        }
    }

    /**
     * Stop the admin panel.
     */
    public static void stop() {
        if (!enabled) {
            MessageManager.logDashboard("STOP_ATTEMPT", "Panel is not running");
            return;
        }

        MessageManager.logDashboard("STOPPING", "Shutting down admin panel on port {PORT}", "PORT", String.valueOf(port));
        Spark.stop();
        Spark.awaitStop();
        enabled = false;
        MessageManager.logDashboard("STOPPED", "Admin panel successfully stopped");
    }

    /**
     * Check if admin panel is running.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the server port.
     */
    public static int getPort() {
        return port;
    }

    /**
     * Enable CORS for web interface.
     */
    private static void enableCORS() {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            response.type("application/json");
        });
    }

    /**
     * Setup API routes.
     */
    private static void setupRoutes() {
        // Dashboard data
        get("/api/dashboard", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Dashboard data requested from {IP}", "IP", req.ip());
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("server", getServerInfo());
            dashboard.put("performance", getPerformanceInfo());
            dashboard.put("plugin", getPluginInfo());
            dashboard.put("players", getPlayerInfo());
            MessageManager.logDashboard("API_RESPONSE", "Dashboard data sent (server: {SERVERS}, players: {PLAYERS})",
                "SERVERS", String.valueOf(Bukkit.getWorlds().size()),
                "PLAYERS", String.valueOf(Bukkit.getOnlinePlayers().size()));
            return gson.toJson(dashboard);
        });

        // Players management
        get("/api/players", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Players data requested from {IP}", "IP", req.ip());
            Map<String, Object> players = new HashMap<>();
            players.put("online", getOnlinePlayers());
            players.put("suspicious", getSuspiciousPlayers());
            players.put("totalTracked", getTotalTrackedPlayers());
            int onlineCount = Bukkit.getOnlinePlayers().size();
            int suspiciousCount = getSuspiciousPlayers().size();
            MessageManager.logDashboard("API_RESPONSE", "Players data sent (online: {ONLINE}, suspicious: {SUSPICIOUS})",
                "ONLINE", String.valueOf(onlineCount),
                "SUSPICIOUS", String.valueOf(suspiciousCount));
            return gson.toJson(players);
        });

        // Settings management
        get("/api/settings", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Settings data requested from {IP}", "IP", req.ip());
            Map<String, Object> settings = getSettings();
            MessageManager.logDashboard("API_RESPONSE", "Settings data sent ({COUNT} settings)", "COUNT", String.valueOf(settings.size()));
            return gson.toJson(settings);
        });

        post("/api/settings", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Settings update requested from {IP}", "IP", req.ip());
            try {
                // Parse JSON settings and apply them
                Map<String, Object> settings = gson.fromJson(req.body(), Map.class);
                applySettings(settings);
                MessageManager.logDashboard("API_SUCCESS", "Settings updated successfully ({COUNT} settings changed)", "COUNT", String.valueOf(settings.size()));
                return gson.toJson(Map.of("success", true, "message", "Settings updated"));
            } catch (Exception e) {
                MessageManager.logDashboard("API_ERROR", "Failed to update settings: {ERROR}", "ERROR", e.getMessage());
                return gson.toJson(Map.of("success", false, "message", "Failed to update settings"));
            }
        });

        // Statistics
        get("/api/statistics", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Statistics data requested from {IP}", "IP", req.ip());
            Map<String, Object> stats = getDetailedStatistics();
            MessageManager.logDashboard("API_RESPONSE", "Statistics data sent");
            return gson.toJson(stats);
        });

        // Commands
        post("/api/commands/:command", (req, res) -> {
            String command = req.params(":command");
            MessageManager.logDashboard("API_ACCESS", "Command execution requested: {COMMAND} from {IP}", "COMMAND", command, "IP", req.ip());
            Map<String, Object> params = gson.fromJson(req.body(), Map.class);
            Map<String, Object> result = executeCommand(command, params);
            boolean success = (Boolean) result.getOrDefault("success", false);
            MessageManager.logDashboard(success ? "API_SUCCESS" : "API_WARNING", "Command {COMMAND} executed ({STATUS})",
                "COMMAND", command, "STATUS", success ? "success" : "failed");
            return gson.toJson(result);
        });

        // Logs
        get("/api/logs", (req, res) -> {
            MessageManager.logDashboard("API_ACCESS", "Logs data requested from {IP}", "IP", req.ip());
            Map<String, Object> logs = getRecentLogs();
            MessageManager.logDashboard("API_RESPONSE", "Logs data sent");
            return gson.toJson(logs);
        });
    }

    /**
     * Setup static file serving for web interface.
     */
    private static void setupStaticFiles() {
        // Serve static HTML/CSS/JS files
        // In a real implementation, you would include web files in resources
        get("/", (req, res) -> {
            MessageManager.logDashboard("WEB_ACCESS", "Dashboard page accessed from {IP}", "IP", req.ip());
            res.type("text/html");
            return getHtmlDashboard();
        });

        get("/static/*", (req, res) -> {
            // Serve static files from resources/static/
            String path = req.splat()[0];
            // Implementation would load from resources
            return "Static file: " + path;
        });
    }

    // ===== DATA PROVIDERS =====

    private static Map<String, Object> getServerInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", Bukkit.getVersion());
        info.put("bukkitVersion", Bukkit.getBukkitVersion());
        info.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        info.put("maxPlayers", Bukkit.getMaxPlayers());
        info.put("uptime", System.currentTimeMillis() - plugin.getStartupTime());
        info.put("worlds", Bukkit.getWorlds().size());
        return info;
    }

    private static Map<String, Object> getPerformanceInfo() {
        PerformanceMonitor.PerformanceStats stats = PerformanceMonitor.getStats();
        Map<String, Object> perf = new HashMap<>();
        perf.put("tps", stats.tps);
        perf.put("mspt", stats.mspt);
        perf.put("memoryUsed", stats.memoryUsedMB);
        perf.put("memoryMax", stats.memoryMaxMB);
        perf.put("memoryPercent", (double) stats.memoryUsedMB / stats.memoryMaxMB * 100);
        perf.put("pluginTPSImpact", PerformanceMonitor.getEstimatedPluginTPSImpact());
        perf.put("isDegraded", PerformanceMonitor.isPerformanceDegraded());
        return perf;
    }

    private static Map<String, Object> getPluginInfo() {
        OverWatchMLAPI api = OverWatchMLAPI.getInstance();
        Map<String, Object> pluginInfo = new HashMap<>();
        pluginInfo.put("version", api != null ? api.getVersion() : "Unknown");
        pluginInfo.put("enabled", plugin.isEnabled());
        pluginInfo.put("mlEnabled", api != null && api.isMachineLearningEnabled());
        pluginInfo.put("disabledWorlds", api != null ? api.getDisabledWorlds() : Set.of());
        return pluginInfo;
    }

    private static Map<String, Object> getPlayerInfo() {
        Map<String, Object> players = new HashMap<>();
        players.put("online", Bukkit.getOnlinePlayers().size());
        players.put("max", Bukkit.getMaxPlayers());

        OverWatchMLAPI api = OverWatchMLAPI.getInstance();
        if (api != null) {
            players.put("suspicious", api.getSuspicionLevel("total")); // This would need to be implemented
        }

        return players;
    }

    private static Map<String, Object> getOnlinePlayers() {
        Map<String, Object> players = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("name", player.getName());
            playerInfo.put("uuid", player.getUniqueId().toString());
            playerInfo.put("world", player.getWorld().getName());
            playerInfo.put("health", player.getHealth());
            playerInfo.put("level", player.getLevel());

            OverWatchMLAPI api = OverWatchMLAPI.getInstance();
            if (api != null) {
                playerInfo.put("suspicious", api.isPlayerSuspicious(player.getUniqueId()));
                playerInfo.put("suspicionLevel", api.getPlayerSuspicionLevel(player.getUniqueId()));
                playerInfo.put("punishmentLevel", api.getPlayerPunishmentLevel(player.getUniqueId()));
            }

            players.put(player.getUniqueId().toString(), playerInfo);
        }
        return players;
    }

    private static Set<String> getSuspiciousPlayers() {
        OverWatchMLAPI api = OverWatchMLAPI.getInstance();
        if (api != null) {
            return api.getSuspiciousPlayers().stream()
                    .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                    .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private static int getTotalTrackedPlayers() {
        // This would need to be implemented in the stats system
        return 0;
    }

    private static Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("debug", plugin.getConfigManager().isDebugEnabled());
        settings.put("mlEnabled", plugin.getConfig().getBoolean("ml.enabled", true));
        settings.put("worldDisabling", plugin.getConfigManager().isWorldDisablingEnabled());
        settings.put("disabledWorlds", plugin.getConfigManager().getDisabledWorlds());
        settings.put("oreThreshold", plugin.getConfigManager().getOreThreshold());
        settings.put("autoSaveInterval", plugin.getConfig().getInt("stats.autoSave.intervalMinutes", 10));
        return settings;
    }

    private static void applySettings(Map<String, Object> settings) {
        try {
            // Apply settings to configuration
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Handle boolean values
                if (value instanceof Boolean) {
                    plugin.getConfig().set(key, value);
                }
                // Handle numeric values
                else if (value instanceof Number) {
                    plugin.getConfig().set(key, ((Number) value).intValue());
                }
                // Handle lists
                else if (value instanceof java.util.List) {
                    plugin.getConfig().set(key, value);
                }
                // Handle strings
                else if (value instanceof String) {
                    plugin.getConfig().set(key, value);
                }
            }

            plugin.saveConfig();
            plugin.reloadConfig();

            MessageManager.logDashboard("SETTINGS_UPDATED", "Settings updated via web panel: {COUNT} settings changed",
                "COUNT", String.valueOf(settings.size()));

        } catch (Exception e) {
            MessageManager.logDashboard("SETTINGS_ERROR", "Failed to apply settings: {ERROR}", "ERROR", e.getMessage());
            throw e;
        }
    }

    private static Map<String, Object> getDetailedStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("performance", getPerformanceInfo());
        stats.put("operations", getOperationStats());
        stats.put("alerts", getAlertStats());
        stats.put("ml", getMLStats());
        return stats;
    }

    private static Map<String, Object> getOperationStats() {
        PerformanceMonitor.PerformanceStats perfStats = PerformanceMonitor.getStats();
        Map<String, Object> ops = new HashMap<>();
        ops.put("blockBreakChecks", perfStats.blockBreakChecks);
        ops.put("mlAnalysisRuns", perfStats.mlAnalysisRuns);
        ops.put("alertTriggers", perfStats.alertTriggers);
        ops.put("dataSaveOperations", perfStats.dataSaveOperations);
        return ops;
    }

    private static Map<String, Object> getAlertStats() {
        Map<String, Object> alerts = new HashMap<>();
        // This would need to be implemented to track alert statistics
        alerts.put("total", 0);
        alerts.put("today", 0);
        alerts.put("byType", Map.of());
        return alerts;
    }

    private static Map<String, Object> getMLStats() {
        Map<String, Object> ml = new HashMap<>();
        OverWatchMLAPI api = OverWatchMLAPI.getInstance();
        ml.put("enabled", api != null && api.isMachineLearningEnabled());
        ml.put("trained", plugin.getMLManager() != null && plugin.getMLManager().isTrained());
        // Add more ML statistics here
        return ml;
    }

    private static Map<String, Object> executeCommand(String command, Map<String, Object> params) {
        // This would implement command execution through the admin panel
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Command execution not implemented yet");
        return result;
    }

    private static Map<String, Object> getRecentLogs() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> logEntries = new ArrayList<>();
        for (WebLogHandler.LogEntry entry : WebLogHandler.getLogs()) {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("timestamp", entry.timestamp);
            logMap.put("level", entry.level);
            logMap.put("message", entry.message);
            logEntries.add(logMap);
        }

        result.put("entries", logEntries);
        return result;
    }

    private static String getHtmlDashboard() {
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OverWatchML Admin Panel</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: #333;
                        min-height: 100vh;
                    }
                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    header {
                        background: rgba(255,255,255,0.95);
                        padding: 20px 30px;
                        border-radius: 15px;
                        margin-bottom: 20px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                    }
                    h1 { color: #667eea; display: inline-block; }
                    .nav {
                        float: right;
                        margin-top: 10px;
                    }
                    .nav button {
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 10px 20px;
                        margin-left: 10px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 14px;
                        transition: all 0.3s;
                    }
                    .nav button:hover { background: #5568d3; transform: translateY(-2px); }
                    .nav button.active { background: #764ba2; }
                    .content {
                        background: rgba(255,255,255,0.95);
                        padding: 30px;
                        border-radius: 15px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                        min-height: 600px;
                    }
                    .tab-content { display: none; }
                    .tab-content.active { display: block; }
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 20px;
                        margin-top: 20px;
                    }
                    .card {
                        background: white;
                        padding: 20px;
                        border-radius: 12px;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                        transition: transform 0.3s;
                    }
                    .card:hover { transform: translateY(-5px); }
                    .card h3 { color: #667eea; margin-bottom: 15px; font-size: 18px; }
                    .stat { margin: 10px 0; font-size: 14px; }
                    .stat strong { color: #764ba2; }
                    .status-good { color: #10b981; font-weight: bold; }
                    .status-warn { color: #f59e0b; font-weight: bold; }
                    .status-bad { color: #ef4444; font-weight: bold; }
                    .setting-group {
                        background: #f9fafb;
                        padding: 20px;
                        margin: 15px 0;
                        border-radius: 10px;
                        border-left: 4px solid #667eea;
                    }
                    .setting-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin: 15px 0;
                        padding: 10px;
                        background: white;
                        border-radius: 8px;
                    }
                    .setting-item label { font-weight: 600; color: #374151; }
                    .setting-item input[type="checkbox"] {
                        width: 50px;
                        height: 25px;
                        cursor: pointer;
                    }
                    .setting-item input[type="number"],
                    .setting-item input[type="text"] {
                        padding: 8px 12px;
                        border: 2px solid #e5e7eb;
                        border-radius: 6px;
                        font-size: 14px;
                        width: 200px;
                    }
                    .btn {
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 12px 25px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 14px;
                        transition: all 0.3s;
                        margin: 10px 5px;
                    }
                    .btn:hover { background: #5568d3; transform: translateY(-2px); }
                    .btn-danger { background: #ef4444; }
                    .btn-danger:hover { background: #dc2626; }
                    .btn-success { background: #10b981; }
                    .btn-success:hover { background: #059669; }
                    .player-list {
                        background: white;
                        border-radius: 10px;
                        overflow: hidden;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .player-item {
                        padding: 15px 20px;
                        border-bottom: 1px solid #e5e7eb;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        transition: background 0.2s;
                    }
                    .player-item:hover { background: #f9fafb; }
                    .player-info { flex: 1; }
                    .player-name { font-weight: bold; color: #667eea; }
                    .player-stats { font-size: 12px; color: #6b7280; margin-top: 5px; }
                    .log-container {
                        background: #1f2937;
                        color: #e5e7eb;
                        padding: 20px;
                        border-radius: 10px;
                        font-family: 'Courier New', monospace;
                        font-size: 13px;
                        max-height: 500px;
                        overflow-y: auto;
                    }
                    .log-entry {
                        margin: 5px 0;
                        padding: 5px;
                        border-left: 3px solid #667eea;
                        padding-left: 10px;
                    }
                    .log-info { border-left-color: #3b82f6; }
                    .log-warn { border-left-color: #f59e0b; background: rgba(245,158,11,0.1); }
                    .log-error { border-left-color: #ef4444; background: rgba(239,68,68,0.1); }
                    .notification {
                        position: fixed;
                        top: 20px;
                        right: 20px;
                        background: #10b981;
                        color: white;
                        padding: 15px 25px;
                        border-radius: 10px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.3);
                        display: none;
                        z-index: 1000;
                    }
                    .notification.show { display: block; animation: slideIn 0.3s; }
                    @keyframes slideIn {
                        from { transform: translateX(400px); }
                        to { transform: translateX(0); }
                    }
                </style>
            </head>
            <body>
                <div class="notification" id="notification"></div>
                <div class="container">
                    <header>
                        <h1>СЂСџвЂєРЋРїС‘РЏ OverWatchML Admin Panel</h1>
                        <div class="nav">
                            <button onclick="showTab('dashboard')" class="active" id="tab-dashboard">СЂСџвЂњР‰ Dashboard</button>
                            <button onclick="showTab('players')" id="tab-players">СЂСџвЂТђ Players</button>
                            <button onclick="showTab('settings')" id="tab-settings">РІС™в„ўРїС‘РЏ Settings</button>
                            <button onclick="showTab('logs')" id="tab-logs">СЂСџвЂњСњ Logs</button>
                        </div>
                        <div style="clear:both;"></div>
                    </header>

                    <div class="content">
                        <!-- Dashboard Tab -->
                        <div id="dashboard-content" class="tab-content active">
                            <h2>Server Overview</h2>
                            <div class="grid">
                                <div class="card">
                                    <h3>СЂСџвЂњР‰ Performance</h3>
                                    <div id="performance">Loading...</div>
                                </div>
                                <div class="card">
                                    <h3>СЂСџвЂТђ Players</h3>
                                    <div id="player-stats">Loading...</div>
                                </div>
                                <div class="card">
                                    <h3>СЂСџвЂќРЊ Detection Stats</h3>
                                    <div id="detection-stats">Loading...</div>
                                </div>
                                <div class="card">
                                    <h3>СЂСџВ¤вЂ“ ML System</h3>
                                    <div id="ml-stats">Loading...</div>
                                </div>
                            </div>
                        </div>

                        <!-- Players Tab -->
                        <div id="players-content" class="tab-content">
                            <h2>Online & Suspicious Players</h2>
                            <div class="player-list" id="player-list">
                                <div style="padding:20px;text-align:center;color:#6b7280;">Loading players...</div>
                            </div>
                        </div>

                        <!-- Settings Tab -->
                        <div id="settings-content" class="tab-content">
                            <h2>Plugin Settings</h2>
                            <div id="settings-form">Loading settings...</div>
                            <button class="btn btn-success" onclick="saveSettings()">СЂСџвЂ™С• Save Settings</button>
                            <button class="btn" onclick="reloadSettings()">СЂСџвЂќвЂћ Reload from Config</button>
                        </div>

                        <!-- Logs Tab -->
                        <div id="logs-content" class="tab-content">
                            <h2>Recent Logs</h2>
                            <button class="btn" onclick="refreshLogs()">СЂСџвЂќвЂћ Refresh</button>
                            <button class="btn" onclick="clearLogs()">СЂСџвЂ”вЂРїС‘РЏ Clear</button>
                            <div class="log-container" id="log-container">
                                <div class="log-entry log-info">Waiting for logs...</div>
                            </div>
                        </div>
                    </div>
                </div>

                <script>
                    let currentTab = 'dashboard';
                    let settings = {};

                    function showTab(tab) {
                        // Update buttons
                        document.querySelectorAll('.nav button').forEach(b => b.classList.remove('active'));
                        document.getElementById('tab-' + tab).classList.add('active');

                        // Update content
                        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                        document.getElementById(tab + '-content').classList.add('active');

                        currentTab = tab;

                        // Load data for tab
                        if (tab === 'players') loadPlayers();
                        if (tab === 'settings') loadSettings();
                        if (tab === 'logs') refreshLogs();
                    }

                    async function loadDashboard() {
                        try {
                            const response = await fetch('/api/dashboard');
                            const data = await response.json();

                            // Performance
                            const perf = data.performance;
                            document.getElementById('performance').innerHTML = `
                                <div class="stat">TPS: <span class="${perf.tps > 18 ? 'status-good' : perf.tps > 15 ? 'status-warn' : 'status-bad'}">${perf.tps.toFixed(1)}</span></div>
                                <div class="stat">MSPT: <strong>${perf.mspt}ms</strong></div>
                                <div class="stat">Memory: <strong>${perf.memoryUsedMB}/${perf.memoryMaxMB}MB</strong> (${perf.memoryPercent.toFixed(1)}%)</div>
                                <div class="stat">Plugin Impact: <strong>${perf.pluginTPSImpact.toFixed(2)}%</strong></div>
                            `;

                            // Players
                            const players = data.players;
                            document.getElementById('player-stats').innerHTML = `
                                <div class="stat">Online: <strong>${players.online}/${players.max}</strong></div>
                                <div class="stat">Suspicious: <span class="status-warn">${players.suspicious || 0}</span></div>
                            `;

                            // Detection stats
                            document.getElementById('detection-stats').innerHTML = `
                                <div class="stat">Block Checks: <strong>${perf.blockBreakChecks || 0}</strong></div>
                                <div class="stat">ML Analysis: <strong>${perf.mlAnalysisRuns || 0}</strong></div>
                                <div class="stat">Alerts: <strong>${perf.alertTriggers || 0}</strong></div>
                            `;

                            // ML Stats
                            const plugin = data.plugin;
                            document.getElementById('ml-stats').innerHTML = `
                                <div class="stat">Status: ${plugin.mlEnabled ? '<span class="status-good">Enabled РІСљвЂњ</span>' : '<span class="status-bad">Disabled РІСљвЂ”</span>'}</div>
                                <div class="stat">Version: <strong>${plugin.version}</strong></div>
                                <div class="stat">Disabled Worlds: <strong>${plugin.disabledWorlds.length}</strong></div>
                            `;

                        } catch (error) {
                            console.error('Failed to load dashboard:', error);
                        }
                    }

                    async function loadPlayers() {
                        try {
                            const response = await fetch('/api/players');
                            const data = await response.json();

                            const playerList = document.getElementById('player-list');
                            if (!data.online || Object.keys(data.online).length === 0) {
                                playerList.innerHTML = '<div style="padding:20px;text-align:center;color:#6b7280;">No players online</div>';
                                return;
                            }

                            let html = '';
                            for (const [uuid, player] of Object.entries(data.online)) {
                                const suspicious = player.suspicious ? 'status-warn' : 'status-good';
                                html += `
                                    <div class="player-item">
                                        <div class="player-info">
                                            <div class="player-name">${player.name}</div>
                                            <div class="player-stats">
                                                World: ${player.world} | Health: ${player.health.toFixed(1)} | Level: ${player.level}
                                                ${player.suspicious ? ' | <span class="status-warn">РІС™В РїС‘РЏ Suspicious</span>' : ''}
                                            </div>
                                        </div>
                                        <div>
                                            <button class="btn" onclick="viewPlayerDetails('${uuid}')">View Stats</button>
                                        </div>
                                    </div>
                                `;
                            }
                            playerList.innerHTML = html;

                        } catch (error) {
                            console.error('Failed to load players:', error);
                        }
                    }

                    async function loadSettings() {
                        try {
                            const response = await fetch('/api/settings');
                            settings = await response.json();

                            const form = document.getElementById('settings-form');
                            form.innerHTML = `
                                <div class="setting-group">
                                    <h3>СЂСџвЂќРЊ Detection Settings</h3>
                                    <div class="setting-item">
                                        <label>Debug Mode</label>
                                        <input type="checkbox" id="debug" ${settings.debug ? 'checked' : ''}>
                                    </div>
                                    <div class="setting-item">
                                        <label>ML Detection Enabled</label>
                                        <input type="checkbox" id="mlEnabled" ${settings.mlEnabled ? 'checked' : ''}>
                                    </div>
                                    <div class="setting-item">
                                        <label>Ore Detection Threshold</label>
                                        <input type="number" id="oreThreshold" value="${settings.oreThreshold || 10}" min="1" max="100">
                                    </div>
                                </div>

                                <div class="setting-group">
                                    <h3>СЂСџРЉРЊ World Settings</h3>
                                    <div class="setting-item">
                                        <label>World Disabling Enabled</label>
                                        <input type="checkbox" id="worldDisabling" ${settings.worldDisabling ? 'checked' : ''}>
                                    </div>
                                    <div class="setting-item">
                                        <label>Disabled Worlds (comma-separated)</label>
                                        <input type="text" id="disabledWorlds" value="${settings.disabledWorlds ? settings.disabledWorlds.join(', ') : ''}" style="width:400px;">
                                    </div>
                                </div>

                                <div class="setting-group">
                                    <h3>СЂСџвЂ™С• Auto-Save Settings</h3>
                                    <div class="setting-item">
                                        <label>Auto-Save Interval (minutes)</label>
                                        <input type="number" id="autoSaveInterval" value="${settings.autoSaveInterval || 10}" min="1" max="60">
                                    </div>
                                </div>
                            `;

                        } catch (error) {
                            console.error('Failed to load settings:', error);
                        }
                    }

                    async function saveSettings() {
                        try {
                            const newSettings = {
                                'debug': document.getElementById('debug').checked,
                                'ml.enabled': document.getElementById('mlEnabled').checked,
                                'ore-threshold': parseInt(document.getElementById('oreThreshold').value),
                                'world-disabling.enabled': document.getElementById('worldDisabling').checked,
                                'world-disabling.worlds': document.getElementById('disabledWorlds').value.split(',').map(w => w.trim()).filter(w => w),
                                'stats.autoSave.intervalMinutes': parseInt(document.getElementById('autoSaveInterval').value)
                            };

                            const response = await fetch('/api/settings', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(newSettings)
                            });

                            const result = await response.json();
                            showNotification(result.success ? 'РІСљвЂњ Settings saved successfully!' : 'РІСљвЂ” Failed to save settings');

                        } catch (error) {
                            console.error('Failed to save settings:', error);
                            showNotification('РІСљвЂ” Error saving settings');
                        }
                    }

                    function reloadSettings() {
                        loadSettings();
                        showNotification('Settings reloaded from config');
                    }

                    async function refreshLogs() {
                        try {
                            const response = await fetch('/api/logs');
                            const data = await response.json();

                            const container = document.getElementById('log-container');
                            if (!data.entries || data.entries.length === 0) {
                                container.innerHTML = '<div class="log-entry log-info">No logs available</div>';
                                return;
                            }

                            let html = '';
                            for (const entry of data.entries) {
                                const levelClass = entry.level.toLowerCase();
                                const time = new Date(entry.timestamp).toLocaleTimeString();
                                html += `<div class="log-entry log-${levelClass}">[${time}] [${entry.level}] ${entry.message}</div>`;
                            }
                            container.innerHTML = html;
                            container.scrollTop = container.scrollHeight;

                        } catch (error) {
                            console.error('Failed to load logs:', error);
                        }
                    }

                    function clearLogs() {
                        document.getElementById('log-container').innerHTML = '<div class="log-entry log-info">Logs cleared</div>';
                    }

                    function viewPlayerDetails(uuid) {
                        showNotification('Player details feature coming soon!');
                    }

                    function showNotification(message) {
                        const notif = document.getElementById('notification');
                        notif.textContent = message;
                        notif.classList.add('show');
                        setTimeout(() => notif.classList.remove('show'), 3000);
                    }

                    // Auto-refresh dashboard
                    setInterval(() => {
                        if (currentTab === 'dashboard') loadDashboard();
                    }, 5000);

                    // Initial load
                    loadDashboard();
                </script>
            </body>
            </html>
            """;
    }
}
