package net.denfry.owml.commands.subcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.AbstractSubCommand;
import net.denfry.owml.detection.AdvancedDetectionEngine;
import net.denfry.owml.detection.PlayerDetectionData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TeleportCommand extends AbstractSubCommand {
   public TeleportCommand(OverWatchML plugin) {
      super(plugin, "teleport", "owml.teleport", "Teleport to player or coordinates", "/owml teleport <player|x y z>", "tp", "goto");
   }

   @Override
   public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission(this.getPermission())) {
            this.sendNoPermission(player);
            return true;
         } else if (args.length == 1) {
            String targetPlayerName = args[0];
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer != null && targetPlayer.isOnline()) {
               player.teleportAsync(targetPlayer.getLocation());
               player.sendMessage(Component.text("Teleported to " + targetPlayer.getName()).color(NamedTextColor.GREEN));
               return true;
            } else {
               OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerName);
               AdvancedDetectionEngine detectionEngine = this.plugin.getAdvancedDetectionEngine();
               if (detectionEngine != null) {
                  PlayerDetectionData playerData = detectionEngine.getPlayerData(offlinePlayer.getUniqueId());
                  if (playerData != null && playerData.getLastSuspiciousLocation() != null) {
                     player.teleportAsync(playerData.getLastSuspiciousLocation());
                     player.sendMessage(Component.text("Teleported to last suspicious location of " + targetPlayerName).color(NamedTextColor.YELLOW));
                     return true;
                  }
               }

               player.sendMessage(Component.text("Player not found or no suspicious location tracked.").color(NamedTextColor.RED));
               return true;
            }
         } else if (args.length == 3) {
            try {
               double x = Double.parseDouble(args[0]);
               double y = Double.parseDouble(args[1]);
               double z = Double.parseDouble(args[2]);
               Location loc = new Location(player.getWorld(), x, y, z);
               player.teleportAsync(loc);
               player.sendMessage(Component.text("Teleported to coordinates.").color(NamedTextColor.GREEN));
               return true;
            } catch (NumberFormatException var11) {
               sender.sendMessage(Component.text("Invalid coordinates.").color(NamedTextColor.RED));
               return true;
            }
         } else {
            this.sendUsage(sender);
            return true;
         }
      } else {
         sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
         return true;
      }
   }

   @Nullable
   @Override
   public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
      if (args.length == 1) {
         String partial = args[0].toLowerCase();
         List<String> completions = new ArrayList<>();

         for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(partial)) {
               completions.add(p.getName());
            }
         }

         return completions;
      } else {
         return Collections.emptyList();
      }
   }
}
