package net.denfry.owml.gui.subgui;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MLReportsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private final Map<Integer, String> reportPaths = new HashMap<>();
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
        refresh(player);
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        inventory.clear();
        reportPaths.clear();
        
        List<String> paths = MLDataManager.getPlayerReports(filter);
        int start = page * 45;
        for (int i = 0; i < Math.min(45, paths.size() - start); i++) {
            String path = paths.get(start + i);
            inventory.setItem(i, ItemBuilder.material(Material.PAPER)
                    .name("§eReport: §f" + new java.io.File(path).getName())
                    .lore(List.of("§7Click to view details"))
                    .build());
            reportPaths.put(i, path);
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            String path = reportPaths.get(slot);
            if (path != null) {
                player.sendMessage("§aDisplaying report: §f" + path);
                // Implementation for reading and showing report details would go here
            }
        } else if (slot == 49) {
            GUINavigationStack.pop(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
