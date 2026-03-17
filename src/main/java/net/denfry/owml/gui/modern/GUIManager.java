package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI Manager responsible for handling events and auto-refresh.
 */
public class GUIManager implements Listener {
    private final OverWatchML plugin;

    public GUIManager(OverWatchML plugin) {
        this.plugin = plugin;

        // Auto-refresh task every 5 seconds
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Inventory topInv = player.getOpenInventory().getTopInventory();
                if (topInv.getHolder() instanceof OverWatchGUI gui) {
                    gui.refresh(player);
                }
            }
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof OverWatchGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof OverWatchGUI) {
            Player player = (Player) event.getPlayer();
            // Clear stack only if GUI was manually closed (not by navigating to another GUI in our system)
            // We check after a small delay if the player has another OverWatch GUI open
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof OverWatchGUI)) {
                    GUINavigationStack.clear(player);
                }
            }, 1L);
        }
    }
}
