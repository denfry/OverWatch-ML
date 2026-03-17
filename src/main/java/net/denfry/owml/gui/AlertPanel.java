package net.denfry.owml.gui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.gui.subgui.AlertHistoryGUI;
import net.denfry.owml.gui.subgui.OreCountersGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Alert Management Panel
 */
public class AlertPanel implements OverWatchGUI {

    private final OverWatchML plugin;
    private final StaffAlertManager alertManager;
    private final Inventory inventory;

    public AlertPanel(OverWatchML plugin) {
        this.plugin = plugin;
        this.alertManager = plugin.getStaffAlertManager();
        this.inventory = Bukkit.createInventory(this, 27, "🚨 Alert Management Panel");
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
        
        // Background
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Toggle
        boolean alertsDisabled = alertManager.areAlertsDisabled(player);
        inventory.setItem(10, ItemBuilder.material(alertsDisabled ? Material.BARRIER : Material.BELL)
                .name(alertsDisabled ? "§c❌ Alerts Disabled" : "§a✅ Alerts Enabled")
                .lore(List.of("§7Click to toggle", "", "§f• Ore detections", "§f• Suspicious activities"))
                .build());

        // Status
        int onlineStaff = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("owml.staff") && !alertManager.areAlertsDisabled(p))
                .count();
        inventory.setItem(12, ItemBuilder.material(Material.PLAYER_HEAD)
                .name("§b👥 Staff Status")
                .lore(List.of("§7Online with alerts: §a" + onlineStaff))
                .build());

        // Ore Counters
        inventory.setItem(14, ItemBuilder.material(Material.DIAMOND)
                .name("§e📊 Ore Counters")
                .lore(List.of("§7View detailed mining activity"))
                .build());

        // History
        inventory.setItem(16, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("§6📜 Alert History")
                .lore(List.of("§7View recent alerts"))
                .build());

        // Back
        inventory.setItem(22, ItemBuilder.material(Material.ARROW).name("§7⬅ Back").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 10:
                boolean newState = alertManager.toggleOreAlert(player);
                actionName = (newState ? "Disable" : "Enable") + " Ore Alerts";
                refresh(player);
                break;
            case 14:
                actionName = "Open Ore Counters";
                GUINavigationStack.push(player, new OreCountersGUI(plugin));
                break;
            case 16:
                actionName = "Open Alert History";
                GUINavigationStack.push(player, new AlertHistoryGUI(plugin));
                break;
            case 22:
                actionName = "Go Back";
                GUINavigationStack.pop(player);
                break;
        }
        
        if (!actionName.equals("Unknown")) {
            plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
