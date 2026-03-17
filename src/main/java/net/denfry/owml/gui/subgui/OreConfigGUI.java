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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OreConfigGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;
    private final Material[] availableOres;

    public OreConfigGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.availableOres = initializeAvailableOres();
        this.inventory = Bukkit.createInventory(this, 27, "⛏ Ore Management");
    }

    private Material[] initializeAvailableOres() {
        List<Material> ores = new ArrayList<>();
        String[] oreNames = {"DIAMOND_ORE", "GOLD_ORE", "IRON_ORE", "COAL_ORE", "LAPIS_ORE", "REDSTONE_ORE", "EMERALD_ORE", 
                             "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_GOLD_ORE", "ANCIENT_DEBRIS"};
        for (String name : oreNames) {
            try {
                Material m = Material.valueOf(name);
                if (m.isBlock()) ores.add(m);
            } catch (Exception ignored) {}
        }
        return ores.toArray(new Material[0]);
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
        Set<Material> naturalOres = configManager.getNaturalOres();
        int slot = 0;
        for (Material ore : availableOres) {
            boolean selected = naturalOres.contains(ore);
            inventory.setItem(slot++, ItemBuilder.material(ore)
                    .name((selected ? "§a" : "§c") + ore.name())
                    .lore(List.of(selected ? "§aSelected" : "§7Not Selected", "§eClick to toggle"))
                    .build());
        }
        inventory.setItem(26, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 26) {
            GUINavigationStack.pop(player);
            return;
        }

        if (slot >= 0 && slot < availableOres.length) {
            Material ore = availableOres[slot];
            if (configManager.getNaturalOres().contains(ore)) {
                configManager.getNaturalOres().remove(ore);
            } else {
                configManager.getNaturalOres().add(ore);
            }
            refresh(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
