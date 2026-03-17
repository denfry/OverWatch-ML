package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandHidingCommand extends AbstractSubCommand {

    public CommandHidingCommand(OverWatchML plugin) {
        super(plugin, "commandhiding", "owml.admin", "Toggle command hiding", "/owml commandhiding <enable|disable>");
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

        String action = args[0].toLowerCase();
        boolean enabled = action.equals("enable");

        plugin.getConfigManager().setCommandHidingEnabled(enabled);
        sender.sendMessage(Component.text("Command hiding set to " + enabled).color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("A server restart is required for this change to take full effect!").color(NamedTextColor.YELLOW));

        return true;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("enable", "disable")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
