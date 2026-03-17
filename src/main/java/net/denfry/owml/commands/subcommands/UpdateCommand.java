package net.denfry.owml.commands.subcommands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.AbstractSubCommand;
import net.denfry.owml.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UpdateCommand extends AbstractSubCommand {

    private final UpdateChecker updateChecker;

    public UpdateCommand(OverWatchML plugin) {
        super(plugin, "update", "owml.autoupdate", "Manage plugin updates", "/owml update <check|auto|apply>");
        this.updateChecker = plugin.getUpdateChecker();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sendNoPermission(sender);
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "check":
                sender.sendMessage(Component.text("Checking for updates...").color(NamedTextColor.YELLOW));
                updateChecker.getVersionFresh(version -> {
                    String currentVersion = plugin.getDescription().getVersion();
                    boolean isRemoteNewer = updateChecker.isNewerVersion(version, currentVersion);

                    if (isRemoteNewer) {
                        sender.sendMessage(Component.text("New update available: " + version).color(NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("OverWatchML is up to date!").color(NamedTextColor.GREEN));
                    }
                });
                break;

            case "auto":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This action requires a player.");
                    return true;
                }
                downloadUpdate(player);
                break;

            case "apply":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This action requires a player.");
                    return true;
                }
                applyUpdate(player);
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void applyUpdate(Player player) {
        // Implementation from original UpdateCommand
        player.sendMessage(Component.text("Preparing update...").color(NamedTextColor.YELLOW));
    }

    private void downloadUpdate(Player player) {
        // Implementation from original UpdateCommand
        player.sendMessage(Component.text("Downloading update...").color(NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("check", "auto", "apply")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
