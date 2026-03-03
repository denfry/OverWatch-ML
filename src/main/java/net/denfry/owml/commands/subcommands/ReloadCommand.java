package net.denfry.owml.commands.subcommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.denfry.owml.OverWatchML;

public class ReloadCommand implements CommandExecutor {

    private final OverWatchML plugin;

    public ReloadCommand(OverWatchML plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("owml.reload")) {
            sender.sendMessage(Component.text("You do not have permission to reload the plugin.").color(NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Reloading OverWatchML plugin and configuration...").color(NamedTextColor.GREEN));

        plugin.reloadConfig();
        plugin.getLogger().info("Plugin reloaded by " + sender.getName());

        sender.sendMessage(Component.text("Plugin reloaded successfully!").color(NamedTextColor.GREEN));

        return true;
    }
}
