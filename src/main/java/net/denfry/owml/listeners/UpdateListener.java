package net.denfry.owml.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.UpdateChecker;

import java.io.File;

public class UpdateListener implements Listener {

    private final OverWatchML plugin;
    private final UpdateChecker updateChecker;
    private final AtomicBoolean periodicChecksSetup = new AtomicBoolean(false);

    public UpdateListener(OverWatchML plugin, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
    }

    /**
     * Manually trigger an update check (call this after plugin is fully enabled)
     */
    public void performInitialUpdateCheck() {
        // Skip initial update check during plugin initialization to avoid scheduler issues
        // Update checks will still happen on player join and via periodic checks
        plugin.getLogger().info("Update checking enabled - will check on player joins and periodically");
    }

    /**
     * Set up periodic update checks when the first player joins (scheduler should be ready by then)
     */
    private void setupPeriodicChecksIfNeeded() {
        if (periodicChecksSetup.compareAndSet(false, true)) {
            if (plugin.getConfig().getBoolean("periodic-update-checks.enabled", true)) {
                try {
                    int checkIntervalHours = plugin.getConfig().getInt("periodic-update-checks.interval-hours", 24);
                    updateChecker.setupPeriodicChecks(checkIntervalHours);
                    plugin.getLogger().info("Scheduled automatic update checks every " + checkIntervalHours + " hours");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to set up periodic update checks: " + e.getMessage());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set up periodic checks on first player join (when scheduler should be ready)
        setupPeriodicChecksIfNeeded();

        if (player.hasPermission("owml.staff") && updateChecker.isUpdateAvailable()) {

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {

                Component divider = Component.text("=================================================").color(NamedTextColor.GREEN);


                player.sendMessage(divider);
                player.sendMessage(Component.text(" OverWatch-ML: New update available!").color(NamedTextColor.GREEN));


                player.sendMessage(Component.text(" Current version: ").color(NamedTextColor.GREEN).append(Component.text(plugin.getPluginMeta().getVersion()).color(NamedTextColor.RED)));


                player.sendMessage(Component.text(" New version: ").color(NamedTextColor.GREEN).append(Component.text(updateChecker.getLatestVersion()).color(NamedTextColor.GREEN)));


                boolean updateDownloaded = isUpdateDownloaded();


                if (player.hasPermission("owml.autoupdate")) {
                    if (!updateDownloaded) {

                        Component updateMessage = Component.text(" Click here to auto-update the plugin ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/OverWatch update auto")).hoverEvent(HoverEvent.showText(Component.text("Click to download and prepare the update for next restart")));

                        player.sendMessage(updateMessage);
                    } else {

                        Component applyMessage = Component.text(" Click here to apply the downloaded update ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/OverWatch update apply")).hoverEvent(HoverEvent.showText(Component.text("Click to apply the update (server restart still required)")));

                        player.sendMessage(applyMessage);
                    }
                }


                Component downloadMessage = Component.text(" Download manually: ").color(NamedTextColor.YELLOW);

                Component downloadLink = Component.text("Click here").color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl("https://www.spigotmc.org/resources/122967")).hoverEvent(HoverEvent.showText(Component.text("Open download page in your browser")));

                player.sendMessage(downloadMessage.append(downloadLink));
                player.sendMessage(divider);
            }, 60L);
        }
    }

    /**
     * Checks if an update has been downloaded but not yet applied
     */
    private boolean isUpdateDownloaded() {
        File updateMarker = new File(plugin.getDataFolder(), "pending_update.txt");
        return updateMarker.exists();
    }
}
