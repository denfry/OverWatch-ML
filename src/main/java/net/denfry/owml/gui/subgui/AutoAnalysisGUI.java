package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.ml.MLConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AutoAnalysisGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final MLConfig mlConfig;
    private final Inventory inventory;

    public AutoAnalysisGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.mlConfig = plugin.getMLManager().getMLConfig();
        this.inventory = Bukkit.createInventory(this, 27, "⚙ Auto ML Analysis Settings");
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

        boolean enabled = mlConfig.isAutoAnalysisEnabled();
        inventory.setItem(10, ItemBuilder.material(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(enabled ? "§aAuto Analysis: ENABLED" : "§cAuto Analysis: DISABLED")
                .lore(List.of("§7Click to toggle"))
                .build());

        int threshold = mlConfig.getSuspiciousThreshold();
        inventory.setItem(12, ItemBuilder.material(Material.HOPPER)
                .name("§bThreshold: §f" + threshold)
                .lore(List.of("§7Points required to trigger analysis"))
                .build());

        inventory.setItem(11, ItemBuilder.material(Material.RED_DYE).name("§cDecrease Threshold").build());
        inventory.setItem(13, ItemBuilder.material(Material.LIME_DYE).name("§aIncrease Threshold").build());

        inventory.setItem(22, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        switch (slot) {
            case 10:
                plugin.getMLManager().setAutoAnalysisEnabled(!mlConfig.isAutoAnalysisEnabled());
                refresh(player);
                break;
            case 11:
                int current = mlConfig.getSuspiciousThreshold();
                if (current > 1) {
                    mlConfig.setSuspiciousThreshold(current - 1);
                    refresh(player);
                }
                break;
            case 13:
                mlConfig.setSuspiciousThreshold(mlConfig.getSuspiciousThreshold() + 1);
                refresh(player);
                break;
            case 22:
                GUINavigationStack.pop(player);
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
