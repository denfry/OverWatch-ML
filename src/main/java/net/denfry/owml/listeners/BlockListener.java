package net.denfry.owml.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.OverWatchContext;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.PunishmentHandlerManager;
import net.denfry.owml.punishments.handlers.Paranoia.ParanoiaHandler;

import java.util.UUID;

import static net.denfry.owml.utils.LocationUtils.formatLocation;
import static net.denfry.owml.utils.LocationUtils.getFriendlyWorldName;

public class BlockListener implements Listener {
    private final ConfigManager configManager;
    private final StaffAlertManager staffAlertManager;
    private final PunishmentManager punishmentManager;
    private final OverWatchML plugin;
    private final PunishmentHandlerManager punishmentHandlerManager;
    private final ParanoiaHandler paranoiaHandler;
    private final OverWatchContext context;

    public BlockListener(OverWatchML plugin, ConfigManager configManager, StaffAlertManager staffAlertManager, DecoyManager decoyManager, PunishmentManager punishmentManager, ParanoiaHandler paranoiaHandler, OverWatchContext context) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.staffAlertManager = staffAlertManager;
        this.punishmentManager = punishmentManager;
        this.paranoiaHandler = paranoiaHandler;
        this.context = context;
        this.punishmentHandlerManager = new PunishmentHandlerManager(plugin, configManager, punishmentManager, paranoiaHandler);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material mat = block.getType();
        if (configManager.getNaturalOres().contains(mat)) {
            Location loc = block.getLocation();
            context.getDecoyService().addPlayerPlacedOre(loc);

            if (configManager.isCachedDebugEnabled()) {
                Player player = event.getPlayer();
                plugin.getLogger().info("Player " + player.getName() + " placed " + mat + " at " + formatLocation(loc) + ", added to player-placed tracking");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material ore = block.getType();
        Location loc = block.getLocation();
        UUID playerId = player.getUniqueId();

        // Check if anti-xray checks are disabled in this world
        if (configManager.isWorldDisabled(loc.getWorld().getName())) {
            return;
        }

        // Record performance metrics
        net.denfry.owml.utils.PerformanceMonitor.recordBlockBreakCheck();

        if (configManager.isCachedDebugEnabled() && configManager.getNaturalOres().contains(ore)) {
            plugin.getLogger().info("[EARLY CHECK] BlockListener processing block break for " + player.getName() + " at " + formatLocation(loc));
        }

        if (configManager.isCachedDebugEnabled() && configManager.getNaturalOres().contains(ore)) {
            boolean isCurrentlyPlayerPlaced = context.getDecoyService().isPlayerPlacedOre(loc);
            plugin.getLogger().info("isPlayerPlacedOre returns: " + isCurrentlyPlayerPlaced + " for location " + formatLocation(loc));
        }

        if (context.getDecoyService().isPlayerPlacedOre(loc)) {
            if (configManager.isCachedDebugEnabled()) {
                plugin.getLogger().info("Player-placed ore detected for " + player.getName() + " at " + formatLocation(loc) + ", skipping processing");
            }
            context.getDecoyService().removePlayerPlacedOre(loc);
            return;
        }

        boolean cancelEvent = punishmentHandlerManager.processBlockBreak(event, player, block);

        if (cancelEvent) {
            event.setCancelled(true);
            return;
        }

        if (punishmentManager.hasParanoiaMode(playerId) || player.getLocation().getY() < 30) {
            paranoiaHandler.processBlockBreak(player, block);
        }

        context.getStatsService().addOreMined(player.getUniqueId(), ore);

        if (!configManager.getNaturalOres().contains(ore)) {
            return;
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Processing natural ore break for " + player.getName() + " at " + formatLocation(loc));
        }

        context.getDecoyService().trackOreBreak(player, block, ore);

        if (context.getDecoyService().isDecoy(loc)) {
            if (configManager.isCachedDebugEnabled()) {
                plugin.getLogger().info("Decoy ore detected for " + player.getName() + " at " + formatLocation(loc));
            }

            context.getSuspiciousService().addSuspicious(player.getUniqueId());

            punishmentManager.checkAndPunish(player);

            if (configManager.isWarnOnDecoy()) {
                player.sendMessage(Component.text("Suspicious mining behavior detected!", NamedTextColor.RED));
            }
            String friendlyWorld = getFriendlyWorldName(loc.getWorld());
            String formattedLoc = formatLocation(loc);
            String rawMessage = "Player " + player.getName() + " broke a decoy ore at " + friendlyWorld + " " + formattedLoc;
            event.getPlayer().getServer().getLogger().warning(rawMessage);
            if (configManager.isStaffAlertEnabled()) {
                staffAlertManager.alertStaffWithTeleport(player, loc, rawMessage);
            }
            staffAlertManager.logDecoyEvent(rawMessage);

            if (plugin.getConfigManager().isWebhookAlertEnabled("xray_detection")) {
                net.denfry.owml.utils.AsyncExecutor.executeIO(() -> 
                    plugin.getWebhookManager().sendXrayAlert(player, ore.name(), loc, 100.0)
                );
            }

            context.getDecoyService().removeDecoy(loc);
            return;
        }


        if (configManager.isStaffOreAlerts()) {
            staffAlertManager.updateStaffOreCounter(player, ore, loc);
        }
    }
}
