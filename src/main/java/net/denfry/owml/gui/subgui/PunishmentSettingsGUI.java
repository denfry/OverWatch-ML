package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class PunishmentSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public PunishmentSettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 54, "⚖ Punishment System Settings");
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
        for (int level = 1; level <= 6; level++) {
            int row = (level - 1) * 9;
            boolean enabled = configManager.isPunishmentEnabled(level);
            
            inventory.setItem(row, ItemBuilder.material(Material.PAPER).name("§eLevel " + level).build());
            inventory.setItem(row + 1, ItemBuilder.material(enabled ? Material.LIME_WOOL : Material.RED_WOOL)
                    .name(enabled ? "§aEnabled" : "§cDisabled")
                    .lore(List.of("§7Click to toggle level " + level))
                    .build());
            
            boolean adminAlert = configManager.isPunishmentOptionEnabled(level, "admin_alert");
            inventory.setItem(row + 2, ItemBuilder.material(Material.BELL)
                    .name("§bAdmin Alerts: " + (adminAlert ? "§aON" : "§cOFF"))
                    .build());

            inventory.setItem(row + 5, ItemBuilder.material(Material.WRITABLE_BOOK)
                    .name("§6Advanced Settings")
                    .lore(List.of("§7Click to configure level " + level))
                    .build());
        }
        inventory.setItem(53, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 53) {
            GUINavigationStack.pop(player);
            return;
        }

        int level = (slot / 9) + 1;
        int col = slot % 9;

        if (level >= 1 && level <= 6) {
            if (col == 1) {
                boolean current = configManager.isPunishmentEnabled(level);
                configManager.setPunishmentEnabled(level, !current);
                refresh(player);
            } else if (col == 2) {
                boolean current = configManager.isPunishmentOptionEnabled(level, "admin_alert");
                configManager.setPunishmentOptionEnabled(level, "admin_alert", !current);
                refresh(player);
            } else if (col == 5) {
                GUINavigationStack.push(player, new LevelPunishmentSettingsGUI(plugin, level));
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
