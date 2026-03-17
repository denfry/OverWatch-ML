package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PunishCommand extends AbstractSubCommand {

    public PunishCommand(OverWatchML plugin) {
        super(plugin, "punish", "owml.punish", "Manage player punishments", "/owml punish <set|remove|check> <player> [level]", "p", "ban");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sendNoPermission(sender);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player " + playerName + " not found or not online.").color(NamedTextColor.RED));
            return true;
        }

        switch (action) {
            case "set":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /owml punish set <player> <level>").color(NamedTextColor.RED));
                    return true;
                }

                int level;
                try {
                    level = Integer.parseInt(args[2]);
                    if (level < 0 || level > 6) {
                        sender.sendMessage(Component.text("Punishment level must be between 0 and 6.").color(NamedTextColor.RED));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid level format. Must be a number between 0 and 6.").color(NamedTextColor.RED));
                    return true;
                }

                plugin.getPunishmentManager().setPunishmentLevel(targetPlayer, level);
                sender.sendMessage(Component.text("Applied punishment level " + level + " to " + targetPlayer.getName()).color(NamedTextColor.GREEN));
                break;

            case "remove":
                plugin.getPunishmentManager().removePunishment(targetPlayer);
                sender.sendMessage(Component.text("Removed all punishments from " + targetPlayer.getName()).color(NamedTextColor.GREEN));
                break;

            case "check":
                int currentLevel = plugin.getPunishmentManager().getPlayerPunishmentLevel(targetPlayer.getUniqueId());
                sender.sendMessage(Component.text(targetPlayer.getName() + " is currently at punishment level " + currentLevel).color(NamedTextColor.GREEN));
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("set", "remove", "check")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) completions.add(p.getName());
            }
            return completions;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("0", "1", "2", "3", "4", "5", "6");
        }
        return Collections.emptyList();
    }
}
