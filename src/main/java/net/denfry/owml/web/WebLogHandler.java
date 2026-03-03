package net.denfry.owml.web;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Stores recent log entries for display in the web admin panel
 */
public class WebLogHandler {

    private static final int MAX_LOGS = 100;
    private static final Queue<LogEntry> logs = new LinkedList<>();

    /**
     * Add a log entry
     */
    public static synchronized void addLog(String level, String message) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, message);
        logs.offer(entry);

        // Remove oldest if we exceed max
        while (logs.size() > MAX_LOGS) {
            logs.poll();
        }
    }

    /**
     * Get all log entries
     */
    public static synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }

    /**
     * Clear all logs
     */
    public static synchronized void clearLogs() {
        logs.clear();
    }

    /**
     * Log entry data structure
     */
    public static class LogEntry {
        public final long timestamp;
        public final String level;
        public final String message;

        public LogEntry(long timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }
}
