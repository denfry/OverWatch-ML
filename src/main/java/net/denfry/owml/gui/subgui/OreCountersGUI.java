package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OreCountersGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final StaffAlertManager alertManager;
    private final Inventory inventory;

    public OreCountersGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.alertManager = plugin.getStaffAlertManager();
        this.inventory = Bukkit.createInventory(this, 54, "⛏ Ore Counters");
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
        Map<Material, StaffAlertManager.OreCounter> oreCounters = alertManager.getAllOreCounters(player.getUniqueId());

        List<Material> ores = Arrays.asList(
            Material.DIAMOND_ORE, Material.GOLD_ORE, Material.IRON_ORE,
            Material.COAL_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.EMERALD_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
        );

        int slot = 0;
        for (Material ore : ores) {
            StaffAlertManager.OreCounter counter = oreCounters.get(ore);
            int count = counter != null ? counter.getCount() : 0;

            inventory.setItem(slot++, ItemBuilder.material(ore)
                    .name("§e" + ore.name().replace("_", " "))
                    .lore(List.of("§7Count: §a" + count))
                    .build());
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 49) {
            GUINavigationStack.pop((Player) event.getWhoClicked());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
