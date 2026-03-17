package net.denfry.owml.gui.subgui;

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

import java.util.List;

public class ConfigSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public ConfigSettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 27, "⚙ Plugin Configuration");
    }

    @Override
    public void open(Player player) {
        refresh(player);
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        inventory.clear();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        inventory.setItem(11, ItemBuilder.material(Material.DIAMOND_PICKAXE).name("§bDecoy Settings").build());
        inventory.setItem(13, ItemBuilder.material(Material.CLOCK).name("§eAuto-Save Settings").build());
        inventory.setItem(15, ItemBuilder.material(Material.SHIELD).name("§aStaff Settings").build());
        inventory.setItem(22, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        switch (slot) {
            case 11 -> GUINavigationStack.push(player, new DecoySettingsGUI(plugin));
            case 13 -> GUINavigationStack.push(player, new AutoSaveSettingsGUI(plugin));
            case 15 -> GUINavigationStack.push(player, new StaffSettingsGUI(plugin));
            case 22 -> GUINavigationStack.pop(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
