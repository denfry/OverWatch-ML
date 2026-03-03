package net.denfry.owml.integrations.discord.commands;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.DiscordCommandHandler;
import net.denfry.owml.managers.StatsManager;
import net.denfry.owml.utils.PerformanceMonitor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Discord command handler for server statistics
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class StatsCommandHandler implements DiscordCommandHandler {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String period = event.getOption("period") != null ?
            event.getOption("period").getAsString() : "hour";

        // Get performance metrics
        double tps = PerformanceMonitor.getCurrentTPS();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Get memory usage
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;

        // Get plugin statistics
        int suspiciousCount = plugin.getSuspiciousManager() != null ?
            plugin.getSuspiciousManager().getSuspiciousCount() : 0;

        int detectionsToday = 0; // Would be tracked separately
        int bansToday = 0; // Would be tracked separately

        // Get ML stats if available
        String mlStats = "";
        if (plugin.getMLManager() != null) {
            var mlStatsObj = plugin.getMLManager().getComprehensiveStats();
            mlStats = String.format("""
                рџ¤– **ML Engine**
                рџ“Љ Accuracy: %.1f%%
                рџЋЇ Detections: %d
                рџ‘Ґ Tracked Players: %d
                рџ§  Clusters: %d
                """,
                mlStatsObj.learningStats.getAverageAccuracy() * 100,
                mlStatsObj.learningStats.totalTrainings,
                mlStatsObj.clusteringStats.trackedPlayers,
                mlStatsObj.clusteringStats.totalClusters
            );
        }

        String statsMessage = String.format("""
            рџ“Љ **OverWatchML Server Statistics**
            рџ•’ **Period:** %s
            рџџў **Server TPS:** %.1f
            рџ‘Ґ **Online Players:** %d/%d
            рџ‘Ђ **Suspicious Players:** %d
            рџљ« **Detections Today:** %d
            рџ”Ё **Bans Today:** %d

            рџ’ѕ **Memory Usage:** %dMB / %dMB (%.1f%%)
            вЏ±пёЏ **Uptime:** %s

            %s

            рџ•ђ **Generated:** %s
            """,
            period.toUpperCase(),
            tps,
            onlinePlayers, Bukkit.getMaxPlayers(),
            suspiciousCount,
            detectionsToday,
            bansToday,
            usedMemory, maxMemory,
            (double) usedMemory / maxMemory * 100,
            getUptime(),
            mlStats,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        event.getHook().editOriginal(statsMessage).queue();
    }

    private String getUptime() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours % 24, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
        }
    }
}
