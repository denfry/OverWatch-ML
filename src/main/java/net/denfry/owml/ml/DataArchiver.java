package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Intelligent data archival system for ML training data.
 * Features:
 * - Time-based archival (old data -> archives)
 * - Size-based archival (when data directory gets too large)
 * - Compressed archives with metadata
 * - Automatic archive cleanup
 * - Archive indexing for quick access
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.6
 */
public class DataArchiver {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Archive settings
    private static final String ARCHIVE_DIR = "archives";
    private static final String INDEX_FILE = "archive_index.json";
    private static final long MAX_ACTIVE_DATA_SIZE = 500 * 1024 * 1024; // 500MB
    private static final long ARCHIVE_OLDER_THAN_DAYS = 30; // Archive data older than 30 days
    private static final long MAX_ARCHIVE_SIZE = 100 * 1024 * 1024; // 100MB per archive
    private static final int MAX_ARCHIVES_TO_KEEP = 50;

    // Archive index
    private static final Map<String, ArchiveInfo> archiveIndex = new HashMap<>();
    private static final AtomicLong totalArchivedBytes = new AtomicLong(0);

    // Date formatter for archive naming
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Check if archival is needed and perform it
     */
    public static void checkAndArchiveData() {
        try {
            long activeDataSize = getActiveDataSize();
            long oldestDataAge = getOldestDataAge();

            boolean needsArchival = activeDataSize > MAX_ACTIVE_DATA_SIZE ||
                                   oldestDataAge > (ARCHIVE_OLDER_THAN_DAYS * 24 * 60 * 60 * 1000);

            if (needsArchival) {
                performArchival();
            }

        } catch (Exception e) {
            MessageManager.log("error", "Error during archival check: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Perform data archival
     */
    private static void performArchival() {
        MessageManager.log("info", "Starting data archival process");

        try {
            // Create archive directory
            Path archivePath = getArchiveDirectory();
            Files.createDirectories(archivePath);

            // Find files to archive
            List<Path> filesToArchive = findFilesToArchive();

            if (filesToArchive.isEmpty()) {
                MessageManager.log("info", "No files found for archival");
                return;
            }

            // Create archive
            String archiveName = "archive_" + LocalDateTime.now().format(DATE_FORMAT) + ".zip";
            Path archiveFile = archivePath.resolve(archiveName);

            createArchive(filesToArchive, archiveFile);

            // Update index
            ArchiveInfo archiveInfo = new ArchiveInfo(archiveName, filesToArchive.size(),
                LocalDateTime.now(), Files.size(archiveFile));
            archiveIndex.put(archiveName, archiveInfo);

            // Save index
            saveArchiveIndex();

            // Clean up old archives
            cleanupOldArchives();

            MessageManager.log("info", "Successfully archived {COUNT} files into {ARCHIVE}",
                "COUNT", String.valueOf(filesToArchive.size()), "ARCHIVE", archiveName);

        } catch (Exception e) {
            MessageManager.log("error", "Error during archival: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Find files that should be archived
     */
    private static List<Path> findFilesToArchive() throws IOException {
        List<Path> filesToArchive = new ArrayList<>();
        long cutoffTime = System.currentTimeMillis() - (ARCHIVE_OLDER_THAN_DAYS * 24 * 60 * 60 * 1000);

        // Check compressed data directory
        Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
        if (Files.exists(compressedPath)) {
            Files.walk(compressedPath)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        // Check file age
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return attrs.lastModifiedTime().toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(filesToArchive::add);
        }

        // Check training data directory (uncompressed old files)
        Path trainingPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "training");
        if (Files.exists(trainingPath)) {
            Files.walk(trainingPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return attrs.lastModifiedTime().toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(filesToArchive::add);
        }

        return filesToArchive;
    }

    /**
     * Create a compressed archive from files
     */
    private static void createArchive(List<Path> files, Path archiveFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
            // Set compression level
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);

            long totalSize = 0;
            for (Path file : files) {
                if (totalSize + Files.size(file) > MAX_ARCHIVE_SIZE) {
                    MessageManager.log("warning", "Archive size limit reached, stopping archival");
                    break;
                }

                // Add file to archive
                ZipEntry entry = new ZipEntry(getRelativePath(file));
                zos.putNextEntry(entry);

                // Copy file content
                try (InputStream is = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }

                zos.closeEntry();

                // Delete original file
                Files.delete(file);
                totalSize += Files.size(file);
                totalArchivedBytes.addAndGet(Files.size(file));
            }
        }
    }

    /**
     * Get relative path for archive entry
     */
    private static String getRelativePath(Path file) {
        Path mlDataPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data");
        return mlDataPath.relativize(file).toString().replace('\\', '/');
    }

    /**
     * Get the size of active (non-archived) data
     */
    private static long getActiveDataSize() throws IOException {
        long totalSize = 0;

        // Sum sizes of active directories
        Path[] activeDirs = {
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

    /**
     * Get the age of oldest data file
     */
    private static long getOldestDataAge() throws IOException {
        long oldestTime = System.currentTimeMillis();

        Path[] activeDirs = {
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

    /**
     * Get directory size recursively
     */
    private static long getDirectorySize(Path dir) throws IOException {
        return Files.walk(dir)
            .filter(Files::isRegularFile)
            .mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            })
            .sum();
    }

    /**
     * Get timestamp of oldest file in directory
     */
    private static long getOldestFileTime(Path dir) throws IOException {
        return Files.walk(dir)
            .filter(Files::isRegularFile)
            .mapToLong(path -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    return attrs.lastModifiedTime().toMillis();
                } catch (IOException e) {
                    return System.currentTimeMillis();
                }
            })
            .min()
            .orElse(System.currentTimeMillis());
    }

    /**
     * Save archive index to disk
     */
    private static void saveArchiveIndex() {
        try {
            Path indexPath = getArchiveDirectory().resolve(INDEX_FILE);

            // Create JSON index
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"archives\": [\n");

            boolean first = true;
            for (ArchiveInfo info : archiveIndex.values()) {
                if (!first) json.append(",\n");
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

        } catch (Exception e) {
            MessageManager.log("error", "Failed to save archive index: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Load archive index from disk
     */
    public static void loadArchiveIndex() {
        try {
            Path indexPath = getArchiveDirectory().resolve(INDEX_FILE);
            if (!Files.exists(indexPath)) {
                return;
            }

            String json = Files.readString(indexPath);
            // Simple JSON parsing (could be improved with a proper JSON library)
            parseArchiveIndex(json);

        } catch (Exception e) {
            MessageManager.log("error", "Failed to load archive index: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Simple JSON parser for archive index
     */
    private static void parseArchiveIndex(String json) {
        // Simplified parsing - extract archive info from JSON
        String[] lines = json.split("\n");
        ArchiveInfo currentArchive = null;

        for (String line : lines) {
            line = line.trim();
            if (line.contains("\"name\":")) {
                if (currentArchive != null) {
                    archiveIndex.put(currentArchive.name, currentArchive);
                }
                String name = line.split(":")[1].trim().replaceAll("\"", "").replaceAll(",", "");
                currentArchive = new ArchiveInfo(name, 0, LocalDateTime.now(), 0);
            } else if (line.contains("\"fileCount\":")) {
                if (currentArchive != null) {
                    String countStr = line.split(":")[1].trim().replaceAll(",", "");
                    currentArchive.fileCount = Integer.parseInt(countStr);
                }
            } else if (line.contains("\"size\":")) {
                if (currentArchive != null) {
                    String sizeStr = line.split(":")[1].trim().replaceAll(",", "").replaceAll("}", "");
                    currentArchive.size = Long.parseLong(sizeStr);
                }
            }
        }

        if (currentArchive != null) {
            archiveIndex.put(currentArchive.name, currentArchive);
        }
    }

    /**
     * Clean up old archives to prevent disk space overflow
     */
    private static void cleanupOldArchives() {
        if (archiveIndex.size() <= MAX_ARCHIVES_TO_KEEP) {
            return;
        }

        try {
            // Sort archives by creation date (oldest first)
            List<ArchiveInfo> sortedArchives = new ArrayList<>(archiveIndex.values());
            sortedArchives.sort((a, b) -> a.created.compareTo(b.created));

            // Remove oldest archives
            int toRemove = sortedArchives.size() - MAX_ARCHIVES_TO_KEEP;
            for (int i = 0; i < toRemove; i++) {
                ArchiveInfo archive = sortedArchives.get(i);

                // Delete archive file
                Path archivePath = getArchiveDirectory().resolve(archive.name);
                if (Files.exists(archivePath)) {
                    Files.delete(archivePath);
                }

                // Remove from index
                archiveIndex.remove(archive.name);
            }

            if (toRemove > 0) {
                saveArchiveIndex();
                MessageManager.log("info", "Cleaned up {COUNT} old archives", "COUNT", String.valueOf(toRemove));
            }

        } catch (Exception e) {
            MessageManager.log("error", "Failed to cleanup old archives: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Get archive directory path
     */
    private static Path getArchiveDirectory() {
        return Paths.get(plugin.getDataFolder().getPath(), "ml-data", ARCHIVE_DIR);
    }

    /**
     * Get archival statistics
     */
    public static Map<String, Object> getArchivalStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalArchives", archiveIndex.size());
        stats.put("totalArchivedBytes", totalArchivedBytes.get());

        long totalArchiveSize = archiveIndex.values().stream()
            .mapToLong(archive -> archive.size)
            .sum();
        stats.put("totalArchiveSize", totalArchiveSize);

        int totalArchivedFiles = archiveIndex.values().stream()
            .mapToInt(archive -> archive.fileCount)
            .sum();
        stats.put("totalArchivedFiles", totalArchivedFiles);

        return stats;
    }

    /**
     * Archive information
     */
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