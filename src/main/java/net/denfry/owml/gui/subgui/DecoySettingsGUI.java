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

public class DecoySettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public DecoySettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 36, "§bDecoy Settings");
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
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        boolean enabled = configManager.isDecoyEnabled();
        inventory.setItem(10, ItemBuilder.material(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name("§bDecoy System: " + (enabled ? "§aENABLED" : "§cDISABLED"))
                .lore(List.of("§7Click to toggle"))
                .build());

        inventory.setItem(11, ItemBuilder.material(Material.PAPER).name("§eOre Threshold").lore(List.of("§7Current: §f" + configManager.getOreThreshold())).build());
        inventory.setItem(31, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 10) {
            configManager.setDecoyEnabled(!configManager.isDecoyEnabled());
            refresh(player);
        } else if (slot == 31) {
            GUINavigationStack.pop(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
