package net.denfry.owml.gui.subgui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.denfry.owml.alerts.StaffAlertManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * GUI for displaying alert history
 */
public class AlertHistoryGUI {
    public static final String PERMISSION = "owml.gui_alert_history";
    private final Inventory inv;
    private final StaffAlertManager alertManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public AlertHistoryGUI(StaffAlertManager alertManager) {
        this.alertManager = alertManager;

        inv = Bukkit.createInventory(null, 54, Component.text("📋 Alert History").color(NamedTextColor.RED));
        initializeItems();
    }

    private void initializeItems() {
        List<StaffAlertManager.AlertRecord> history = alertManager.getAlertHistory();

        // Display alerts in reverse chronological order (newest first)
        Collections.reverse(history);

        int slot = 0;
        for (StaffAlertManager.AlertRecord record : history) {
            if (slot >= 45) break; // Leave space for navigation

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            String time = dateFormat.format(new Date(record.getTimestamp()));
            meta.displayName(Component.text("Alert at " + time).color(NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Player: " + record.getPlayerName()).color(NamedTextColor.GREEN));
            lore.add(Component.text("Message: " + record.getMessage()).color(NamedTextColor.WHITE));

            Location loc = record.getLocation();
            String locationText = loc.getWorld().getName() + " " + loc.getBlockX() + "," +
                                loc.getBlockY() + "," + loc.getBlockZ();
            lore.add(Component.text("Location: " + locationText).color(NamedTextColor.GRAY));

            // Click to teleport
            lore.add(Component.text(""));
            lore.add(Component.text("Click to teleport").color(NamedTextColor.AQUA));

            meta.lore(lore);

            // Add click event for teleportation
            Component displayName = Component.text("Alert at " + time)
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/tp " + loc.getBlockX() + " " +
                    loc.getBlockY() + " " + loc.getBlockZ()));

            meta.displayName(displayName);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }

        // Add summary item
        ItemStack summary = new ItemStack(Material.BOOK);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.displayName(Component.text("Summary").color(NamedTextColor.AQUA));

        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.text("Total alerts: " + history.size()).color(NamedTextColor.GREEN));
        summaryLore.add(Component.text("Showing last " + Math.min(history.size(), 45) + " alerts").color(NamedTextColor.GREEN));
        summaryLore.add(Component.text("Maximum stored: 100 alerts").color(NamedTextColor.GRAY));

        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inv.setItem(49, summary);

        // Add clear history button
        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clear.getItemMeta();
        clearMeta.displayName(Component.text("🗑 Clear History").color(NamedTextColor.RED));

        List<Component> clearLore = new ArrayList<>();
        clearLore.add(Component.text("Click to clear all alert history").color(NamedTextColor.GRAY));
        clearLore.add(Component.text("This action cannot be undone!").color(NamedTextColor.RED));

        clearMeta.lore(clearLore);
        clear.setItemMeta(clearMeta);
        inv.setItem(50, clear);

        // Add back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("← Back to Alerts").color(NamedTextColor.RED));
        back.setItemMeta(backMeta);
        inv.setItem(53, back);
    }

    public void openInventory(Player player) {
        player.openInventory(inv);
    }

    public static boolean handleClick(Player player, int slot, StaffAlertManager alertManager) {
        switch (slot) {
            case 50: // Clear history button
                alertManager.clearAlertHistory();
                player.sendMessage(Component.text("Alert history cleared!").color(NamedTextColor.GREEN));
                new AlertHistoryGUI(alertManager).openInventory(player); // Refresh GUI
                return true;
            case 53: // Back button
                // Import and use AlertPanel - this would need to be handled by the calling class
                return true;
            default:
                return false;
        }
    }
}
