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

public class AutoSaveSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public AutoSaveSettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 45, "§eAuto-Save Settings");
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
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        addToggle(10, "stats.autoSave.enabled", "Stats Auto-Save", Material.EXPERIENCE_BOTTLE);
        addToggle(13, "suspicious.autoSave.enabled", "Suspicious Auto-Save", Material.ENDER_EYE);
        addToggle(16, "punishment.autoSave.enabled", "Punishment Auto-Save", Material.IRON_AXE);

        inventory.setItem(40, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    private void addToggle(int slot, String path, String name, Material material) {
        boolean enabled = plugin.getConfig().getBoolean(path, true);
        inventory.setItem(slot, ItemBuilder.material(enabled ? material : Material.GRAY_DYE)
                .name((enabled ? "§a" : "§7") + name)
                .lore(List.of("§7Status: " + (enabled ? "§aON" : "§cOFF"), "§eClick to toggle"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 40) {
            GUINavigationStack.pop(player);
            return;
        }

        switch (slot) {
            case 10: toggle("stats.autoSave.enabled", player); break;
            case 13: toggle("suspicious.autoSave.enabled", player); break;
            case 16: toggle("punishment.autoSave.enabled", player); break;
        }
    }

    private void toggle(String path, Player player) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
        plugin.saveConfig();
        refresh(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
