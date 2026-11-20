package net.denfry.owml.commands.subcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.AdvancedDetectionEngine;
import net.denfry.owml.detection.PlayerDetectionData;
import net.denfry.owml.utils.LocationUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SuspiciousLocationsCommand implements CommandExecutor {
   private final OverWatchML plugin;

   public SuspiciousLocationsCommand(OverWatchML plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("owml.suspicious.view")) {
            player.sendMessage(Component.text("You don't have permission to view suspicious locations.").color(NamedTextColor.RED));
            return true;
         } else if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /owml suspicious <player>").color(NamedTextColor.RED));
            return true;
         } else {
            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore()) {
               player.sendMessage(Component.text("Player not found: " + targetName).color(NamedTextColor.RED));
               return true;
            } else {
               AdvancedDetectionEngine detectionEngine = this.plugin.getAdvancedDetectionEngine();
               if (detectionEngine == null) {
                  player.sendMessage(Component.text("Detection engine not available.").color(NamedTextColor.RED));
                  return true;
               } else {
                  PlayerDetectionData playerData = detectionEngine.getPlayerData(target.getUniqueId());
                  if (playerData == null) {
                     player.sendMessage(Component.text("No data found for player: " + targetName).color(NamedTextColor.RED));
                     return true;
                  } else {
                     List<PlayerDetectionData.SuspiciousLocationRecord> suspiciousLocs = playerData.getSuspiciousLocations();
                     if (suspiciousLocs.isEmpty()) {
                        player.sendMessage(Component.text("No suspicious locations found for " + targetName).color(NamedTextColor.YELLOW));
                        return true;
                     } else {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("═══════════════════════════════════════════").color(NamedTextColor.GOLD));
                        player.sendMessage(
                           Component.text("  Suspicious Locations for ", NamedTextColor.YELLOW)
                              .append(Component.text(targetName, NamedTextColor.AQUA, new TextDecoration[]{TextDecoration.BOLD}))
                        );
                        player.sendMessage(Component.text("  Total: " + suspiciousLocs.size() + " location(s)", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("═══════════════════════════════════════════").color(NamedTextColor.GOLD));
                        player.sendMessage(Component.empty());
                        List<PlayerDetectionData.SuspiciousLocationRecord> reversedList = new ArrayList<>(suspiciousLocs);
                        Collections.reverse(reversedList);
                        int index = 1;

                        for (PlayerDetectionData.SuspiciousLocationRecord record : reversedList) {
                           Location loc = record.getLocation();
                           String formattedLoc = LocationUtils.formatLocation(loc);
                           String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
                           String tpCommand = String.format("/owml teleport %d %d %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                           Component message = ((TextComponent)((TextComponent)((TextComponent)Component.text(
                                          String.format("#%d ", index), NamedTextColor.GOLD, new TextDecoration[]{TextDecoration.BOLD}
                                       )
                                       .append(Component.text(formattedLoc, NamedTextColor.YELLOW)))
                                    .append(Component.text(" in ", NamedTextColor.GRAY)))
                                 .append(Component.text(worldName, NamedTextColor.GREEN)))
                              .append(
                                 ((TextComponent)Component.text(" [TP]", NamedTextColor.AQUA, new TextDecoration[]{TextDecoration.BOLD})
                                       .clickEvent(ClickEvent.runCommand(tpCommand)))
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to teleport", NamedTextColor.GREEN)))
                              );
                           player.sendMessage(message);
                           Component details = ((TextComponent)((TextComponent)((TextComponent)Component.text("   ", NamedTextColor.GRAY)
                                       .append(Component.text("Reason: ", NamedTextColor.DARK_GRAY)))
                                    .append(Component.text(record.getReason(), NamedTextColor.RED)))
                                 .append(Component.text(" • ", NamedTextColor.DARK_GRAY)))
                              .append(Component.text(record.getFormattedTime(), NamedTextColor.DARK_GRAY));
                           player.sendMessage(details);
                           player.sendMessage(Component.empty());
                           index++;
                        }

                        player.sendMessage(Component.text("═══════════════════════════════════════════").color(NamedTextColor.GOLD));
                        player.sendMessage(
                           Component.text("  Click [TP] to teleport to a location", NamedTextColor.GRAY, new TextDecoration[]{TextDecoration.ITALIC})
                        );
                        player.sendMessage(Component.text("═══════════════════════════════════════════").color(NamedTextColor.GOLD));
                        return true;
                     }
                  }
               }
            }
         }
      } else {
         sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
         return true;
      }
   }
}
