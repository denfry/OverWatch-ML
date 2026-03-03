package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Automatic data cleanup and maintenance system for ML training data.
 * Features:
 * - Time-based cleanup (delete very old data)
 * - Quality-based cleanup (remove low-quality samples)
 * - Size-based cleanup (maintain disk space limits)
 * - Redundancy cleanup (remove unnecessary backups)
 * - Health checks and data integrity validation
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.6
 */
public class DataCleanupManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Cleanup settings
    private static final long MAX_DATA_AGE_MS = 90L * 24 * 60 * 60 * 1000; // 90 days
    private static final long MAX_TOTAL_DATA_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    private static final double MIN_QUALITY_THRESHOLD = 0.3; // Minimum sample quality
    private static final long CLEANUP_INTERVAL = 24 * 60 * 60 * 1000; // Daily cleanup
    private static final int MAX_BACKUP_FILES = 10;

    // Statistics
    private static final AtomicLong filesCleaned = new AtomicLong(0);
    private static final AtomicLong bytesFreed = new AtomicLong(0);
    private static volatile long lastCleanup = 0;

    /**
     * Perform automatic cleanup if needed
     */
    public static void performAutomaticCleanup() {
        long timeSinceLastCleanup = System.currentTimeMillis() - lastCleanup;

        if (timeSinceLastCleanup >= CLEANUP_INTERVAL) {
            performFullCleanup();
        }
    }

    /**
     * Perform full cleanup cycle
     */
    public static void performFullCleanup() {
        lastCleanup = System.currentTimeMillis();
        long startTime = System.currentTimeMillis();

        MessageManager.log("info", "Starting ML data cleanup cycle");

        try {
            // Step 1: Remove old data
            cleanupOldData();

            // Step 2: Remove low-quality data
            cleanupLowQualityData();

            // Step 3: Maintain size limits
            cleanupBySize();

            // Step 4: Clean up redundant backups
            cleanupRedundantBackups();

            // Step 5: Validate data integrity
            validateDataIntegrity();

            // Step 6: Update deduplication
            DataDeduplicator.deduplicateExistingData();

            long duration = System.currentTimeMillis() - startTime;
            MessageManager.log("info", "ML data cleanup completed in {TIME}ms - cleaned {FILES} files, freed {BYTES}MB",
                "TIME", String.valueOf(duration),
                "FILES", String.valueOf(filesCleaned.get()),
                "BYTES", String.valueOf(bytesFreed.get() / (1024 * 1024)));

            // Reset counters for next cycle
            filesCleaned.set(0);
            bytesFreed.set(0);

        } catch (Exception e) {
            MessageManager.log("error", "Error during data cleanup: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Remove data older than the maximum age
     */
    private static void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - MAX_DATA_AGE_MS;

            // Clean compressed data
            Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
            if (Files.exists(compressedPath)) {
                Files.walk(compressedPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                            return attrs.lastModifiedTime().toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(DataCleanupManager::deleteFile);
            }

            // Clean training data
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
                    .forEach(DataCleanupManager::deleteFile);
            }

        } catch (Exception e) {
            MessageManager.log("error", "Error cleaning old data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Remove low-quality training samples
     */
    private static void cleanupLowQualityData() {
        try {
            Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");

            if (!Files.exists(compressedPath)) return;

            Files.walk(compressedPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".dat"))
                .forEach(path -> {
                    try {
                        // Load and check quality
                        Map<String, Object> sample = DataCompressor.loadAndDecompressSample(path);
                        double quality = assessSampleQuality(sample);

                        if (quality < MIN_QUALITY_THRESHOLD) {
                            deleteFile(path);
                        }

                    } catch (Exception e) {
                        // If we can't read the file, it's probably corrupted - delete it
                        deleteFile(path);
                    }
                });

        } catch (Exception e) {
            MessageManager.log("error", "Error cleaning low-quality data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Assess the quality of a training sample
     */
    private static double assessSampleQuality(Map<String, Object> sample) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Double> features = (Map<String, Double>) sample.get("features");
            if (features == null || features.isEmpty()) {
                return 0.0;
            }

            double quality = 0.0;
            int validFeatures = 0;

            // Check for reasonable feature values
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                double value = entry.getValue();

                // Check if value is reasonable (not NaN, not infinite, within reasonable bounds)
                if (!Double.isNaN(value) && !Double.isInfinite(value) &&
                    value >= -1000000 && value <= 1000000) {
                    quality += 1.0;
                    validFeatures++;
                }
            }

            // Bonus for having many features
            if (validFeatures > 10) {
                quality *= 1.2;
            }

            // Penalty for too few features
            if (validFeatures < 3) {
                quality *= 0.5;
            }

            return Math.min(1.0, quality / Math.max(validFeatures, 1));

        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Maintain size limits by removing oldest files
     */
    private static void cleanupBySize() {
        try {
            long totalSize = getTotalDataSize();

            if (totalSize <= MAX_TOTAL_DATA_SIZE) {
                return; // No cleanup needed
            }

            MessageManager.log("info", "Data size ({SIZE}MB) exceeds limit ({LIMIT}MB), performing size-based cleanup",
                "SIZE", String.valueOf(totalSize / (1024 * 1024)),
                "LIMIT", String.valueOf(MAX_TOTAL_DATA_SIZE / (1024 * 1024)));

            // Get all data files sorted by age (oldest first)
            List<Path> allFiles = getAllDataFiles();
            allFiles.sort((a, b) -> {
                try {
                    BasicFileAttributes attrA = Files.readAttributes(a, BasicFileAttributes.class);
                    BasicFileAttributes attrB = Files.readAttributes(b, BasicFileAttributes.class);
                    return Long.compare(attrA.lastModifiedTime().toMillis(), attrB.lastModifiedTime().toMillis());
                } catch (IOException e) {
                    return 0;
                }
            });

            // Delete oldest files until we're under the limit
            long targetSize = (long) (MAX_TOTAL_DATA_SIZE * 0.8); // Leave 20% buffer

            for (Path file : allFiles) {
                if (totalSize <= targetSize) break;

                try {
                    long fileSize = Files.size(file);
                    deleteFile(file);
                    totalSize -= fileSize;
                } catch (IOException e) {
                    // Continue with next file
                }
            }

        } catch (Exception e) {
            MessageManager.log("error", "Error in size-based cleanup: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Clean up redundant backup files
     */
    private static void cleanupRedundantBackups() {
        try {
            Path mlDataPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data");

            // Find backup files (*.bak, *.backup, etc.)
            List<Path> backupFiles = new ArrayList<>();
            if (Files.exists(mlDataPath)) {
                Files.walk(mlDataPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName().toString().toLowerCase();
                        return filename.endsWith(".bak") ||
                               filename.endsWith(".backup") ||
                               filename.endsWith(".old") ||
                               filename.contains("backup");
                    })
                    .forEach(backupFiles::add);
            }

            // Sort by modification time (newest first)
            backupFiles.sort((a, b) -> {
                try {
                    BasicFileAttributes attrA = Files.readAttributes(a, BasicFileAttributes.class);
                    BasicFileAttributes attrB = Files.readAttributes(b, BasicFileAttributes.class);
                    return Long.compare(attrB.lastModifiedTime().toMillis(), attrA.lastModifiedTime().toMillis());
                } catch (IOException e) {
                    return 0;
                }
            });

            // Keep only the most recent backups
            if (backupFiles.size() > MAX_BACKUP_FILES) {
                for (int i = MAX_BACKUP_FILES; i < backupFiles.size(); i++) {
                    deleteFile(backupFiles.get(i));
                }
            }

        } catch (Exception e) {
            MessageManager.log("error", "Error cleaning redundant backups: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Validate data integrity and repair if possible
     */
    private static void validateDataIntegrity() {
        try {
            Path compressedPath = Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed");
            int corruptedFiles = 0;

            if (Files.exists(compressedPath)) {
                for (Path file : Files.newDirectoryStream(compressedPath, "*.dat")) {
                    try {
                        // Try to load the file
                        DataCompressor.loadAndDecompressSample(file);
                    } catch (Exception e) {
                        // File is corrupted - delete it
                        MessageManager.log("warning", "Removing corrupted data file: {FILE}",
                            "FILE", file.getFileName().toString());
                        deleteFile(file);
                        corruptedFiles++;
                    }
                }
            }

            if (corruptedFiles > 0) {
                MessageManager.log("info", "Removed {COUNT} corrupted data files", "COUNT", String.valueOf(corruptedFiles));
            }

        } catch (Exception e) {
            MessageManager.log("error", "Error validating data integrity: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Get all data files
     */
    private static List<Path> getAllDataFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        Path[] dataDirs = {
            Paths.get(plugin.getDataFolder().getPath(), "ml-data", "compressed"),
            Paths.get(plugin.getDataFolder().getPath(), "ml-data", "training"),
            Paths.get(plugin.getDataFolder().getPath(), "ml-data", "analysis")
        };

        for (Path dir : dataDirs) {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
            }
        }

        return files;
    }

    /**
     * Get total size of all data
     */
    private static long getTotalDataSize() throws IOException {
        long total = 0;
        for (Path file : getAllDataFiles()) {
            total += Files.size(file);
        }
        return total;
    }

    /**
     * Delete a file and update statistics
     */
    private static void deleteFile(Path file) {
        try {
            long fileSize = Files.size(file);
            Files.delete(file);
            filesCleaned.incrementAndGet();
            bytesFreed.addAndGet(fileSize);
        } catch (IOException e) {
            MessageManager.log("warning", "Failed to delete file: {FILE}", "FILE", file.toString());
        }
    }

    /**
     * Force immediate cleanup (admin command)
     */
    public static void forceCleanup() {
        MessageManager.log("info", "Forced ML data cleanup initiated by admin");
        performFullCleanup();
    }

    /**
     * Get cleanup statistics
     */
    public static Map<String, Object> getCleanupStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("lastCleanup", lastCleanup);
        stats.put("filesCleanedLastCycle", filesCleaned.get());
        stats.put("bytesFreedLastCycle", bytesFreed.get());
        stats.put("maxDataAgeDays", MAX_DATA_AGE_MS / (24 * 60 * 60 * 1000));
        stats.put("maxTotalDataSizeGB", MAX_TOTAL_DATA_SIZE / (1024.0 * 1024.0 * 1024.0));
        stats.put("minQualityThreshold", MIN_QUALITY_THRESHOLD);

        try {
            stats.put("currentDataSizeMB", getTotalDataSize() / (1024.0 * 1024.0));
        } catch (Exception e) {
            stats.put("currentDataSizeMB", 0.0);
        }

        return stats;
    }
}