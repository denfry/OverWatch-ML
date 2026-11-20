package net.denfry.owml.gui.subgui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class AlertHistoryGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final StaffAlertManager alertManager;
   private final Inventory inventory;
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

   public AlertHistoryGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.alertManager = plugin.getStaffAlertManager();
      this.inventory = Bukkit.createInventory(this, 54, "\ud83d\udccb Alert History");
   }

   @Override
   public void open(Player player) {
      this.refresh(player);
      player.openInventory(this.inventory);
      GUIEffects.playOpen(player);
   }

   @Override
   public void close(Player player) {
   }

   @Override
   public void refresh(Player player) {
      this.inventory.clear();
      List<StaffAlertManager.AlertRecord> history = new ArrayList<>(this.alertManager.getAlertHistory());
      Collections.reverse(history);
      int slot = 0;

      for (StaffAlertManager.AlertRecord record : history) {
         if (slot >= 45) {
            break;
         }

         String time = this.dateFormat.format(new Date(record.getTimestamp()));
         Location loc = record.getLocation();
         this.inventory
            .setItem(
               slot++,
               ItemBuilder.material(Material.PAPER)
                  .name("§eAlert at " + time)
                  .lore(
                     List.of(
                        "§7Player: §f" + record.getPlayerName(),
                        "§7Message: §f" + record.getMessage(),
                        "§7Location: §8" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                        "",
                        "§bClick to Teleport"
                     )
                  )
                  .build()
            );
      }

      this.inventory.setItem(49, ItemBuilder.material(Material.BOOK).name("§bSummary").lore(List.of("§7Total: §f" + history.size())).build());
      this.inventory.setItem(50, ItemBuilder.material(Material.BARRIER).name("§cClear History").build());
      this.inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Back").build());
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot < 45) {
         List<StaffAlertManager.AlertRecord> history = new ArrayList<>(this.alertManager.getAlertHistory());
         Collections.reverse(history);
         if (slot < history.size()) {
            player.teleportAsync(history.get(slot).getLocation());
            player.sendMessage("§aTeleported to alert location!");
         }
      } else if (slot == 50) {
         this.alertManager.clearAlertHistory();
         this.refresh(player);
      } else if (slot == 53) {
         GUINavigationStack.pop(player);
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
