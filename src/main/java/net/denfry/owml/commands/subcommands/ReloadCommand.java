package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import net.denfry.owml.OverWatchML;

public class ReloadCommand extends AbstractSubCommand {

    public ReloadCommand(OverWatchML plugin) {
        super(plugin, "reload", "owml.reload", "Reload plugin configuration", "/owml reload", "r");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sendNoPermission(sender);
            return true;
        }

        sender.sendMessage(Component.text("Reloading OverWatchML plugin and configuration...").color(NamedTextColor.GREEN));

        plugin.reloadConfig();
        plugin.getLogger().info("Plugin reloaded by " + sender.getName());

        sender.sendMessage(Component.text("Plugin reloaded successfully!").color(NamedTextColor.GREEN));

        return true;
    }
}
