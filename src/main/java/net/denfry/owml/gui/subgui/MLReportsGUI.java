package net.denfry.owml.gui.subgui;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.ml.MLDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class MLReportsGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final Inventory inventory;
   private final Map<Integer, String> reportPaths = new ConcurrentHashMap<>();
   private int page = 0;
   private final String filter;

   public MLReportsGUI(OverWatchML plugin, int page, String filter) {
      this.plugin = plugin;
      this.page = page;
      this.filter = filter;
      this.inventory = Bukkit.createInventory(this, 54, "ML Reports" + (filter != null ? ": " + filter : ""));
   }

   public MLReportsGUI(OverWatchML plugin, int page) {
      this(plugin, page, null);
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
      this.reportPaths.clear();
      List<String> paths = MLDataManager.getPlayerReports(this.filter);
      int start = this.page * 45;

      for (int i = 0; i < Math.min(45, paths.size() - start); i++) {
         String path = paths.get(start + i);
         this.inventory
            .setItem(i, ItemBuilder.material(Material.PAPER).name("§eReport: §f" + new File(path).getName()).lore(List.of("§7Click to view details")).build());
         this.reportPaths.put(i, path);
      }

      this.inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot < 45) {
         String path = this.reportPaths.get(slot);
         if (path != null) {
            player.sendMessage("§aDisplaying report: §f" + path);
         }
      } else if (slot == 49) {
         GUINavigationStack.pop(player);
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
