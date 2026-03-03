package net.denfry.owml.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.CheatCategory;
import net.denfry.owml.OverWatchContext;

/**
 * Listener for various player actions to trigger anti-cheat detections via the Orchestrator.
 * Automatically ignores players in Creative or Spectator mode to prevent false positives.
 */
public class CheatListener implements Listener {
    private final OverWatchML plugin;
    private final OverWatchContext context;

    public CheatListener(OverWatchML plugin, OverWatchContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    private boolean shouldSkip(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null || shouldSkip(player)) return;
        
        // Record event
        context.getProfileManager().getEventBuffer(player.getUniqueId())
            .addEvent(new net.denfry.owml.detection.PlayerEventBuffer.BehavioralEvent(
                System.currentTimeMillis(), "move", 1.0, event.getTo().toVector()));

        // Basic movement check trigger
        context.getDetectionOrchestrator().runDetection(player, CheatCategory.MOVEMENT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (shouldSkip(player)) return;
            
            // Record event
            context.getProfileManager().getEventBuffer(player.getUniqueId())
                .addEvent(new net.denfry.owml.detection.PlayerEventBuffer.BehavioralEvent(
                    System.currentTimeMillis(), "combat_hit", event.getFinalDamage(), event.getEntity().getType().name()));

            // Basic combat check trigger
            context.getDetectionOrchestrator().runDetection(player, CheatCategory.COMBAT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (shouldSkip(player)) return;
        
        // Record event
        context.getProfileManager().getEventBuffer(player.getUniqueId())
            .addEvent(new net.denfry.owml.detection.PlayerEventBuffer.BehavioralEvent(
                System.currentTimeMillis(), "place", 1.0, event.getBlock().getType().name()));

        // Basic world/scaffold check trigger
        context.getDetectionOrchestrator().runDetection(player, CheatCategory.SCAFFOLD);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (shouldSkip(player)) return;
        
        // Record event
        context.getProfileManager().getEventBuffer(player.getUniqueId())
            .addEvent(new net.denfry.owml.detection.PlayerEventBuffer.BehavioralEvent(
                System.currentTimeMillis(), "interact", 1.0, event.getAction().name()));

        // Basic interaction/autoclicker check trigger
        context.getDetectionOrchestrator().runDetection(player, CheatCategory.AUTOCLICKER);
    }
}
