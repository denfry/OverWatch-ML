package net.denfry.owml.commands.subcommands;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AlertCommand extends AbstractSubCommand {
   private final StaffAlertManager staffAlertManager;

   public AlertCommand(OverWatchML plugin) {
      super(plugin, "alert", "owml.togglealert", "Toggle ore alerts", "/owml alert", "notify");
      this.staffAlertManager = plugin.getStaffAlertManager();
   }

   @Override
   public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission(this.getPermission())) {
            this.sendNoPermission(player);
            return true;
         } else {
            boolean alertsDisabled = this.staffAlertManager.toggleOreAlert(player);
            if (alertsDisabled) {
               player.sendMessage(Component.text("Алерты OverWatch выключены (снова включить: /owml alert).").color(NamedTextColor.RED));
            } else {
               player.sendMessage(Component.text("Алерты OverWatch включены.").color(NamedTextColor.GREEN));
            }

            return true;
         }
      } else {
         sender.sendMessage("This command can only be used by players.");
         return true;
      }
   }
}
