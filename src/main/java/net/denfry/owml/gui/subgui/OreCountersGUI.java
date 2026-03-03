package net.denfry.owml.gui.subgui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.alerts.StaffAlertManager.OreCounter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * GUI for displaying detailed ore counters for staff
 */
public class OreCountersGUI {
    public static final String PERMISSION = "owml.gui_ore_counters";
    private final Inventory inv;
    private final Player player;
    private final StaffAlertManager alertManager;

    public OreCountersGUI(Player player, StaffAlertManager alertManager) {
        this.player = player;
        this.alertManager = alertManager;

        inv = Bukkit.createInventory(null, 54, Component.text("⛏ Ore Counters").color(NamedTextColor.GOLD));
        initializeItems();
    }

    private void initializeItems() {
        Map<Material, OreCounter> oreCounters = alertManager.getAllOreCounters(player.getUniqueId());

        // Define ore types to display
        List<Material> ores = Arrays.asList(
            Material.DIAMOND_ORE, Material.GOLD_ORE, Material.IRON_ORE,
            Material.COAL_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.EMERALD_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
        );

        int slot = 0;
        for (Material ore : ores) {
            if (slot >= 45) break; // Leave space for navigation

            OreCounter counter = oreCounters.get(ore);
            int count = counter != null ? counter.getCount() : 0;

            ItemStack item = new ItemStack(ore, Math.min(Math.max(1, count), 64));
            ItemMeta meta = item.getItemMeta();

            String displayName = ore.name().replace("_ORE", "").replace("_", " ") + " Counter";
            meta.displayName(Component.text(displayName).color(NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Count: " + count).color(NamedTextColor.GREEN));
            lore.add(Component.text("Total mined by suspicious players").color(NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }

        // Add summary item
        int totalOres = oreCounters.values().stream().mapToInt(OreCounter::getCount).sum();
        ItemStack summary = new ItemStack(Material.BOOK);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.displayName(Component.text("Summary").color(NamedTextColor.AQUA));

        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.text("Total ores mined: " + totalOres).color(NamedTextColor.GREEN));
        summaryLore.add(Component.text("Different ore types: " + oreCounters.size()).color(NamedTextColor.GREEN));
        summaryLore.add(Component.text("Player: " + player.getName()).color(NamedTextColor.GRAY));

        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inv.setItem(49, summary);

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
            case 53: // Back button
                // Import and use AlertPanel - this would need to be handled by the calling class
                return true;
            default:
                return false;
        }
    }
}
