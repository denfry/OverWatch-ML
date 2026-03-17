package net.denfry.owml.gui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Main entry point for staff members to manage OverWatch-ML.
 * Links all modern GUI components together.
 */
public class StaffMenuGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;

    public StaffMenuGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "§bOverWatch-ML §8- §9Control Panel");
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
        
        // Borders
        for (int i = 0; i < 9; i++) inventory.setItem(i, ItemBuilder.material(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());
        for (int i = 45; i < 54; i++) inventory.setItem(i, ItemBuilder.material(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());

        // Main Sections
        inventory.setItem(11, ItemBuilder.material(Material.SPYGLASS)
                .name("§c§lSuspicious Activity")
                .lore(LoreFormatter.format(List.of(
                        "§7Monitor flagged players and behavioral anomalies.",
                        "§f• Real-time violation tracking",
                        "§f• Cross-category suspicion scores",
                        "",
                        "§e» Click to investigate"
                ))).enchanted().build());

        inventory.setItem(13, ItemBuilder.material(Material.NETHER_STAR)
                .name("§d§lMain Dashboard")
                .lore(LoreFormatter.format(List.of(
                        "§7High-level overview of server protection status.",
                        "§f• Current TPS and system health",
                        "§f• Active detection summary",
                        "",
                        "§e» Click to open hub"
                ))).enchanted().build());

        inventory.setItem(15, ItemBuilder.material(Material.COMPASS)
                .name("§b§lPlayer Analytics")
                .lore(LoreFormatter.format(List.of(
                        "§7Detailed behavioral profiling for all players.",
                        "§f• Mining trends and history",
                        "§f• Combat performance metrics",
                        "",
                        "§e» Click to view analytics"
                ))).enchanted().build());

        inventory.setItem(29, ItemBuilder.material(Material.COMMAND_BLOCK)
                .name("§6§lML System Tools")
                .lore(LoreFormatter.format(List.of(
                        "§7Manage Machine Learning models and training.",
                        "§f• Retrain models with new data",
                        "§f• View model accuracy metrics",
                        "",
                        "§e» Click to access ML dashboard"
                ))).enchanted().build());

        inventory.setItem(31, ItemBuilder.material(Material.BELL)
                .name("§e§lLive Alerts")
                .lore(LoreFormatter.format(List.of(
                        "§7Real-time notification history for staff.",
                        "§f• Quick teleport to violations",
                        "§f• Historical alert logging",
                        "",
                        "§e» Click to view alerts"
                ))).enchanted().build());

        inventory.setItem(33, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("§6§lPunishment Panel")
                .lore(LoreFormatter.format(List.of(
                        "§7Manage automated and manual enforcement.",
                        "§f• Configure punishment tiers",
                        "§f• Review player appeals",
                        "",
                        "§e» Click to manage"
                ))).enchanted().build());

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cClose Menu").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 11:
                actionName = "Open Suspicious Activity";
                GUINavigationStack.push(player, new SuspiciousPlayersGUI(plugin));
                break;
            case 13:
                actionName = "Open Main Hub";
                GUINavigationStack.push(player, new MainHubGUI(plugin));
                break;
            case 15:
                actionName = "Open Player Analytics";
                GUINavigationStack.push(player, new net.denfry.owml.gui.subgui.PlayerStatsMainGUI(plugin));
                break;
            case 29:
                actionName = "Open ML Tools";
                GUINavigationStack.push(player, new MLDashboardGUI(plugin));
                break;
            case 31:
                actionName = "Open Live Alerts";
                GUINavigationStack.push(player, new LiveAlertsGUI(plugin));
                break;
            case 33:
                actionName = "Open Punishment Panel";
                GUINavigationStack.push(player, new PunishmentPanelGUI(plugin));
                break;
            case 49:
                actionName = "Close Menu";
                player.closeInventory();
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
