package net.denfry.owml.integrations.discord;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Discord bot integration for OverWatch-ML.
 * Provides interactive commands and real-time notifications.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class DiscordBot extends ListenerAdapter {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    private JDA jda;
    private boolean enabled = false;
    private String botToken;
    private String notificationChannelId;
    private String commandChannelId;

    // Command handlers
    private final Map<String, DiscordCommandHandler> commandHandlers = new HashMap<>();

    public DiscordBot() {
        initializeCommandHandlers();
    }

    /**
     * Initialize Discord bot
     */
    public CompletableFuture<Void> initialize(String token, String notificationChannel, String commandChannel) {
        this.botToken = token;
        this.notificationChannelId = notificationChannel;
        this.commandChannelId = commandChannel;

        return CompletableFuture.runAsync(() -> {
            try {
                jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .build();

                jda.awaitReady();

                registerSlashCommands();
                enabled = true;

                MessageManager.log("info", "Discord bot initialized successfully");

            } catch (Exception e) {
                MessageManager.log("error", "Failed to initialize Discord bot: {ERROR}", "ERROR", e.getMessage());
                enabled = false;
            }
        });
    }

    /**
     * Initialize command handlers
     */
    private void initializeCommandHandlers() {
        commandHandlers.put("stats", new net.denfry.owml.integrations.discord.commands.StatsCommandHandler());
        commandHandlers.put("check", new net.denfry.owml.integrations.discord.commands.CheckCommandHandler());
        // Other commands will be implemented later
    }

    /**
     * Register slash commands
     */
    private void registerSlashCommands() {
        if (jda == null) return;

        jda.updateCommands().addCommands(
            Commands.slash("OverWatch-stats", "Get server statistics")
                .addOption(OptionType.STRING, "period", "Time period (hour, day, week)", false),

            Commands.slash("OverWatch-check", "Check player status")
                .addOption(OptionType.STRING, "player", "Player name", true)
        ).queue();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        MessageManager.log("info", "Discord bot is ready! Connected to {GUILDS} guilds",
            "GUILDS", String.valueOf(jda.getGuilds().size()));

        sendNotification("рџ¤– **OverWatchML Bot Online**\nServer monitoring active!", "success");
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!enabled) return;

        String commandName = event.getName();
        if (!commandName.startsWith("OverWatch-")) return;

        // Check if command is from allowed channel
        if (commandChannelId != null && !event.getChannel().getId().equals(commandChannelId)) {
            event.reply("вќЊ Commands can only be used in the designated command channel!")
                .setEphemeral(true).queue();
            return;
        }

        String subCommand = commandName.substring("OverWatch-".length());
        DiscordCommandHandler handler = commandHandlers.get(subCommand);

        if (handler != null) {
            event.deferReply().queue();
            try {
                handler.handle(event);
            } catch (Exception e) {
                event.getHook().editOriginal("вќЊ Error executing command: " + e.getMessage()).queue();
                MessageManager.log("error", "Discord command error: {ERROR}", "ERROR", e.getMessage());
            }
        } else {
            event.getHook().editOriginal("вќЊ Unknown command!").queue();
        }
    }

    /**
     * Send notification to Discord
     */
    public void sendNotification(String message, String type) {
        if (!enabled || jda == null || notificationChannelId == null) return;

        TextChannel channel = jda.getTextChannelById(notificationChannelId);
        if (channel == null) return;

        String emoji = switch (type.toLowerCase()) {
            case "alert" -> "рџљЁ";
            case "warning" -> "вљ пёЏ";
            case "success" -> "вњ…";
            case "error" -> "вќЊ";
            case "info" -> "в„№пёЏ";
            default -> "рџ“ў";
        };

        channel.sendMessage(emoji + " " + message).queue(
            success -> {},
            error -> MessageManager.log("error", "Failed to send Discord notification: {ERROR}", "ERROR", error.getMessage())
        );
    }

    /**
     * Send alert notification
     */
    public void sendAlert(String playerName, String alertType, String details, int suspicionLevel) {
        String alertEmoji = suspicionLevel >= 80 ? "рџљЁ" : suspicionLevel >= 60 ? "вљ пёЏ" : "рџ””";

        String message = String.format("""
            %s **X-RAY ALERT**
            рџ‘¤ **Player:** %s
            рџЋЇ **Type:** %s
            рџ“Љ **Suspicion Level:** %d%%
            рџ“ќ **Details:** %s
            рџ•’ **Time:** <t:%d:F>
            """,
            alertEmoji, playerName, alertType, suspicionLevel, details,
            System.currentTimeMillis() / 1000
        );

        sendNotification(message, "alert");
    }

    /**
     * Send performance report
     */
    public void sendPerformanceReport(double tps, int onlinePlayers, int suspiciousPlayers) {
        String statusEmoji = tps >= 19.0 ? "рџџў" : tps >= 15.0 ? "рџџЎ" : "рџ”ґ";

        String message = String.format("""
            рџ“Љ **Performance Report**
            %s **TPS:** %.1f
            рџ‘Ґ **Online Players:** %d
            рџ‘Ђ **Suspicious Players:** %d
            рџ’ѕ **Memory Usage:** %.1f%%
            """,
            statusEmoji, tps, onlinePlayers, suspiciousPlayers,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) * 100.0 / Runtime.getRuntime().totalMemory()
        );

        sendNotification(message, "info");
    }

    /**
     * Shutdown Discord bot
     */
    public void shutdown() {
        if (jda != null) {
            sendNotification("рџ”Њ **OverWatchML Bot Shutting Down**", "info");
            jda.shutdownNow();
            enabled = false;
        }
    }

    /**
     * Check if Discord bot is enabled
     */
    public boolean isEnabled() {
        return enabled && jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    /**
     * Get bot ping
     */
    public long getPing() {
        return jda != null ? jda.getGatewayPing() : -1;
    }

    /**
     * Get connected guilds
     */
    public int getGuildCount() {
        return jda != null ? jda.getGuilds().size() : 0;
    }
}
