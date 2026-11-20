package net.denfry.owml.gui.modern;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class LiveAlertsGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final Inventory inventory;
   private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
   private LiveAlertsGUI.AlertTypeFilter typeFilter = LiveAlertsGUI.AlertTypeFilter.ALL;
   private int currentPage = 0;

   public LiveAlertsGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.inventory = Bukkit.createInventory(this, 54, "Live Alerts Panel");
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
      List<StaffAlertManager.AlertRecord> allAlerts = new ArrayList<>(this.plugin.getStaffAlertManager().getAlertHistory());
      Collections.reverse(allAlerts);
      List<StaffAlertManager.AlertRecord> filteredAlerts = allAlerts.stream().filter(alertx -> {
         if (this.typeFilter == LiveAlertsGUI.AlertTypeFilter.ALL) {
            return true;
         } else {
            String msg = alertx.getMessage().toLowerCase();
            if (this.typeFilter == LiveAlertsGUI.AlertTypeFilter.CRITICAL) {
               return msg.contains("critical") || msg.contains("high");
            } else {
               return this.typeFilter != LiveAlertsGUI.AlertTypeFilter.WARNING ? true : msg.contains("suspicious") || msg.contains("warn");
            }
         }
      }).collect(Collectors.toList());
      int totalPages = (int)Math.ceil(filteredAlerts.size() / 45.0);
      int start = this.currentPage * 45;
      int end = Math.min(start + 45, filteredAlerts.size());

      for (int i = 0; i < end - start; i++) {
         StaffAlertManager.AlertRecord alert = filteredAlerts.get(start + i);
         Material mat = alert.getMessage().toLowerCase().contains("critical")
            ? Material.RED_CONCRETE
            : (alert.getMessage().toLowerCase().contains("suspicious") ? Material.YELLOW_CONCRETE : Material.BLUE_CONCRETE);
         String locationStr = "Unknown";
         if (alert.getLocation() != null) {
            locationStr = alert.getLocation().getBlockX() + ", " + alert.getLocation().getBlockY() + ", " + alert.getLocation().getBlockZ();
         }

         this.inventory
            .setItem(
               i,
               ItemBuilder.material(mat)
                  .name("§e" + alert.getPlayerName() + " §7- " + alert.getMessage())
                  .lore(
                     List.of(
                        "§7Time: §f" + this.timeFormat.format(alert.getTimestamp()),
                        "§7Location: §f" + locationStr,
                        "§7Confidence: §d85%",
                        "",
                        "§eLeft-Click: §7View Profile",
                        "§eRight-Click: §7Mark Read (Dismiss)",
                        "§eShift-Click: §7Teleport & Dismiss"
                     )
                  )
                  .build()
            );
      }

      this.inventory.setItem(45, ItemBuilder.material(Material.HOPPER).name("§bFilter: §f" + this.typeFilter).build());
      this.inventory.setItem(49, ItemBuilder.material(Material.BELL).name("§eUnread Alerts: " + filteredAlerts.size()).enchanted().build());
      this.inventory.setItem(46, ItemBuilder.material(Material.LAVA_BUCKET).name("§cMark All Read").lore(List.of("§7Right-click to clear all alerts")).build());
      this.inventory.setItem(53, ItemBuilder.material(Material.WRITABLE_BOOK).name("§6Export Today's Log").build());
      if (this.currentPage > 0) {
         this.inventory.setItem(51, ItemBuilder.material(Material.ARROW).name("§7Prev").build());
      }

      this.inventory.setItem(52, ItemBuilder.material(Material.PAPER).name("§fPage " + (this.currentPage + 1)).build());
      if (this.currentPage < totalPages - 1) {
         this.inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Next").build());
      }
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot < 45) {
         List<StaffAlertManager.AlertRecord> allAlerts = new ArrayList<>(this.plugin.getStaffAlertManager().getAlertHistory());
         Collections.reverse(allAlerts);
         int index = this.currentPage * 45 + slot;
         if (index < allAlerts.size()) {
            StaffAlertManager.AlertRecord alert = allAlerts.get(index);
            if (event.isLeftClick() && !event.isShiftClick()) {
               OfflinePlayer target = Bukkit.getOfflinePlayer(alert.getPlayerName());
               GUINavigationStack.push(player, new PlayerProfileGUI(this.plugin, target.getUniqueId()));
            } else if (event.isRightClick()) {
               this.dismissAlert(alert);
               this.refresh(player);
               GUIEffects.playOpen(player);
            } else if (event.isShiftClick()) {
               if (alert.getLocation() != null) {
                  player.teleportAsync(alert.getLocation());
               }

               this.dismissAlert(alert);
               player.sendMessage("§aTeleported to alert location and dismissed.");
               player.closeInventory();
            }
         }
      } else {
         switch (slot) {
            case 45:
               this.typeFilter = LiveAlertsGUI.AlertTypeFilter.values()[(this.typeFilter.ordinal() + 1) % LiveAlertsGUI.AlertTypeFilter.values().length];
               this.refresh(player);
               break;
            case 46:
               if (event.isRightClick()) {
                  this.plugin.getStaffAlertManager().getAlertHistory().clear();
                  player.sendMessage("§aAll alerts cleared!");
                  this.refresh(player);
                  GUIEffects.playSuccess(player);
               }
            case 47:
            case 48:
            case 49:
            case 50:
            case 52:
            default:
               break;
            case 51:
               if (this.currentPage > 0) {
                  this.currentPage--;
                  this.refresh(player);
               }
               break;
            case 53:
               this.currentPage++;
               this.refresh(player);
         }
      }
   }

   private void dismissAlert(StaffAlertManager.AlertRecord alert) {
      this.plugin.getStaffAlertManager().getAlertHistory().removeIf(a -> a.equals(alert));
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   static enum AlertTypeFilter {
      ALL,
      CRITICAL,
      WARNING,
      INFO;
   }
}
