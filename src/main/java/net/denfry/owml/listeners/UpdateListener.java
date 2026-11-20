package net.denfry.owml.listeners;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;
import net.denfry.owml.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateListener implements Listener {
   private final OverWatchML plugin;
   private final UpdateChecker updateChecker;
   private final AtomicBoolean periodicChecksSetup = new AtomicBoolean(false);

   public UpdateListener(OverWatchML plugin, UpdateChecker updateChecker) {
      this.plugin = plugin;
      this.updateChecker = updateChecker;
   }

   public void performInitialUpdateCheck() {
      this.plugin.getLogger().info("Update checking enabled - will check on player joins and periodically");
   }

   private void setupPeriodicChecksIfNeeded() {
      if (this.periodicChecksSetup.compareAndSet(false, true) && this.plugin.getConfig().getBoolean("periodic-update-checks.enabled", true)) {
         try {
            int checkIntervalHours = this.plugin.getConfig().getInt("periodic-update-checks.interval-hours", 24);
            this.updateChecker.setupPeriodicChecks(checkIntervalHours);
            this.plugin.getLogger().info("Scheduled automatic update checks every " + checkIntervalHours + " hours");
         } catch (Exception var2) {
            this.plugin.getLogger().warning("Failed to set up periodic update checks: " + var2.getMessage());
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.setupPeriodicChecksIfNeeded();
      if (player.hasPermission("owml.staff") && this.updateChecker.isUpdateAvailable()) {
         Schedulers.entityLater(
            player,
            () -> {
               Component divider = Component.text("=================================================").color(NamedTextColor.GREEN);
               player.sendMessage(divider);
               player.sendMessage(Component.text(" OverWatch-ML: New update available!").color(NamedTextColor.GREEN));
               player.sendMessage(
                  ((TextComponent)Component.text(" Current version: ").color(NamedTextColor.GREEN))
                     .append(Component.text(this.plugin.getPluginMeta().getVersion()).color(NamedTextColor.RED))
               );
               player.sendMessage(
                  ((TextComponent)Component.text(" New version: ").color(NamedTextColor.GREEN))
                     .append(Component.text(this.updateChecker.getLatestVersion()).color(NamedTextColor.GREEN))
               );
               boolean updateDownloaded = this.isUpdateDownloaded();
               if (player.hasPermission("owml.autoupdate")) {
                  if (!updateDownloaded) {
                     Component updateMessage = ((TextComponent)((TextComponent)((TextComponent)Component.text(" Click here to auto-update the plugin ")
                                 .color(NamedTextColor.GOLD))
                              .decorate(TextDecoration.BOLD))
                           .clickEvent(ClickEvent.runCommand("/OverWatch update auto")))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to download and prepare the update for next restart")));
                     player.sendMessage(updateMessage);
                  } else {
                     Component applyMessage = ((TextComponent)((TextComponent)((TextComponent)Component.text(" Click here to apply the downloaded update ")
                                 .color(NamedTextColor.GOLD))
                              .decorate(TextDecoration.BOLD))
                           .clickEvent(ClickEvent.runCommand("/OverWatch update apply")))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to apply the update (server restart still required)")));
                     player.sendMessage(applyMessage);
                  }
               }

               Component downloadMessage = Component.text(" Download manually: ").color(NamedTextColor.YELLOW);
               Component downloadLink = ((TextComponent)((TextComponent)((TextComponent)Component.text("Click here").color(NamedTextColor.AQUA))
                        .decorate(TextDecoration.UNDERLINED))
                     .clickEvent(ClickEvent.openUrl("https://www.spigotmc.org/resources/122967")))
                  .hoverEvent(HoverEvent.showText(Component.text("Open download page in your browser")));
               player.sendMessage(downloadMessage.append(downloadLink));
               player.sendMessage(divider);
            },
            60L
         );
      }
   }

   private boolean isUpdateDownloaded() {
      File updateMarker = new File(this.plugin.getDataFolder(), "pending_update.txt");
      return updateMarker.exists();
   }
}
