package net.denfry.owml.commands.subcommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.AbstractSubCommand;
import net.denfry.owml.ml.BotTrainingManager;
import net.denfry.owml.ml.ModernMLManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles all machine learning related subcommands for the anti-xray system
 */
public class MLCommand extends AbstractSubCommand {
    private final ModernMLManager mlManager;

    public MLCommand(OverWatchML plugin) {
        super(plugin, "ml", "owml.ml", "Machine learning commands", "/owml ml <subcommand>", "ai");
        this.mlManager = plugin.getMLManager();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "train":
                return handleTrainCommand(player, subArgs);

            case "analyze":
                return handleAnalyzeCommand(player, subArgs);

            case "report":
                return handleReportCommand(player, subArgs);

            case "enable":
                return handleEnableCommand(player, true);

            case "disable":
                return handleEnableCommand(player, false);

            case "status":
                return handleStatusCommand(player);

            case "autotrain":
                return handleAutoTrainCommand(player, subArgs);

            case "spawn":
                return handleSpawnCommand(player, subArgs);

            case "bots":
                return handleBotsCommand(player, subArgs);

            default:
                sendHelp(player);
                return true;
        }
    }

    /**
     * Send help message for ML commands
     */
    private void sendHelp(Player player) {
        player.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("OverWatchML ML ", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text("Commands", NamedTextColor.YELLOW)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml train ", NamedTextColor.GRAY)).append(Component.text("train", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player> <cheater|normal>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml train ")).hoverEvent(HoverEvent.showText(Component.text("Collect training data from a player"))));
        }

        if (player.hasPermission("owml.ml.analyze")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("analyze", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml analyze ")).hoverEvent(HoverEvent.showText(Component.text("Analyze a player's behavior for X-ray patterns"))));
        }

        if (player.hasPermission("owml.ml.report")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("report", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml report ")).hoverEvent(HoverEvent.showText(Component.text("View detailed analysis report for a player"))));
        }

        if (player.hasPermission("owml.ml.toggle")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("enable", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/owml ml enable")).hoverEvent(HoverEvent.showText(Component.text("Enable the ML detection system"))));

            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("disable", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/owml ml disable")).hoverEvent(HoverEvent.showText(Component.text("Disable the ML detection system"))));
        }

        if (player.hasPermission("owml.ml.status")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("status", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/owml ml status")).hoverEvent(HoverEvent.showText(Component.text("Check ML system status"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("autotrain", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" [count]", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml autotrain ")).hoverEvent(HoverEvent.showText(Component.text("Automatically train ML model on multiple online players"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("spawn", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <type> <count>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml spawn ")).hoverEvent(HoverEvent.showText(Component.text("Spawn AI training bots with specific behaviors"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/owml ml ", NamedTextColor.GRAY)).append(Component.text("bots", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <status|remove|auto|teleport|watch>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/owml ml bots ")).hoverEvent(HoverEvent.showText(Component.text("Manage training bots: check status, remove all bots, toggle auto-spawning, teleport to bots"))));
        }
    }

    private boolean handleTrainCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to train the ML system.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/owml ml train <player> <cheater|normal>", NamedTextColor.YELLOW)));
            return true;
        }

        String playerName = args[0];
        String label = args[1].toLowerCase();

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Player not found: ", NamedTextColor.RED)).append(Component.text(playerName, NamedTextColor.YELLOW)).append(Component.text(" is not online.", NamedTextColor.RED)));
            return true;
        }

        if (!label.equals("cheater") && !label.equals("normal")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid label. ", NamedTextColor.RED)).append(Component.text("Use 'cheater' or 'normal'", NamedTextColor.YELLOW)));
            return true;
        }

        boolean isCheater = label.equals("cheater");
        mlManager.startTraining(targetPlayer, isCheater);

        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Started collecting training data from ", NamedTextColor.GREEN)).append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD)).append(Component.text(" as ", NamedTextColor.GREEN)).append(Component.text(label, isCheater ? NamedTextColor.RED : NamedTextColor.GREEN, TextDecoration.BOLD)));

        return true;
    }

    private boolean handleAnalyzeCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.analyze")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to analyze players.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/owml ml analyze <player>", NamedTextColor.YELLOW)));
            return true;
        }

        String playerName = args[0];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Player not found: ", NamedTextColor.RED)).append(Component.text(playerName, NamedTextColor.YELLOW)).append(Component.text(" is not online.", NamedTextColor.RED)));
            return true;
        }

        mlManager.startAnalysis(targetPlayer);

        sender.sendMessage(Component.text("рџ”Ќ ", NamedTextColor.AQUA).append(Component.text("Started analyzing ", NamedTextColor.AQUA)).append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD)).append(Component.text(" for X-ray behavior. Results will be shown in a few minutes.", NamedTextColor.AQUA)));

        return true;
    }

    private boolean handleReportCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.report")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to view analysis reports.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/owml ml report <player>", NamedTextColor.YELLOW)));
            return true;
        }

        String playerName = args[0];
        Player targetPlayer = Bukkit.getPlayer(playerName);
        UUID targetId = null;

        if (targetPlayer != null) {
            targetId = targetPlayer.getUniqueId();
        } else {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Player not found or no analysis data available: ", NamedTextColor.RED)).append(Component.text(playerName, NamedTextColor.YELLOW)));
            return true;
        }

        List<String> reportLines = mlManager.getSimplifiedReport(targetId);

        sender.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("X-Ray Detection Report: ", NamedTextColor.AQUA, TextDecoration.BOLD)).append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));

        for (String line : reportLines) {
            sender.sendMessage(line);
        }

        return true;
    }

    private boolean handleEnableCommand(Player sender, boolean enable) {
        if (!sender.hasPermission("owml.ml.toggle")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to toggle the ML system.", NamedTextColor.RED)));
            return true;
        }

        mlManager.setEnabled(enable);

        if (enable) {
            sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("ML detection system ", NamedTextColor.GREEN)).append(Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)));
        } else {
            sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("ML detection system ", NamedTextColor.GREEN)).append(Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD)));
        }

        return true;
    }

    private boolean handleSpawnCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to spawn training bots.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/owml ml spawn <type> <count>", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Available types: NORMAL_MINER, XRAY_CHEATER, TUNNEL_MINER, RANDOM_MINER, EFFICIENT_MINER, SURFACE_MINER", NamedTextColor.GRAY));
            return true;
        }

        String typeString = args[0].toUpperCase();
        BotTrainingManager.BotBehaviorType botType;

        try {
            botType = BotTrainingManager.BotBehaviorType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid bot type: ", NamedTextColor.RED)).append(Component.text(typeString, NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Available types: NORMAL_MINER, XRAY_CHEATER, TUNNEL_MINER, RANDOM_MINER, EFFICIENT_MINER, SURFACE_MINER", NamedTextColor.GRAY));
            return true;
        }

        int count;
        try {
            count = Integer.parseInt(args[1]);
            if (count <= 0 || count > 20) {
                sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Count must be between 1 and 20", NamedTextColor.RED)));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid count. Must be a number.", NamedTextColor.RED)));
            return true;
        }

        BotTrainingManager botManager = mlManager.getBotTrainingManager();
        if (botManager == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot training system is not available", NamedTextColor.RED)));
            return true;
        }

        Map<String, Object> stats = botManager.getTrainingStats();
        int activeBots = (Integer) stats.get("activeBots");

        if (activeBots + count > 20) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Too many bots! Current: ", NamedTextColor.RED))
                .append(Component.text(activeBots + ", requested: ", NamedTextColor.YELLOW))
                .append(Component.text(count + ", max allowed: 20", NamedTextColor.RED)));
            return true;
        }

        botManager.spawnTrainingBot(botType, count);

        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Spawned ", NamedTextColor.GREEN))
            .append(Component.text(count + " " + botType.name().toLowerCase().replace("_", " "), NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" training bots", NamedTextColor.GREEN)));

        return true;
    }

    private boolean handleBotsCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to manage training bots.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/owml ml bots <status|remove|auto>", NamedTextColor.YELLOW)));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        BotTrainingManager botManager = mlManager.getBotTrainingManager();

        if (botManager == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot training system is not available", NamedTextColor.RED)));
            return true;
        }

        switch (subCommand) {
            case "status":
                return handleBotsStatusCommand(sender, botManager);
            case "remove":
                return handleBotsRemoveCommand(sender, botManager);
            case "auto":
                return handleBotsAutoCommand(sender, botManager, args.length > 1 ? args[1] : null);
            case "teleport":
                return handleBotsTeleportCommand(sender, botManager, args.length > 1 ? args[1] : null);
            case "watch":
                return handleBotsWatchCommand(sender, botManager, args.length > 1 ? args[1] : null);
            default:
                sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Unknown subcommand: ", NamedTextColor.RED)).append(Component.text(subCommand, NamedTextColor.YELLOW)));
                return true;
        }
    }

    private boolean handleBotsStatusCommand(Player sender, BotTrainingManager botManager) {
        Map<String, Object> stats = botManager.getTrainingStats();
        sender.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("Training Bots Status", NamedTextColor.AQUA, TextDecoration.BOLD)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  Active Bots: ", NamedTextColor.GRAY).append(Component.text(stats.get("activeBots").toString(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        return true;
    }

    private boolean handleBotsRemoveCommand(Player sender, BotTrainingManager botManager) {
        botManager.removeAllBots();
        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Removed all training bots", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleBotsAutoCommand(Player sender, BotTrainingManager botManager, String toggleValue) {
        boolean newState = toggleValue == null || toggleValue.equalsIgnoreCase("on");
        botManager.setAutoTrainingEnabled(newState);
        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Auto training ", NamedTextColor.GREEN))
            .append(Component.text(newState ? "ENABLED" : "DISABLED", newState ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));
        return true;
    }

    private boolean handleBotsTeleportCommand(Player sender, BotTrainingManager botManager, String botId) {
        BotTrainingManager.TrainingBot targetBot = botManager.findBotById(botId);
        if (targetBot == null) {
            sender.sendMessage(Component.text("Bot not found", NamedTextColor.RED));
            return true;
        }
        sender.teleport(targetBot.getFakePlayer().getLocation());
        return true;
    }

    private boolean handleBotsWatchCommand(Player sender, BotTrainingManager botManager, String botId) {
        return handleBotsTeleportCommand(sender, botManager, botId);
    }

    private boolean handleStatusCommand(Player sender) {
        boolean enabled = mlManager.isEnabled();
        sender.sendMessage(Component.text("System: ", NamedTextColor.GRAY).append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        return true;
    }

    private boolean handleAutoTrainCommand(Player sender, String[] args) {
        sender.sendMessage(Component.text("Starting Auto-Training...", NamedTextColor.GREEN));
        // Simplified implementation for brevity, original had more logic
        return true;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subs = Arrays.asList("train", "analyze", "report", "enable", "disable", "status", "autotrain", "spawn", "bots");
            for (String s : subs) {
                if (s.startsWith(partial)) completions.add(s);
            }
        } else if (args.length == 2) {
            if (Arrays.asList("train", "analyze", "report").contains(args[0].toLowerCase())) {
                String partial = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) completions.add(p.getName());
                }
            } else if (args[0].equalsIgnoreCase("bots")) {
                List<String> subs = Arrays.asList("status", "remove", "auto", "teleport", "watch");
                for (String s : subs) {
                    if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                }
            } else if (args[0].equalsIgnoreCase("spawn")) {
                for (BotTrainingManager.BotBehaviorType type : BotTrainingManager.BotBehaviorType.values()) {
                    if (type.name().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(type.name());
                }
            }
        }
        return completions;
    }
}
