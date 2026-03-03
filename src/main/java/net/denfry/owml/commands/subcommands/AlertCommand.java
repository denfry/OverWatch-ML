package net.denfry.owml.commands.subcommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.OverWatchML;

public class AlertCommand implements CommandExecutor {

    private final StaffAlertManager staffAlertManager;
    private final OverWatchML plugin;

    public AlertCommand(OverWatchML plugin) {
        this.plugin = plugin;
        this.staffAlertManager = plugin.getStaffAlertManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("owml.togglealert")) {
            player.sendMessage(Component.text("You don't have permission to toggle ore alerts.").color(NamedTextColor.RED));
            return true;
        }
        boolean alertsDisabled = staffAlertManager.toggleOreAlert(player);
        if (alertsDisabled) {
            player.sendMessage(Component.text("Ore alerts disabled.").color(NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Ore alerts enabled.").color(NamedTextColor.GREEN));
        }
        return true;
    }
}
