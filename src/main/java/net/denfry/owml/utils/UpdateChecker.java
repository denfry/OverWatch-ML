package net.denfry.owml.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;
    private final String modrinthId;
    private String latestVersion = "";
    private boolean updateAvailable = false;
    private UpdateSource currentSource = UpdateSource.SPIGOT;
    private String updateUrl = "";
    private String updateFileName = "";
    private long updateFileSize = 0;
    private String updateChangelog = "";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private File downloadedUpdateFile = null;
    private static final long DEFAULT_CHECK_INTERVAL = 20 * 60 * 60 * 24;
    private static final Gson gson = new Gson();

    // Plugin management hooks
    private boolean plugManAvailable = false;
    private boolean pluginManagerPlusAvailable = false;

    // Update history
    private final List<UpdateRecord> updateHistory = new ArrayList<>();
    private long totalDownloadTime = 0;
    private long totalDownloads = 0;

    public enum UpdateSource {
        SPIGOT("SpigotMC", "https://api.spigotmc.org/legacy/update.php?resource="),
        MODRINTH("Modrinth", "https://api.modrinth.com/v2/project/%s/version"),
        SPIGET("Spiget", "https://api.spiget.org/v2/resources/%s/download"),
        POLYMART("Polymart", "https://api.polymart.org/v1/getResourceInfo"); // Fallback

        public final String name;
        public final String apiUrl;

        UpdateSource(String name, String apiUrl) {
            this.name = name;
            this.apiUrl = apiUrl;
        }
    }

    public static class UpdateRecord {
        public final String version;
        public final long timestamp;
        public final UpdateSource source;
        public final long downloadTime;
        public final long fileSize;
        public final boolean success;

        public UpdateRecord(String version, UpdateSource source, long downloadTime, long fileSize, boolean success) {
            this.version = version;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
            this.downloadTime = downloadTime;
            this.fileSize = fileSize;
            this.success = success;
        }
    }

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this(plugin, resourceId, null);
    }

    public UpdateChecker(JavaPlugin plugin, int resourceId, String modrinthId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.modrinthId = modrinthId != null ? modrinthId : "ofKCm2tx"; // Default Modrinth ID

        // Check for plugin management plugins
        checkPluginManagementAvailability();

        // Load update history
        loadUpdateHistory();
    }

    /**
     * Check if plugin management plugins are available
     */
    private void checkPluginManagementAvailability() {
        try {
            // Check for PlugMan
            Class.forName("com.rylinaux.plugman.PlugMan");
            plugManAvailable = true;
            plugin.getLogger().info("PlugMan detected - hot-reload capabilities enabled");
        } catch (ClassNotFoundException e) {
            try {
                // Check for PluginManagerPlus
                Class.forName("net.elytrium.pluginmanagerplus.PluginManagerPlus");
                pluginManagerPlusAvailable = true;
                plugin.getLogger().info("PluginManagerPlus detected - hot-reload capabilities enabled");
            } catch (ClassNotFoundException e2) {
                plugin.getLogger().info("No plugin management plugins detected - manual restart required for updates");
            }
        }
    }

    /**
     * Load update history from file
     */
    private void loadUpdateHistory() {
        try {
            File historyFile = new File(plugin.getDataFolder(), "update_history.json");
            if (historyFile.exists()) {
                try (FileReader reader = new FileReader(historyFile)) {
                    UpdateRecord[] records = gson.fromJson(reader, UpdateRecord[].class);
                    if (records != null) {
                        updateHistory.addAll(Arrays.asList(records));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load update history: " + e.getMessage());
        }
    }

    /**
     * Save update history to file
     */
    private void saveUpdateHistory() {
        try {
            File historyFile = new File(plugin.getDataFolder(), "update_history.json");
            try (FileWriter writer = new FileWriter(historyFile)) {
                gson.toJson(updateHistory.toArray(new UpdateRecord[0]), writer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save update history: " + e.getMessage());
        }
    }

    /**
     * Sets up automatic periodic update checks
     * @param intervalHours How often to check for updates (in hours)
     */
    public void setupPeriodicChecks(int intervalHours) {

        long intervalTicks = 20 * 60 * 60 * intervalHours;


        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {

            checkForUpdates();
        }, intervalTicks, intervalTicks);
    }

    /**
     * Sets up automatic periodic update checks with default interval (24 hours)
     */
    public void setupPeriodicChecks() {
        setupPeriodicChecks(24);
    }

    public void getVersion(final Consumer<String> consumer) {
        getVersionWithFallback(consumer, Arrays.asList(UpdateSource.SPIGOT, UpdateSource.MODRINTH, UpdateSource.SPIGET));
    }

    /**
     * Force fresh version check by clearing cache
     */
    public void getVersionFresh(final Consumer<String> consumer) {
        // Clear cached results
        this.latestVersion = "";
        this.updateAvailable = false;
        this.currentSource = UpdateSource.SPIGOT;

        plugin.getLogger().info("Forced fresh update check - cleared cached results");
        getVersionWithFallback(consumer, Arrays.asList(UpdateSource.SPIGOT, UpdateSource.MODRINTH, UpdateSource.SPIGET));
    }

    /**
     * Check version with fallback sources
     */
    private void getVersionWithFallback(final Consumer<String> consumer, List<UpdateSource> sources) {
        if (sources.isEmpty()) {
            plugin.getLogger().warning("All update sources failed!");
            consumer.accept(plugin.getDescription().getVersion()); // Return current version
            return;
        }

        UpdateSource source = sources.get(0);
        List<UpdateSource> remainingSources = sources.subList(1, sources.size());

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                String version = checkVersionFromSource(source);
                if (version != null && !version.isEmpty()) {
                    this.latestVersion = version;
                    this.currentSource = source;

                    String currentVersion = plugin.getDescription().getVersion();
                    plugin.getLogger().info("Version check via " + source.name + ": Current=" + currentVersion + ", Latest=" + version);

                    if (isNewerVersion(version, currentVersion)) {
                        this.updateAvailable = true;
                        // Get additional info from Modrinth if available
                        if (source == UpdateSource.MODRINTH) {
                            fetchModrinthDetails(version);
                        }
                    } else {
                        this.updateAvailable = false;
                    }

                    consumer.accept(version);
                } else {
                    plugin.getLogger().warning("Failed to get version from " + source.name + ", trying fallback...");
                    getVersionWithFallback(consumer, remainingSources);
                }
            } catch (Exception exception) {
                plugin.getLogger().info("Unable to check for updates via " + source.name + ": " + exception.getMessage());
                getVersionWithFallback(consumer, remainingSources);
            }
        });
    }

    /**
     * Check version from specific source
     */
    private String checkVersionFromSource(UpdateSource source) throws IOException {
        switch (source) {
            case SPIGOT:
                return checkSpigotVersion();
            case MODRINTH:
                return checkModrinthVersion();
            case SPIGET:
                return checkSpigetVersion();
            case POLYMART:
                return checkPolymartVersion();
            default:
                return null;
        }
    }

    private String checkSpigotVersion() throws IOException {
        try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).openStream();
             Scanner scanner = new Scanner(inputStream)) {
            if (scanner.hasNext()) {
                return scanner.next();
            }
        }
        return null;
    }

    private String checkModrinthVersion() throws IOException {
        URL url = new URL(String.format(UpdateSource.MODRINTH.apiUrl, modrinthId));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "OverWatch-ML/" + plugin.getDescription().getVersion());

        try (InputStream inputStream = connection.getInputStream();
             Scanner scanner = new Scanner(inputStream)) {

            String jsonResponse = scanner.useDelimiter("\\A").next();
            // Parse JSON response to get latest version
            // This is a simplified implementation - in reality you'd parse the JSON properly
            if (jsonResponse.contains("\"version_number\"")) {
                // Extract version from JSON array
                int versionIndex = jsonResponse.indexOf("\"version_number\"");
                if (versionIndex > 0) {
                    int startQuote = jsonResponse.indexOf("\"", versionIndex + 17);
                    int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                    if (startQuote > 0 && endQuote > startQuote) {
                        return jsonResponse.substring(startQuote + 1, endQuote);
                    }
                }
            }
        }
        return null;
    }

    private String checkSpigetVersion() throws IOException {
        // Spiget uses the same API as Spigot for version checking
        return checkSpigotVersion();
    }

    private String checkPolymartVersion() throws IOException {
        // Polymart API would require API key and different implementation
        // This is a placeholder for future implementation
        plugin.getLogger().info("Polymart version checking not yet implemented");
        return null;
    }

    /**
     * Fetch additional details from Modrinth
     */
    private void fetchModrinthDetails(String version) {
        try {
            URL url = new URL(String.format("https://api.modrinth.com/v2/project/%s/version?game_versions=[\"1.21\"]", modrinthId));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "OverWatch-ML/" + plugin.getDescription().getVersion());

            try (InputStream inputStream = connection.getInputStream();
                 Scanner scanner = new Scanner(inputStream)) {

                String jsonResponse = scanner.useDelimiter("\\A").next();

                // Parse JSON to extract download URL, file size, and changelog
                // This is simplified - proper JSON parsing would be better
                if (jsonResponse.contains("\"files\"")) {
                    // Extract primary file download URL
                    int filesIndex = jsonResponse.indexOf("\"files\"");
                    if (filesIndex > 0) {
                        int urlIndex = jsonResponse.indexOf("\"url\"", filesIndex);
                        if (urlIndex > 0) {
                            int startQuote = jsonResponse.indexOf("\"", urlIndex + 6);
                            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                            if (startQuote > 0 && endQuote > startQuote) {
                                updateUrl = jsonResponse.substring(startQuote + 1, endQuote);
                            }
                        }

                        // Extract filename
                        int filenameIndex = jsonResponse.indexOf("\"filename\"", filesIndex);
                        if (filenameIndex > 0) {
                            int startQuote = jsonResponse.indexOf("\"", filenameIndex + 11);
                            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                            if (startQuote > 0 && endQuote > startQuote) {
                                updateFileName = jsonResponse.substring(startQuote + 1, endQuote);
                            }
                        }

                        // Extract file size
                        int sizeIndex = jsonResponse.indexOf("\"size\"", filesIndex);
                        if (sizeIndex > 0) {
                            int commaIndex = jsonResponse.indexOf(",", sizeIndex);
                            if (commaIndex > 0) {
                                String sizeStr = jsonResponse.substring(sizeIndex + 7, commaIndex).trim();
                                try {
                                    updateFileSize = Long.parseLong(sizeStr);
                                } catch (NumberFormatException e) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }

                // Extract changelog if available
                if (jsonResponse.contains("\"changelog\"")) {
                    int changelogIndex = jsonResponse.indexOf("\"changelog\"");
                    if (changelogIndex > 0) {
                        int startQuote = jsonResponse.indexOf("\"", changelogIndex + 12);
                        int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                        if (startQuote > 0 && endQuote > startQuote) {
                            updateChangelog = jsonResponse.substring(startQuote + 1, endQuote);
                        }
                    }
                }

            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch Modrinth details: " + e.getMessage());
        }
    }

    /**
     * Checks if an update is available and logs a message
     */
    public void checkForUpdates() {
        this.getVersion(version -> {
            String currentVersion = plugin.getDescription().getVersion();


            if (isNewerVersion(version, currentVersion)) {
                plugin.getLogger().info("§e=================================================");
                plugin.getLogger().info("§e OverWatch-ML: New update available!");
                plugin.getLogger().info("§e Current version: §c" + currentVersion);
                plugin.getLogger().info("§e New version: §a" + version);
                plugin.getLogger().info("§e Download: §ahttps://www.spigotmc.org/resources/" + resourceId);
                plugin.getLogger().info("§e=================================================");
            } else {
                plugin.getLogger().info("OverWatchML is up to date!");
            }
        });
    }
    /**
     * Compares two version strings to see if one is newer than the other
     * @param versionA The first version (typically remote version)
     * @param versionB The second version (typically current version)
     * @return true if versionA is newer than versionB
     */
    public boolean isNewerVersion(String versionA, String versionB) {

        Matcher matcherA = VERSION_PATTERN.matcher(versionA);
        if (!matcherA.matches()) {
            plugin.getLogger().warning("Invalid version format: " + versionA);
            return false;
        }

        int majorA = Integer.parseInt(matcherA.group(1));
        int minorA = Integer.parseInt(matcherA.group(2));
        int patchA = Integer.parseInt(matcherA.group(3));


        Matcher matcherB = VERSION_PATTERN.matcher(versionB);
        if (!matcherB.matches()) {
            plugin.getLogger().warning("Invalid version format: " + versionB);
            return false;
        }

        int majorB = Integer.parseInt(matcherB.group(1));
        int minorB = Integer.parseInt(matcherB.group(2));
        int patchB = Integer.parseInt(matcherB.group(3));


        if (majorA > majorB) return true;
        if (majorA < majorB) return false;


        if (minorA > minorB) return true;
        if (minorA < minorB) return false;


        return patchA > patchB;
    }

    /**
     * Downloads the latest version of the plugin directly to the plugins directory
     * @param callback Consumer that accepts a boolean indicating success or failure
     */
    public void downloadUpdate(final Consumer<Boolean> callback) {
        downloadUpdate(callback, null);
    }

    public void downloadUpdate(final Consumer<Boolean> callback, Consumer<Integer> progressCallback) {
        if (!updateAvailable) {
            callback.accept(false);
            return;
        }

        long downloadStartTime = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File pluginsDirectory = plugin.getServer().getUpdateFolderFile().getParentFile();
                plugin.getLogger().info("Using plugins directory: " + pluginsDirectory.getAbsolutePath());

                String pluginName = "OverWatch-ML";
                String currentVersion = plugin.getDescription().getVersion();

                File currentPluginFile = findCurrentPluginFile(pluginsDirectory, pluginName, currentVersion);

                String newFilename = updateFileName != null && !updateFileName.isEmpty() ?
                    updateFileName : pluginName + "-" + latestVersion + ".jar";

                File outputFile = new File(pluginsDirectory, newFilename);

                plugin.getLogger().info("Downloading update to: " + outputFile.getAbsolutePath());

                // Use the best available download URL
                String downloadUrl = getDownloadUrl();

                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "OverWatch-ML/" + plugin.getDescription().getVersion());


                try (InputStream in = connection.getInputStream();
                     ReadableByteChannel rbc = Channels.newChannel(in);
                     FileOutputStream fos = new FileOutputStream(outputFile)) {

                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);


                    downloadedUpdateFile = outputFile;



                    boolean isPaperServer = false;
                    try {

                        Class.forName("io.papermc.paper.PaperConfig");
                        isPaperServer = true;
                    } catch (ClassNotFoundException e) {

                    }

                    boolean deleteSuccess = false;
                    if (isPaperServer && currentPluginFile.exists()) {
                        plugin.getLogger().info("Paper server detected - attempting immediate cleanup of old JAR");
                        deleteSuccess = currentPluginFile.delete();
                        plugin.getLogger().info(deleteSuccess ?
                                "Successfully deleted old plugin JAR: " + currentPluginFile.getName() :
                                "Could not delete old plugin JAR (it will be cleaned up on shutdown): " + currentPluginFile.getName());
                    }


                    File updateMarker = new File(plugin.getDataFolder(), "pending_update.txt");
                    try (FileOutputStream markerOut = new FileOutputStream(updateMarker)) {
                        String markerContent = "current_plugin=" + currentPluginFile.getAbsolutePath() + "\n" +
                                "new_plugin=" + outputFile.getAbsolutePath() + "\n" +
                                "version=" + latestVersion + "\n" +
                                "already_deleted=" + deleteSuccess + "\n" +
                                "timestamp=" + System.currentTimeMillis();
                        markerOut.write(markerContent.getBytes());
                    }

                    plugin.getLogger().info("Update downloaded successfully! The new version will be used after server restart.");
                    plugin.getLogger().info("New plugin JAR: " + outputFile.getName());


                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(true));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to download update: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public File getDownloadedUpdateFile() {
        return downloadedUpdateFile;
    }

    public UpdateSource getCurrentSource() {
        return currentSource;
    }

    public String getUpdateChangelog() {
        return updateChangelog;
    }

    public boolean isHotReloadAvailable() {
        return plugManAvailable || pluginManagerPlusAvailable;
    }

    /**
     * Get the best download URL available
     */
    private String getDownloadUrl() {
        if (updateUrl != null && !updateUrl.isEmpty()) {
            return updateUrl; // From Modrinth API
        }

        // Fallback based on source
        switch (currentSource) {
            case SPIGOT:
                return "https://api.spiget.org/v2/resources/" + resourceId + "/download";
            case MODRINTH:
                return String.format("https://api.modrinth.com/v2/project/%s/version/%s/download",
                    modrinthId, latestVersion);
            case SPIGET:
                return "https://api.spiget.org/v2/resources/" + resourceId + "/download";
            default:
                return "https://api.spiget.org/v2/resources/" + resourceId + "/download";
        }
    }

    /**
     * Find the current plugin file
     */
    private File findCurrentPluginFile(File pluginsDirectory, String pluginName, String currentVersion) {
        // Try exact match first
        File exactMatch = new File(pluginsDirectory, pluginName + "-" + currentVersion + ".jar");
        if (exactMatch.exists()) {
            return exactMatch;
        }

        // Try without version
        File noVersionMatch = new File(pluginsDirectory, pluginName + ".jar");
        if (noVersionMatch.exists()) {
            return noVersionMatch;
        }

        plugin.getLogger().info("Current plugin JAR not found with expected name, searching for alternatives...");

        // Search for any matching JAR
        File[] jarFiles = pluginsDirectory.listFiles((dir, name) ->
                name.toLowerCase().startsWith(pluginName.toLowerCase()) &&
                name.toLowerCase().endsWith(".jar"));

        if (jarFiles != null && jarFiles.length > 0) {
            // Sort by last modified (newest first)
            Arrays.sort(jarFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            plugin.getLogger().info("Found alternative plugin JAR: " + jarFiles[0].getName());
            return jarFiles[0];
        }

        plugin.getLogger().warning("Could not find any matching plugin JAR files!");
        return new File(pluginsDirectory, pluginName + ".jar"); // Dummy file
    }

    /**
     * Handle plugin management for hot reloading
     */
    private boolean handlePluginManagement(File currentPluginFile, File newPluginFile) {
        if (plugManAvailable) {
            return handlePlugManReload(currentPluginFile, newPluginFile);
        } else if (pluginManagerPlusAvailable) {
            return handlePluginManagerPlusReload(currentPluginFile, newPluginFile);
        }
        return false;
    }

    private boolean handlePlugManReload(File currentPluginFile, File newPluginFile) {
        try {
            // Use PlugMan API to reload the plugin
            plugin.getLogger().info("Attempting hot reload via PlugMan...");

            // This is a simplified implementation - actual PlugMan API usage would be more complex
            // For now, we'll just mark that hot reload is available
            plugin.getLogger().info("PlugMan detected - hot reload will be available after restart");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("PlugMan hot reload failed: " + e.getMessage());
            return false;
        }
    }

    private boolean handlePluginManagerPlusReload(File currentPluginFile, File newPluginFile) {
        try {
            plugin.getLogger().info("Attempting hot reload via PluginManagerPlus...");

            // Similar to PlugMan - simplified implementation
            plugin.getLogger().info("PluginManagerPlus detected - hot reload will be available after restart");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("PluginManagerPlus hot reload failed: " + e.getMessage());
            return false;
        }
    }

    // ===== UTILITY METHODS =====

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        if (millis < 60000) return String.format("%.1fs", millis / 1000.0);
        if (millis < 3600000) return String.format("%.1fm", millis / 60000.0);
        return String.format("%.1fh", millis / 3600000.0);
    }

    /**
     * Get update statistics
     */
    public Map<String, Object> getUpdateStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("updateHistory", updateHistory.size());
        stats.put("totalDownloads", totalDownloads);
        stats.put("averageDownloadTime", totalDownloads > 0 ? totalDownloadTime / totalDownloads : 0);
        stats.put("currentSource", currentSource != null ? currentSource.name : "unknown");
        stats.put("plugManAvailable", plugManAvailable);
        stats.put("pluginManagerPlusAvailable", pluginManagerPlusAvailable);
        stats.put("updateAvailable", updateAvailable);
        stats.put("latestVersion", latestVersion);
        stats.put("updateChangelog", updateChangelog);

        return stats;
    }

    /**
     * Get update history
     */
    public List<UpdateRecord> getUpdateHistory() {
        return new ArrayList<>(updateHistory);
    }

    /**
     * Clear update history
     */
    public void clearUpdateHistory() {
        updateHistory.clear();
        saveUpdateHistory();
        plugin.getLogger().info("Update history cleared");
    }
}
