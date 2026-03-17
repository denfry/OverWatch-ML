package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
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
import net.denfry.owml.managers.StatsManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerMiningStatsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final UUID targetId;
    private final String targetName;
    private final Inventory inventory;

    public PlayerMiningStatsGUI(OverWatchML plugin, UUID targetId, String targetName) {
        this.plugin = plugin;
        this.targetId = targetId;
        this.targetName = targetName;
        this.inventory = Bukkit.createInventory(this, 54, "Mining Stats: " + targetName);
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
        Map<Material, Integer> stats = StatsManager.getOreStats(targetId);

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : stats.entrySet()) {
            if (slot >= 45) break;
            inventory.setItem(slot++, ItemBuilder.material(entry.getKey())
                    .name("§e" + entry.getKey().name())
                    .lore(List.of("§7Mined: §f" + entry.getValue()))
                    .build());
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player staff = (Player) event.getWhoClicked();
        if (event.getSlot() == 49) {
            GUINavigationStack.pop(staff);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
