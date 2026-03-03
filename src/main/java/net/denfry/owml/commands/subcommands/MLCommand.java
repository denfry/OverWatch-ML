package net.denfry.owml.commands.subcommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.BotTrainingManager;
import net.denfry.owml.ml.ModernMLManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Handles all machine learning related subcommands for the anti-xray system
 */
public class MLCommand implements CommandExecutor, TabCompleter {
    private final OverWatchML plugin;
    private final ModernMLManager mlManager;

    public MLCommand(OverWatchML plugin, ModernMLManager mlManager) {
        this.plugin = plugin;
        this.mlManager = mlManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml train ", NamedTextColor.GRAY)).append(Component.text("train", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player> <cheater|normal>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml train ")).hoverEvent(HoverEvent.showText(Component.text("Collect training data from a player"))));
        }

        if (player.hasPermission("owml.ml.analyze")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("analyze", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml analyze ")).hoverEvent(HoverEvent.showText(Component.text("Analyze a player's behavior for X-ray patterns"))));
        }

        if (player.hasPermission("owml.ml.report")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("report", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <player>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml report ")).hoverEvent(HoverEvent.showText(Component.text("View detailed analysis report for a player"))));
        }

        if (player.hasPermission("owml.ml.toggle")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("enable", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/OverWatchx ml enable")).hoverEvent(HoverEvent.showText(Component.text("Enable the ML detection system"))));

            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("disable", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/OverWatchx ml disable")).hoverEvent(HoverEvent.showText(Component.text("Disable the ML detection system"))));
        }

        if (player.hasPermission("owml.ml.status")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("status", NamedTextColor.GREEN, TextDecoration.BOLD)).clickEvent(ClickEvent.runCommand("/OverWatchx ml status")).hoverEvent(HoverEvent.showText(Component.text("Check ML system status"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("autotrain", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" [count]", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml autotrain ")).hoverEvent(HoverEvent.showText(Component.text("Automatically train ML model on multiple online players"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("spawn", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <type> <count>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml spawn ")).hoverEvent(HoverEvent.showText(Component.text("Spawn AI training bots with specific behaviors"))));
        }

        if (player.hasPermission("owml.ml.train")) {
            player.sendMessage(Component.text("  вЂў ", NamedTextColor.GRAY).append(Component.text("/OverWatchx ml ", NamedTextColor.GRAY)).append(Component.text("bots", NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" <status|remove|auto|teleport|watch>", NamedTextColor.YELLOW)).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml bots ")).hoverEvent(HoverEvent.showText(Component.text("Manage training bots: check status, remove all bots, toggle auto-spawning, teleport to bots"))));
        }
    }

    /**
     * Handle train command - collect training data from a player
     */
    private boolean handleTrainCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to train the ML system.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/OverWatchx ml train <player> <cheater|normal>", NamedTextColor.YELLOW)));
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

    /**
     * Handle analyze command - analyze a player's behavior
     */
    private boolean handleAnalyzeCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.analyze")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to analyze players.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/OverWatchx ml analyze <player>", NamedTextColor.YELLOW)));
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

    /**
     * Handle report command - show detailed analysis results for a player
     */
    private boolean handleReportCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.report")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to view analysis reports.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/OverWatchx ml report <player>", NamedTextColor.YELLOW)));
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

    /**
     * Handle enable/disable commands - toggle ML system
     */
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

    /**
     * Handle spawn command - spawn training bots
     */
    private boolean handleSpawnCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to spawn training bots.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/OverWatchx ml spawn <type> <count>", NamedTextColor.YELLOW)));
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

        // Get bot training manager
        BotTrainingManager botManager = mlManager.getBotTrainingManager();
        if (botManager == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot training system is not available", NamedTextColor.RED)));
            return true;
        }

        // Check current bot count
        Map<String, Object> stats = botManager.getTrainingStats();
        int activeBots = (Integer) stats.get("activeBots");

        if (activeBots + count > 20) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Too many bots! Current: ", NamedTextColor.RED))
                .append(Component.text(activeBots + ", requested: ", NamedTextColor.YELLOW))
                .append(Component.text(count + ", max allowed: 20", NamedTextColor.RED)));
            return true;
        }

        // Spawn bots
        botManager.spawnTrainingBot(botType, count);

        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Spawned ", NamedTextColor.GREEN))
            .append(Component.text(count + " " + botType.name().toLowerCase().replace("_", " "), NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" training bots", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("в„№ ", NamedTextColor.AQUA).append(Component.text("Bots will automatically mine for 2 minutes and submit training data to the ML system", NamedTextColor.AQUA)));

        return true;
    }

    /**
     * Handle bots command - manage training bots
     */
    private boolean handleBotsCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to manage training bots.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED)).append(Component.text("/OverWatchx ml bots <status|remove|auto>", NamedTextColor.YELLOW)));
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
                sender.sendMessage(Component.text("Available: status, remove, auto, teleport, watch", NamedTextColor.GRAY));
                return true;
        }
    }

    private boolean handleBotsStatusCommand(Player sender, BotTrainingManager botManager) {
        Map<String, Object> stats = botManager.getTrainingStats();

        sender.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("Training Bots Status", NamedTextColor.AQUA, TextDecoration.BOLD)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));

        sender.sendMessage(Component.text("  Active Bots: ", NamedTextColor.GRAY).append(Component.text(stats.get("activeBots").toString(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("  Total Spawned: ", NamedTextColor.GRAY).append(Component.text(stats.get("totalBotsSpawned").toString(), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  Training Sessions: ", NamedTextColor.GRAY).append(Component.text(stats.get("totalTrainingSessions").toString(), NamedTextColor.BLUE)));
        sender.sendMessage(Component.text("  Auto Training: ", NamedTextColor.GRAY).append(Component.text(stats.get("autoTrainingEnabled").toString(), (Boolean)stats.get("autoTrainingEnabled") ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("  Max Concurrent: ", NamedTextColor.GRAY).append(Component.text(stats.get("maxConcurrentBots").toString(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Session Duration: ", NamedTextColor.GRAY).append(Component.text(stats.get("sessionDuration").toString() + "s", NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  Spawn Mode: ", NamedTextColor.GRAY).append(Component.text("Dynamic Search", NamedTextColor.BLUE)));

        @SuppressWarnings("unchecked")
        Map<String, Integer> botTypes = (Map<String, Integer>) stats.get("activeBotTypes");
        if (!botTypes.isEmpty()) {
            sender.sendMessage(Component.text("  Active Types:", NamedTextColor.GRAY));
            for (Map.Entry<String, Integer> entry : botTypes.entrySet()) {
                sender.sendMessage(Component.text("    вЂў " + entry.getKey().toLowerCase().replace("_", " ") + ": ", NamedTextColor.GRAY).append(Component.text(entry.getValue().toString(), NamedTextColor.YELLOW)));
            }
        }

        return true;
    }

    private boolean handleBotsRemoveCommand(Player sender, BotTrainingManager botManager) {
        Map<String, Object> stats = botManager.getTrainingStats();
        int activeBots = (Integer) stats.get("activeBots");

        if (activeBots == 0) {
            sender.sendMessage(Component.text("в„№ ", NamedTextColor.AQUA).append(Component.text("No active training bots to remove", NamedTextColor.AQUA)));
            return true;
        }

        botManager.removeAllBots();
        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Removed all ", NamedTextColor.GREEN))
            .append(Component.text(activeBots + " training bots", NamedTextColor.YELLOW, TextDecoration.BOLD)));

        return true;
    }

    private boolean handleBotsAutoCommand(Player sender, BotTrainingManager botManager, String toggleValue) {
        Map<String, Object> stats = botManager.getTrainingStats();
        boolean currentlyEnabled = (Boolean) stats.get("autoTrainingEnabled");

        boolean newState;
        if (toggleValue != null) {
            if (toggleValue.equalsIgnoreCase("on") || toggleValue.equalsIgnoreCase("true") || toggleValue.equalsIgnoreCase("enable")) {
                newState = true;
            } else if (toggleValue.equalsIgnoreCase("off") || toggleValue.equalsIgnoreCase("false") || toggleValue.equalsIgnoreCase("disable")) {
                newState = false;
            } else {
                sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid value. Use 'on' or 'off'", NamedTextColor.RED)));
                return true;
            }
        } else {
            newState = !currentlyEnabled;
        }

        botManager.setAutoTrainingEnabled(newState);
        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Auto training ", NamedTextColor.GREEN))
            .append(Component.text(newState ? "ENABLED" : "DISABLED", newState ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));

        return true;
    }

    private boolean handleBotsTeleportCommand(Player sender, BotTrainingManager botManager, String botId) {
        if (botId == null || botId.trim().isEmpty()) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED))
                .append(Component.text("/OverWatchx ml bots teleport <bot_id>", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY).append(Component.text("/owml ml bots status", NamedTextColor.GREEN))
                .append(Component.text(" to see active bot IDs", NamedTextColor.GRAY)));
            return true;
        }

        // Try to find bot by partial ID match
        BotTrainingManager.TrainingBot targetBot = findBotById(botManager, botId);

        if (targetBot == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot not found: ", NamedTextColor.RED))
                .append(Component.text(botId, NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY).append(Component.text("/owml ml bots status", NamedTextColor.GREEN))
                .append(Component.text(" to see active bot IDs", NamedTextColor.GRAY)));
            return true;
        }

        // Get bot location
        Location botLocation = targetBot.getFakePlayer().getLocation();
        if (botLocation == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot location unavailable", NamedTextColor.RED)));
            return true;
        }

        // Teleport player to bot location (slightly above to avoid getting stuck)
        Location teleportLoc = botLocation.clone().add(0, 2, 0);
        sender.teleport(teleportLoc);

        sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Teleported to bot ", NamedTextColor.GREEN))
            .append(Component.text(targetBot.getBotId().toString().substring(0, 8), NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" (", NamedTextColor.GRAY))
            .append(Component.text(targetBot.getBehaviorType().name(), NamedTextColor.AQUA))
            .append(Component.text(")", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("рџ“Ќ ", NamedTextColor.BLUE).append(Component.text("Location: ", NamedTextColor.BLUE))
            .append(Component.text(formatLocation(botLocation), NamedTextColor.YELLOW)));

        return true;
    }

    private boolean handleBotsWatchCommand(Player sender, BotTrainingManager botManager, String botId) {
        if (botId == null || botId.trim().isEmpty()) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Usage: ", NamedTextColor.RED))
                .append(Component.text("/OverWatchx ml bots watch <bot_id>", NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY).append(Component.text("/owml ml bots status", NamedTextColor.GREEN))
                .append(Component.text(" to see active bot IDs", NamedTextColor.GRAY)));
            return true;
        }

        // Try to find bot by partial ID match
        BotTrainingManager.TrainingBot targetBot = findBotById(botManager, botId);

        if (targetBot == null) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot not found: ", NamedTextColor.RED))
                .append(Component.text(botId, NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY).append(Component.text("/owml ml bots status", NamedTextColor.GREEN))
                .append(Component.text(" to see active bot IDs", NamedTextColor.GRAY)));
            return true;
        }

        // Start watching the bot - teleport and follow
        Location botLocation = targetBot.getFakePlayer().getLocation();
        if (botLocation != null) {
            Location teleportLoc = botLocation.clone().add(0, 2, 0);
            sender.teleport(teleportLoc);

            sender.sendMessage(Component.text("рџ‘ЃпёЏ ", NamedTextColor.BLUE).append(Component.text("Now watching bot ", NamedTextColor.BLUE))
                .append(Component.text(targetBot.getBotId().toString().substring(0, 8), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(targetBot.getBehaviorType().name(), NamedTextColor.AQUA))
                .append(Component.text(")", NamedTextColor.GRAY)));

            sender.sendMessage(Component.text("рџ’Ў ", NamedTextColor.BLUE).append(Component.text("Bot will continue mining. Use ", NamedTextColor.BLUE))
                .append(Component.text("/owml ml bots teleport <id>", NamedTextColor.GREEN))
                .append(Component.text(" to follow again", NamedTextColor.BLUE)));
        } else {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Bot location unavailable", NamedTextColor.RED)));
        }

        return true;
    }

    private BotTrainingManager.TrainingBot findBotById(BotTrainingManager botManager, String partialId) {
        return botManager.findBotById(partialId);
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Handle status command - display ML system status
     */
    private boolean handleStatusCommand(Player sender) {
        if (!sender.hasPermission("owml.ml.status")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to check ML system status.", NamedTextColor.RED)));
            return true;
        }

        boolean enabled = mlManager.isEnabled();

        sender.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("OverWatchML ML Status", NamedTextColor.AQUA, TextDecoration.BOLD)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));

        sender.sendMessage(Component.text("  System: ", NamedTextColor.GRAY).append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));

        if (mlManager.isTrained()) {
            sender.sendMessage(Component.text("  Model: ", NamedTextColor.GRAY).append(Component.text("TRAINED", NamedTextColor.GREEN, TextDecoration.BOLD)));
        } else {
            sender.sendMessage(Component.text("  Model: ", NamedTextColor.GRAY).append(Component.text("NOT TRAINED", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text(" - Use ", NamedTextColor.YELLOW)).append(Component.text("/OverWatchx ml train", NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand("/OverWatchx ml train ")).hoverEvent(HoverEvent.showText(Component.text("Click to suggest command")))));
        }

        return true;
    }

    /**
     * Handle autotrain command - automatically train on multiple online players
     */
    private boolean handleAutoTrainCommand(Player sender, String[] args) {
        if (!sender.hasPermission("owml.ml.train")) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Permission Denied: ", NamedTextColor.RED, TextDecoration.BOLD)).append(Component.text("You don't have permission to train the ML system.", NamedTextColor.RED)));
            return true;
        }

        int maxPlayersToTrain = Integer.MAX_VALUE; // Default: all players

        if (args.length > 0) {
            try {
                maxPlayersToTrain = Integer.parseInt(args[0]);
                if (maxPlayersToTrain <= 0) {
                    sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid count. Must be a positive number.", NamedTextColor.RED)));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Invalid count. Must be a number.", NamedTextColor.RED)));
                return true;
            }
        }

        // Get all online players except the sender (to avoid self-training issues)
        List<Player> availablePlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(sender) && !mlManager.getPlayersInTraining().containsKey(player.getUniqueId())) {
                availablePlayers.add(player);
            }
        }

        if (availablePlayers.isEmpty()) {
            sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("No available players for training. All players are either already in training or it's just you online.", NamedTextColor.RED)));
            return true;
        }

        int playersToTrain = Math.min(maxPlayersToTrain, availablePlayers.size());
        int normalPlayers = playersToTrain / 2; // Half normal, half cheater simulation
        int cheaterPlayers = playersToTrain - normalPlayers;

        sender.sendMessage(Component.text("в–¶ ", NamedTextColor.GOLD).append(Component.text("Starting Auto-Training", NamedTextColor.AQUA, TextDecoration.BOLD)).append(Component.text(" в—Ђ", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("Training on ", NamedTextColor.GRAY).append(Component.text(playersToTrain + " players", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" (", NamedTextColor.GRAY)).append(Component.text(normalPlayers + " normal", NamedTextColor.GREEN))
            .append(Component.text(", ", NamedTextColor.GRAY)).append(Component.text(cheaterPlayers + " simulated cheaters", NamedTextColor.RED))
            .append(Component.text(")", NamedTextColor.GRAY)));

        // Shuffle players for random selection
        Collections.shuffle(availablePlayers);

        // Start training on normal players
        for (int i = 0; i < normalPlayers && i < availablePlayers.size(); i++) {
            Player targetPlayer = availablePlayers.get(i);
            mlManager.startTraining(targetPlayer, false); // false = normal player
            sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Started normal training on ", NamedTextColor.GREEN)).append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW)));
        }

        // Start training on simulated cheater players
        for (int i = normalPlayers; i < playersToTrain && i < availablePlayers.size(); i++) {
            Player targetPlayer = availablePlayers.get(i);
            mlManager.startTraining(targetPlayer, true); // true = cheater
            sender.sendMessage(Component.text("вњ“ ", NamedTextColor.GREEN).append(Component.text("Started cheater simulation on ", NamedTextColor.GREEN)).append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW)));
        }

        sender.sendMessage(Component.text("в„№ ", NamedTextColor.AQUA).append(Component.text("Training sessions will run for ", NamedTextColor.AQUA))
            .append(Component.text(mlManager.getTrainingSessionDuration() + " seconds", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" each. Players should mine normally during this time.", NamedTextColor.AQUA)));

        sender.sendMessage(Component.text("вљ  ", NamedTextColor.GOLD).append(Component.text("Important: ", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("For accurate results, normal players should mine legitimately, and 'cheater' players should simulate X-ray behavior.", NamedTextColor.YELLOW)));

        return true;
    }

    /**
     * Handle tab completion for ML subcommands
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add all available commands based on permissions and partial match
            if (sender.hasPermission("owml.ml.train") && "train".startsWith(partial)) {
                completions.add("train");
            }
            if (sender.hasPermission("owml.ml.analyze") && "analyze".startsWith(partial)) {
                completions.add("analyze");
            }
            if (sender.hasPermission("owml.ml.report") && "report".startsWith(partial)) {
                completions.add("report");
            }
            if (sender.hasPermission("owml.ml.toggle")) {
                if ("enable".startsWith(partial)) completions.add("enable");
                if ("disable".startsWith(partial)) completions.add("disable");
            }
            if (sender.hasPermission("owml.ml.status") && "status".startsWith(partial)) {
                completions.add("status");
            }
            if (sender.hasPermission("owml.ml.train")) {
                if ("autotrain".startsWith(partial)) completions.add("autotrain");
                if ("spawn".startsWith(partial)) completions.add("spawn");
                if ("bots".startsWith(partial)) completions.add("bots");
            }
        } else if (args.length == 2) {
            if ((args[0].equalsIgnoreCase("train") && sender.hasPermission("owml.ml.train")) ||
                (args[0].equalsIgnoreCase("analyze") && sender.hasPermission("owml.ml.analyze")) ||
                (args[0].equalsIgnoreCase("report") && sender.hasPermission("owml.ml.report"))) {

                String partial = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String playerName = player.getName();
                    if (playerName.toLowerCase().startsWith(partial) ||
                        playerName.toLowerCase().contains(partial)) {
                        completions.add(playerName);
                    }
                }

                // If no matches, suggest current player as example
                if (completions.isEmpty() && sender instanceof Player) {
                    completions.add(((Player) sender).getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("train")) {
            if (sender.hasPermission("owml.ml.train")) {
                String partial = args[2].toLowerCase();
                if ("cheater".startsWith(partial) || "c".startsWith(partial)) completions.add("cheater");
                if ("normal".startsWith(partial) || "n".startsWith(partial)) completions.add("normal");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("autotrain")) {
            if (sender.hasPermission("owml.ml.train")) {
                // Suggest reasonable numbers based on online players
                int onlineCount = Bukkit.getOnlinePlayers().size();
                completions.add(String.valueOf(Math.min(5, onlineCount))); // Default suggestion
                completions.add(String.valueOf(Math.min(10, onlineCount)));
                completions.add(String.valueOf(Math.min(15, onlineCount)));
                completions.add(String.valueOf(onlineCount)); // All players

                // Also suggest common numbers
                completions.add("1");
                completions.add("3");
                completions.add("5");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            // No additional parameters needed for enable/disable
            // Could suggest help or common next commands
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            if (sender.hasPermission("owml.ml.train")) {
                String partial = args[1].toLowerCase();

                // Suggest bot behavior types with smart matching
                String[] botTypes = {
                    "NORMAL_MINER", "XRAY_CHEATER", "TUNNEL_MINER",
                    "RANDOM_MINER", "EFFICIENT_MINER", "SURFACE_MINER"
                };

                for (String botType : botTypes) {
                    if (botType.toLowerCase().startsWith(partial) ||
                        botType.toLowerCase().contains(partial)) {
                        completions.add(botType);
                    }
                }

                // If no matches and partial is short, suggest common ones
                if (completions.isEmpty() && partial.length() <= 2) {
                    if ("n".startsWith(partial)) completions.add("NORMAL_MINER");
                    if ("x".startsWith(partial)) completions.add("XRAY_CHEATER");
                    if ("t".startsWith(partial)) completions.add("TUNNEL_MINER");
                    if ("r".startsWith(partial)) completions.add("RANDOM_MINER");
                    if ("e".startsWith(partial)) completions.add("EFFICIENT_MINER");
                    if ("s".startsWith(partial)) completions.add("SURFACE_MINER");
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            if (sender.hasPermission("owml.ml.train")) {
                // Suggest reasonable bot counts
                completions.add("1");
                completions.add("2");
                completions.add("3");
                completions.add("5");
                completions.add("10");
                completions.add("15");
                completions.add("20");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bots")) {
            if (sender.hasPermission("owml.ml.train")) {
                String partial = args[1].toLowerCase();

                // Suggest bot subcommands with smart matching
                String[] subcommands = {"status", "remove", "auto", "teleport", "watch"};

                for (String subcommand : subcommands) {
                    if (subcommand.startsWith(partial)) {
                        completions.add(subcommand);
                    }
                }

                // If no matches and partial is short, suggest common abbreviations
                if (completions.isEmpty() && partial.length() <= 2) {
                    if ("s".startsWith(partial)) completions.add("status");
                    if ("r".startsWith(partial)) completions.add("remove");
                    if ("a".startsWith(partial)) completions.add("auto");
                    if ("t".startsWith(partial)) completions.add("teleport");
                    if ("w".startsWith(partial)) completions.add("watch");
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("bots") && args[1].equalsIgnoreCase("auto")) {
            if (sender.hasPermission("owml.ml.train")) {
                completions.add("on");
                completions.add("off");
                completions.add("true");
                completions.add("false");
                completions.add("enable");
                completions.add("disable");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("bots") &&
                   (args[1].equalsIgnoreCase("teleport") || args[1].equalsIgnoreCase("watch"))) {
            if (sender.hasPermission("owml.ml.train")) {
                // Suggest active bot IDs
                BotTrainingManager botManager = mlManager.getBotTrainingManager();
                if (botManager != null) {
                    Map<UUID, BotTrainingManager.TrainingBot> activeBots = botManager.getActiveBots();
                    String partial = args[2].toLowerCase();

                    for (Map.Entry<UUID, BotTrainingManager.TrainingBot> entry : activeBots.entrySet()) {
                        String botId = entry.getKey().toString().substring(0, 8); // First 8 chars
                        if (botId.toLowerCase().startsWith(partial)) {
                            completions.add(botId);
                        }

                        // Also suggest full UUID if it matches
                        String fullId = entry.getKey().toString();
                        if (fullId.toLowerCase().startsWith(partial)) {
                            completions.add(fullId);
                        }
                    }

                    // If no matches, suggest some examples
                    if (completions.isEmpty() && partial.isEmpty()) {
                        completions.add("abc12345");
                        completions.add("use_status_first");
                    }
                }
            }
        }

        return completions;
    }
}
