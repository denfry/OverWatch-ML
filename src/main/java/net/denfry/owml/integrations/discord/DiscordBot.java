package net.denfry.owml.integrations.discord;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.integrations.discord.commands.CheckCommandHandler;
import net.denfry.owml.integrations.discord.commands.StatsCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.denfry.owml.utils.MessageManager;
import org.jetbrains.annotations.NotNull;

public class DiscordBot extends ListenerAdapter {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private JDA jda;
   private boolean enabled = false;
   private String botToken;
   private String notificationChannelId;
   private String commandChannelId;
   private final Map<String, DiscordCommandHandler> commandHandlers = new ConcurrentHashMap<>();

   public DiscordBot() {
      this.initializeCommandHandlers();
   }

   public CompletableFuture<Void> initialize(String token, String notificationChannel, String commandChannel) {
      this.botToken = token;
      this.notificationChannelId = notificationChannel;
      this.commandChannelId = commandChannel;
      return CompletableFuture.runAsync(
         () -> {
            try {
               this.jda = JDABuilder.createDefault(token)
                  .enableIntents(GatewayIntent.GUILD_MESSAGES, new GatewayIntent[]{GatewayIntent.MESSAGE_CONTENT})
                  .addEventListeners(new Object[]{this})
                  .build();
               this.jda.awaitReady();
               this.registerSlashCommands();
               this.enabled = true;
               MessageManager.log("info", "Discord bot initialized successfully");
            } catch (Exception var3x) {
               MessageManager.log("error", "Failed to initialize Discord bot: {ERROR}", "ERROR", var3x.getMessage());
               this.enabled = false;
            }
         }
      );
   }

   private void initializeCommandHandlers() {
      this.commandHandlers.put("stats", new StatsCommandHandler());
      this.commandHandlers.put("check", new CheckCommandHandler());
   }

   private void registerSlashCommands() {
      if (this.jda != null) {
         this.jda
            .updateCommands()
            .addCommands(
               new CommandData[]{
                  Commands.slash("OverWatch-stats", "Get server statistics").addOption(OptionType.STRING, "period", "Time period (hour, day, week)", false),
                  Commands.slash("OverWatch-check", "Check player status").addOption(OptionType.STRING, "player", "Player name", true)
               }
            )
            .queue();
      }
   }

   public void onReady(@NotNull ReadyEvent event) {
      MessageManager.log("info", "Discord bot is ready! Connected to {GUILDS} guilds", "GUILDS", String.valueOf(this.jda.getGuilds().size()));
      this.sendNotification("\ud83e\udd16 **OverWatchML Bot Online**\nServer monitoring active!", "success");
   }

   public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
      if (this.enabled) {
         String commandName = event.getName();
         if (commandName.startsWith("OverWatch-")) {
            if (this.commandChannelId != null && !event.getChannel().getId().equals(this.commandChannelId)) {
               event.reply("❌ Commands can only be used in the designated command channel!").setEphemeral(true).queue();
            } else {
               String subCommand = commandName.substring("OverWatch-".length());
               DiscordCommandHandler handler = this.commandHandlers.get(subCommand);
               if (handler != null) {
                  event.deferReply().queue();

                  try {
                     handler.handle(event);
                  } catch (Exception var6) {
                     event.getHook().editOriginal("❌ Error executing command: " + var6.getMessage()).queue();
                     MessageManager.log("error", "Discord command error: {ERROR}", "ERROR", var6.getMessage());
                  }
               } else {
                  event.getHook().editOriginal("❌ Unknown command!").queue();
               }
            }
         }
      }
   }

   public void sendNotification(String message, String type) {
      if (this.enabled && this.jda != null && this.notificationChannelId != null) {
         TextChannel channel = this.jda.getTextChannelById(this.notificationChannelId);
         if (channel != null) {
            String var5 = type.toLowerCase();

            String emoji = switch (var5) {
               case "alert" -> "\ud83d\udea8";
               case "warning" -> "⚠️";
               case "success" -> "✅";
               case "error" -> "❌";
               case "info" -> "ℹ️";
               default -> "\ud83d\udce2";
            };
            channel.sendMessage(emoji + " " + message)
               .queue(success -> {}, error -> MessageManager.log("error", "Failed to send Discord notification: {ERROR}", "ERROR", error.getMessage()));
         }
      }
   }

   public void sendAlert(String playerName, String alertType, String details, int suspicionLevel) {
      String alertEmoji = suspicionLevel >= 80 ? "\ud83d\udea8" : (suspicionLevel >= 60 ? "⚠️" : "\ud83d\udd14");
      String message = String.format(
         "%s **X-RAY ALERT**\n\ud83d\udc64 **Player:** %s\n\ud83c\udfaf **Type:** %s\n\ud83d\udcca **Suspicion Level:** %d%%\n\ud83d\udcdd **Details:** %s\n\ud83d\udd52 **Time:** <t:%d:F>\n",
         alertEmoji,
         playerName,
         alertType,
         suspicionLevel,
         details,
         System.currentTimeMillis() / 1000L
      );
      this.sendNotification(message, "alert");
   }

   public void sendPerformanceReport(double tps, int onlinePlayers, int suspiciousPlayers) {
      String statusEmoji = tps >= 19.0 ? "\ud83d\udfe2" : (tps >= 15.0 ? "\ud83d\udfe1" : "\ud83d\udd34");
      String message = String.format(
         "\ud83d\udcca **Performance Report**\n%s **TPS:** %.1f\n\ud83d\udc65 **Online Players:** %d\n\ud83d\udc40 **Suspicious Players:** %d\n\ud83d\udcbe **Memory Usage:** %.1f%%\n",
         statusEmoji,
         tps,
         onlinePlayers,
         suspiciousPlayers,
         (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) * 100.0 / Runtime.getRuntime().totalMemory()
      );
      this.sendNotification(message, "info");
   }

   public void shutdown() {
      if (this.jda != null) {
         this.sendNotification("\ud83d\udd0c **OverWatchML Bot Shutting Down**", "info");
         this.jda.shutdownNow();
         this.enabled = false;
      }
   }

   public boolean isEnabled() {
      return this.enabled && this.jda != null && this.jda.getStatus() == Status.CONNECTED;
   }

   public long getPing() {
      return this.jda != null ? this.jda.getGatewayPing() : -1L;
   }

   public int getGuildCount() {
      return this.jda != null ? this.jda.getGuilds().size() : 0;
   }
}
