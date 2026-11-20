package net.denfry.owml.audit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.Schedulers;

public class LogAuditor {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Path auditLogDir;
   private final Path archiveDir;
   private final Queue<String> logQueue = new ConcurrentLinkedQueue<>();

   public LogAuditor() {
      this.auditLogDir = plugin.getDataFolder().toPath().resolve("audit");
      this.archiveDir = this.auditLogDir.resolve("archive");
      this.createDirectories();
      this.startLogRotationTask();
      this.startLogFlushTask();
   }

   public void logEvent(LogAuditor.AuditEventType eventType, LogAuditor.AuditLevel level, String message, Map<String, Object> metadata) {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
      String logEntry = this.formatLogEntry(timestamp, eventType, level, message, metadata);
      this.writeToAuditLog(logEntry);
      if (level.getSeverity() >= LogAuditor.AuditLevel.WARNING.getSeverity()) {
         MessageManager.log(level.name().toLowerCase(), "AUDIT: {MESSAGE}", "MESSAGE", message);
      }
   }

   public void logDetection(String playerName, String detectionType, double confidence, String details) {
      Map<String, Object> metadata = Map.of(
         "player", playerName, "detectionType", detectionType, "confidence", String.format("%.3f", confidence), "details", details
      );
      this.logEvent(LogAuditor.AuditEventType.DETECTION_TRIGGERED, LogAuditor.AuditLevel.INFO, "Detection triggered", metadata);
   }

   public void logSecurityViolation(String source, String violationType, String details) {
      Map<String, Object> metadata = Map.of("source", source, "violationType", violationType, "details", details);
      this.logEvent(LogAuditor.AuditEventType.SECURITY_VIOLATION, LogAuditor.AuditLevel.CRITICAL, "Security violation detected", metadata);
   }

   public void logConfigChange(String user, String key, String oldValue, String newValue) {
      Map<String, Object> metadata = Map.of(
         "user", user, "configKey", key, "oldValue", this.maskSensitiveValue(oldValue), "newValue", this.maskSensitiveValue(newValue)
      );
      this.logEvent(LogAuditor.AuditEventType.CONFIG_CHANGED, LogAuditor.AuditLevel.WARNING, "Configuration changed", metadata);
   }

   public List<LogAuditor.AuditLogEntry> searchLogs(LogAuditor.LogSearchCriteria criteria) {
      List<LogAuditor.AuditLogEntry> results = new ArrayList<>();

      try {
         Path auditLogPath = this.auditLogDir.resolve("audit.log");
         if (Files.exists(auditLogPath)) {
            results.addAll(this.searchInFile(auditLogPath, criteria));
         }

         if (criteria.includeArchives && Files.exists(this.archiveDir)) {
            for (Path path : Files.walk(this.archiveDir).filter(pathx -> pathx.toString().endsWith(".log.gz")).toList()) {
               try {
                  results.addAll(this.searchInCompressedFile(path, criteria));
               } catch (Exception var7) {
               }
            }
         }

         results.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
         if (criteria.limit > 0 && results.size() > criteria.limit) {
            results = new ArrayList<>(results.subList(0, criteria.limit));
         }
      } catch (Exception var8) {
         MessageManager.log("error", "Failed to search audit logs: {ERROR}", "ERROR", var8.getMessage());
      }

      return results;
   }

   public LogAuditor.AuditStatistics getStatistics(long startTime, long endTime) {
      LogAuditor.LogSearchCriteria criteria = new LogAuditor.LogSearchCriteria();
      criteria.startTime = startTime;
      criteria.endTime = endTime;
      criteria.includeArchives = true;
      List<LogAuditor.AuditLogEntry> entries = this.searchLogs(criteria);
      Map<LogAuditor.AuditEventType, Integer> eventCounts = new HashMap<>();
      Map<LogAuditor.AuditLevel, Integer> levelCounts = new HashMap<>();
      Map<String, Integer> sourceCounts = new HashMap<>();

      for (LogAuditor.AuditLogEntry entry : entries) {
         eventCounts.merge(entry.eventType, 1, Integer::sum);
         levelCounts.merge(entry.level, 1, Integer::sum);
         if (entry.metadata.containsKey("source")) {
            Object source = entry.metadata.get("source");
            if (source instanceof String) {
               sourceCounts.merge((String)source, 1, Integer::sum);
            }
         }
      }

      return new LogAuditor.AuditStatistics(entries.size(), eventCounts, levelCounts, sourceCounts, startTime, endTime);
   }

   public void exportLogs(LogAuditor.LogSearchCriteria criteria, String format, Path exportPath) throws Exception {
      List<LogAuditor.AuditLogEntry> entries = this.searchLogs(criteria);
      String var5 = format.toLowerCase();
      switch (var5) {
         case "json":
            this.exportAsJson(entries, exportPath);
            break;
         case "csv":
            this.exportAsCsv(entries, exportPath);
            break;
         case "txt":
            this.exportAsText(entries, exportPath);
            break;
         default:
            throw new IllegalArgumentException("Unsupported format: " + format);
      }

      MessageManager.log("info", "Exported {COUNT} audit log entries to {FILE}", "COUNT", String.valueOf(entries.size()), "FILE", exportPath.toString());
   }

   public void rotateLogs() {
      try {
         Path auditLogPath = this.auditLogDir.resolve("audit.log");
         if (!Files.exists(auditLogPath)) {
            return;
         }

         String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
         Path archivePath = this.archiveDir.resolve("audit-" + dateStr + ".log.gz");
         this.compressFile(auditLogPath, archivePath);
         Files.writeString(auditLogPath, "");
         MessageManager.log("info", "Rotated audit logs - archived to {FILE}", "FILE", archivePath.getFileName());
      } catch (Exception var4) {
         MessageManager.log("error", "Failed to rotate audit logs: {ERROR}", "ERROR", var4.getMessage());
      }
   }

   private void createDirectories() {
      try {
         Files.createDirectories(this.auditLogDir);
         Files.createDirectories(this.archiveDir);
      } catch (IOException var2) {
         MessageManager.log("error", "Failed to create audit directories: {ERROR}", "ERROR", var2.getMessage());
      }
   }

   private String formatLogEntry(
      String timestamp, LogAuditor.AuditEventType eventType, LogAuditor.AuditLevel level, String message, Map<String, Object> metadata
   ) {
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
      this.logQueue.offer(logEntry);
   }

   private void startLogFlushTask() {
      Schedulers.asyncTimer(() -> {
         if (!this.logQueue.isEmpty()) {
            List<String> toWrite = new ArrayList<>();

            String entry;
            while ((entry = this.logQueue.poll()) != null) {
               toWrite.add(entry);
            }

            try {
               Path auditLogPath = this.auditLogDir.resolve("audit.log");
               Files.write(auditLogPath, toWrite, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException var4) {
               MessageManager.log("error", "Failed to write to audit log: {ERROR}", "ERROR", var4.getMessage());
            }
         }
      }, 20L, 20L);
   }

   private void startLogRotationTask() {
      Schedulers.asyncTimer(this::rotateLogs, this.getSecondsUntilMidnight(), 1728000L);
   }

   private long getSecondsUntilMidnight() {
      return 86400L;
   }

   private List<LogAuditor.AuditLogEntry> searchInFile(Path filePath, LogAuditor.LogSearchCriteria criteria) throws IOException {
      List<LogAuditor.AuditLogEntry> results = new ArrayList<>();

      for (String line : Files.readAllLines(filePath)) {
         LogAuditor.AuditLogEntry entry = this.parseLogEntry(line);
         if (entry != null && this.matchesCriteria(entry, criteria)) {
            results.add(entry);
         }
      }

      return results;
   }

   private List<LogAuditor.AuditLogEntry> searchInCompressedFile(Path filePath, LogAuditor.LogSearchCriteria criteria) {
      List<LogAuditor.AuditLogEntry> results = new ArrayList<>();

      String line;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(filePath))))) {
         while ((line = reader.readLine()) != null) {
            LogAuditor.AuditLogEntry entry = this.parseLogEntry(line);
            if (entry != null && this.matchesCriteria(entry, criteria)) {
               results.add(entry);
            }
         }
      } catch (IOException var9) {
         MessageManager.log("error", "Failed to search compressed log file {FILE}: {ERROR}", "FILE", filePath.toString(), "ERROR", var9.getMessage());
      }

      return results;
   }

   private LogAuditor.AuditLogEntry parseLogEntry(String line) {
      try {
         if (line.contains(" [") && line.contains("] ")) {
            int levelStart = line.indexOf(" [") + 2;
            int levelEnd = line.indexOf("]", levelStart);
            if (levelEnd == -1) {
               return null;
            } else {
               String levelStr = line.substring(levelStart, levelEnd);
               int eventStart = line.indexOf(" [", levelEnd) + 2;
               int eventEnd = line.indexOf("]", eventStart);
               if (eventEnd == -1) {
                  return null;
               } else {
                  String eventStr = line.substring(eventStart, eventEnd);
                  String messageAndMetadata = line.substring(eventEnd + 2);
                  String message = messageAndMetadata;
                  Map<String, Object> metadata = new HashMap<>();
                  if (messageAndMetadata.contains(" | ")) {
                     String[] parts = messageAndMetadata.split(" \\| ", 2);
                     message = parts[0];
                     if (parts.length > 1) {
                        metadata = this.parseMetadata(parts[1]);
                     }
                  }

                  String timestamp = line.substring(0, levelStart - 2);
                  return new LogAuditor.AuditLogEntry(
                     timestamp, LogAuditor.AuditLevel.valueOf(levelStr), LogAuditor.AuditEventType.valueOf(eventStr), message, metadata
                  );
               }
            }
         } else {
            return null;
         }
      } catch (Exception var12) {
         return null;
      }
   }

   private Map<String, Object> parseMetadata(String metadataStr) {
      Map<String, Object> metadata = new HashMap<>();
      if (metadataStr != null && !metadataStr.trim().isEmpty()) {
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
      } else {
         return metadata;
      }
   }

   private boolean matchesCriteria(LogAuditor.AuditLogEntry entry, LogAuditor.LogSearchCriteria criteria) {
      if (criteria.startTime > 0L && entry.getTimestampMillis() < criteria.startTime) {
         return false;
      } else if (criteria.endTime > 0L && entry.getTimestampMillis() > criteria.endTime) {
         return false;
      } else if (criteria.minLevel != null && entry.level.getSeverity() < criteria.minLevel.getSeverity()) {
         return false;
      } else if (criteria.maxLevel != null && entry.level.getSeverity() > criteria.maxLevel.getSeverity()) {
         return false;
      } else if (criteria.eventTypes != null && !criteria.eventTypes.isEmpty() && !criteria.eventTypes.contains(entry.eventType)) {
         return false;
      } else {
         if (criteria.searchText != null && !criteria.searchText.isEmpty()) {
            String searchLower = criteria.searchText.toLowerCase();
            boolean matches = entry.message.toLowerCase().contains(searchLower)
               || entry.metadata.values().stream().anyMatch(value -> value.toString().toLowerCase().contains(searchLower));
            if (criteria.searchText.startsWith("!") && matches) {
               return false;
            }

            if (!criteria.searchText.startsWith("!") && !matches) {
               return false;
            }
         }

         return true;
      }
   }

   private void compressFile(Path source, Path destination) throws IOException {
      String line;
      try (
         GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(destination));
         BufferedReader reader = Files.newBufferedReader(source);
      ) {
         while ((line = reader.readLine()) != null) {
            gos.write((line + "\n").getBytes());
         }
      }
   }

   private void exportAsJson(List<LogAuditor.AuditLogEntry> entries, Path path) throws IOException {
      StringBuilder json = new StringBuilder("[\n");

      for (int i = 0; i < entries.size(); i++) {
         json.append("  {\n");
         json.append("    \"timestamp\": \"").append(entries.get(i).timestamp).append("\",\n");
         json.append("    \"level\": \"").append(entries.get(i).level).append("\",\n");
         json.append("    \"eventType\": \"").append(entries.get(i).eventType).append("\",\n");
         json.append("    \"message\": \"").append(entries.get(i).message.replace("\"", "\\\"")).append("\"\n");
         json.append("  }");
         if (i < entries.size() - 1) {
            json.append(",");
         }

         json.append("\n");
      }

      json.append("]\n");
      Files.writeString(path, json.toString());
   }

   private void exportAsCsv(List<LogAuditor.AuditLogEntry> entries, Path path) throws IOException {
      StringBuilder csv = new StringBuilder("timestamp,level,event_type,message\n");

      for (LogAuditor.AuditLogEntry entry : entries) {
         csv.append(entry.timestamp)
            .append(",")
            .append(entry.level)
            .append(",")
            .append(entry.eventType)
            .append(",")
            .append("\"")
            .append(entry.message.replace("\"", "\"\""))
            .append("\"\n");
      }

      Files.writeString(path, csv.toString());
   }

   private void exportAsText(List<LogAuditor.AuditLogEntry> entries, Path path) throws IOException {
      StringBuilder text = new StringBuilder("Audit Log Export\n");
      text.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

      for (LogAuditor.AuditLogEntry entry : entries) {
         text.append(entry.timestamp)
            .append(" [")
            .append(entry.level)
            .append("] ")
            .append("[")
            .append(entry.eventType)
            .append("] ")
            .append(entry.message)
            .append("\n");
      }

      Files.writeString(path, text.toString());
   }

   private String maskSensitiveValue(String value) {
      if (value == null) {
         return null;
      } else if (value.length() <= 4) {
         return "***";
      } else {
         int showChars = Math.min(2, value.length() / 4);
         String start = value.substring(0, showChars);
         String end = value.substring(value.length() - showChars);
         String middle = "*".repeat(Math.max(0, value.length() - 2 * showChars));
         return start + middle + end;
      }
   }

   public static enum AuditEventType {
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
      SYSTEM_ERROR;
   }

   public static enum AuditLevel {
      TRACE(0, "TRACE"),
      DEBUG(1, "DEBUG"),
      INFO(2, "INFO"),
      WARNING(3, "WARNING"),
      ERROR(4, "ERROR"),
      CRITICAL(5, "CRITICAL");

      private final int severity;
      private final String displayName;

      private AuditLevel(int severity, String displayName) {
         this.severity = severity;
         this.displayName = displayName;
      }

      public int getSeverity() {
         return this.severity;
      }

      public String getDisplayName() {
         return this.displayName;
      }
   }

   public static class AuditLogEntry {
      public final String timestamp;
      public final LogAuditor.AuditLevel level;
      public final LogAuditor.AuditEventType eventType;
      public final String message;
      public final Map<String, Object> metadata;

      public AuditLogEntry(String timestamp, LogAuditor.AuditLevel level, LogAuditor.AuditEventType eventType, String message, Map<String, Object> metadata) {
         this.timestamp = timestamp;
         this.level = level;
         this.eventType = eventType;
         this.message = message;
         this.metadata = (Map<String, Object>)(metadata != null ? metadata : new HashMap<>());
      }

      public long getTimestampMillis() {
         try {
            return LocalDateTime.parse(this.timestamp.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
               .toInstant(ZoneOffset.UTC)
               .toEpochMilli();
         } catch (Exception var2) {
            return 0L;
         }
      }
   }

   public static class AuditStatistics {
      public final int totalEvents;
      public final Map<LogAuditor.AuditEventType, Integer> eventsByType;
      public final Map<LogAuditor.AuditLevel, Integer> eventsByLevel;
      public final Map<String, Integer> eventsBySource;
      public final long startTime;
      public final long endTime;

      public AuditStatistics(
         int totalEvents,
         Map<LogAuditor.AuditEventType, Integer> eventsByType,
         Map<LogAuditor.AuditLevel, Integer> eventsByLevel,
         Map<String, Integer> eventsBySource,
         long startTime,
         long endTime
      ) {
         this.totalEvents = totalEvents;
         this.eventsByType = eventsByType;
         this.eventsByLevel = eventsByLevel;
         this.eventsBySource = eventsBySource;
         this.startTime = startTime;
         this.endTime = endTime;
      }
   }

   public static class LogSearchCriteria {
      public long startTime = 0L;
      public long endTime = 0L;
      public LogAuditor.AuditLevel minLevel = null;
      public LogAuditor.AuditLevel maxLevel = null;
      public Set<LogAuditor.AuditEventType> eventTypes = null;
      public String searchText = null;
      public boolean includeArchives = false;
      public int limit = 1000;
   }
}
