package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GUIManager implements Listener {
   private final OverWatchML plugin;

   public GUIManager(OverWatchML plugin) {
      this.plugin = plugin;
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
         Player player = (Player)event.getPlayer();
         Schedulers.entityLater(player, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof OverWatchGUI)) {
               GUINavigationStack.clear(player);
            }
         }, 1L);
      }
   }
}
