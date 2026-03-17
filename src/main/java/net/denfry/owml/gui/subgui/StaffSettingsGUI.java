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

public class StaffSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public StaffSettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 27, "§aStaff Settings");
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

        boolean oreAlerts = configManager.isStaffOreAlerts();
        inventory.setItem(10, ItemBuilder.material(oreAlerts ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name("§eStaff Ore Alerts: " + (oreAlerts ? "§aON" : "§cOFF"))
                .build());

        boolean staffAlerts = configManager.isStaffAlertEnabled();
        inventory.setItem(12, ItemBuilder.material(staffAlerts ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name("§bStaff Security Alerts: " + (staffAlerts ? "§aON" : "§cOFF"))
                .build());

        inventory.setItem(22, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 10) {
            configManager.setStaffOreAlerts(!configManager.isStaffOreAlerts());
            refresh(player);
        } else if (slot == 12) {
            configManager.setStaffAlertEnabled(!configManager.isStaffAlertEnabled());
            refresh(player);
        } else if (slot == 22) {
            GUINavigationStack.pop(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
