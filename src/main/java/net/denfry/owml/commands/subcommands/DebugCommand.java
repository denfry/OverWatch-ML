package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.denfry.owml.OverWatchML;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DebugCommand extends AbstractSubCommand {

    public DebugCommand(OverWatchML plugin) {
        super(plugin, "debug", "owml.debug", "Toggle debug mode", "/owml debug <true|false>", "d");
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

        String value = args[0].toLowerCase();
        boolean debugValue;

        if (value.equals("true") || value.equals("on") || value.equals("enable")) {
            debugValue = true;
        } else if (value.equals("false") || value.equals("off") || value.equals("disable")) {
            debugValue = false;
        } else {
            sender.sendMessage(Component.text("Invalid value. Use true or false.").color(NamedTextColor.RED));
            return true;
        }

        plugin.getConfig().set("debug.enabled", debugValue);
        plugin.saveConfig();
        plugin.reloadConfig();

        sender.sendMessage(Component.text("Debug mode set to " + debugValue + ".").color(NamedTextColor.GREEN));

        return true;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("true", "false", "on", "off", "enable", "disable")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        }
        return super.tabComplete(sender, args);
    }
}
