package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.OverWatchML;
import org.jetbrains.annotations.NotNull;

public class AlertCommand extends AbstractSubCommand {

    private final StaffAlertManager staffAlertManager;

    public AlertCommand(OverWatchML plugin) {
        super(plugin, "alert", "owml.togglealert", "Toggle ore alerts", "/owml alert", "notify");
        this.staffAlertManager = plugin.getStaffAlertManager();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission(getPermission())) {
            sendNoPermission(player);
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
