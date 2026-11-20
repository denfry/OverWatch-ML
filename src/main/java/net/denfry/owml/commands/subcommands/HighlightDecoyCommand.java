package net.denfry.owml.commands.subcommands;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.utils.Locs;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class HighlightDecoyCommand implements CommandExecutor {
   private final OverWatchML plugin;
   private final Map<UUID, Schedulers.Task> highlightTasks = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> highlightRadius = new ConcurrentHashMap<>();
   private final Map<UUID, GameMode> originalGameModes = new ConcurrentHashMap<>();
   private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> shouldReturnToOriginalLocation = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> inNearestMode = new ConcurrentHashMap<>();

   public HighlightDecoyCommand(OverWatchML plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         UUID playerId = player.getUniqueId();
         if (this.inNearestMode.getOrDefault(playerId, false)) {
            player.sendMessage(Component.text("You are already using a highlight command. Please wait for it to complete.").color(NamedTextColor.RED));
            return true;
         } else if (args.length == 0) {
            this.toggleHighlighting(player, false);
            return true;
         } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable")) {
               this.disableHighlighting(player);
               return true;
            } else if (args[0].equalsIgnoreCase("nearest")) {
               if (this.highlightTasks.containsKey(playerId)) {
                  this.disableHighlighting(player);
               }

               this.highlightNearestDecoy(player);
               return true;
            } else if (args[0].equalsIgnoreCase("return")) {
               boolean shouldReturn = !this.shouldReturnToOriginalLocation.getOrDefault(playerId, false);
               this.shouldReturnToOriginalLocation.put(playerId, shouldReturn);
               player.sendMessage(
                  Component.text("Return to original location on disable: " + (shouldReturn ? "ENABLED" : "DISABLED")).color(NamedTextColor.GREEN)
               );
               return true;
            } else {
               try {
                  int radius = Integer.parseInt(args[0]);
                  if (radius < 5) {
                     radius = 5;
                  }

                  if (radius > 50) {
                     radius = 50;
                  }

                  this.highlightRadius.put(playerId, radius);
                  player.sendMessage(Component.text("Highlight radius set to " + radius + " blocks.").color(NamedTextColor.GREEN));
                  if (this.highlightTasks.containsKey(playerId)) {
                     this.toggleHighlighting(player, false);
                     this.toggleHighlighting(player, false);
                  }

                  return true;
               } catch (NumberFormatException var8) {
                  player.sendMessage(Component.text("Invalid radius. Please enter a number between 5 and 50.").color(NamedTextColor.RED));
                  return true;
               }
            }
         } else {
            player.sendMessage(Component.text("Usage:").color(NamedTextColor.YELLOW));
            player.sendMessage(
               ((TextComponent)Component.text("/" + label + " highlight").color(NamedTextColor.GOLD))
                  .append(Component.text(" - Toggle highlighting of decoy veins").color(NamedTextColor.YELLOW))
            );
            player.sendMessage(
               ((TextComponent)Component.text("/" + label + " highlight <radius>").color(NamedTextColor.GOLD))
                  .append(Component.text(" - Set highlight radius (5-50 blocks)").color(NamedTextColor.YELLOW))
            );
            player.sendMessage(
               ((TextComponent)Component.text("/" + label + " highlight off").color(NamedTextColor.GOLD))
                  .append(Component.text(" - Disable highlighting").color(NamedTextColor.YELLOW))
            );
            player.sendMessage(
               ((TextComponent)Component.text("/" + label + " highlight nearest").color(NamedTextColor.GOLD))
                  .append(Component.text(" - Highlight only nearest decoy vein").color(NamedTextColor.YELLOW))
            );
            player.sendMessage(
               ((TextComponent)Component.text("/" + label + " highlight return").color(NamedTextColor.GOLD))
                  .append(Component.text(" - Toggle returning to original location").color(NamedTextColor.YELLOW))
            );
            return true;
         }
      } else {
         sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
         return true;
      }
   }

   private void toggleHighlighting(Player player, boolean isNearestMode) {
      UUID playerId = player.getUniqueId();
      if (this.highlightTasks.containsKey(playerId)) {
         this.disableHighlighting(player);
      } else {
         this.enableHighlighting(player, isNearestMode);
      }
   }

   private void enableHighlighting(final Player player, boolean isNearestMode) {
      final UUID playerId = player.getUniqueId();
      this.disableHighlighting(player);
      GameMode originalMode = player.getGameMode();
      Location originalLoc = player.getLocation().clone();
      this.originalGameModes.put(playerId, originalMode);
      this.originalLocations.put(playerId, originalLoc);
      this.inNearestMode.put(playerId, isNearestMode);
      player.setGameMode(GameMode.SPECTATOR);
      player.sendMessage(Component.text("Switched to spectator mode. You can now see through blocks.").color(NamedTextColor.YELLOW));
      final int radius = this.highlightRadius.getOrDefault(playerId, 30);
      Schedulers.Task task = (new Schedulers.Repeating() {
            private int ticks = 0;

            @Override
            public void run() {
               this.ticks++;
               if (!player.isOnline()) {
                  this.cancel();
                  HighlightDecoyCommand.this.cleanupPlayer(playerId);
               } else {
                  DecoyManager decoyManager = HighlightDecoyCommand.this.plugin.getDecoyManager();
                  Map<Location, Set<Location>> decoyVeins = decoyManager.getAllDecoyVeins();
                  if (decoyVeins.isEmpty()) {
                     if (this.ticks % 40 == 1) {
                        player.sendMessage(Component.text("No decoy veins found in range.").color(NamedTextColor.YELLOW));
                     }
                  } else {
                     int highlightedVeins = 0;
                     int totalBlocks = 0;
                     Location playerLoc = player.getLocation();

                     for (Entry<Location, Set<Location>> entry : decoyVeins.entrySet()) {
                        Location primaryLoc = entry.getKey();
                        Set<Location> veinBlocks = entry.getValue();
                        if (primaryLoc.getWorld() == playerLoc.getWorld() && !(primaryLoc.distance(playerLoc) > radius)) {
                           boolean validVein = false;

                           for (Location blockLoc : veinBlocks) {
                              if (decoyManager.isDecoy(blockLoc)) {
                                 HighlightDecoyCommand.this.highlightBlock(player, blockLoc);
                                 totalBlocks++;
                                 validVein = true;
                              }
                           }

                           if (validVein) {
                              highlightedVeins++;
                           }
                        }
                     }

                     if (highlightedVeins > 0) {
                        if (this.ticks % 40 == 1) {
                           player.sendMessage(
                              Component.text("Highlighting " + totalBlocks + " decoy blocks in " + highlightedVeins + " veins within " + radius + " blocks.")
                                 .color(NamedTextColor.GREEN)
                           );
                        }
                     } else if (this.ticks % 40 == 1) {
                        player.sendMessage(
                           Component.text("No decoy veins found within " + radius + " blocks. Move around to find some.").color(NamedTextColor.YELLOW)
                        );
                     }
                  }
               }
            }
         })
         .entityTimer(player, 1L, 15L);
      this.highlightTasks.put(playerId, task);
      player.sendMessage(
         Component.text("Decoy vein highlighting enabled with radius " + this.highlightRadius.getOrDefault(playerId, 30) + " blocks.")
            .color(NamedTextColor.GREEN)
      );
      Bukkit.getPluginManager().registerEvents(new Listener() {
         @EventHandler
         public void onPlayerQuit(PlayerQuitEvent event) {
            if (event.getPlayer().getUniqueId().equals(playerId)) {
               HighlightDecoyCommand.this.cleanupPlayer(playerId);
            }
         }
      }, this.plugin);
   }

   private void disableHighlighting(Player player) {
      UUID playerId = player.getUniqueId();
      Schedulers.Task task = this.highlightTasks.remove(playerId);
      if (task != null) {
         task.cancel();
      }

      GameMode originalMode = this.originalGameModes.remove(playerId);
      if (originalMode != null && player.isOnline()) {
         player.setGameMode(originalMode);
         player.sendMessage(Component.text("Restored your original game mode (" + this.formatGameMode(originalMode) + ").").color(NamedTextColor.YELLOW));
      }

      boolean shouldReturn = this.shouldReturnToOriginalLocation.getOrDefault(playerId, false) || this.inNearestMode.getOrDefault(playerId, false);
      if (shouldReturn && player.isOnline()) {
         Location originalLoc = this.originalLocations.get(playerId);
         if (originalLoc != null) {
            player.teleportAsync(originalLoc);
            player.sendMessage(Component.text("Returned you to your original location.").color(NamedTextColor.YELLOW));
         }
      }

      this.inNearestMode.remove(playerId);
      this.originalLocations.remove(playerId);
      if (player.isOnline()) {
         player.sendMessage(Component.text("Decoy vein highlighting disabled.").color(NamedTextColor.YELLOW));
      }
   }

   private void cleanupPlayer(UUID playerId) {
      Schedulers.Task quitTask = this.highlightTasks.remove(playerId);
      if (quitTask != null) {
         quitTask.cancel();
      }

      this.originalGameModes.remove(playerId);
      this.originalLocations.remove(playerId);
      this.inNearestMode.remove(playerId);
      this.highlightRadius.remove(playerId);
   }

   private String formatGameMode(GameMode mode) {
      switch (mode) {
         case CREATIVE:
            return "Creative";
         case SURVIVAL:
            return "Survival";
         case ADVENTURE:
            return "Adventure";
         case SPECTATOR:
            return "Spectator";
         default:
            return mode.toString();
      }
   }

   private void highlightNearestDecoy(final Player player) {
      final UUID playerId = player.getUniqueId();
      this.inNearestMode.put(playerId, true);
      GameMode originalMode = player.getGameMode();
      Location originalLocation = player.getLocation().clone();
      this.originalGameModes.put(playerId, originalMode);
      this.originalLocations.put(playerId, originalLocation);
      player.setGameMode(GameMode.SPECTATOR);
      player.sendMessage(Component.text("Switched to spectator mode temporarily.").color(NamedTextColor.YELLOW));
      DecoyManager decoyManager = this.plugin.getDecoyManager();
      Location playerLoc = player.getLocation();
      Map<Location, Set<Location>> decoyVeins = decoyManager.getAllDecoyVeins();
      if (decoyVeins.isEmpty()) {
         player.sendMessage(Component.text("No decoy veins found.").color(NamedTextColor.YELLOW));
         Schedulers.entityLater(player, () -> this.cleanupAndRestore(player), 40L);
      } else {
         Location nearestVeinLoc = null;
         double nearestDistance = Double.MAX_VALUE;
         Set<Location> nearestVeinBlocks = null;

         for (Entry<Location, Set<Location>> entry : decoyVeins.entrySet()) {
            Location veinLoc = entry.getKey();
            Set<Location> veinBlocks = entry.getValue();
            if (veinLoc.getWorld() == playerLoc.getWorld()) {
               boolean validVein = false;

               for (Location blockLoc : veinBlocks) {
                  if (decoyManager.isDecoy(blockLoc)) {
                     validVein = true;
                     break;
                  }
               }

               if (validVein) {
                  double distance = Locs.distance(veinLoc, playerLoc);
                  if (distance < nearestDistance) {
                     nearestDistance = distance;
                     nearestVeinLoc = veinLoc;
                     nearestVeinBlocks = veinBlocks;
                  }
               }
            }
         }

         if (nearestVeinLoc != null && nearestVeinBlocks != null) {
            player.teleportAsync(nearestVeinLoc);
            final Set<Location> finalNearestVeinBlocks = nearestVeinBlocks;
            final DecoyManager finalDecoyManager = decoyManager;
            Schedulers.Task highlightTask = (new Schedulers.Repeating() {
               private int count = 0;

               @Override
               public void run() {
                  if (this.count < 10 && player.isOnline()) {
                     if (this.count == 5) {
                        player.sendMessage(Component.text("You will be returned to your original location in 5 seconds...").color(NamedTextColor.YELLOW));
                     }

                     int validBlocks = 0;

                     for (Location blockLocx : finalNearestVeinBlocks) {
                        if (finalDecoyManager.isDecoy(blockLocx)) {
                           HighlightDecoyCommand.this.highlightBlock(player, blockLocx);
                           validBlocks++;
                        }
                     }

                     if (validBlocks == 0 && this.count > 0) {
                        player.sendMessage(Component.text("No decoy blocks remain in this vein. Returning you now.").color(NamedTextColor.YELLOW));
                        HighlightDecoyCommand.this.cleanupAndRestore(player);
                        this.cancel();
                     } else {
                        this.count++;
                     }
                  } else {
                     if (player.isOnline()) {
                        HighlightDecoyCommand.this.cleanupAndRestore(player);
                     } else {
                        HighlightDecoyCommand.this.cleanupPlayer(playerId);
                     }

                     this.cancel();
                  }
               }
            }).entityTimer(player, 1L, 20L);
            this.highlightTasks.put(playerId, highlightTask);
            player.sendMessage(
               Component.text(
                     "Teleported to and highlighting nearest decoy vein at "
                        + this.formatLocation(nearestVeinLoc)
                        + " ("
                        + String.format("%.1f", nearestDistance)
                        + " blocks away)"
                  )
                  .color(NamedTextColor.GREEN)
            );
            player.sendMessage(Component.text("You will be returned to your original location and game mode in 10 seconds.").color(NamedTextColor.YELLOW));
         } else {
            player.sendMessage(Component.text("No valid decoy veins found in your world.").color(NamedTextColor.YELLOW));
            Schedulers.entityLater(player, () -> this.cleanupAndRestore(player), 40L);
         }
      }
   }

   private void cleanupAndRestore(Player player) {
      UUID playerId = player.getUniqueId();
      GameMode originalMode = this.originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
      Location originalLoc = this.originalLocations.getOrDefault(playerId, player.getLocation());
      Schedulers.Task task = this.highlightTasks.remove(playerId);
      if (task != null) {
         task.cancel();
      }

      this.originalGameModes.remove(playerId);
      this.originalLocations.remove(playerId);
      this.inNearestMode.remove(playerId);
      player.setGameMode(originalMode);
      player.teleportAsync(originalLoc);
      player.sendMessage(Component.text("Returned you to your original game mode and location.").color(NamedTextColor.YELLOW));
   }

   private void highlightBlock(Player player, Location location) {
      World world = location.getWorld();
      if (world != null) {
         player.spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.5, 0.5), 5, 0.4, 0.4, 0.4, 0.01);
         player.spawnParticle(Particle.ASH, location.clone().add(0.5, 0.5, 0.5), 1, 0.0, 0.0, 0.0, 1.0);
      }
   }

   private String formatLocation(Location loc) {
      return loc.getWorld().getName() + " [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
   }
}
