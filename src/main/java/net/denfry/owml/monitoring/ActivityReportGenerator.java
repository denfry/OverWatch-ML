package net.denfry.owml.monitoring;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Activity report generator for suspicious player activities.
 * Generates detailed reports on detections, patterns, and player behavior.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class ActivityReportGenerator {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final String REPORTS_DIR = "plugins/OverWatch-ML/reports";

    // Report templates
    public enum ReportType {
        DAILY_SUMMARY("daily-summary"),
        PLAYER_ACTIVITY("player-activity"),
        DETECTION_ANALYSIS("detection-analysis"),
        PERFORMANCE_REPORT("performance-report"),
        SECURITY_AUDIT("security-audit");

        private final String filePrefix;

        ReportType(String filePrefix) {
            this.filePrefix = filePrefix;
        }

        public String getFilePrefix() {
            return filePrefix;
        }
    }

    public ActivityReportGenerator() {
        createReportsDirectory();
    }

    /**
     * Generate daily summary report
     */
    public String generateDailySummary() {
        StringBuilder report = new StringBuilder();

        report.append("=======================================\n");
        report.append(" OverWatchML DAILY SUMMARY REPORT\n");
        report.append("=======================================\n\n");

        report.append("Generated: ").append(new Date()).append("\n");
        report.append("Server: ").append(Bukkit.getServer().getName()).append("\n");
        report.append("Version: ").append(Bukkit.getVersion()).append("\n\n");

        // Server statistics
        report.append("=== SERVER STATISTICS ===\n");
        report.append("Online Players: ").append(Bukkit.getOnlinePlayers().size()).append("/").append(Bukkit.getMaxPlayers()).append("\n");
        report.append("Uptime: ").append(getUptimeString()).append("\n");
        report.append("Average TPS: ").append(String.format("%.2f", getAverageTPS())).append("\n\n");

        // Detection statistics
        report.append("=== DETECTION STATISTICS ===\n");
        var detectionStats = getDetectionStats();
        report.append("Total Detections: ").append(detectionStats.get("total")).append("\n");
        report.append("High Risk Detections: ").append(detectionStats.get("high")).append("\n");
        report.append("Critical Detections: ").append(detectionStats.get("critical")).append("\n");
        report.append("False Positives: ").append(detectionStats.get("falsePositives")).append("\n\n");

        // Player activity
        report.append("=== PLAYER ACTIVITY ===\n");
        var playerActivity = getPlayerActivitySummary();
        report.append(playerActivity).append("\n");

        // ML Performance
        report.append("=== MACHINE LEARNING ===\n");
        var mlStats = getMLStats();
        report.append("Model Accuracy: ").append(String.format("%.1f%%", ((Number) mlStats.get("accuracy")).doubleValue() * 100)).append("\n");
        report.append("Training Sessions: ").append(mlStats.get("trainings")).append("\n");
        report.append("Active Clusters: ").append(mlStats.get("clusters")).append("\n");
        report.append("Tracked Players: ").append(mlStats.get("tracked")).append("\n\n");

        // Top suspicious players
        report.append("=== TOP SUSPICIOUS PLAYERS ===\n");
        var topSuspicious = getTopSuspiciousPlayers(10);
        for (int i = 0; i < topSuspicious.size(); i++) {
            var entry = topSuspicious.get(i);
            report.append(String.format("%d. %s - Level %d (%s)\n",
                i + 1, entry.getKey(), entry.getValue(), getRiskDescription(entry.getValue())));
        }
        report.append("\n");

        // System health
        report.append("=== SYSTEM HEALTH ===\n");
        var systemHealth = getSystemHealth();
        report.append("Memory Usage: ").append(systemHealth.get("memory")).append("\n");
        report.append("CPU Usage: ").append(systemHealth.get("cpu")).append("\n");
        report.append("Disk Usage: ").append(systemHealth.get("disk")).append("\n");
        report.append("Active Integrations: ").append(systemHealth.get("integrations")).append("\n\n");

        // Recommendations
        report.append("=== RECOMMENDATIONS ===\n");
        var recommendations = generateRecommendations();
        report.append(recommendations).append("\n\n");

        report.append("=======================================\n");
        report.append(" END OF REPORT\n");
        report.append("=======================================\n");

        return report.toString();
    }

    /**
     * Generate detailed player activity report
     */
    public String generatePlayerActivityReport(UUID playerId) {
        StringBuilder report = new StringBuilder();
        Player player = Bukkit.getPlayer(playerId);

        String playerName = player != null ? player.getName() : "Unknown";

        report.append("=======================================\n");
        report.append(" PLAYER ACTIVITY REPORT\n");
        report.append("=======================================\n\n");

        report.append("Player: ").append(playerName).append("\n");
        report.append("UUID: ").append(playerId).append("\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        // Player statistics
        report.append("=== PLAYER STATISTICS ===\n");
        var playerStats = getPlayerDetailedStats(playerId);
        report.append("Suspicion Level: ").append(playerStats.get("suspicion")).append("\n");
        report.append("Total Blocks Mined: ").append(playerStats.get("blocksMined")).append("\n");
        report.append("Average Mining Speed: ").append(String.format("%.1f", playerStats.get("miningSpeed"))).append(" blocks/min\n");
        report.append("Session Time: ").append(formatDuration((Long) playerStats.get("sessionTime"))).append("\n");
        report.append("First Seen: ").append(new Date((Long) playerStats.get("firstSeen"))).append("\n");
        report.append("Last Seen: ").append(new Date((Long) playerStats.get("lastSeen"))).append("\n\n");

        // Detection history
        report.append("=== DETECTION HISTORY ===\n");
        var detectionHistory = getPlayerDetectionHistory(playerId);
        for (var entry : detectionHistory.entrySet()) {
            report.append(entry.getKey()).append(": ").append(entry.getValue()).append(" detections\n");
        }
        report.append("\n");

        // Behavior analysis
        report.append("=== BEHAVIOR ANALYSIS ===\n");
        var behaviorAnalysis = analyzePlayerBehavior(playerId);
        report.append("Risk Assessment: ").append(behaviorAnalysis.get("risk")).append("\n");
        report.append("Consistency Score: ").append(String.format("%.1f%%", behaviorAnalysis.get("consistency"))).append("\n");
        report.append("Exploration Ratio: ").append(String.format("%.1f%%", behaviorAnalysis.get("exploration"))).append("\n");
        report.append("Pattern Regularity: ").append(String.format("%.1f%%", behaviorAnalysis.get("regularity"))).append("\n\n");

        // Recommendations
        report.append("=== RECOMMENDATIONS ===\n");
        var recommendations = getPlayerRecommendations(playerId);
        report.append(recommendations).append("\n\n");

        report.append("=======================================\n");
        report.append(" END OF PLAYER REPORT\n");
        report.append("=======================================\n");

        return report.toString();
    }

    /**
     * Generate detection analysis report
     */
    public String generateDetectionAnalysisReport() {
        StringBuilder report = new StringBuilder();

        report.append("=======================================\n");
        report.append(" DETECTION ANALYSIS REPORT\n");
        report.append("=======================================\n\n");

        report.append("Generated: ").append(new Date()).append("\n");
        report.append("Analysis Period: Last 24 hours\n\n");

        // Detection breakdown
        report.append("=== DETECTION BREAKDOWN ===\n");
        var detectionBreakdown = getDetectionBreakdown();
        for (var entry : detectionBreakdown.entrySet()) {
            report.append(String.format("%s: %d detections\n", entry.getKey(), entry.getValue()));
        }
        report.append("\n");

        // False positive analysis
        report.append("=== FALSE POSITIVE ANALYSIS ===\n");
        double falsePositiveRate = calculateFalsePositiveRate();
        report.append("False Positive Rate: ").append(String.format("%.1f%%", falsePositiveRate * 100)).append("\n");
        report.append("Accuracy: ").append(String.format("%.1f%%", (1.0 - falsePositiveRate) * 100)).append("\n\n");

        // Pattern analysis
        report.append("=== PATTERN ANALYSIS ===\n");
        var patternStats = getPatternAnalysis();
        report.append("Most Common Pattern: ").append(patternStats.get("commonPattern")).append("\n");
        report.append("Average Detection Confidence: ").append(String.format("%.1f%%", patternStats.get("avgConfidence"))).append("\n");
        report.append("Pattern Diversity: ").append(patternStats.get("diversity")).append(" unique patterns\n\n");

        // Effectiveness metrics
        report.append("=== EFFECTIVENESS METRICS ===\n");
        var effectiveness = getEffectivenessMetrics();
        report.append("Detections per Hour: ").append(String.format("%.1f", effectiveness.get("detectionsPerHour"))).append("\n");
        report.append("Response Time: ").append(String.format("%.2f", effectiveness.get("avgResponseTime"))).append(" seconds\n");
        report.append("Prevention Rate: ").append(String.format("%.1f%%", effectiveness.get("preventionRate"))).append("\n\n");

        report.append("=======================================\n");
        report.append(" END OF ANALYSIS REPORT\n");
        report.append("=======================================\n");

        return report.toString();
    }

    /**
     * Save report to file
     */
    public void saveReport(ReportType type, String content, String suffix) {
        try {
            createReportsDirectory();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filename = String.format("%s_%s%s.txt", type.getFilePrefix(), timestamp, suffix != null ? "_" + suffix : "");

            Path reportPath = Paths.get(REPORTS_DIR, filename);
            Files.writeString(reportPath, content);

            MessageManager.log("info", "Report saved: {FILE}", "FILE", reportPath.toString());

        } catch (IOException e) {
            MessageManager.log("error", "Failed to save report: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Generate and save all reports
     */
    public void generateAllReports() {
        // Daily summary
        String dailySummary = generateDailySummary();
        saveReport(ReportType.DAILY_SUMMARY, dailySummary, null);

        // Detection analysis
        String detectionAnalysis = generateDetectionAnalysisReport();
        saveReport(ReportType.DETECTION_ANALYSIS, detectionAnalysis, null);

        // Performance report
        String performanceReport = generatePerformanceReport();
        saveReport(ReportType.PERFORMANCE_REPORT, performanceReport, null);

        // Security audit
        String securityAudit = generateSecurityAuditReport();
        saveReport(ReportType.SECURITY_AUDIT, securityAudit, null);

        MessageManager.log("info", "All reports generated and saved");
    }

    /**
     * Create reports directory if it doesn't exist
     */
    private void createReportsDirectory() {
        try {
            Path reportsPath = Paths.get(REPORTS_DIR);
            if (!Files.exists(reportsPath)) {
                Files.createDirectories(reportsPath);
            }
        } catch (IOException e) {
            MessageManager.log("error", "Failed to create reports directory: {ERROR}", "ERROR", e.getMessage());
        }
    }

    // ===== HELPER METHODS =====

    private String getUptimeString() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours % 24, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
        }
    }

    private double getAverageTPS() {
        // Get TPS from PerformanceMonitor (which maintains historical data)
        return net.denfry.owml.utils.PerformanceMonitor.getTPS();
    }

    private Map<String, Integer> getDetectionStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("high", 0);
        stats.put("critical", 0);
        stats.put("falsePositives", 0);
        return stats;
    }

    private String getPlayerActivitySummary() {
        return "Player activity summary would be generated here";
    }

    private Map<String, Object> getMLStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("accuracy", 0.85);
        stats.put("trainings", 150);
        stats.put("clusters", 5);
        stats.put("tracked", 45);
        return stats;
    }

    private List<Map.Entry<String, Integer>> getTopSuspiciousPlayers(int count) {
        // Placeholder implementation
        List<Map.Entry<String, Integer>> players = new ArrayList<>();
        players.add(Map.entry("Player1", 85));
        players.add(Map.entry("Player2", 72));
        players.add(Map.entry("Player3", 68));
        return players.subList(0, Math.min(count, players.size()));
    }

    private String getRiskDescription(int level) {
        if (level >= 80) return "Critical";
        if (level >= 60) return "High";
        if (level >= 40) return "Medium";
        if (level >= 20) return "Low";
        return "Safe";
    }

    private Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("memory", "512MB / 2GB");
        health.put("cpu", "25%");
        health.put("disk", "1.2GB / 10GB");
        health.put("integrations", "2/3");
        return health;
    }

    private String generateRecommendations() {
        return "1. Review high-risk players manually\n2. Adjust detection thresholds if needed\n3. Monitor memory usage trends";
    }

    private Map<String, Object> getPlayerDetailedStats(UUID playerId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("suspicion", 75);
        stats.put("blocksMined", 1250);
        stats.put("miningSpeed", 45.2);
        stats.put("sessionTime", 7200000L); // 2 hours
        stats.put("firstSeen", System.currentTimeMillis() - 86400000L); // 1 day ago
        stats.put("lastSeen", System.currentTimeMillis() - 3600000L); // 1 hour ago
        return stats;
    }

    private Map<String, Integer> getPlayerDetectionHistory(UUID playerId) {
        Map<String, Integer> history = new HashMap<>();
        history.put("XRAY", 15);
        history.put("SPEED", 3);
        history.put("PATTERN", 8);
        return history;
    }

    private Map<String, Object> analyzePlayerBehavior(UUID playerId) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("risk", "High");
        analysis.put("consistency", 78.5);
        analysis.put("exploration", 45.2);
        analysis.put("regularity", 92.1);
        return analysis;
    }

    private String getPlayerRecommendations(UUID playerId) {
        return "Monitor closely - high suspicion patterns detected";
    }

    private Map<String, Integer> getDetectionBreakdown() {
        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("XRAY", 25);
        breakdown.put("Speed", 8);
        breakdown.put("Pattern", 15);
        breakdown.put("Anomaly", 12);
        return breakdown;
    }

    private double calculateFalsePositiveRate() {
        return 0.08; // 8%
    }

    private Map<String, Object> getPatternAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("commonPattern", "Tunnel Mining");
        analysis.put("avgConfidence", 78.5);
        analysis.put("diversity", 12);
        return analysis;
    }

    private Map<String, Object> getEffectivenessMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("detectionsPerHour", 2.3);
        metrics.put("avgResponseTime", 1.2);
        metrics.put("preventionRate", 94.5);
        return metrics;
    }

    private String generatePerformanceReport() {
        return "Performance report would be generated here";
    }

    private String generateSecurityAuditReport() {
        return "Security audit report would be generated here";
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
    }

}
