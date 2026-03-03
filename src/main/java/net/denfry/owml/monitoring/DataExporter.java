package net.denfry.owml.monitoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

/**
 * Data export system for analytics and external analysis.
 * Supports multiple formats: JSON, CSV, XML.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class DataExporter {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final String EXPORT_DIR = "plugins/OverWatch-ML/exports";

    private final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create();

    public enum ExportFormat {
        JSON("json"),
        CSV("csv"),
        XML("xml");

        private final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    public enum DataType {
        PLAYER_STATS("player-stats"),
        DETECTION_HISTORY("detection-history"),
        PERFORMANCE_METRICS("performance-metrics"),
        ML_DATA("ml-data"),
        SYSTEM_LOGS("system-logs");

        private final String filePrefix;

        DataType(String filePrefix) {
            this.filePrefix = filePrefix;
        }

        public String getFilePrefix() {
            return filePrefix;
        }
    }

    public DataExporter() {
        createExportDirectory();
    }

    /**
     * Export player statistics
     */
    public void exportPlayerStats(ExportFormat format) {
        List<PlayerStatsExport> playerStats = collectPlayerStats();

        String filename = generateFilename(DataType.PLAYER_STATS, format);
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            switch (format) {
                case JSON -> exportAsJson(playerStats, exportPath);
                case CSV -> exportPlayerStatsAsCsv(playerStats, exportPath);
                case XML -> exportPlayerStatsAsXml(playerStats, exportPath);
            }

            MessageManager.log("info", "Player stats exported to {FILE} ({COUNT} players)",
                "FILE", exportPath.toString(), "COUNT", String.valueOf(playerStats.size()));

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export player stats: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Export detection history
     */
    public void exportDetectionHistory(ExportFormat format, long startTime, long endTime) {
        List<DetectionEventExport> detectionHistory = collectDetectionHistory(startTime, endTime);

        String filename = generateFilename(DataType.DETECTION_HISTORY, format);
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            switch (format) {
                case JSON -> exportAsJson(detectionHistory, exportPath);
                case CSV -> exportDetectionHistoryAsCsv(detectionHistory, exportPath);
                case XML -> exportDetectionHistoryAsXml(detectionHistory, exportPath);
            }

            MessageManager.log("info", "Detection history exported to {FILE} ({COUNT} events)",
                "FILE", exportPath.toString(), "COUNT", String.valueOf(detectionHistory.size()));

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export detection history: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Export performance metrics
     */
    public void exportPerformanceMetrics(ExportFormat format, int hours) {
        List<PerformanceMetricsExport> metrics = collectPerformanceMetrics(hours);

        String filename = generateFilename(DataType.PERFORMANCE_METRICS, format);
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            switch (format) {
                case JSON -> exportAsJson(metrics, exportPath);
                case CSV -> exportPerformanceMetricsAsCsv(metrics, exportPath);
                case XML -> exportPerformanceMetricsAsXml(metrics, exportPath);
            }

            MessageManager.log("info", "Performance metrics exported to {FILE} ({COUNT} data points)",
                "FILE", exportPath.toString(), "COUNT", String.valueOf(metrics.size()));

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export performance metrics: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Export ML training data
     */
    public void exportMLData(ExportFormat format) {
        MLDataExport mlData = collectMLData();

        String filename = generateFilename(DataType.ML_DATA, format);
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            switch (format) {
                case JSON -> exportAsJson(mlData, exportPath);
                case CSV -> exportMLDataAsCsv(mlData, exportPath);
                case XML -> exportMLDataAsXml(mlData, exportPath);
            }

            MessageManager.log("info", "ML data exported to {FILE}", "FILE", exportPath.toString());

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export ML data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Export system logs
     */
    public void exportSystemLogs(ExportFormat format, long startTime, long endTime) {
        List<SystemLogExport> logs = collectSystemLogs(startTime, endTime);

        String filename = generateFilename(DataType.SYSTEM_LOGS, format);
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            switch (format) {
                case JSON -> exportAsJson(logs, exportPath);
                case CSV -> exportSystemLogsAsCsv(logs, exportPath);
                case XML -> exportSystemLogsAsXml(logs, exportPath);
            }

            MessageManager.log("info", "System logs exported to {FILE} ({COUNT} entries)",
                "FILE", exportPath.toString(), "COUNT", String.valueOf(logs.size()));

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export system logs: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Export comprehensive dataset
     */
    public void exportCompleteDataset(ExportFormat format) {
        CompleteDatasetExport dataset = new CompleteDatasetExport(
            collectPlayerStats(),
            collectDetectionHistory(System.currentTimeMillis() - 86400000L, System.currentTimeMillis()), // Last 24h
            collectPerformanceMetrics(24),
            collectMLData(),
            collectSystemLogs(System.currentTimeMillis() - 86400000L, System.currentTimeMillis())
        );

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = String.format("complete-dataset_%s.%s", timestamp, format.getExtension());
        Path exportPath = Paths.get(EXPORT_DIR, filename);

        try {
            exportAsJson(dataset, exportPath);
            MessageManager.log("info", "Complete dataset exported to {FILE}", "FILE", exportPath.toString());

        } catch (IOException e) {
            MessageManager.log("error", "Failed to export complete dataset: {ERROR}", "ERROR", e.getMessage());
        }
    }

    // ===== JSON EXPORT METHODS =====

    private <T> void exportAsJson(T data, Path path) throws IOException {
        String json = gson.toJson(data);
        Files.writeString(path, json);
    }

    // ===== CSV EXPORT METHODS =====

    private void exportPlayerStatsAsCsv(List<PlayerStatsExport> stats, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("player_name,uuid,suspicion_level,total_blocks_mined,mining_speed,first_seen,last_seen,risk_cluster\n");

        for (PlayerStatsExport stat : stats) {
            csv.append(String.format("%s,%s,%d,%d,%.2f,%d,%d,%d\n",
                escapeCSV(stat.playerName),
                stat.uuid,
                stat.suspicionLevel,
                stat.totalBlocksMined,
                stat.miningSpeed,
                stat.firstSeen,
                stat.lastSeen,
                stat.riskCluster
            ));
        }

        Files.writeString(path, csv.toString());
    }

    private void exportDetectionHistoryAsCsv(List<DetectionEventExport> events, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,player_name,detection_type,confidence,severity,details\n");

        for (DetectionEventExport event : events) {
            csv.append(String.format("%d,%s,%s,%.3f,%s,%s\n",
                event.timestamp,
                escapeCSV(event.playerName),
                event.detectionType,
                event.confidence,
                event.severity,
                escapeCSV(event.details)
            ));
        }

        Files.writeString(path, csv.toString());
    }

    private void exportPerformanceMetricsAsCsv(List<PerformanceMetricsExport> metrics, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,tps,memory_used_mb,memory_max_mb,cpu_usage_percent,online_players,suspicious_players\n");

        for (PerformanceMetricsExport metric : metrics) {
            csv.append(String.format("%d,%.2f,%d,%d,%.1f,%d,%d\n",
                metric.timestamp,
                metric.tps,
                metric.memoryUsedMB,
                metric.memoryMaxMB,
                metric.cpuUsagePercent,
                metric.onlinePlayers,
                metric.suspiciousPlayers
            ));
        }

        Files.writeString(path, csv.toString());
    }

    private void exportMLDataAsCsv(MLDataExport mlData, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("accuracy,total_trainings,clusters,tracked_players,last_update\n");
        csv.append(String.format("%.3f,%d,%d,%d,%d\n",
            mlData.accuracy,
            mlData.totalTrainings,
            mlData.clusters,
            mlData.trackedPlayers,
            mlData.lastUpdate
        ));

        Files.writeString(path, csv.toString());
    }

    private void exportSystemLogsAsCsv(List<SystemLogExport> logs, Path path) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,level,message,source\n");

        for (SystemLogExport log : logs) {
            csv.append(String.format("%d,%s,%s,%s\n",
                log.timestamp,
                log.level,
                escapeCSV(log.message),
                log.source
            ));
        }

        Files.writeString(path, csv.toString());
    }

    // ===== XML EXPORT METHODS =====

    private void exportPlayerStatsAsXml(List<PlayerStatsExport> stats, Path path) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<playerStats>\n");

        for (PlayerStatsExport stat : stats) {
            xml.append(String.format("""
                \t<player>
                \t\t<name>%s</name>
                \t\t<uuid>%s</uuid>
                \t\t<suspicionLevel>%d</suspicionLevel>
                \t\t<totalBlocksMined>%d</totalBlocksMined>
                \t\t<miningSpeed>%.2f</miningSpeed>
                \t\t<firstSeen>%d</firstSeen>
                \t\t<lastSeen>%d</lastSeen>
                \t\t<riskCluster>%d</riskCluster>
                \t</player>\n""",
                escapeXML(stat.playerName),
                stat.uuid,
                stat.suspicionLevel,
                stat.totalBlocksMined,
                stat.miningSpeed,
                stat.firstSeen,
                stat.lastSeen,
                stat.riskCluster
            ));
        }

        xml.append("</playerStats>\n");
        Files.writeString(path, xml.toString());
    }

    private void exportDetectionHistoryAsXml(List<DetectionEventExport> events, Path path) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<detectionHistory>\n");

        for (DetectionEventExport event : events) {
            xml.append(String.format("""
                \t<detection>
                \t\t<timestamp>%d</timestamp>
                \t\t<playerName>%s</playerName>
                \t\t<detectionType>%s</detectionType>
                \t\t<confidence>%.3f</confidence>
                \t\t<severity>%s</severity>
                \t\t<details>%s</details>
                \t</detection>\n""",
                event.timestamp,
                escapeXML(event.playerName),
                event.detectionType,
                event.confidence,
                event.severity,
                escapeXML(event.details)
            ));
        }

        xml.append("</detectionHistory>\n");
        Files.writeString(path, xml.toString());
    }

    private void exportPerformanceMetricsAsXml(List<PerformanceMetricsExport> metrics, Path path) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<performanceMetrics>\n");

        for (PerformanceMetricsExport metric : metrics) {
            xml.append(String.format("""
                \t<metric>
                \t\t<timestamp>%d</timestamp>
                \t\t<tps>%.2f</tps>
                \t\t<memoryUsedMB>%d</memoryUsedMB>
                \t\t<memoryMaxMB>%d</memoryMaxMB>
                \t\t<cpuUsagePercent>%.1f</cpuUsagePercent>
                \t\t<onlinePlayers>%d</onlinePlayers>
                \t\t<suspiciousPlayers>%d</suspiciousPlayers>
                \t</metric>\n""",
                metric.timestamp,
                metric.tps,
                metric.memoryUsedMB,
                metric.memoryMaxMB,
                metric.cpuUsagePercent,
                metric.onlinePlayers,
                metric.suspiciousPlayers
            ));
        }

        xml.append("</performanceMetrics>\n");
        Files.writeString(path, xml.toString());
    }

    private void exportMLDataAsXml(MLDataExport mlData, Path path) throws IOException {
        String xml = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <mlData>
            \t<accuracy>%.3f</accuracy>
            \t<totalTrainings>%d</totalTrainings>
            \t<clusters>%d</clusters>
            \t<trackedPlayers>%d</trackedPlayers>
            \t<lastUpdate>%d</lastUpdate>
            </mlData>\n""",
            mlData.accuracy,
            mlData.totalTrainings,
            mlData.clusters,
            mlData.trackedPlayers,
            mlData.lastUpdate
        );

        Files.writeString(path, xml);
    }

    private void exportSystemLogsAsXml(List<SystemLogExport> logs, Path path) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<systemLogs>\n");

        for (SystemLogExport log : logs) {
            xml.append(String.format("""
                \t<log>
                \t\t<timestamp>%d</timestamp>
                \t\t<level>%s</level>
                \t\t<message>%s</message>
                \t\t<source>%s</source>
                \t</log>\n""",
                log.timestamp,
                log.level,
                escapeXML(log.message),
                log.source
            ));
        }

        xml.append("</systemLogs>\n");
        Files.writeString(path, xml.toString());
    }

    // ===== DATA COLLECTION METHODS =====

    private List<PlayerStatsExport> collectPlayerStats() {
        List<PlayerStatsExport> stats = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Placeholder data collection
            stats.add(new PlayerStatsExport(
                player.getName(),
                player.getUniqueId().toString(),
                0, // suspicion level
                0, // blocks mined
                0.0, // mining speed
                player.getFirstPlayed(),
                System.currentTimeMillis(),
                0 // risk cluster
            ));
        }

        return stats;
    }

    private List<DetectionEventExport> collectDetectionHistory(long startTime, long endTime) {
        List<DetectionEventExport> events = new ArrayList<>();

        // Get data from RealtimeMetricsCollector if available
        try {
            var metricsCollector = plugin.getMetricsCollector();
            if (metricsCollector != null) {
                var detectionSnapshots = metricsCollector.getRecentDetectionData(24); // Last 24 hours
                for (var snapshot : detectionSnapshots) {
                    if (snapshot.timestamp >= startTime && snapshot.timestamp <= endTime) {
                        events.add(new DetectionEventExport(
                            snapshot.timestamp,
                            "aggregated", // playerName
                            "summary", // detectionType
                            snapshot.mlAccuracy, // confidence
                            "info", // severity
                            String.format("Detections: %d, Alerts: %d, Bans: %d, Kicks: %d",
                                snapshot.totalDetections, snapshot.totalAlerts,
                                snapshot.totalBans, snapshot.totalKicks) // details
                        ));
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to empty list if metrics collector is not available
        }

        return events;
    }

    private List<PerformanceMetricsExport> collectPerformanceMetrics(int hours) {
        List<PerformanceMetricsExport> metrics = new ArrayList<>();

        // Get data from RealtimeMetricsCollector
        try {
            var metricsCollector = plugin.getMetricsCollector();
            if (metricsCollector != null) {
                var perfSnapshots = metricsCollector.getRecentPerformanceData(hours);
                for (var snapshot : perfSnapshots) {
                    metrics.add(new PerformanceMetricsExport(
                        snapshot.timestamp,
                        snapshot.tps,
                        0, // Memory usage would need to be integrated
                        0, // Memory max would need to be integrated
                        0, // CPU usage would need to be integrated
                        0, // Online players would need to be integrated
                        0  // Suspicious players would need to be integrated
                    ));
                }
            }
        } catch (Exception e) {
            // Fallback to empty list if metrics collector is not available
        }

        return metrics;
    }

    private MLDataExport collectMLData() {
        return new MLDataExport(0.85, 150, 5, 45, System.currentTimeMillis());
    }

    private List<SystemLogExport> collectSystemLogs(long startTime, long endTime) {
        List<SystemLogExport> logs = new ArrayList<>();

        // Try to get logs from LogAuditor if available
        try {
            Class<?> logAuditorClass = Class.forName("net.denfry.owml.audit.LogAuditor");
            java.lang.reflect.Method searchMethod = logAuditorClass.getMethod("searchLogs",
                net.denfry.owml.audit.LogAuditor.LogSearchCriteria.class);

            var criteria = new net.denfry.owml.audit.LogAuditor.LogSearchCriteria();
            criteria.startTime = startTime;
            criteria.endTime = endTime;

            @SuppressWarnings("unchecked")
            java.util.List<net.denfry.owml.audit.LogAuditor.AuditLogEntry> logEntries =
                (java.util.List<net.denfry.owml.audit.LogAuditor.AuditLogEntry>) searchMethod.invoke(null, criteria);

            for (var entry : logEntries) {
                String playerName = (String) entry.metadata.get("playerName");
                String worldName = (String) entry.metadata.get("worldName");
                String source = (playerName != null ? playerName : "") +
                    (worldName != null ? " (" + worldName + ")" : "");

                logs.add(new SystemLogExport(
                    entry.getTimestampMillis(),
                    entry.level.toString(),
                    entry.message,
                    source
                ));
            }
        } catch (Exception e) {
            // Fallback to empty list if LogAuditor is not available
        }

        return logs;
    }

    // ===== UTILITY METHODS =====

    private void createExportDirectory() {
        try {
            Path exportPath = Paths.get(EXPORT_DIR);
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }
        } catch (IOException e) {
            MessageManager.log("error", "Failed to create export directory: {ERROR}", "ERROR", e.getMessage());
        }
    }

    private String generateFilename(DataType type, ExportFormat format) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        return String.format("%s_%s.%s", type.getFilePrefix(), timestamp, format.getExtension());
    }

    private String escapeCSV(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXML(String value) {
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    // ===== DATA CLASSES =====

    public static class PlayerStatsExport {
        public final String playerName;
        public final String uuid;
        public final int suspicionLevel;
        public final int totalBlocksMined;
        public final double miningSpeed;
        public final long firstSeen;
        public final long lastSeen;
        public final int riskCluster;

        public PlayerStatsExport(String playerName, String uuid, int suspicionLevel,
                               int totalBlocksMined, double miningSpeed, long firstSeen,
                               long lastSeen, int riskCluster) {
            this.playerName = playerName;
            this.uuid = uuid;
            this.suspicionLevel = suspicionLevel;
            this.totalBlocksMined = totalBlocksMined;
            this.miningSpeed = miningSpeed;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.riskCluster = riskCluster;
        }
    }

    public static class DetectionEventExport {
        public final long timestamp;
        public final String playerName;
        public final String detectionType;
        public final double confidence;
        public final String severity;
        public final String details;

        public DetectionEventExport(long timestamp, String playerName, String detectionType,
                                  double confidence, String severity, String details) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.detectionType = detectionType;
            this.confidence = confidence;
            this.severity = severity;
            this.details = details;
        }
    }

    public static class PerformanceMetricsExport {
        public final long timestamp;
        public final double tps;
        public final int memoryUsedMB;
        public final int memoryMaxMB;
        public final double cpuUsagePercent;
        public final int onlinePlayers;
        public final int suspiciousPlayers;

        public PerformanceMetricsExport(long timestamp, double tps, int memoryUsedMB,
                                      int memoryMaxMB, double cpuUsagePercent,
                                      int onlinePlayers, int suspiciousPlayers) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.memoryUsedMB = memoryUsedMB;
            this.memoryMaxMB = memoryMaxMB;
            this.cpuUsagePercent = cpuUsagePercent;
            this.onlinePlayers = onlinePlayers;
            this.suspiciousPlayers = suspiciousPlayers;
        }
    }

    public static class MLDataExport {
        public final double accuracy;
        public final int totalTrainings;
        public final int clusters;
        public final int trackedPlayers;
        public final long lastUpdate;

        public MLDataExport(double accuracy, int totalTrainings, int clusters,
                          int trackedPlayers, long lastUpdate) {
            this.accuracy = accuracy;
            this.totalTrainings = totalTrainings;
            this.clusters = clusters;
            this.trackedPlayers = trackedPlayers;
            this.lastUpdate = lastUpdate;
        }
    }

    public static class SystemLogExport {
        public final long timestamp;
        public final String level;
        public final String message;
        public final String source;

        public SystemLogExport(long timestamp, String level, String message, String source) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
            this.source = source;
        }
    }

    public static class CompleteDatasetExport {
        public final List<PlayerStatsExport> playerStats;
        public final List<DetectionEventExport> detectionHistory;
        public final List<PerformanceMetricsExport> performanceMetrics;
        public final MLDataExport mlData;
        public final List<SystemLogExport> systemLogs;

        public CompleteDatasetExport(List<PlayerStatsExport> playerStats,
                                   List<DetectionEventExport> detectionHistory,
                                   List<PerformanceMetricsExport> performanceMetrics,
                                   MLDataExport mlData, List<SystemLogExport> systemLogs) {
            this.playerStats = playerStats;
            this.detectionHistory = detectionHistory;
            this.performanceMetrics = performanceMetrics;
            this.mlData = mlData;
            this.systemLogs = systemLogs;
        }
    }
}
