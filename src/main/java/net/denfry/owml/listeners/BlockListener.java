package net.denfry.owml.listeners;

import java.util.Set;
import java.util.UUID;
import net.denfry.owml.OverWatchContext;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.detection.CheatCategory;
import net.denfry.owml.detection.DetectionOrchestrator;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.managers.IDecoyService;
import net.denfry.owml.managers.IStatsService;
import net.denfry.owml.managers.ISuspiciousService;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.PunishmentHandlerManager;
import net.denfry.owml.punishments.handlers.Paranoia.ParanoiaHandler;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.LocationUtils;
import net.denfry.owml.utils.PerformanceMonitor;
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

public class BlockListener implements Listener {
   private final ConfigManager configManager;
   private final StaffAlertManager staffAlertManager;
   private final PunishmentManager punishmentManager;
   private final OverWatchML plugin;
   private final PunishmentHandlerManager punishmentHandlerManager;
   private final ParanoiaHandler paranoiaHandler;
   private final OverWatchContext context;
   private final Set<Material> naturalOres;
   private final IDecoyService decoyService;
   private final ISuspiciousService suspiciousService;
   private final IStatsService statsService;
   private final DetectionOrchestrator detectionOrchestrator;

   public BlockListener(
      OverWatchML plugin,
      ConfigManager configManager,
      StaffAlertManager staffAlertManager,
      DecoyManager decoyManager,
      PunishmentManager punishmentManager,
      ParanoiaHandler paranoiaHandler,
      OverWatchContext context
   ) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.staffAlertManager = staffAlertManager;
      this.punishmentManager = punishmentManager;
      this.paranoiaHandler = paranoiaHandler;
      this.context = context;
      this.punishmentHandlerManager = new PunishmentHandlerManager(plugin, configManager, punishmentManager, paranoiaHandler);
      this.naturalOres = configManager.getNaturalOres();
      this.decoyService = context.getDecoyService();
      this.suspiciousService = context.getSuspiciousService();
      this.statsService = context.getStatsService();
      this.detectionOrchestrator = context.getDetectionOrchestrator();
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onBlockPlace(BlockPlaceEvent event) {
      if (!event.isCancelled()) {
         Block block = event.getBlock();
         Material mat = block.getType();
         if (this.naturalOres.contains(mat)) {
            Location loc = block.getLocation();
            this.decoyService.addPlayerPlacedOre(loc);
            if (this.configManager.isCachedDebugEnabled()) {
               Player player = event.getPlayer();
               this.plugin
                  .getLogger()
                  .info("Player " + player.getName() + " placed " + mat + " at " + LocationUtils.formatLocation(loc) + ", added to player-placed tracking");
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onBlockBreak(BlockBreakEvent event) {
      if (!event.isCancelled()) {
         Player player = event.getPlayer();
         if (!player.hasPermission("owml.bypass")) {
            Block block = event.getBlock();
            Material ore = block.getType();
            Location loc = block.getLocation();
            UUID playerId = player.getUniqueId();
            if (!this.configManager.isWorldDisabled(loc.getWorld().getName())) {
               PerformanceMonitor.recordBlockBreakCheck();
               if (this.configManager.isCachedDebugEnabled() && this.naturalOres.contains(ore)) {
                  this.plugin
                     .getLogger()
                     .info("[EARLY CHECK] BlockListener processing block break for " + player.getName() + " at " + LocationUtils.formatLocation(loc));
               }

               if (this.decoyService.isPlayerPlacedOre(loc)) {
                  if (this.configManager.isCachedDebugEnabled()) {
                     this.plugin
                        .getLogger()
                        .info("Player-placed ore detected for " + player.getName() + " at " + LocationUtils.formatLocation(loc) + ", skipping processing");
                  }

                  this.context.getDecoyService().removePlayerPlacedOre(loc);
               } else {
                  boolean cancelEvent = this.punishmentHandlerManager.processBlockBreak(event, player, block);
                  if (cancelEvent) {
                     event.setCancelled(true);
                  } else {
                     if (this.punishmentManager.hasParanoiaMode(playerId) || player.getLocation().getY() < 30.0) {
                        this.paranoiaHandler.processBlockBreak(player, block);
                     }

                     this.statsService.addOreMined(player.getUniqueId(), ore);
                     if (this.naturalOres.contains(ore)) {
                        if (this.configManager.isDebugEnabled()) {
                           this.plugin.getLogger().info("Processing natural ore break for " + player.getName() + " at " + LocationUtils.formatLocation(loc));
                        }

                        this.decoyService.trackOreBreak(player, block, ore);
                        this.detectionOrchestrator.runDetection(player, CheatCategory.XRAY);
                        if (this.decoyService.isDecoy(loc)) {
                           if (this.configManager.isCachedDebugEnabled()) {
                              this.plugin.getLogger().info("Decoy ore detected for " + player.getName() + " at " + LocationUtils.formatLocation(loc));
                           }

                           this.suspiciousService.addSuspicious(player.getUniqueId());
                           this.punishmentManager.checkAndPunish(player);
                           if (this.configManager.isWarnOnDecoy()) {
                              player.sendMessage(Component.text("Suspicious mining behavior detected!", NamedTextColor.RED));
                           }

                           String friendlyWorld = LocationUtils.getFriendlyWorldName(loc.getWorld());
                           String formattedLoc = LocationUtils.formatLocation(loc);
                           String rawMessage = "Player " + player.getName() + " broke a decoy ore at " + friendlyWorld + " " + formattedLoc;
                           event.getPlayer().getServer().getLogger().warning(rawMessage);
                           if (this.configManager.isStaffAlertEnabled()) {
                              this.staffAlertManager.alertStaffWithTeleport(player, loc, rawMessage);
                           }

                           this.staffAlertManager.logDecoyEvent(rawMessage);
                           if (this.plugin.getConfigManager().isWebhookAlertEnabled("xray_detection")) {
                              AsyncExecutor.executeIO(() -> this.plugin.getWebhookManager().sendXrayAlert(player, ore.name(), loc, 100.0));
                           }

                           this.decoyService.removeDecoy(loc);
                        } else {
                           if (this.configManager.isStaffOreAlerts()) {
                              this.staffAlertManager.updateStaffOreCounter(player, ore, loc);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
