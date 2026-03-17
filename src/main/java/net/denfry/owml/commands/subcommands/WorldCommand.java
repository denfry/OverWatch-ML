package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WorldCommand extends AbstractSubCommand {

    public WorldCommand(OverWatchML plugin) {
        super(plugin, "world", "owml.staff", "Manage world-specific settings", "/owml world <enable|disable|add|remove|list|status>", "w");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sendNoPermission(sender);
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "enable":
            case "disable":
                handleToggle(sender, args);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /owml world " + args[0] + " <true/false>").color(NamedTextColor.RED));
            return;
        }

        String value = args[1].toLowerCase();
        boolean enabled = value.equals("true") || value.equals("enable");
        
        plugin.getConfigManager().setWorldDisablingEnabled(enabled);
        sender.sendMessage(Component.text("World disabling set to " + enabled).color(NamedTextColor.GREEN));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /owml world add <world_name>").color(NamedTextColor.RED));
            return;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(Component.text("World not found: " + worldName).color(NamedTextColor.RED));
            return;
        }

        plugin.getConfigManager().addDisabledWorld(worldName);
        sender.sendMessage(Component.text("Added " + worldName + " to disabled worlds.").color(NamedTextColor.GREEN));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /owml world remove <world_name>").color(NamedTextColor.RED));
            return;
        }

        String worldName = args[1];
        plugin.getConfigManager().removeDisabledWorld(worldName);
        sender.sendMessage(Component.text("Removed " + worldName + " from disabled worlds.").color(NamedTextColor.GREEN));
    }

    private void handleList(CommandSender sender) {
        Set<String> disabledWorlds = plugin.getConfigManager().getDisabledWorlds();
        sender.sendMessage(Component.text("Disabled worlds: " + String.join(", ", disabledWorlds)).color(NamedTextColor.GOLD));
    }

    private void handleStatus(CommandSender sender) {
        boolean enabled = plugin.getConfigManager().isWorldDisablingEnabled();
        sender.sendMessage(Component.text("World disabling: " + (enabled ? "ENABLED" : "DISABLED")).color(NamedTextColor.GOLD));
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("enable", "disable", "add", "remove", "list", "status")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            String partial = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(partial)) completions.add(w.getName());
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
