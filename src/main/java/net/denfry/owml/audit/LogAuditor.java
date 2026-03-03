package net.denfry.owml.audit;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

/**
 * Log auditing system with search and filtering capabilities.
 * Provides advanced log analysis and archival features.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class LogAuditor {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private final Path auditLogDir;
    private final Path archiveDir;
    
    private final Queue<String> logQueue = new ConcurrentLinkedQueue<>();

    // Audit log levels
    public enum AuditLevel {
        TRACE(0, "TRACE"),
        DEBUG(1, "DEBUG"),
        INFO(2, "INFO"),
        WARNING(3, "WARNING"),
        ERROR(4, "ERROR"),
        CRITICAL(5, "CRITICAL");

        private final int severity;
        private final String displayName;

        AuditLevel(int severity, String displayName) {
            this.severity = severity;
            this.displayName = displayName;
        }

        public int getSeverity() { return severity; }
        public String getDisplayName() { return displayName; }
    }

    // Audit event types
    public enum AuditEventType {
        DETECTION_TRIGGERED,
        PLAYER_BANNED,
        PLAYER_KICKED,
        CONFIG_CHANGED,
        PLUGIN_LOADED,
        PLUGIN_UNLOADED,
        INTEGRATION_CONNECTED,
        INTEGRATION_DISCONNECTED,
        SECURITY_VIOLATION,
        DATA_EXPORTED,
        BACKUP_CREATED,
        SYSTEM_ERROR
    }

    public LogAuditor() {
        this.auditLogDir = plugin.getDataFolder().toPath().resolve("audit");
        this.archiveDir = auditLogDir.resolve("archive");
        createDirectories();
        startLogRotationTask();
        startLogFlushTask();
    }

    /**
     * Log an audit event
     */
    public void logEvent(AuditEventType eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String logEntry = formatLogEntry(timestamp, eventType, level, message, metadata);

        // Write to current audit log
        writeToAuditLog(logEntry);

        // Also log to console if important
        if (level.getSeverity() >= AuditLevel.WARNING.getSeverity()) {
            MessageManager.log(level.name().toLowerCase(), "AUDIT: {MESSAGE}", "MESSAGE", message);
        }
    }

    /**
     * Log a detection event
     */
    public void logDetection(String playerName, String detectionType, double confidence, String details) {
        Map<String, Object> metadata = Map.of(
            "player", playerName,
            "detectionType", detectionType,
            "confidence", String.format("%.3f", confidence),
            "details", details
        );

        logEvent(AuditEventType.DETECTION_TRIGGERED, AuditLevel.INFO, "Detection triggered", metadata);
    }

    /**
     * Log a security violation
     */
    public void logSecurityViolation(String source, String violationType, String details) {
        Map<String, Object> metadata = Map.of(
            "source", source,
            "violationType", violationType,
            "details", details
        );

        logEvent(AuditEventType.SECURITY_VIOLATION, AuditLevel.CRITICAL, "Security violation detected", metadata);
    }

    /**
     * Log configuration change
     */
    public void logConfigChange(String user, String key, String oldValue, String newValue) {
        Map<String, Object> metadata = Map.of(
            "user", user,
            "configKey", key,
            "oldValue", maskSensitiveValue(oldValue),
            "newValue", maskSensitiveValue(newValue)
        );

        logEvent(AuditEventType.CONFIG_CHANGED, AuditLevel.WARNING, "Configuration changed", metadata);
    }

    /**
     * Search audit logs
     */
    public List<AuditLogEntry> searchLogs(LogSearchCriteria criteria) {
        List<AuditLogEntry> results = new ArrayList<>();

        try {
            // Search current audit log
            Path auditLogPath = auditLogDir.resolve("audit.log");
            if (Files.exists(auditLogPath)) {
                results.addAll(searchInFile(auditLogPath, criteria));
            }

            // Search archived logs if requested
            if (criteria.includeArchives) {
                if (Files.exists(archiveDir)) {
                    for (Path path : Files.walk(archiveDir)
                            .filter(path -> path.toString().endsWith(".log.gz"))
                            .toList()) {
                        try {
                            results.addAll(searchInCompressedFile(path, criteria));
                        } catch (Exception e) {
                            // Skip files that can't be read
                        }
                    }
                }
            }

            // Sort by timestamp (newest first)
            results.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

            // Apply limit
            if (criteria.limit > 0 && results.size() > criteria.limit) {
                results = new ArrayList<>(results.subList(0, criteria.limit));
            }

        } catch (Exception e) {
            MessageManager.log("error", "Failed to search audit logs: {ERROR}", "ERROR", e.getMessage());
        }

        return results;
    }

    /**
     * Get audit statistics
     */
    public AuditStatistics getStatistics(long startTime, long endTime) {
        LogSearchCriteria criteria = new LogSearchCriteria();
        criteria.startTime = startTime;
        criteria.endTime = endTime;
        criteria.includeArchives = true;

        List<AuditLogEntry> entries = searchLogs(criteria);

        Map<AuditEventType, Integer> eventCounts = new HashMap<>();
        Map<AuditLevel, Integer> levelCounts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();

        for (AuditLogEntry entry : entries) {
            eventCounts.merge(entry.eventType, 1, Integer::sum);
            levelCounts.merge(entry.level, 1, Integer::sum);

            if (entry.metadata.containsKey("source")) {
                Object source = entry.metadata.get("source");
                if (source instanceof String) {
                    sourceCounts.merge((String) source, 1, Integer::sum);
                }
            }
        }

        return new AuditStatistics(
            entries.size(),
            eventCounts,
            levelCounts,
            sourceCounts,
            startTime,
            endTime
        );
    }

    /**
     * Export audit logs in specified format
     */
    public void exportLogs(LogSearchCriteria criteria, String format, Path exportPath) throws Exception {
        List<AuditLogEntry> entries = searchLogs(criteria);

        switch (format.toLowerCase()) {
            case "json" -> exportAsJson(entries, exportPath);
            case "csv" -> exportAsCsv(entries, exportPath);
            case "txt" -> exportAsText(entries, exportPath);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }

        MessageManager.log("info", "Exported {COUNT} audit log entries to {FILE}",
            "COUNT", String.valueOf(entries.size()), "FILE", exportPath.toString());
    }

    /**
     * Rotate audit logs (compress old logs)
     */
    public void rotateLogs() {
        try {
            Path auditLogPath = auditLogDir.resolve("audit.log");
            if (!Files.exists(auditLogPath)) {
                return;
            }

            // Create archive filename with date
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path archivePath = archiveDir.resolve("audit-" + dateStr + ".log.gz");

            // Compress current log
            compressFile(auditLogPath, archivePath);

            // Create new empty log file
            Files.writeString(auditLogPath, "");

            MessageManager.log("info", "Rotated audit logs - archived to {FILE}", "FILE", archivePath.getFileName());

        } catch (Exception e) {
            MessageManager.log("error", "Failed to rotate audit logs: {ERROR}", "ERROR", e.getMessage());
        }
    }

    // ===== PRIVATE METHODS =====

    private void createDirectories() {
        try {
            Files.createDirectories(auditLogDir);
            Files.createDirectories(archiveDir);
        } catch (IOException e) {
            MessageManager.log("error", "Failed to create audit directories: {ERROR}", "ERROR", e.getMessage());
        }
    }

    private String formatLogEntry(String timestamp, AuditEventType eventType, AuditLevel level,
                                String message, Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" [").append(level.getDisplayName()).append("] ");
        sb.append("[").append(eventType.name()).append("] ");
        sb.append(message);

        if (metadata != null && !metadata.isEmpty()) {
            sb.append(" | ");
            metadata.forEach((key, value) -> sb.append(key).append("=").append(value).append(" "));
        }

        return sb.toString().trim();
    }

    private void writeToAuditLog(String logEntry) {
        logQueue.offer(logEntry);
    }

    private void startLogFlushTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (logQueue.isEmpty()) return;
            
            List<String> toWrite = new ArrayList<>();
            String entry;
            while ((entry = logQueue.poll()) != null) {
                toWrite.add(entry);
            }
            
            try {
                Path auditLogPath = auditLogDir.resolve("audit.log");
                Files.write(auditLogPath, toWrite, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                MessageManager.log("error", "Failed to write to audit log: {ERROR}", "ERROR", e.getMessage());
            }
        }, 20L, 20L); // Flush every second
    }

    private void startLogRotationTask() {
        // Rotate logs daily at midnight
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::rotateLogs,
            getSecondsUntilMidnight(), 24 * 60 * 60 * 20); // 24 hours in ticks
    }

    private long getSecondsUntilMidnight() {
        // Calculate seconds until next midnight for initial delay
        return 24 * 60 * 60; // Simplified - would calculate actual time
    }

    private List<AuditLogEntry> searchInFile(Path filePath, LogSearchCriteria criteria) throws IOException {
        List<AuditLogEntry> results = new ArrayList<>();

        List<String> lines = Files.readAllLines(filePath);
        for (String line : lines) {
            AuditLogEntry entry = parseLogEntry(line);
            if (entry != null && matchesCriteria(entry, criteria)) {
                results.add(entry);
            }
        }

        return results;
    }

    private List<AuditLogEntry> searchInCompressedFile(Path filePath, LogSearchCriteria criteria) {
        List<AuditLogEntry> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(filePath))))) {

            String line;
            while ((line = reader.readLine()) != null) {
                AuditLogEntry entry = parseLogEntry(line);
                if (entry != null && matchesCriteria(entry, criteria)) {
                    results.add(entry);
                }
            }
        } catch (IOException e) {
            MessageManager.log("error", "Failed to search compressed log file {FILE}: {ERROR}",
                "FILE", filePath.toString(), "ERROR", e.getMessage());
        }

        return results;
    }

    private AuditLogEntry parseLogEntry(String line) {
        try {
            // Parse format: "timestamp [LEVEL] [EVENT_TYPE] message | key1=value1 key2=value2"
            if (!line.contains(" [") || !line.contains("] ")) {
                return null;
            }

            int levelStart = line.indexOf(" [") + 2;
            int levelEnd = line.indexOf("]", levelStart);
            if (levelEnd == -1) return null;

            String levelStr = line.substring(levelStart, levelEnd);

            int eventStart = line.indexOf(" [", levelEnd) + 2;
            int eventEnd = line.indexOf("]", eventStart);
            if (eventEnd == -1) return null;

            String eventStr = line.substring(eventStart, eventEnd);
            String messageAndMetadata = line.substring(eventEnd + 2);

            String message = messageAndMetadata;
            Map<String, Object> metadata = new HashMap<>();

            if (messageAndMetadata.contains(" | ")) {
                String[] parts = messageAndMetadata.split(" \\| ", 2);
                message = parts[0];
                if (parts.length > 1) {
                    metadata = parseMetadata(parts[1]);
                }
            }

            // Parse timestamp
            String timestamp = line.substring(0, levelStart - 2);

            return new AuditLogEntry(
                timestamp,
                AuditLevel.valueOf(levelStr),
                AuditEventType.valueOf(eventStr),
                message,
                metadata
            );

        } catch (Exception e) {
            return null; // Skip malformed entries
        }
    }

    private Map<String, Object> parseMetadata(String metadataStr) {
        Map<String, Object> metadata = new HashMap<>();
        if (metadataStr == null || metadataStr.trim().isEmpty()) {
            return metadata;
        }

        String[] pairs = metadataStr.split(" ");
        for (String pair : pairs) {
            if (pair.contains("=")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    metadata.put(keyValue[0], keyValue[1]);
                }
            }
        }

        return metadata;
    }

    private boolean matchesCriteria(AuditLogEntry entry, LogSearchCriteria criteria) {
        // Time range filter
        if (criteria.startTime > 0 && entry.getTimestampMillis() < criteria.startTime) return false;
        if (criteria.endTime > 0 && entry.getTimestampMillis() > criteria.endTime) return false;

        // Level filter
        if (criteria.minLevel != null && entry.level.getSeverity() < criteria.minLevel.getSeverity()) return false;
        if (criteria.maxLevel != null && entry.level.getSeverity() > criteria.maxLevel.getSeverity()) return false;

        // Event type filter
        if (criteria.eventTypes != null && !criteria.eventTypes.isEmpty() &&
            !criteria.eventTypes.contains(entry.eventType)) return false;

        // Text search
        if (criteria.searchText != null && !criteria.searchText.isEmpty()) {
            String searchLower = criteria.searchText.toLowerCase();
            boolean matches = entry.message.toLowerCase().contains(searchLower) ||
                            entry.metadata.values().stream()
                                .anyMatch(value -> value.toString().toLowerCase().contains(searchLower));

            if (criteria.searchText.startsWith("!") && matches) return false; // NOT search
            if (!criteria.searchText.startsWith("!") && !matches) return false;
        }

        return true;
    }

    private void compressFile(Path source, Path destination) throws IOException {
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(destination));
             BufferedReader reader = Files.newBufferedReader(source)) {

            String line;
            while ((line = reader.readLine()) != null) {
                gos.write((line + "\n").getBytes());
            }
        }
    }

    private void exportAsJson(List<AuditLogEntry> entries, Path path) throws IOException {
        // Simple JSON export - would use Gson in real implementation
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            json.append("  {\n");
            json.append("    \"timestamp\": \"").append(entries.get(i).timestamp).append("\",\n");
            json.append("    \"level\": \"").append(entries.get(i).level).append("\",\n");
            json.append("    \"eventType\": \"").append(entries.get(i).eventType).append("\",\n");
            json.append("    \"message\": \"").append(entries.get(i).message.replace("\"", "\\\"")).append("\"\n");
            json.append("  }");
            if (i < entries.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("]\n");
        Files.writeString(path, json.toString());
    }

    private void exportAsCsv(List<AuditLogEntry> entries, Path path) throws IOException {
        StringBuilder csv = new StringBuilder("timestamp,level,event_type,message\n");
        for (AuditLogEntry entry : entries) {
            csv.append(entry.timestamp).append(",")
               .append(entry.level).append(",")
               .append(entry.eventType).append(",")
               .append("\"").append(entry.message.replace("\"", "\"\"")).append("\"\n");
        }
        Files.writeString(path, csv.toString());
    }

    private void exportAsText(List<AuditLogEntry> entries, Path path) throws IOException {
        StringBuilder text = new StringBuilder("Audit Log Export\n");
        text.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

        for (AuditLogEntry entry : entries) {
            text.append(entry.timestamp).append(" [").append(entry.level).append("] ")
                .append("[").append(entry.eventType).append("] ").append(entry.message).append("\n");
        }

        Files.writeString(path, text.toString());
    }

    private String maskSensitiveValue(String value) {
        if (value == null) return null;
        if (value.length() <= 4) return "***";

        // Mask middle characters
        int showChars = Math.min(2, value.length() / 4);
        String start = value.substring(0, showChars);
        String end = value.substring(value.length() - showChars);
        String middle = "*".repeat(Math.max(0, value.length() - 2 * showChars));

        return start + middle + end;
    }

    // ===== DATA CLASSES =====

    /**
     * Audit log entry
     */
    public static class AuditLogEntry {
        public final String timestamp;
        public final AuditLevel level;
        public final AuditEventType eventType;
        public final String message;
        public final Map<String, Object> metadata;

        public AuditLogEntry(String timestamp, AuditLevel level, AuditEventType eventType,
                           String message, Map<String, Object> metadata) {
            this.timestamp = timestamp;
            this.level = level;
            this.eventType = eventType;
            this.message = message;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

        public long getTimestampMillis() {
            try {
                return LocalDateTime.parse(timestamp.substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /**
     * Search criteria for log queries
     */
    public static class LogSearchCriteria {
        public long startTime = 0;
        public long endTime = 0;
        public AuditLevel minLevel = null;
        public AuditLevel maxLevel = null;
        public Set<AuditEventType> eventTypes = null;
        public String searchText = null;
        public boolean includeArchives = false;
        public int limit = 1000;
    }

    /**
     * Audit statistics
     */
    public static class AuditStatistics {
        public final int totalEvents;
        public final Map<AuditEventType, Integer> eventsByType;
        public final Map<AuditLevel, Integer> eventsByLevel;
        public final Map<String, Integer> eventsBySource;
        public final long startTime;
        public final long endTime;

        public AuditStatistics(int totalEvents, Map<AuditEventType, Integer> eventsByType,
                             Map<AuditLevel, Integer> eventsByLevel, Map<String, Integer> eventsBySource,
                             long startTime, long endTime) {
            this.totalEvents = totalEvents;
            this.eventsByType = eventsByType;
            this.eventsByLevel = eventsByLevel;
            this.eventsBySource = eventsBySource;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
