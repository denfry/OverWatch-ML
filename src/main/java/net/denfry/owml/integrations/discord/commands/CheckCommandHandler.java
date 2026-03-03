package net.denfry.owml.integrations.discord.commands;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.DiscordCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Discord command handler for checking player status
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class CheckCommandHandler implements DiscordCommandHandler {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();

        // Find player
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer == null || (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline())) {
            event.getHook().editOriginal("вќЊ Player **" + playerName + "** not found!").queue();
            return;
        }

        UUID playerId = offlinePlayer.getUniqueId();
        boolean isOnline = offlinePlayer.isOnline();

        // Get suspicion data
        int suspicionLevel = 0;
        String suspicionStatus = "вњ… Clean";

        if (plugin.getSuspiciousManager() != null) {
            suspicionLevel = plugin.getSuspiciousManager().getSuspicionPoints(playerId);

            if (suspicionLevel >= 80) {
                suspicionStatus = "рџљЁ Critical";
            } else if (suspicionLevel >= 60) {
                suspicionStatus = "вљ пёЏ High";
            } else if (suspicionLevel >= 40) {
                suspicionStatus = "рџџЎ Medium";
            } else if (suspicionLevel >= 20) {
                suspicionStatus = "рџџў Low";
            }
        }

        // Get mining statistics
        String miningStats = "No data available";
        var oreStats = net.denfry.owml.managers.StatsManager.getOreStats(playerId);
        if (!oreStats.isEmpty()) {
            int totalBlocks = oreStats.values().stream().mapToInt(Integer::intValue).sum();
            int diamonds = oreStats.getOrDefault(org.bukkit.Material.DIAMOND_ORE, 0);
            miningStats = String.format("""
                в›ЏпёЏ **Total Blocks Mined:** %d
                рџ’Ћ **Diamonds Found:** %d
                рџ“… **Last Seen:** %s
                """,
                totalBlocks,
                diamonds,
                getLastSeenStatus(offlinePlayer)
            );
        }

        // Get cluster info if available
        String clusterInfo = "";
        if (plugin.getMLManager() != null && plugin.getMLManager().getClusteringEngine() != null) {
            int clusterId = plugin.getMLManager().getClusteringEngine().getPlayerCluster(playerId);
            if (clusterId >= 0) {
                var profile = plugin.getMLManager().getClusteringEngine().getClusterProfile(clusterId);
                if (profile != null) {
                    clusterInfo = String.format("""
                        рџЋЇ **Behavior Cluster:** %d
                        рџ‘Ґ **Cluster Size:** %d
                        рџ“Љ **Risk Level:** %.1f%%
                        """,
                        clusterId,
                        profile.size,
                        profile.calculateRiskLevel() * 100
                    );
                }
            }
        }

        String statusEmoji = isOnline ? "рџџў" : "рџ”ґ";
        String onlineStatus = isOnline ? "**ONLINE**" : "**OFFLINE**";

        String checkMessage = String.format("""
            рџ‘¤ **Player Check: %s**
            %s %s

            рџљЁ **Suspicion Level:** %d (%s)

            %s
            %s

            рџ†” **UUID:** `%s`
            рџ“… **First Joined:** %s
            """,
            playerName,
            statusEmoji, onlineStatus,
            suspicionLevel, suspicionStatus,
            miningStats,
            clusterInfo,
            playerId.toString(),
            offlinePlayer.getFirstPlayed() > 0 ?
                new java.util.Date(offlinePlayer.getFirstPlayed()).toString() : "Unknown"
        );

        event.getHook().editOriginal(checkMessage).queue();
    }

    /**
     * Get the last seen status for a player
     */
    private String getLastSeenStatus(OfflinePlayer offlinePlayer) {
        if (offlinePlayer.isOnline()) {
            return "Online now";
        } else if (offlinePlayer.getLastPlayed() > 0) {
            return new java.util.Date(offlinePlayer.getLastPlayed()).toString();
        } else {
            return "Never";
        }
    }
}
