package net.denfry.owml.gui.subgui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class PlayerStatsMainGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final int itemsPerPage = 45;
   private final Map<Integer, UUID> playerSlots = new ConcurrentHashMap<>();
   private final Inventory inv;
   private int currentPage;

   public PlayerStatsMainGUI(OverWatchML plugin, int currentPage) {
      this.plugin = plugin;
      this.currentPage = currentPage;
      this.inv = Bukkit.createInventory(this, 54, "\ud83d\udcca Player Analytics");
   }

   public PlayerStatsMainGUI(OverWatchML plugin) {
      this(plugin, 0);
   }

   @Override
   public void open(Player player) {
      this.refresh(player);
      player.openInventory(this.inv);
      GUIEffects.playOpen(player);
   }

   @Override
   public void close(Player player) {
   }

   @Override
   public void refresh(Player player) {
      this.inv.clear();
      this.playerSlots.clear();
      List<OfflinePlayer> playersWithStats = this.getPlayersWithStats();
      int totalPages = Math.max(1, (int)Math.ceil(playersWithStats.size() / 45.0));
      if (this.currentPage >= totalPages) {
         this.currentPage = totalPages - 1;
      }

      if (this.currentPage < 0) {
         this.currentPage = 0;
      }

      this.setNavigationItems(totalPages);
      int startIndex = this.currentPage * 45;
      int endIndex = Math.min(startIndex + 45, playersWithStats.size());

      for (int i = startIndex; i < endIndex; i++) {
         int slot = i - startIndex;
         if (slot >= 45) {
            break;
         }

         OfflinePlayer p = playersWithStats.get(i);
         this.inv.setItem(slot, this.createPlayerHead(p));
         this.playerSlots.put(slot, p.getUniqueId());
      }
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player staff = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot < 45) {
         UUID targetId = this.playerSlots.get(slot);
         if (targetId != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            GUINavigationStack.push(staff, new PlayerMiningStatsGUI(this.plugin, targetId, op.getName() != null ? op.getName() : "Unknown"));
         }
      } else if (slot == 45 && this.inv.getItem(45).getType() == Material.ARROW) {
         this.currentPage--;
         this.refresh(staff);
      } else if (slot == 53 && this.inv.getItem(53).getType() == Material.ARROW) {
         this.currentPage++;
         this.refresh(staff);
      } else if (slot == 47) {
         GUINavigationStack.pop(staff);
      }
   }

   public Inventory getInventory() {
      return this.inv;
   }

   private List<OfflinePlayer> getPlayersWithStats() {
      List<OfflinePlayer> players = new ArrayList<>();

      for (UUID playerId : StatsManager.getAllPlayerIds()) {
         OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
         if (offlinePlayer != null && offlinePlayer.getName() != null) {
            players.add(offlinePlayer);
         }
      }

      return players;
   }

   private void setNavigationItems(int totalPages) {
      if (this.currentPage > 0) {
         this.inv.setItem(45, this.createNavItem(Material.ARROW, "Previous Page", NamedTextColor.GREEN));
      } else {
         this.inv.setItem(45, this.createNavItem(Material.BARRIER, "No Previous Page", NamedTextColor.RED));
      }

      this.inv.setItem(47, this.createNavItem(Material.ARROW, "Back", NamedTextColor.RED));
      this.inv.setItem(49, this.createNavItem(Material.PAPER, "Page " + (this.currentPage + 1) + " / " + totalPages, NamedTextColor.YELLOW));
      if (this.currentPage < totalPages - 1) {
         this.inv.setItem(53, this.createNavItem(Material.ARROW, "Next Page", NamedTextColor.GREEN));
      } else {
         this.inv.setItem(53, this.createNavItem(Material.BARRIER, "No Next Page", NamedTextColor.RED));
      }
   }

   private ItemStack createNavItem(Material material, String name, NamedTextColor color) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name).color(color));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createPlayerHead(OfflinePlayer player) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
      SkullMeta meta = (SkullMeta)head.getItemMeta();
      meta.setOwningPlayer(player);
      meta.displayName(Component.text(player.getName() != null ? player.getName() : "Unknown").color(NamedTextColor.GOLD));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.text("Click to view mining stats").color(NamedTextColor.GRAY));
      boolean isOnline = player.isOnline();
      lore.add(Component.text("Status: " + (isOnline ? "Online" : "Offline")).color(isOnline ? NamedTextColor.GREEN : NamedTextColor.RED));
      meta.lore(lore);
      head.setItemMeta(meta);
      return head;
   }
}
