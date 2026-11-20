package net.denfry.owml.gui.subgui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.AppealManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class AppealGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final AppealManager appealManager;
   private final Inventory inventory;
   private final Map<Integer, AppealManager.Appeal> appealSlots = new ConcurrentHashMap<>();
   private int page = 0;

   public AppealGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.appealManager = plugin.getAppealManager();
      this.inventory = Bukkit.createInventory(this, 54, "⚖ Player Appeals");
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
      this.appealSlots.clear();
      List<AppealManager.Appeal> pending = this.appealManager.getPendingAppeals();
      pending.sort(Comparator.comparing(AppealManager.Appeal::getTimestamp).reversed());
      int start = this.page * 45;

      for (int i = 0; i < Math.min(45, pending.size() - start); i++) {
         AppealManager.Appeal appeal = pending.get(start + i);
         this.inventory
            .setItem(
               i,
               ItemBuilder.material(Material.PLAYER_HEAD)
                  .skull(appeal.getPlayerName())
                  .name("§6Appeal: §e" + appeal.getPlayerName())
                  .lore(List.of("§7Level: §c" + appeal.getPunishmentLevel(), "§7Date: §f" + appeal.getFormattedTimestamp(), "", "§eClick to review"))
                  .build()
            );
         this.appealSlots.put(i, appeal);
      }

      this.inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot < 45) {
         AppealManager.Appeal appeal = this.appealSlots.get(slot);
         if (appeal != null) {
            GUINavigationStack.push(player, new AppealDetailsGUI(this.plugin, this.appealManager, appeal));
         }
      } else if (slot == 49) {
         GUINavigationStack.pop(player);
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
