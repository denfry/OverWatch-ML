package net.denfry.owml.integrations.discord.commands;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.DiscordCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.denfry.owml.managers.StatsManager;
import net.denfry.owml.ml.PlayerClusteringEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

public class CheckCommandHandler implements DiscordCommandHandler {
   private static final OverWatchML plugin = OverWatchML.getInstance();

   @Override
   public void handle(SlashCommandInteractionEvent event) {
      String playerName = event.getOption("player").getAsString();
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
      if (offlinePlayer != null && (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline())) {
         UUID playerId = offlinePlayer.getUniqueId();
         boolean isOnline = offlinePlayer.isOnline();
         int suspicionLevel = 0;
         String suspicionStatus = "✅ Clean";
         if (plugin.getSuspiciousManager() != null) {
            suspicionLevel = plugin.getSuspiciousManager().getSuspicionPoints(playerId);
            if (suspicionLevel >= 80) {
               suspicionStatus = "\ud83d\udea8 Critical";
            } else if (suspicionLevel >= 60) {
               suspicionStatus = "⚠️ High";
            } else if (suspicionLevel >= 40) {
               suspicionStatus = "\ud83d\udfe1 Medium";
            } else if (suspicionLevel >= 20) {
               suspicionStatus = "\ud83d\udfe2 Low";
            }
         }

         String miningStats = "No data available";
         Map<Material, Integer> oreStats = StatsManager.getOreStats(playerId);
         if (!oreStats.isEmpty()) {
            int totalBlocks = oreStats.values().stream().mapToInt(Integer::intValue).sum();
            int diamonds = oreStats.getOrDefault(Material.DIAMOND_ORE, 0);
            miningStats = String.format(
               "⛏️ **Total Blocks Mined:** %d\n\ud83d\udc8e **Diamonds Found:** %d\n\ud83d\udcc5 **Last Seen:** %s\n",
               totalBlocks,
               diamonds,
               this.getLastSeenStatus(offlinePlayer)
            );
         }

         String clusterInfo = "";
         if (plugin.getMLManager() != null && plugin.getMLManager().getClusteringEngine() != null) {
            int clusterId = plugin.getMLManager().getClusteringEngine().getPlayerCluster(playerId);
            if (clusterId >= 0) {
               PlayerClusteringEngine.ClusterProfile profile = plugin.getMLManager().getClusteringEngine().getClusterProfile(clusterId);
               if (profile != null) {
                  clusterInfo = String.format(
                     "\ud83c\udfaf **Behavior Cluster:** %d\n\ud83d\udc65 **Cluster Size:** %d\n\ud83d\udcca **Risk Level:** %.1f%%\n",
                     clusterId,
                     profile.size,
                     profile.calculateRiskLevel() * 100.0
                  );
               }
            }
         }

         String statusEmoji = isOnline ? "\ud83d\udfe2" : "\ud83d\udd34";
         String onlineStatus = isOnline ? "**ONLINE**" : "**OFFLINE**";
         String checkMessage = String.format(
            "\ud83d\udc64 **Player Check: %s**\n%s %s\n\n\ud83d\udea8 **Suspicion Level:** %d (%s)\n\n%s\n%s\n\n\ud83c\udd94 **UUID:** `%s`\n\ud83d\udcc5 **First Joined:** %s\n",
            playerName,
            statusEmoji,
            onlineStatus,
            suspicionLevel,
            suspicionStatus,
            miningStats,
            clusterInfo,
            playerId.toString(),
            offlinePlayer.getFirstPlayed() > 0L ? new Date(offlinePlayer.getFirstPlayed()).toString() : "Unknown"
         );
         event.getHook().editOriginal(checkMessage).queue();
      } else {
         event.getHook().editOriginal("❌ Player **" + playerName + "** not found!").queue();
      }
   }

   private String getLastSeenStatus(OfflinePlayer offlinePlayer) {
      if (offlinePlayer.isOnline()) {
         return "Online now";
      } else {
         return offlinePlayer.getLastPlayed() > 0L ? new Date(offlinePlayer.getLastPlayed()).toString() : "Never";
      }
   }
}
