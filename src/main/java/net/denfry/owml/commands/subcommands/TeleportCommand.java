package net.denfry.owml.commands.subcommands;

import net.denfry.owml.OverWatchML;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.utils.LocationUtils;

public class TeleportCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("owml.teleport")) {
            player.sendMessage(Component.text("You do not have permission to teleport.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 1) {
            String targetPlayerName = args[0];
            Player targetPlayer = player.getServer().getPlayer(targetPlayerName);

            if (targetPlayer != null && targetPlayer.isOnline()) {
                // Teleport to online player
                player.teleport(targetPlayer.getLocation());
                player.sendMessage(Component.text("Teleported to " + targetPlayer.getName() + " at " + LocationUtils.formatLocation(targetPlayer.getLocation())).color(NamedTextColor.GREEN));
                return true;
            } else {
                // Try to find last suspicious location for offline player
                OverWatchML plugin = OverWatchML.getInstance();
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerName);
                
                if (offlinePlayer.hasPlayedBefore()) {
                    var detectionEngine = plugin.getAdvancedDetectionEngine();
                    if (detectionEngine != null) {
                        var playerData = detectionEngine.getPlayerData(offlinePlayer.getUniqueId());
                        if (playerData != null) {
                            Location suspiciousLoc = playerData.getLastSuspiciousLocation();
                            if (suspiciousLoc != null) {
                                player.teleport(suspiciousLoc);
                                player.sendMessage(Component.text("Player is offline. Teleported to last suspicious location: " + LocationUtils.formatLocation(suspiciousLoc)).color(NamedTextColor.YELLOW));
                                return true;
                            }
                        }
                    }
                }
                
                player.sendMessage(Component.text("Player " + targetPlayerName + " is not online and has no tracked suspicious location.").color(NamedTextColor.RED));
                return true;
            }
        }
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /<main> teleport <player> or /<main> teleport <x> <y> <z>").color(NamedTextColor.RED));
            return true;
        }

        try {
            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int z = Integer.parseInt(args[2]);
            Location loc = player.getWorld().getBlockAt(x, y, z).getLocation();
            player.teleport(loc);
            player.sendMessage(Component.text("Teleported to " + LocationUtils.formatLocation(loc)).color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid coordinates.").color(NamedTextColor.RED));
        }

        return true;
    }
}
