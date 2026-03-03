package net.denfry.owml.commands.subcommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.util.Set;

public class WorldCommand implements CommandExecutor {

    private final OverWatchML plugin;

    public WorldCommand(OverWatchML plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("owml.staff")) {
            MessageManager.send(sender, "command.no-permission", "PREFIX", "World Management", "REASON", "manage world settings");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "enable":
            case "disable":
                handleToggle(sender, label, args);
                break;
            case "add":
                handleAdd(sender, label, args);
                break;
            case "remove":
                handleRemove(sender, label, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            default:
                sendUsage(sender, label);
                break;
        }

        return true;
    }

    private void handleToggle(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " world " + args[0] + " <true/false>").color(NamedTextColor.RED));
            return;
        }

        String value = args[1].toLowerCase();
        boolean enabled;

        if (value.equals("true") || value.equals("enable")) {
            enabled = true;
        } else if (value.equals("false") || value.equals("disable")) {
            enabled = false;
        } else {
            sender.sendMessage(Component.text("Invalid value. Use true/false or enable/disable.").color(NamedTextColor.RED));
            return;
        }

        plugin.getConfigManager().setWorldDisablingEnabled(enabled);
        MessageManager.send(sender, enabled ? "success.enabled" : "success.disabled", "FEATURE", "World disabling");
    }

    private void handleAdd(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " world add <world_name>").color(NamedTextColor.RED));
            return;
        }

        String worldName = args[1];

        // Check if world exists
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            MessageManager.send(sender, "error.not-found", "ITEM", "World", "NAME", worldName);
            return;
        }

        if (plugin.getConfigManager().getDisabledWorlds().contains(worldName)) {
            MessageManager.send(sender, "error.already-exists", "ITEM", "World", "NAME", worldName);
            return;
        }

        plugin.getConfigManager().addDisabledWorld(worldName);
        MessageManager.send(sender, "world.added", "WORLD", worldName);
    }

    private void handleRemove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " world remove <world_name>").color(NamedTextColor.RED));
            return;
        }

        String worldName = args[1];

        if (!plugin.getConfigManager().getDisabledWorlds().contains(worldName)) {
            MessageManager.send(sender, "error.not-found", "ITEM", "World in disabled list", "NAME", worldName);
            return;
        }

        plugin.getConfigManager().removeDisabledWorld(worldName);
        MessageManager.send(sender, "world.removed", "WORLD", worldName);
    }

    private void handleList(CommandSender sender) {
        Set<String> disabledWorlds = plugin.getConfigManager().getDisabledWorlds();

        if (disabledWorlds.isEmpty()) {
            MessageManager.send(sender, "world.list-empty");
            return;
        }

        MessageManager.send(sender, "world.list-header", "COUNT", String.valueOf(disabledWorlds.size()));
        for (String worldName : disabledWorlds) {
            sender.sendMessage(Component.text(" - " + worldName).color(NamedTextColor.WHITE));
        }
    }

    private void handleStatus(CommandSender sender) {
        boolean enabled = plugin.getConfigManager().isWorldDisablingEnabled();
        Set<String> disabledWorlds = plugin.getConfigManager().getDisabledWorlds();

        sender.sendMessage(Component.text("World disabling: " + (enabled ? "ENABLED" : "DISABLED"))
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (enabled) {
            if (disabledWorlds.isEmpty()) {
                MessageManager.send(sender, "world.list-empty");
            } else {
                MessageManager.send(sender, "world.list-header", "COUNT", String.valueOf(disabledWorlds.size()));
                for (String worldName : disabledWorlds) {
                    sender.sendMessage(Component.text(" - " + worldName).color(NamedTextColor.WHITE));
                }
            }
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        MessageManager.send(sender, "command.usage", "USAGE", "/" + label + " world <enable|disable|add|remove|list|status>");
        sender.sendMessage(Component.text("  enable/disable <true/false> - Enable/disable world checking").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  add <world_name> - Add world to disabled list").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  remove <world_name> - Remove world from disabled list").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  list - Show all disabled worlds").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  status - Show current configuration").color(NamedTextColor.GRAY));
    }
}
