package net.denfry.owml.commands.subcommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.AbstractSubCommand;
import net.denfry.owml.ml.BotTrainingManager;
import net.denfry.owml.ml.ModernMLManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MLCommand extends AbstractSubCommand {
   private final ModernMLManager mlManager;

   public MLCommand(OverWatchML plugin) {
      super(plugin, "ml", "owml.ml", "Machine learning commands", "/owml ml <subcommand>", "ai");
      this.mlManager = plugin.getMLManager();
   }

   @Override
   public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.sendHelp(player);
            return true;
         } else {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            switch (subCommand) {
               case "train":
                  return this.handleTrainCommand(player, subArgs);
               case "analyze":
                  return this.handleAnalyzeCommand(player, subArgs);
               case "report":
                  return this.handleReportCommand(player, subArgs);
               case "enable":
                  return this.handleEnableCommand(player, true);
               case "disable":
                  return this.handleEnableCommand(player, false);
               case "status":
                  return this.handleStatusCommand(player);
               case "autotrain":
                  return this.handleAutoTrainCommand(player, subArgs);
               case "spawn":
                  return this.handleSpawnCommand(player, subArgs);
               case "bots":
                  return this.handleBotsCommand(player, subArgs);
               default:
                  this.sendHelp(player);
                  return true;
            }
         }
      } else {
         sender.sendMessage("This command can only be used by players.");
         return true;
      }
   }

   private void sendHelp(Player player) {
      player.sendMessage(
         ((TextComponent)((TextComponent)Component.text("▶ ", NamedTextColor.GOLD)
                  .append(Component.text("OverWatchML ML ", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("Commands", NamedTextColor.YELLOW)))
            .append(Component.text(" ◀", NamedTextColor.GOLD))
      );
      if (player.hasPermission("owml.ml.train")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml train ", NamedTextColor.GRAY)))
                        .append(Component.text("train", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" <player> <cheater|normal>", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml train ")))
               .hoverEvent(HoverEvent.showText(Component.text("Collect training data from a player")))
         );
      }

      if (player.hasPermission("owml.ml.analyze")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                        .append(Component.text("analyze", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" <player>", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml analyze ")))
               .hoverEvent(HoverEvent.showText(Component.text("Analyze a player's behavior for X-ray patterns")))
         );
      }

      if (player.hasPermission("owml.ml.report")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                        .append(Component.text("report", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" <player>", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml report ")))
               .hoverEvent(HoverEvent.showText(Component.text("View detailed analysis report for a player")))
         );
      }

      if (player.hasPermission("owml.ml.toggle")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                        .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                     .append(Component.text("enable", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                  .clickEvent(ClickEvent.runCommand("/owml ml enable")))
               .hoverEvent(HoverEvent.showText(Component.text("Enable the ML detection system")))
         );
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                        .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                     .append(Component.text("disable", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                  .clickEvent(ClickEvent.runCommand("/owml ml disable")))
               .hoverEvent(HoverEvent.showText(Component.text("Disable the ML detection system")))
         );
      }

      if (player.hasPermission("owml.ml.status")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                        .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                     .append(Component.text("status", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                  .clickEvent(ClickEvent.runCommand("/owml ml status")))
               .hoverEvent(HoverEvent.showText(Component.text("Check ML system status")))
         );
      }

      if (player.hasPermission("owml.ml.train")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                        .append(Component.text("autotrain", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" [count]", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml autotrain ")))
               .hoverEvent(HoverEvent.showText(Component.text("Automatically train ML model on multiple online players")))
         );
      }

      if (player.hasPermission("owml.ml.train")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                        .append(Component.text("spawn", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" <type> <count>", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml spawn ")))
               .hoverEvent(HoverEvent.showText(Component.text("Spawn AI training bots with specific behaviors")))
         );
      }

      if (player.hasPermission("owml.ml.train")) {
         player.sendMessage(
            ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("  • ", NamedTextColor.GRAY)
                           .append(Component.text("/owml ml ", NamedTextColor.GRAY)))
                        .append(Component.text("bots", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" <status|remove|auto|teleport|watch>", NamedTextColor.YELLOW)))
                  .clickEvent(ClickEvent.suggestCommand("/owml ml bots ")))
               .hoverEvent(HoverEvent.showText(Component.text("Manage training bots: check status, remove all bots, toggle auto-spawning, teleport to bots")))
         );
      }
   }

   private boolean handleTrainCommand(Player sender, String[] args) {
      if (!sender.hasPermission("owml.ml.train")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to train the ML system.", NamedTextColor.RED))
         );
         return true;
      } else if (args.length < 2) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)))
               .append(Component.text("/owml ml train <player> <cheater|normal>", NamedTextColor.YELLOW))
         );
         return true;
      } else {
         String playerName = args[0];
         String label = args[1].toLowerCase();
         Player targetPlayer = Bukkit.getPlayer(playerName);
         if (targetPlayer == null) {
            sender.sendMessage(
               ((TextComponent)((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Player not found: ", NamedTextColor.RED)))
                     .append(Component.text(playerName, NamedTextColor.YELLOW)))
                  .append(Component.text(" is not online.", NamedTextColor.RED))
            );
            return true;
         } else if (!label.equals("cheater") && !label.equals("normal")) {
            sender.sendMessage(
               ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Invalid label. ", NamedTextColor.RED)))
                  .append(Component.text("Use 'cheater' or 'normal'", NamedTextColor.YELLOW))
            );
            return true;
         } else {
            boolean isCheater = label.equals("cheater");
            this.mlManager.startTraining(targetPlayer, isCheater);
            sender.sendMessage(
               ((TextComponent)((TextComponent)((TextComponent)Component.text("✓ ", NamedTextColor.GREEN)
                           .append(Component.text("Started collecting training data from ", NamedTextColor.GREEN)))
                        .append(Component.text(playerName, NamedTextColor.YELLOW, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(" as ", NamedTextColor.GREEN)))
                  .append(Component.text(label, isCheater ? NamedTextColor.RED : NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD}))
            );
            return true;
         }
      }
   }

   private boolean handleAnalyzeCommand(Player sender, String[] args) {
      if (!sender.hasPermission("owml.ml.analyze")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to analyze players.", NamedTextColor.RED))
         );
         return true;
      } else if (args.length < 1) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)))
               .append(Component.text("/owml ml analyze <player>", NamedTextColor.YELLOW))
         );
         return true;
      } else {
         String playerName = args[0];
         Player targetPlayer = Bukkit.getPlayer(playerName);
         if (targetPlayer == null) {
            sender.sendMessage(
               ((TextComponent)((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Player not found: ", NamedTextColor.RED)))
                     .append(Component.text(playerName, NamedTextColor.YELLOW)))
                  .append(Component.text(" is not online.", NamedTextColor.RED))
            );
            return true;
         } else {
            this.mlManager.startAnalysis(targetPlayer);
            sender.sendMessage(
               ((TextComponent)((TextComponent)Component.text("\ud83d\udd0d ", NamedTextColor.AQUA)
                        .append(Component.text("Started analyzing ", NamedTextColor.AQUA)))
                     .append(Component.text(playerName, NamedTextColor.YELLOW, new TextDecoration[]{TextDecoration.BOLD})))
                  .append(Component.text(" for X-ray behavior. Results will be shown in a few minutes.", NamedTextColor.AQUA))
            );
            return true;
         }
      }
   }

   private boolean handleReportCommand(Player sender, String[] args) {
      if (!sender.hasPermission("owml.ml.report")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to view analysis reports.", NamedTextColor.RED))
         );
         return true;
      } else if (args.length < 1) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)))
               .append(Component.text("/owml ml report <player>", NamedTextColor.YELLOW))
         );
         return true;
      } else {
         String playerName = args[0];
         Player targetPlayer = Bukkit.getPlayer(playerName);
         UUID targetId = null;
         if (targetPlayer == null) {
            sender.sendMessage(
               ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                     .append(Component.text("Player not found or no analysis data available: ", NamedTextColor.RED)))
                  .append(Component.text(playerName, NamedTextColor.YELLOW))
            );
            return true;
         } else {
            targetId = targetPlayer.getUniqueId();
            List<String> reportLines = this.mlManager.getSimplifiedReport(targetId);
            sender.sendMessage(
               ((TextComponent)((TextComponent)Component.text("▶ ", NamedTextColor.GOLD)
                        .append(Component.text("X-Ray Detection Report: ", NamedTextColor.AQUA, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text(playerName, NamedTextColor.YELLOW, new TextDecoration[]{TextDecoration.BOLD})))
                  .append(Component.text(" ◀", NamedTextColor.GOLD))
            );

            for (String line : reportLines) {
               sender.sendMessage(line);
            }

            return true;
         }
      }
   }

   private boolean handleEnableCommand(Player sender, boolean enable) {
      if (!sender.hasPermission("owml.ml.toggle")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to toggle the ML system.", NamedTextColor.RED))
         );
         return true;
      } else {
         this.mlManager.setEnabled(enable);
         if (enable) {
            sender.sendMessage(
               ((TextComponent)Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("ML detection system ", NamedTextColor.GREEN)))
                  .append(Component.text("ENABLED", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD}))
            );
         } else {
            sender.sendMessage(
               ((TextComponent)Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("ML detection system ", NamedTextColor.GREEN)))
                  .append(Component.text("DISABLED", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD}))
            );
         }

         return true;
      }
   }

   private boolean handleSpawnCommand(Player sender, String[] args) {
      if (!sender.hasPermission("owml.ml.train")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to spawn training bots.", NamedTextColor.RED))
         );
         return true;
      } else if (args.length < 2) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)))
               .append(Component.text("/owml ml spawn <type> <count>", NamedTextColor.YELLOW))
         );
         sender.sendMessage(
            Component.text("Available types: NORMAL_MINER, XRAY_CHEATER, TUNNEL_MINER, RANDOM_MINER, EFFICIENT_MINER, SURFACE_MINER", NamedTextColor.GRAY)
         );
         return true;
      } else {
         String typeString = args[0].toUpperCase();

         BotTrainingManager.BotBehaviorType botType;
         try {
            botType = BotTrainingManager.BotBehaviorType.valueOf(typeString);
         } catch (IllegalArgumentException var9) {
            sender.sendMessage(
               ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Invalid bot type: ", NamedTextColor.RED)))
                  .append(Component.text(typeString, NamedTextColor.YELLOW))
            );
            sender.sendMessage(
               Component.text("Available types: NORMAL_MINER, XRAY_CHEATER, TUNNEL_MINER, RANDOM_MINER, EFFICIENT_MINER, SURFACE_MINER", NamedTextColor.GRAY)
            );
            return true;
         }

         int count;
         try {
            count = Integer.parseInt(args[1]);
            if (count <= 0 || count > 20) {
               sender.sendMessage(Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Count must be between 1 and 20", NamedTextColor.RED)));
               return true;
            }
         } catch (NumberFormatException var10) {
            sender.sendMessage(Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Invalid count. Must be a number.", NamedTextColor.RED)));
            return true;
         }

         BotTrainingManager botManager = this.mlManager.getBotTrainingManager();
         if (botManager == null) {
            sender.sendMessage(Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Bot training system is not available", NamedTextColor.RED)));
            return true;
         } else {
            Map<String, Object> stats = botManager.getTrainingStats();
            int activeBots = (Integer)stats.get("activeBots");
            if (activeBots + count > 20) {
               sender.sendMessage(
                  ((TextComponent)((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                           .append(Component.text("Too many bots! Current: ", NamedTextColor.RED)))
                        .append(Component.text(activeBots + ", requested: ", NamedTextColor.YELLOW)))
                     .append(Component.text(count + ", max allowed: 20", NamedTextColor.RED))
               );
               return true;
            } else {
               botManager.spawnTrainingBot(botType, count);
               sender.sendMessage(
                  ((TextComponent)((TextComponent)Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("Spawned ", NamedTextColor.GREEN)))
                        .append(
                           Component.text(
                              count + " " + botType.name().toLowerCase().replace("_", " "), NamedTextColor.YELLOW, new TextDecoration[]{TextDecoration.BOLD}
                           )
                        ))
                     .append(Component.text(" training bots", NamedTextColor.GREEN))
               );
               return true;
            }
         }
      }
   }

   private boolean handleBotsCommand(Player sender, String[] args) {
      if (!sender.hasPermission("owml.ml.train")) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD)
                  .append(Component.text("Permission Denied: ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("You don't have permission to manage training bots.", NamedTextColor.RED))
         );
         return true;
      } else if (args.length < 1) {
         sender.sendMessage(
            ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)))
               .append(Component.text("/owml ml bots <status|remove|auto>", NamedTextColor.YELLOW))
         );
         return true;
      } else {
         String subCommand = args[0].toLowerCase();
         BotTrainingManager botManager = this.mlManager.getBotTrainingManager();
         if (botManager == null) {
            sender.sendMessage(Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Bot training system is not available", NamedTextColor.RED)));
            return true;
         } else {
            switch (subCommand) {
               case "status":
                  return this.handleBotsStatusCommand(sender, botManager);
               case "remove":
                  return this.handleBotsRemoveCommand(sender, botManager);
               case "auto":
                  return this.handleBotsAutoCommand(sender, botManager, args.length > 1 ? args[1] : null);
               case "teleport":
                  return this.handleBotsTeleportCommand(sender, botManager, args.length > 1 ? args[1] : null);
               case "watch":
                  return this.handleBotsWatchCommand(sender, botManager, args.length > 1 ? args[1] : null);
               default:
                  sender.sendMessage(
                     ((TextComponent)Component.text("⚠ ", NamedTextColor.GOLD).append(Component.text("Unknown subcommand: ", NamedTextColor.RED)))
                        .append(Component.text(subCommand, NamedTextColor.YELLOW))
                  );
                  return true;
            }
         }
      }
   }

   private boolean handleBotsStatusCommand(Player sender, BotTrainingManager botManager) {
      Map<String, Object> stats = botManager.getTrainingStats();
      sender.sendMessage(
         ((TextComponent)Component.text("▶ ", NamedTextColor.GOLD)
               .append(Component.text("Training Bots Status", NamedTextColor.AQUA, new TextDecoration[]{TextDecoration.BOLD})))
            .append(Component.text(" ◀", NamedTextColor.GOLD))
      );
      sender.sendMessage(
         Component.text("  Active Bots: ", NamedTextColor.GRAY)
            .append(Component.text(stats.get("activeBots").toString(), NamedTextColor.YELLOW, new TextDecoration[]{TextDecoration.BOLD}))
      );
      return true;
   }

   private boolean handleBotsRemoveCommand(Player sender, BotTrainingManager botManager) {
      botManager.removeAllBots();
      sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("Removed all training bots", NamedTextColor.GREEN)));
      return true;
   }

   private boolean handleBotsAutoCommand(Player sender, BotTrainingManager botManager, String toggleValue) {
      boolean newState = toggleValue == null || toggleValue.equalsIgnoreCase("on");
      botManager.setAutoTrainingEnabled(newState);
      sender.sendMessage(
         ((TextComponent)Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("Auto training ", NamedTextColor.GREEN)))
            .append(
               Component.text(
                  newState ? "ENABLED" : "DISABLED", newState ? NamedTextColor.GREEN : NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD}
               )
            )
      );
      return true;
   }

   private boolean handleBotsTeleportCommand(Player sender, BotTrainingManager botManager, String botId) {
      BotTrainingManager.TrainingBot targetBot = botManager.findBotById(botId);
      if (targetBot == null) {
         sender.sendMessage(Component.text("Bot not found", NamedTextColor.RED));
         return true;
      } else {
         sender.teleportAsync(targetBot.getFakePlayer().getLocation());
         return true;
      }
   }

   private boolean handleBotsWatchCommand(Player sender, BotTrainingManager botManager, String botId) {
      return this.handleBotsTeleportCommand(sender, botManager, botId);
   }

   private boolean handleStatusCommand(Player sender) {
      boolean enabled = this.mlManager.isEnabled();
      sender.sendMessage(
         Component.text("System: ", NamedTextColor.GRAY)
            .append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
      );
      return true;
   }

   private boolean handleAutoTrainCommand(Player sender, String[] args) {
      sender.sendMessage(Component.text("Starting Auto-Training...", NamedTextColor.GREEN));
      return true;
   }

   @Nullable
   @Override
   public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
      List<String> completions = new ArrayList<>();
      if (args.length == 1) {
         String partial = args[0].toLowerCase();

         for (String s : Arrays.asList("train", "analyze", "report", "enable", "disable", "status", "autotrain", "spawn", "bots")) {
            if (s.startsWith(partial)) {
               completions.add(s);
            }
         }
      } else if (args.length == 2) {
         if (Arrays.asList("train", "analyze", "report").contains(args[0].toLowerCase())) {
            String partial = args[1].toLowerCase();

            for (Player p : Bukkit.getOnlinePlayers()) {
               if (p.getName().toLowerCase().startsWith(partial)) {
                  completions.add(p.getName());
               }
            }
         } else if (args[0].equalsIgnoreCase("bots")) {
            for (String sx : Arrays.asList("status", "remove", "auto", "teleport", "watch")) {
               if (sx.startsWith(args[1].toLowerCase())) {
                  completions.add(sx);
               }
            }
         } else if (args[0].equalsIgnoreCase("spawn")) {
            for (BotTrainingManager.BotBehaviorType type : BotTrainingManager.BotBehaviorType.values()) {
               if (type.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                  completions.add(type.name());
               }
            }
         }
      }

      return completions;
   }
}
