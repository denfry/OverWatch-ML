package net.denfry.owml.gui.modern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class SettingsGUI implements OverWatchGUI {
    private final Inventory inventory;

    public SettingsGUI() {
        this.inventory = Bukkit.createInventory(this, 54, "§8Настройки OverWatch");
        setupItems();
    }

    private void setupItems() {
        // ML Thresholds
        inventory.setItem(10, ItemBuilder.material(Material.REDSTONE_TORCH).name("§eПорог предупреждения: 75").lore(List.of("§7ЛКМ: -5 | ПКМ: +5", "§7Шифт: Сброс")).build());
        inventory.setItem(11, ItemBuilder.material(Material.REDSTONE_BLOCK).name("§cПорог автонаказания: 95").lore(List.of("§7ЛКМ: -5 | ПКМ: +5", "§7Шифт: Сброс")).build());
        inventory.setItem(12, ItemBuilder.material(Material.BOOK).name("§bМин. данных: 500").lore(List.of("§7ЛКМ: -100 | ПКМ: +100")).build());
        
        // Automation
        inventory.setItem(19, ItemBuilder.material(Material.REPEATER).name("§aАвтонаказание: Вкл").lore(List.of("§7ЛКМ/ПКМ: Переключить")).build());
        inventory.setItem(20, ItemBuilder.material(Material.IRON_AXE).name("§eТип автонаказания: Tempban").lore(List.of("§7ЛКМ/ПКМ: Изменить")).build());
        inventory.setItem(21, ItemBuilder.material(Material.BELL).name("§9Discord Alerts: Вкл").lore(List.of("§7ЛКМ/ПКМ: Переключить")).build());

        // Performance
        inventory.setItem(28, ItemBuilder.material(Material.CLOCK).name("§fИнтервал анализа: 100 тиков").lore(List.of("§7ЛКМ: -20 | ПКМ: +20")).build());
        inventory.setItem(29, ItemBuilder.material(Material.MINECART).name("§fМакс. очередь: 10").lore(List.of("§7ЛКМ: -1 | ПКМ: +1")).build());

        inventory.setItem(45, ItemBuilder.material(Material.RED_WOOL).name("§cОтмена").lore(List.of("§7Сбросить изменения")).build());
        inventory.setItem(49, ItemBuilder.material(Material.LIME_WOOL).name("§aСохранить").lore(List.of("§7Применить настройки")).build());
        inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§cНазад").build());
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        setupItems();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 53 || slot == 45) {
            GUINavigationStack.pop(player);
        } else if (slot == 49) {
            GUIEffects.playSuccess(player);
            player.sendMessage("§aНастройки успешно сохранены!");
            GUINavigationStack.pop(player);
        } else {
            GUIEffects.playSuccess(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
