package net.denfry.owml.gui.subgui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.StaffMenuGUI;
import net.denfry.owml.managers.SuspiciousManager;
import net.denfry.owml.ml.MLDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class LegacySuspiciousPlayersGUI {
   public static final String PERMISSION = "owml.gui_suspicious";
   private final List<UUID> suspiciousPlayers;
   private final int itemsPerPage = 36;
   private final int currentPage;
   private final int totalPages;
   private final Map<Integer, UUID> playerSlots = new ConcurrentHashMap<>();
   private final Inventory inv;

   public LegacySuspiciousPlayersGUI(int currentPage) {
      this.currentPage = currentPage;
      this.inv = Bukkit.createInventory(null, 54, Component.text("\ud83d\udd0d Suspicious Activity").color(NamedTextColor.AQUA));
      this.suspiciousPlayers = new ArrayList<>();
      Map<UUID, Integer> counts = SuspiciousManager.getSuspiciousCounts();
      this.suspiciousPlayers.addAll(counts.keySet());
      this.totalPages = (int)Math.ceil(this.suspiciousPlayers.size() / 36.0);
      this.initializeItems();
   }

   public static void handleClick(Player staff, int slot, Inventory clickedInventory) {
      new LegacySuspiciousPlayersGUI(0);
      if (slot == 45 && clickedInventory.getItem(45).getType() == Material.ARROW) {
         String titleText = clickedInventory.getItem(46).getItemMeta().getDisplayName();
         int currentPage = Integer.parseInt(titleText.split("/")[0].split(" ")[1]) - 1;
         new LegacySuspiciousPlayersGUI(currentPage - 1).openInventory(staff);
      } else if (slot == 53 && clickedInventory.getItem(53).getType() == Material.ARROW) {
         String titleText = clickedInventory.getItem(46).getItemMeta().getDisplayName();
         int currentPage = Integer.parseInt(titleText.split("/")[0].split(" ")[1]) - 1;
         new LegacySuspiciousPlayersGUI(currentPage + 1).openInventory(staff);
      } else if (slot == 49) {
         new StaffMenuGUI(OverWatchML.getInstance()).open(staff);
      }
   }

   private void initializeItems() {
      this.inv.clear();
      this.playerSlots.clear();
      this.setNavigationItems();
      int startIndex = this.currentPage * 36;
      int endIndex = Math.min(startIndex + 36, this.suspiciousPlayers.size());
      int slot = 0;

      for (int i = startIndex; i < endIndex && slot < 36; i++) {
         UUID uuid = this.suspiciousPlayers.get(i);
         OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
         int count = SuspiciousManager.getSuspiciousCounts().getOrDefault(uuid, 0);
         int reportCount = op.getName() != null ? MLDataManager.getPlayerReports(op.getName()).size() : 0;
         this.inv.setItem(slot, this.createPlayerHead(op, count, reportCount));
         this.playerSlots.put(slot, uuid);
         slot++;
      }

      ItemStack backButton = new ItemStack(Material.BARRIER);
      ItemMeta backMeta = backButton.getItemMeta();
      backMeta.displayName(Component.text("Back").color(NamedTextColor.RED));
      backButton.setItemMeta(backMeta);
      this.inv.setItem(49, backButton);
   }

   private void setNavigationItems() {
      if (this.currentPage > 0) {
         this.inv.setItem(45, this.createNavItem(Material.ARROW, "Previous", NamedTextColor.GREEN));
      }

      this.inv.setItem(46, this.createNavItem(Material.PAPER, "Page " + (this.currentPage + 1), NamedTextColor.YELLOW));
      if (this.currentPage < this.totalPages - 1) {
         this.inv.setItem(53, this.createNavItem(Material.ARROW, "Next", NamedTextColor.GREEN));
      }
   }

   private ItemStack createNavItem(Material material, String name, NamedTextColor color) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name).color(color));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createPlayerHead(OfflinePlayer op, int count, int reports) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)head.getItemMeta();
      meta.setOwningPlayer(op);
      meta.displayName(Component.text(op.getName() != null ? op.getName() : "Unknown").color(NamedTextColor.GOLD));
      head.setItemMeta(meta);
      return head;
   }

   public void openInventory(Player player) {
      player.openInventory(this.inv);
   }
}
