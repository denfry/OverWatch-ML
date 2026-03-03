package net.denfry.owml.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.MainHubGUI;
import net.denfry.owml.gui.modern.PlayerProfileGUI;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private final OverWatchML plugin;
    private final Map<String, ISubCommand> subCommands = new HashMap<>();

    public AntiXrayCommand(OverWatchML plugin, UpdateChecker updateChecker) {
        this.plugin = plugin;
        // Subcommands would be registered here in a real scenario
        // For this task, we focus on the base command and the 'player' shortcut
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // /owml (без аргументов) -> Открыть StaffMenuGUI
        if (args.length == 0) {
            if (!player.hasPermission("owml.staff")) {
                player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                return true;
            }
            GUINavigationStack.push(player, new net.denfry.owml.gui.StaffMenuGUI(plugin));
            return true;
        }

        // /owml staff -> Открыть StaffMenuGUI
        if (args[0].equalsIgnoreCase("staff")) {
            if (!player.hasPermission("owml.staff")) {
                player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                return true;
            }
            GUINavigationStack.push(player, new net.denfry.owml.gui.StaffMenuGUI(plugin));
            return true;
        }

        // /owml player <name> -> Открыть PlayerProfileGUI напрямую
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            if (!player.hasPermission("owml.gui")) {
                player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            GUINavigationStack.push(player, new PlayerProfileGUI(plugin, target.getUniqueId()));
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            showHelpMenu(player, label);
            return true;
        }

        // Обработка остальных подкоманд (упрощенно)
        player.sendMessage(Component.text("Unknown subcommand. Use /" + label + " help", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("help", "player", "reload", "staff", "ml", "debug");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) suggestions.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            return null; // Позволяет Bukkit дополнять имена игроков
        }
        return suggestions;
    }

    private void showHelpMenu(Player player, String label) {
        player.sendMessage(Component.text("=== OverWatchML Help ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/" + label + " - Open Control Center", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/" + label + " player <name> - Open player profile", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/" + label + " help - Show this message", NamedTextColor.GREEN));
    }
}
