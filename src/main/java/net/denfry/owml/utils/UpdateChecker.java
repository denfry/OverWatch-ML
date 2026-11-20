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
import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

public class UpdateChecker {
   private final JavaPlugin plugin;
   private final int resourceId;
   private final String modrinthId;
   private String latestVersion = "";
   private boolean updateAvailable = false;
   private UpdateChecker.UpdateSource currentSource = UpdateChecker.UpdateSource.SPIGOT;
   private String updateUrl = "";
   private String updateFileName = "";
   private long updateFileSize = 0L;
   private String updateChangelog = "";
   private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
   private File downloadedUpdateFile = null;
   private static final long DEFAULT_CHECK_INTERVAL = 1728000L;
   private static final Gson gson = new Gson();
   private boolean plugManAvailable = false;
   private boolean pluginManagerPlusAvailable = false;
   private final List<UpdateChecker.UpdateRecord> updateHistory = new ArrayList<>();
   private long totalDownloadTime = 0L;
   private long totalDownloads = 0L;

   public UpdateChecker(JavaPlugin plugin, int resourceId) {
      this(plugin, resourceId, null);
   }

   public UpdateChecker(JavaPlugin plugin, int resourceId, String modrinthId) {
      this.plugin = plugin;
      this.resourceId = resourceId;
      this.modrinthId = modrinthId != null ? modrinthId : "ofKCm2tx";
      this.checkPluginManagementAvailability();
      this.loadUpdateHistory();
   }

   private void checkPluginManagementAvailability() {
      try {
         Class.forName("com.rylinaux.plugman.PlugMan");
         this.plugManAvailable = true;
         this.plugin.getLogger().info("PlugMan detected - hot-reload capabilities enabled");
      } catch (ClassNotFoundException var4) {
         try {
            Class.forName("net.elytrium.pluginmanagerplus.PluginManagerPlus");
            this.pluginManagerPlusAvailable = true;
            this.plugin.getLogger().info("PluginManagerPlus detected - hot-reload capabilities enabled");
         } catch (ClassNotFoundException var3) {
            this.plugin.getLogger().info("No plugin management plugins detected - manual restart required for updates");
         }
      }
   }

   private void loadUpdateHistory() {
      try {
         File historyFile = new File(this.plugin.getDataFolder(), "update_history.json");
         if (historyFile.exists()) {
            try (FileReader reader = new FileReader(historyFile)) {
               UpdateChecker.UpdateRecord[] records = (UpdateChecker.UpdateRecord[])gson.fromJson(reader, UpdateChecker.UpdateRecord[].class);
               if (records != null) {
                  this.updateHistory.addAll(Arrays.asList(records));
               }
            }
         }
      } catch (Exception var7) {
         this.plugin.getLogger().warning("Failed to load update history: " + var7.getMessage());
      }
   }

   private void saveUpdateHistory() {
      try {
         File historyFile = new File(this.plugin.getDataFolder(), "update_history.json");

         try (FileWriter writer = new FileWriter(historyFile)) {
            gson.toJson(this.updateHistory.toArray(new UpdateChecker.UpdateRecord[0]), writer);
         }
      } catch (Exception var7) {
         this.plugin.getLogger().warning("Failed to save update history: " + var7.getMessage());
      }
   }

   public void setupPeriodicChecks(int intervalHours) {
      long intervalTicks = 72000 * intervalHours;
      Schedulers.asyncTimer(() -> this.checkForUpdates(), intervalTicks, intervalTicks);
   }

   public void setupPeriodicChecks() {
      this.setupPeriodicChecks(24);
   }

   public void getVersion(Consumer<String> consumer) {
      this.getVersionWithFallback(
         consumer, Arrays.asList(UpdateChecker.UpdateSource.SPIGOT, UpdateChecker.UpdateSource.MODRINTH, UpdateChecker.UpdateSource.SPIGET)
      );
   }

   public void getVersionFresh(Consumer<String> consumer) {
      this.latestVersion = "";
      this.updateAvailable = false;
      this.currentSource = UpdateChecker.UpdateSource.SPIGOT;
      this.plugin.getLogger().info("Forced fresh update check - cleared cached results");
      this.getVersionWithFallback(
         consumer, Arrays.asList(UpdateChecker.UpdateSource.SPIGOT, UpdateChecker.UpdateSource.MODRINTH, UpdateChecker.UpdateSource.SPIGET)
      );
   }

   private void getVersionWithFallback(Consumer<String> consumer, List<UpdateChecker.UpdateSource> sources) {
      if (sources.isEmpty()) {
         this.plugin.getLogger().warning("All update sources failed!");
         consumer.accept(this.plugin.getDescription().getVersion());
      } else {
         UpdateChecker.UpdateSource source = sources.get(0);
         List<UpdateChecker.UpdateSource> remainingSources = sources.subList(1, sources.size());
         Schedulers.async(() -> {
            try {
               String version = this.checkVersionFromSource(source);
               if (version != null && !version.isEmpty()) {
                  this.latestVersion = version;
                  this.currentSource = source;
                  String currentVersion = this.plugin.getDescription().getVersion();
                  this.plugin.getLogger().info("Version check via " + source.name + ": Current=" + currentVersion + ", Latest=" + version);
                  if (this.isNewerVersion(version, currentVersion)) {
                     this.updateAvailable = true;
                     if (source == UpdateChecker.UpdateSource.MODRINTH) {
                        this.fetchModrinthDetails(version);
                     }
                  } else {
                     this.updateAvailable = false;
                  }

                  consumer.accept(version);
               } else {
                  this.plugin.getLogger().warning("Failed to get version from " + source.name + ", trying fallback...");
                  this.getVersionWithFallback(consumer, remainingSources);
               }
            } catch (Exception var6) {
               this.plugin.getLogger().info("Unable to check for updates via " + source.name + ": " + var6.getMessage());
               this.getVersionWithFallback(consumer, remainingSources);
            }
         });
      }
   }

   private String checkVersionFromSource(UpdateChecker.UpdateSource source) throws IOException {
      switch (source) {
         case SPIGOT:
            return this.checkSpigotVersion();
         case MODRINTH:
            return this.checkModrinthVersion();
         case SPIGET:
            return this.checkSpigetVersion();
         case POLYMART:
            return this.checkPolymartVersion();
         default:
            return null;
      }
   }

   private String checkSpigotVersion() throws IOException {
      try (
         InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).openStream();
         Scanner scanner = new Scanner(inputStream);
      ) {
         if (scanner.hasNext()) {
            return scanner.next();
         }
      }

      return null;
   }

   private String checkModrinthVersion() throws IOException {
      URL url = new URL(String.format(UpdateChecker.UpdateSource.MODRINTH.apiUrl, this.modrinthId));
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setRequestProperty("User-Agent", "OverWatch-ML/" + this.plugin.getDescription().getVersion());

      try (
         InputStream inputStream = connection.getInputStream();
         Scanner scanner = new Scanner(inputStream);
      ) {
         String jsonResponse = scanner.useDelimiter("\\A").next();
         if (jsonResponse.contains("\"version_number\"")) {
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
      return this.checkSpigotVersion();
   }

   private String checkPolymartVersion() throws IOException {
      this.plugin.getLogger().info("Polymart version checking not yet implemented");
      return null;
   }

   private void fetchModrinthDetails(String version) {
      try {
         URL url = new URL(String.format("https://api.modrinth.com/v2/project/%s/version?game_versions=[\"1.21\"]", this.modrinthId));
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestProperty("User-Agent", "OverWatch-ML/" + this.plugin.getDescription().getVersion());

         try (
            InputStream inputStream = connection.getInputStream();
            Scanner scanner = new Scanner(inputStream);
         ) {
            String jsonResponse = scanner.useDelimiter("\\A").next();
            if (jsonResponse.contains("\"files\"")) {
               int filesIndex = jsonResponse.indexOf("\"files\"");
               if (filesIndex > 0) {
                  int urlIndex = jsonResponse.indexOf("\"url\"", filesIndex);
                  if (urlIndex > 0) {
                     int startQuote = jsonResponse.indexOf("\"", urlIndex + 6);
                     int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                     if (startQuote > 0 && endQuote > startQuote) {
                        this.updateUrl = jsonResponse.substring(startQuote + 1, endQuote);
                     }
                  }

                  int filenameIndex = jsonResponse.indexOf("\"filename\"", filesIndex);
                  if (filenameIndex > 0) {
                     int startQuote = jsonResponse.indexOf("\"", filenameIndex + 11);
                     int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                     if (startQuote > 0 && endQuote > startQuote) {
                        this.updateFileName = jsonResponse.substring(startQuote + 1, endQuote);
                     }
                  }

                  int sizeIndex = jsonResponse.indexOf("\"size\"", filesIndex);
                  if (sizeIndex > 0) {
                     int commaIndex = jsonResponse.indexOf(",", sizeIndex);
                     if (commaIndex > 0) {
                        String sizeStr = jsonResponse.substring(sizeIndex + 7, commaIndex).trim();

                        try {
                           this.updateFileSize = Long.parseLong(sizeStr);
                        } catch (NumberFormatException var16) {
                        }
                     }
                  }
               }
            }

            if (jsonResponse.contains("\"changelog\"")) {
               int changelogIndex = jsonResponse.indexOf("\"changelog\"");
               if (changelogIndex > 0) {
                  int startQuote = jsonResponse.indexOf("\"", changelogIndex + 12);
                  int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                  if (startQuote > 0 && endQuote > startQuote) {
                     this.updateChangelog = jsonResponse.substring(startQuote + 1, endQuote);
                  }
               }
            }
         }
      } catch (Exception var19) {
         this.plugin.getLogger().warning("Failed to fetch Modrinth details: " + var19.getMessage());
      }
   }

   public void checkForUpdates() {
      this.getVersion(version -> {
         String currentVersion = this.plugin.getDescription().getVersion();
         if (this.isNewerVersion(version, currentVersion)) {
            this.plugin.getLogger().info("§e=================================================");
            this.plugin.getLogger().info("§e OverWatch-ML: New update available!");
            this.plugin.getLogger().info("§e Current version: §c" + currentVersion);
            this.plugin.getLogger().info("§e New version: §a" + version);
            this.plugin.getLogger().info("§e Download: §ahttps://www.spigotmc.org/resources/" + this.resourceId);
            this.plugin.getLogger().info("§e=================================================");
         } else {
            this.plugin.getLogger().info("OverWatchML is up to date!");
         }
      });
   }

   public boolean isNewerVersion(String versionA, String versionB) {
      Matcher matcherA = VERSION_PATTERN.matcher(versionA);
      if (!matcherA.matches()) {
         this.plugin.getLogger().warning("Invalid version format: " + versionA);
         return false;
      } else {
         int majorA = Integer.parseInt(matcherA.group(1));
         int minorA = Integer.parseInt(matcherA.group(2));
         int patchA = Integer.parseInt(matcherA.group(3));
         Matcher matcherB = VERSION_PATTERN.matcher(versionB);
         if (!matcherB.matches()) {
            this.plugin.getLogger().warning("Invalid version format: " + versionB);
            return false;
         } else {
            int majorB = Integer.parseInt(matcherB.group(1));
            int minorB = Integer.parseInt(matcherB.group(2));
            int patchB = Integer.parseInt(matcherB.group(3));
            if (majorA > majorB) {
               return true;
            } else if (majorA < majorB) {
               return false;
            } else if (minorA > minorB) {
               return true;
            } else {
               return minorA < minorB ? false : patchA > patchB;
            }
         }
      }
   }

   public void downloadUpdate(Consumer<Boolean> callback) {
      this.downloadUpdate(callback, null);
   }

   public void downloadUpdate(Consumer<Boolean> callback, Consumer<Integer> progressCallback) {
      if (!this.updateAvailable) {
         callback.accept(false);
      } else {
         long downloadStartTime = System.currentTimeMillis();
         Schedulers.async(
            () -> {
               try {
                  File pluginsDirectory = this.plugin.getServer().getUpdateFolderFile().getParentFile();
                  this.plugin.getLogger().info("Using plugins directory: " + pluginsDirectory.getAbsolutePath());
                  String pluginName = "OverWatch-ML";
                  String currentVersion = this.plugin.getDescription().getVersion();
                  File currentPluginFile = this.findCurrentPluginFile(pluginsDirectory, pluginName, currentVersion);
                  String newFilename = this.updateFileName != null && !this.updateFileName.isEmpty()
                     ? this.updateFileName
                     : pluginName + "-" + this.latestVersion + ".jar";
                  File outputFile = new File(pluginsDirectory, newFilename);
                  this.plugin.getLogger().info("Downloading update to: " + outputFile.getAbsolutePath());
                  String downloadUrl = this.getDownloadUrl();
                  URL url = new URL(downloadUrl);
                  HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                  connection.setRequestProperty("User-Agent", "OverWatch-ML/" + this.plugin.getDescription().getVersion());

                  try (
                     InputStream in = connection.getInputStream();
                     ReadableByteChannel rbc = Channels.newChannel(in);
                     FileOutputStream fos = new FileOutputStream(outputFile);
                  ) {
                     fos.getChannel().transferFrom(rbc, 0L, Long.MAX_VALUE);
                     this.downloadedUpdateFile = outputFile;
                     boolean isPaperServer = false;

                     try {
                        Class.forName("io.papermc.paper.PaperConfig");
                        isPaperServer = true;
                     } catch (ClassNotFoundException var25) {
                     }

                     boolean deleteSuccess = false;
                     if (isPaperServer && currentPluginFile.exists()) {
                        this.plugin.getLogger().info("Paper server detected - attempting immediate cleanup of old JAR");
                        deleteSuccess = currentPluginFile.delete();
                        this.plugin
                           .getLogger()
                           .info(
                              deleteSuccess
                                 ? "Successfully deleted old plugin JAR: " + currentPluginFile.getName()
                                 : "Could not delete old plugin JAR (it will be cleaned up on shutdown): " + currentPluginFile.getName()
                           );
                     }

                     File updateMarker = new File(this.plugin.getDataFolder(), "pending_update.txt");

                     try (FileOutputStream markerOut = new FileOutputStream(updateMarker)) {
                        String markerContent = "current_plugin="
                           + currentPluginFile.getAbsolutePath()
                           + "\nnew_plugin="
                           + outputFile.getAbsolutePath()
                           + "\nversion="
                           + this.latestVersion
                           + "\nalready_deleted="
                           + deleteSuccess
                           + "\ntimestamp="
                           + System.currentTimeMillis();
                        markerOut.write(markerContent.getBytes());
                     }

                     this.plugin.getLogger().info("Update downloaded successfully! The new version will be used after server restart.");
                     this.plugin.getLogger().info("New plugin JAR: " + outputFile.getName());
                     Schedulers.global(() -> callback.accept(true));
                  }
               } catch (Exception var29) {
                  this.plugin.getLogger().severe("Failed to download update: " + var29.getMessage());
                  var29.printStackTrace();
                  Schedulers.global(() -> callback.accept(false));
               }
            }
         );
      }
   }

   public boolean isUpdateAvailable() {
      return this.updateAvailable;
   }

   public String getLatestVersion() {
      return this.latestVersion;
   }

   public File getDownloadedUpdateFile() {
      return this.downloadedUpdateFile;
   }

   public UpdateChecker.UpdateSource getCurrentSource() {
      return this.currentSource;
   }

   public String getUpdateChangelog() {
      return this.updateChangelog;
   }

   public boolean isHotReloadAvailable() {
      return this.plugManAvailable || this.pluginManagerPlusAvailable;
   }

   private String getDownloadUrl() {
      if (this.updateUrl != null && !this.updateUrl.isEmpty()) {
         return this.updateUrl;
      } else {
         switch (this.currentSource) {
            case SPIGOT:
               return "https://api.spiget.org/v2/resources/" + this.resourceId + "/download";
            case MODRINTH:
               return String.format("https://api.modrinth.com/v2/project/%s/version/%s/download", this.modrinthId, this.latestVersion);
            case SPIGET:
               return "https://api.spiget.org/v2/resources/" + this.resourceId + "/download";
            default:
               return "https://api.spiget.org/v2/resources/" + this.resourceId + "/download";
         }
      }
   }

   private File findCurrentPluginFile(File pluginsDirectory, String pluginName, String currentVersion) {
      File exactMatch = new File(pluginsDirectory, pluginName + "-" + currentVersion + ".jar");
      if (exactMatch.exists()) {
         return exactMatch;
      } else {
         File noVersionMatch = new File(pluginsDirectory, pluginName + ".jar");
         if (noVersionMatch.exists()) {
            return noVersionMatch;
         } else {
            this.plugin.getLogger().info("Current plugin JAR not found with expected name, searching for alternatives...");
            File[] jarFiles = pluginsDirectory.listFiles(
               (dir, name) -> name.toLowerCase().startsWith(pluginName.toLowerCase()) && name.toLowerCase().endsWith(".jar")
            );
            if (jarFiles != null && jarFiles.length > 0) {
               Arrays.sort(jarFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
               this.plugin.getLogger().info("Found alternative plugin JAR: " + jarFiles[0].getName());
               return jarFiles[0];
            } else {
               this.plugin.getLogger().warning("Could not find any matching plugin JAR files!");
               return new File(pluginsDirectory, pluginName + ".jar");
            }
         }
      }
   }

   private boolean handlePluginManagement(File currentPluginFile, File newPluginFile) {
      if (this.plugManAvailable) {
         return this.handlePlugManReload(currentPluginFile, newPluginFile);
      } else {
         return this.pluginManagerPlusAvailable ? this.handlePluginManagerPlusReload(currentPluginFile, newPluginFile) : false;
      }
   }

   private boolean handlePlugManReload(File currentPluginFile, File newPluginFile) {
      try {
         this.plugin.getLogger().info("Attempting hot reload via PlugMan...");
         this.plugin.getLogger().info("PlugMan detected - hot reload will be available after restart");
         return true;
      } catch (Exception var4) {
         this.plugin.getLogger().warning("PlugMan hot reload failed: " + var4.getMessage());
         return false;
      }
   }

   private boolean handlePluginManagerPlusReload(File currentPluginFile, File newPluginFile) {
      try {
         this.plugin.getLogger().info("Attempting hot reload via PluginManagerPlus...");
         this.plugin.getLogger().info("PluginManagerPlus detected - hot reload will be available after restart");
         return true;
      } catch (Exception var4) {
         this.plugin.getLogger().warning("PluginManagerPlus hot reload failed: " + var4.getMessage());
         return false;
      }
   }

   private String formatFileSize(long bytes) {
      if (bytes < 1024L) {
         return bytes + " B";
      } else if (bytes < 1048576L) {
         return String.format("%.1f KB", bytes / 1024.0);
      } else {
         return bytes < 1073741824L ? String.format("%.1f MB", bytes / 1048576.0) : String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
      }
   }

   private String formatDuration(long millis) {
      if (millis < 1000L) {
         return millis + "ms";
      } else if (millis < 60000L) {
         return String.format("%.1fs", millis / 1000.0);
      } else {
         return millis < 3600000L ? String.format("%.1fm", millis / 60000.0) : String.format("%.1fh", millis / 3600000.0);
      }
   }

   public Map<String, Object> getUpdateStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("updateHistory", this.updateHistory.size());
      stats.put("totalDownloads", this.totalDownloads);
      stats.put("averageDownloadTime", this.totalDownloads > 0L ? this.totalDownloadTime / this.totalDownloads : 0L);
      stats.put("currentSource", this.currentSource != null ? this.currentSource.name : "unknown");
      stats.put("plugManAvailable", this.plugManAvailable);
      stats.put("pluginManagerPlusAvailable", this.pluginManagerPlusAvailable);
      stats.put("updateAvailable", this.updateAvailable);
      stats.put("latestVersion", this.latestVersion);
      stats.put("updateChangelog", this.updateChangelog);
      return stats;
   }

   public List<UpdateChecker.UpdateRecord> getUpdateHistory() {
      return new ArrayList<>(this.updateHistory);
   }

   public void clearUpdateHistory() {
      this.updateHistory.clear();
      this.saveUpdateHistory();
      this.plugin.getLogger().info("Update history cleared");
   }

   public static class UpdateRecord {
      public final String version;
      public final long timestamp;
      public final UpdateChecker.UpdateSource source;
      public final long downloadTime;
      public final long fileSize;
      public final boolean success;

      public UpdateRecord(String version, UpdateChecker.UpdateSource source, long downloadTime, long fileSize, boolean success) {
         this.version = version;
         this.timestamp = System.currentTimeMillis();
         this.source = source;
         this.downloadTime = downloadTime;
         this.fileSize = fileSize;
         this.success = success;
      }
   }

   public static enum UpdateSource {
      SPIGOT("SpigotMC", "https://api.spigotmc.org/legacy/update.php?resource="),
      MODRINTH("Modrinth", "https://api.modrinth.com/v2/project/%s/version"),
      SPIGET("Spiget", "https://api.spiget.org/v2/resources/%s/download"),
      POLYMART("Polymart", "https://api.polymart.org/v1/getResourceInfo");

      public final String name;
      public final String apiUrl;

      private UpdateSource(String name, String apiUrl) {
         this.name = name;
         this.apiUrl = apiUrl;
      }
   }
}
