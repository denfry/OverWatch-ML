package net.denfry.owml.integrations.discord.commands;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.DiscordCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.denfry.owml.ml.ModernMLManager;
import net.denfry.owml.utils.PerformanceMonitor;
import org.bukkit.Bukkit;

public class StatsCommandHandler implements DiscordCommandHandler {
   private static final OverWatchML plugin = OverWatchML.getInstance();

   @Override
   public void handle(SlashCommandInteractionEvent event) {
      String period = event.getOption("period") != null ? event.getOption("period").getAsString() : "hour";
      double tps = PerformanceMonitor.getCurrentTPS();
      int onlinePlayers = Bukkit.getOnlinePlayers().size();
      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / 1024L / 1024L;
      long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / 1024L / 1024L;
      int suspiciousCount = plugin.getSuspiciousManager() != null ? plugin.getSuspiciousManager().getSuspiciousCount() : 0;
      int detectionsToday = 0;
      int bansToday = 0;
      String mlStats = "";
      if (plugin.getMLManager() != null) {
         ModernMLManager.MLStats mlStatsObj = plugin.getMLManager().getComprehensiveStats();
         mlStats = String.format(
            "\ud83e\udd16 **ML Engine**\n\ud83d\udcca Accuracy: %.1f%%\n\ud83c\udfaf Detections: %d\n\ud83d\udc65 Tracked Players: %d\n\ud83e\udde0 Clusters: %d\n",
            mlStatsObj.learningStats.getAverageAccuracy() * 100.0,
            mlStatsObj.learningStats.totalTrainings,
            mlStatsObj.clusteringStats.trackedPlayers,
            mlStatsObj.clusteringStats.totalClusters
         );
      }

      String statsMessage = String.format(
         "\ud83d\udcca **OverWatchML Server Statistics**\n\ud83d\udd52 **Period:** %s\n\ud83d\udfe2 **Server TPS:** %.1f\n\ud83d\udc65 **Online Players:** %d/%d\n\ud83d\udc40 **Suspicious Players:** %d\n\ud83d\udeab **Detections Today:** %d\n\ud83d\udd28 **Bans Today:** %d\n\n\ud83d\udcbe **Memory Usage:** %dMB / %dMB (%.1f%%)\n⏱️ **Uptime:** %s\n\n%s\n\n\ud83d\udd50 **Generated:** %s\n",
         period.toUpperCase(),
         tps,
         onlinePlayers,
         Bukkit.getMaxPlayers(),
         suspiciousCount,
         detectionsToday,
         bansToday,
         usedMemory,
         maxMemory,
         (double)usedMemory / maxMemory * 100.0,
         this.getUptime(),
         mlStats,
         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      );
      event.getHook().editOriginal(statsMessage).queue();
   }

   private String getUptime() {
      long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
      long seconds = uptimeMillis / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      long days = hours / 24L;
      return days > 0L
         ? String.format("%dd %02d:%02d:%02d", days, hours % 24L, minutes % 60L, seconds % 60L)
         : String.format("%02d:%02d:%02d", hours % 24L, minutes % 60L, seconds % 60L);
   }
}
