package net.denfry.owml.integrations;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.DiscordBot;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for all external integrations (Discord, anti-cheat plugins, etc.)
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class IntegrationManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Discord integration
    private DiscordBot discordBot;


    public IntegrationManager() {
        initializeIntegrations();
    }

    /**
     * Initialize all integrations
     */
    private void initializeIntegrations() {
        // Initialize Discord bot
        initializeDiscordBot();

        MessageManager.log("info", "Integration manager initialized with {COUNT} integrations",
            "COUNT", String.valueOf(getActiveIntegrationsCount()));
    }

    /**
     * Initialize Discord bot
     */
    private void initializeDiscordBot() {
        try {
            String botToken = plugin.getConfig().getString("integrations.discord.bot-token");
            String notificationChannel = plugin.getConfig().getString("integrations.discord.notification-channel");
            String commandChannel = plugin.getConfig().getString("integrations.discord.command-channel");

            if (botToken != null && !botToken.isEmpty()) {
                discordBot = new DiscordBot();

                // Initialize asynchronously
                discordBot.initialize(botToken, notificationChannel, commandChannel)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            MessageManager.log("error", "Failed to initialize Discord bot: {ERROR}",
                                "ERROR", throwable.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            MessageManager.log("error", "Failed to initialize Discord integration: {ERROR}",
                "ERROR", e.getMessage());
        }
    }


    /**
     * Send alert to all integrations
     */
    public void sendAlert(String playerName, String alertType, String details, int suspicionLevel) {
        // Send to Discord
        if (discordBot != null && discordBot.isEnabled()) {
            discordBot.sendAlert(playerName, alertType, details, suspicionLevel);
        }
    }

    /**
     * Send performance report
     */
    public void sendPerformanceReport(double tps, int onlinePlayers, int suspiciousPlayers) {
        if (discordBot != null && discordBot.isEnabled()) {
            discordBot.sendPerformanceReport(tps, onlinePlayers, suspiciousPlayers);
        }
    }

    /**
     * Get combined violation level from all integrations
     */
    public double getCombinedViolationLevel(Player player) {
        // No anti-cheat integrations available
        return 0.0;
    }

    /**
     * Check if player is exempted in any integration
     */
    public boolean isPlayerExempted(Player player) {
        // No anti-cheat integrations available
        return false;
    }

    /**
     * Request ban through available integrations
     */
    public boolean requestBan(Player player, String reason, long duration) {
        // No anti-cheat integrations available
        return false;
    }

    /**
     * Request kick through available integrations
     */
    public boolean requestKick(Player player, String reason) {
        // No anti-cheat integrations available
        return false;
    }

    /**
     * Get Discord bot instance
     */
    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    /**
     * Get anti-cheat integrations (none available)
     */
    public List<?> getAntiCheatIntegrations() {
        return new ArrayList<>();
    }

    /**
     * Get integration status information
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();

        status.append("рџ”— **Integration Status**\n\n");

        // Discord status
        if (discordBot != null && discordBot.isEnabled()) {
            status.append(String.format("рџ¤– **Discord Bot:** вњ… Active\n"));
            status.append(String.format("   рџ“Љ Ping: %dms\n", discordBot.getPing()));
            status.append(String.format("   рџЏ  Guilds: %d\n", discordBot.getGuildCount()));
        } else {
            status.append("рџ¤– **Discord Bot:** вќЊ Inactive\n");
        }

        status.append("\nрџ›ЎпёЏ **Anti-Cheat Integrations**\n");
        status.append("   No anti-cheat integrations configured\n");

        return status.toString();
    }

    /**
     * Get count of active integrations
     */
    public int getActiveIntegrationsCount() {
        int count = 0;

        if (discordBot != null && discordBot.isEnabled()) count++;

        return count;
    }

    /**
     * Shutdown all integrations
     */
    public void shutdown() {
        // Shutdown Discord bot
        if (discordBot != null) {
            discordBot.shutdown();
        }


        MessageManager.log("info", "All integrations shut down");
    }
}
