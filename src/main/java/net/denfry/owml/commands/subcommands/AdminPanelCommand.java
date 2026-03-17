package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.web.AdminPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command for managing the administrative web panel.
 */
public class AdminPanelCommand extends AbstractSubCommand {

    public AdminPanelCommand(OverWatchML plugin) {
        super(plugin, "adminpanel", "owml.admin", "Manage the administrative web panel", "/owml adminpanel <start|stop|status|port> [port]", "ap");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            MessageManager.send(sender, "command.no-permission", "PREFIX", "Admin Panel", "REASON", "manage admin panel");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                handleStart(sender, args);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "port":
                handlePort(sender, args);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (AdminPanel.isEnabled()) {
            MessageManager.send(sender, "error.already-exists", "ITEM", "Admin panel", "NAME", "running");
            return;
        }

        int port = 8080;

        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
                if (port < 1024 || port > 65535) {
                    MessageManager.send(sender, "error.invalid-value", "VALUE", String.valueOf(port), "EXPECTED", "1024-65535");
                    return;
                }
            } catch (NumberFormatException e) {
                MessageManager.send(sender, "error.invalid-value", "VALUE", args[1], "EXPECTED", "valid port number");
                return;
            }
        }

        try {
            AdminPanel.start(port);
            MessageManager.send(sender, "success.enabled", "FEATURE", "Admin panel on port " + port);
            sender.sendMessage(Component.text("Access at: http://localhost:" + port).color(NamedTextColor.AQUA));
        } catch (Exception e) {
            MessageManager.send(sender, "error", "Failed to start admin panel: " + e.getMessage());
        }
    }

    private void handleStop(CommandSender sender) {
        if (!AdminPanel.isEnabled()) {
            MessageManager.send(sender, "error.not-found", "ITEM", "Admin panel", "NAME", "running");
            return;
        }

        AdminPanel.stop();
        MessageManager.send(sender, "success.disabled", "FEATURE", "Admin panel");
    }

    private void handleStatus(CommandSender sender) {
        boolean enabled = AdminPanel.isEnabled();
        int port = AdminPanel.getPort();

        sender.sendMessage(Component.text("Admin Panel Status:").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Status: ").color(NamedTextColor.GRAY)
                .append(Component.text(enabled ? "RUNNING" : "STOPPED").color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        if (enabled) {
            sender.sendMessage(Component.text("  Port: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(port)).color(NamedTextColor.AQUA)));
        }
    }

    private void handlePort(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Current port: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(AdminPanel.getPort())).color(NamedTextColor.AQUA)));
            return;
        }

        if (AdminPanel.isEnabled()) {
            MessageManager.send(sender, "error", "Cannot change port while admin panel is running. Stop it first.");
            return;
        }

        try {
            int newPort = Integer.parseInt(args[1]);
            if (newPort < 1024 || newPort > 65535) {
                MessageManager.send(sender, "error.invalid-value", "VALUE", String.valueOf(newPort), "EXPECTED", "1024-65535");
                return;
            }
            MessageManager.send(sender, "success", "Admin panel port set to " + newPort);
        } catch (NumberFormatException e) {
            MessageManager.send(sender, "error.invalid-value", "VALUE", args[1], "EXPECTED", "valid port number");
        }
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("start", "stop", "status", "port")) {
                if (s.startsWith(partial)) completions.add(s);
            }
            return completions;
        }
        return super.tabComplete(sender, args);
    }
}
