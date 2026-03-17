package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.SuspiciousManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModifySuspiciousCommand extends AbstractSubCommand {

    public ModifySuspiciousCommand(OverWatchML plugin) {
        super(plugin, "points", "owml.modify", "Modify player suspicious points", "/owml points <add|remove|set|check> <player> [amount]");
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
        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (action.equalsIgnoreCase("check")) {
            int currentPoints = SuspiciousManager.getSuspiciousCounts().getOrDefault(target.getUniqueId(), 0);
            sender.sendMessage(Component.text(targetName + "'s suspicious points: " + currentPoints).color(NamedTextColor.YELLOW));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount.").color(NamedTextColor.RED));
            return true;
        }

        int currentCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(target.getUniqueId(), 0);
        int newCount = currentCount;

        switch (action) {
            case "add": newCount += amount; break;
            case "remove": newCount = Math.max(0, currentCount - amount); break;
            case "set": newCount = amount; break;
            default: sendUsage(sender); return true;
        }

        SuspiciousManager.getSuspiciousCounts().put(target.getUniqueId(), newCount);
        sender.sendMessage(Component.text("Updated " + targetName + "'s points to " + newCount).color(NamedTextColor.GREEN));

        return true;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("add", "remove", "set", "check")) {
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
        }
        return Collections.emptyList();
    }
}
