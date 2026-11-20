package net.denfry.owml.gui.subgui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class LevelPunishmentSettingsGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final ConfigManager configManager;
   private final int level;
   private final Inventory inventory;
   private final Map<Integer, String> slotToKey = new ConcurrentHashMap<>();

   public LevelPunishmentSettingsGUI(OverWatchML plugin, int level) {
      this.plugin = plugin;
      this.configManager = plugin.getConfigManager();
      this.level = level;
      this.inventory = Bukkit.createInventory(this, 54, "Settings for Level " + level);
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
      this.slotToKey.clear();
      switch (this.level) {
         case 1:
            this.addOption(10, "mining_fatigue", Material.IRON_PICKAXE, "Mining Fatigue");
            this.addOption(14, "fake_diamonds", Material.COAL, "Fake Diamonds");
            this.addOption(28, "heavy_pickaxe", Material.NETHERITE_PICKAXE, "Heavy Pickaxe");
            break;
         case 2:
            this.addOption(10, "fake_ore_veins", Material.DIAMOND_ORE, "Fake Ore Veins");
            this.addOption(12, "inventory_drop", Material.DROPPER, "Inventory Drop");
            this.addOption(14, "xray_vision_blur", Material.GLASS, "X-Ray Vision Blur");
            this.addOption(28, "paranoia_mode", Material.CREEPER_HEAD, "Paranoia Mode");
            break;
         case 3:
            this.addOption(10, "temporary_kick", Material.IRON_DOOR, "Temporary Kick");
            this.addOption(12, "mining_license_suspension", Material.FILLED_MAP, "License Suspension");
            this.addOption(28, "fools_gold", Material.GOLD_ORE, "Fool's Gold");
            break;
         case 4:
            this.addOption(10, "extended_ban", Material.REDSTONE_BLOCK, "Extended Ban");
            this.addOption(28, "cursed_pickaxe", Material.ENCHANTED_BOOK, "Cursed Pickaxe");
            break;
         case 5:
            this.addOption(10, "long_term_ban", Material.BEDROCK, "Long-Term Ban");
            this.addOption(28, "stone_vision", Material.STONE, "Stone Vision");
            break;
         case 6:
            this.addOption(10, "permanent_ban", Material.TNT, "Permanent Ban");
      }

      this.inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
   }

   private void addOption(int slot, String key, Material material, String name) {
      boolean enabled = this.configManager.isPunishmentOptionEnabled(this.level, key);
      this.inventory
         .setItem(
            slot,
            ItemBuilder.material(enabled ? material : Material.GRAY_DYE)
               .name((enabled ? "§a" : "§7") + name)
               .lore(List.of("§7Status: " + (enabled ? "§aON" : "§cOFF"), "§7Click to toggle"))
               .build()
         );
      this.slotToKey.put(slot, key);
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      if (slot == 49) {
         GUINavigationStack.pop(player);
      } else {
         String key = this.slotToKey.get(slot);
         if (key != null) {
            boolean current = this.configManager.isPunishmentOptionEnabled(this.level, key);
            this.configManager.setPunishmentOptionEnabled(this.level, key, !current);
            this.refresh(player);
         }
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
