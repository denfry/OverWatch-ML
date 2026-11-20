package net.denfry.owml.ml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class DataArchiver {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private static final String ARCHIVE_DIR = "archives";
   private static final String INDEX_FILE = "archive_index.json";
   private static final long MAX_ACTIVE_DATA_SIZE = 524288000L;
   private static final long ARCHIVE_OLDER_THAN_DAYS = 30L;
   private static final long MAX_ARCHIVE_SIZE = 104857600L;
   private static final int MAX_ARCHIVES_TO_KEEP = 50;
   private static final Map<String, DataArchiver.ArchiveInfo> archiveIndex = new ConcurrentHashMap<>();
   private static final AtomicLong totalArchivedBytes = new AtomicLong(0L);
   private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

   public static void checkAndArchiveData() {
      try {
         long activeDataSize = getActiveDataSize();
         long oldestDataAge = getOldestDataAge();
         boolean needsArchival = activeDataSize > 524288000L || oldestDataAge > 2592000000L;
         if (needsArchival) {
            performArchival();
         }
      } catch (Exception var5) {
         MessageManager.log("error", "Error during archival check: {ERROR}", "ERROR", var5.getMessage());
      }
   }

   private static void performArchival() {
      MessageManager.log("info", "Starting data archival process");

      try {
         Path archivePath = getArchiveDirectory();
         Files.createDirectories(archivePath);
         List<Path> filesToArchive = findFilesToArchive();
         if (filesToArchive.isEmpty()) {
            MessageManager.log("info", "No files found for archival");
            return;
         }

         String archiveName = "archive_" + LocalDateTime.now().format(DATE_FORMAT) + ".zip";
         Path archiveFile = archivePath.resolve(archiveName);
         createArchive(filesToArchive, archiveFile);
         DataArchiver.ArchiveInfo archiveInfo = new DataArchiver.ArchiveInfo(archiveName, filesToArchive.size(), LocalDateTime.now(), Files.size(archiveFile));
         archiveIndex.put(archiveName, archiveInfo);
         saveArchiveIndex();
         cleanupOldArchives();
         MessageManager.log(
            "info", "Successfully archived {COUNT} files into {ARCHIVE}", "COUNT", String.valueOf(filesToArchive.size()), "ARCHIVE", archiveName
         );
      } catch (Exception var5) {
         MessageManager.log("error", "Error during archival: {ERROR}", "ERROR", var5.getMessage());
      }
   }

   private static List<Path> findFilesToArchive() throws IOException {
      List<Path> filesToArchive = new ArrayList<>();
      long cutoffTime = System.currentTimeMillis() - 2592000000L;
      Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
      if (Files.exists(compressedPath)) {
         Files.walk(compressedPath).filter(x$0 -> Files.isRegularFile(x$0)).filter(path -> {
            try {
               BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
               return attrs.lastModifiedTime().toMillis() < cutoffTime;
            } catch (IOException var4x) {
               return false;
            }
         }).forEach(filesToArchive::add);
      }

      Path trainingPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "training");
      if (Files.exists(trainingPath)) {
         Files.walk(trainingPath).filter(x$0 -> Files.isRegularFile(x$0)).filter(path -> path.toString().endsWith(".json")).filter(path -> {
            try {
               BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
               return attrs.lastModifiedTime().toMillis() < cutoffTime;
            } catch (IOException var4x) {
               return false;
            }
         }).forEach(filesToArchive::add);
      }

      return filesToArchive;
   }

   private static void createArchive(List<Path> files, Path archiveFile) throws IOException {
      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
         zos.setLevel(9);
         long totalSize = 0L;

         for (Path file : files) {
            if (totalSize + Files.size(file) > 104857600L) {
               MessageManager.log("warning", "Archive size limit reached, stopping archival");
               break;
            }

            ZipEntry entry = new ZipEntry(getRelativePath(file));
            zos.putNextEntry(entry);

            try (InputStream is = Files.newInputStream(file)) {
               byte[] buffer = new byte[8192];

               int bytesRead;
               while ((bytesRead = is.read(buffer)) != -1) {
                  zos.write(buffer, 0, bytesRead);
               }
            }

            zos.closeEntry();
            Files.delete(file);
            totalSize += Files.size(file);
            totalArchivedBytes.addAndGet(Files.size(file));
         }
      }
   }

   private static String getRelativePath(Path file) {
      Path mlDataPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data");
      return mlDataPath.relativize(file).toString().replace('\\', '/');
   }

   private static long getActiveDataSize() throws IOException {
      long totalSize = 0L;
      Path[] activeDirs = new Path[]{
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed"),
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "training"),
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "analysis")
      };

      for (Path dir : activeDirs) {
         if (Files.exists(dir)) {
            totalSize += getDirectorySize(dir);
         }
      }

      return totalSize;
   }

   private static long getOldestDataAge() throws IOException {
      long oldestTime = System.currentTimeMillis();
      Path[] activeDirs = new Path[]{
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed"),
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "training"),
         Paths.get(plugin.getDataFolder().getPath(), "ml-data", "analysis")
      };

      for (Path dir : activeDirs) {
         if (Files.exists(dir)) {
            long dirOldest = getOldestFileTime(dir);
            oldestTime = Math.min(oldestTime, dirOldest);
         }
      }

      return System.currentTimeMillis() - oldestTime;
   }

   private static long getDirectorySize(Path dir) throws IOException {
      return Files.walk(dir).filter(x$0 -> Files.isRegularFile(x$0)).mapToLong(path -> {
         try {
            return Files.size(path);
         } catch (IOException var2) {
            return 0L;
         }
      }).sum();
   }

   private static long getOldestFileTime(Path dir) throws IOException {
      return Files.walk(dir).filter(x$0 -> Files.isRegularFile(x$0)).mapToLong(path -> {
         try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return attrs.lastModifiedTime().toMillis();
         } catch (IOException var2) {
            return System.currentTimeMillis();
         }
      }).min().orElse(System.currentTimeMillis());
   }

   private static void saveArchiveIndex() {
      try {
         Path indexPath = getArchiveDirectory().resolve("archive_index.json");
         StringBuilder json = new StringBuilder();
         json.append("{\n");
         json.append("  \"archives\": [\n");
         boolean first = true;

         for (DataArchiver.ArchiveInfo info : archiveIndex.values()) {
            if (!first) {
               json.append(",\n");
            }

            json.append("    {\n");
            json.append("      \"name\": \"").append(info.name).append("\",\n");
            json.append("      \"fileCount\": ").append(info.fileCount).append(",\n");
            json.append("      \"created\": \"").append(info.created.format(DATE_FORMAT)).append("\",\n");
            json.append("      \"size\": ").append(info.size).append("\n");
            json.append("    }");
            first = false;
         }

         json.append("\n  ]\n");
         json.append("}\n");
         Files.writeString(indexPath, json.toString());
      } catch (Exception var5) {
         MessageManager.log("error", "Failed to save archive index: {ERROR}", "ERROR", var5.getMessage());
      }
   }

   public static void loadArchiveIndex() {
      try {
         Path indexPath = getArchiveDirectory().resolve("archive_index.json");
         if (!Files.exists(indexPath)) {
            return;
         }

         String json = Files.readString(indexPath);
         parseArchiveIndex(json);
      } catch (Exception var2) {
         MessageManager.log("error", "Failed to load archive index: {ERROR}", "ERROR", var2.getMessage());
      }
   }

   private static void parseArchiveIndex(String json) {
      String[] lines = json.split("\n");
      DataArchiver.ArchiveInfo currentArchive = null;

      for (String line : lines) {
         line = line.trim();
         if (line.contains("\"name\":")) {
            if (currentArchive != null) {
               archiveIndex.put(currentArchive.name, currentArchive);
            }

            String name = line.split(":")[1].trim().replaceAll("\"", "").replaceAll(",", "");
            currentArchive = new DataArchiver.ArchiveInfo(name, 0, LocalDateTime.now(), 0L);
         } else if (line.contains("\"fileCount\":")) {
            if (currentArchive != null) {
               String countStr = line.split(":")[1].trim().replaceAll(",", "");
               currentArchive.fileCount = Integer.parseInt(countStr);
            }
         } else if (line.contains("\"size\":") && currentArchive != null) {
            String sizeStr = line.split(":")[1].trim().replaceAll(",", "").replaceAll("}", "");
            currentArchive.size = Long.parseLong(sizeStr);
         }
      }

      if (currentArchive != null) {
         archiveIndex.put(currentArchive.name, currentArchive);
      }
   }

   private static void cleanupOldArchives() {
      if (archiveIndex.size() > 50) {
         try {
            List<DataArchiver.ArchiveInfo> sortedArchives = new ArrayList<>(archiveIndex.values());
            sortedArchives.sort((a, b) -> a.created.compareTo(b.created));
            int toRemove = sortedArchives.size() - 50;

            for (int i = 0; i < toRemove; i++) {
               DataArchiver.ArchiveInfo archive = sortedArchives.get(i);
               Path archivePath = getArchiveDirectory().resolve(archive.name);
               if (Files.exists(archivePath)) {
                  Files.delete(archivePath);
               }

               archiveIndex.remove(archive.name);
            }

            if (toRemove > 0) {
               saveArchiveIndex();
               MessageManager.log("info", "Cleaned up {COUNT} old archives", "COUNT", String.valueOf(toRemove));
            }
         } catch (Exception var5) {
            MessageManager.log("error", "Failed to cleanup old archives: {ERROR}", "ERROR", var5.getMessage());
         }
      }
   }

   private static Path getArchiveDirectory() {
      return Paths.get(plugin.getDataFolder().getPath(), "ml-data", "archives");
   }

   public static Map<String, Object> getArchivalStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("totalArchives", archiveIndex.size());
      stats.put("totalArchivedBytes", totalArchivedBytes.get());
      long totalArchiveSize = archiveIndex.values().stream().mapToLong(archive -> archive.size).sum();
      stats.put("totalArchiveSize", totalArchiveSize);
      int totalArchivedFiles = archiveIndex.values().stream().mapToInt(archive -> archive.fileCount).sum();
      stats.put("totalArchivedFiles", totalArchivedFiles);
      return stats;
   }

   public static class ArchiveInfo {
      public final String name;
      public int fileCount;
      public final LocalDateTime created;
      public long size;

      public ArchiveInfo(String name, int fileCount, LocalDateTime created, long size) {
         this.name = name;
         this.fileCount = fileCount;
         this.created = created;
         this.size = size;
      }
   }
}
