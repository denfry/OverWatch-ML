package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.AppealManager;
import net.denfry.owml.managers.AppealManager.Appeal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppealGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final AppealManager appealManager;
    private final Inventory inventory;
    private final Map<Integer, Appeal> appealSlots = new HashMap<>();
    private int page = 0;

    public AppealGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.appealManager = plugin.getAppealManager();
        this.inventory = Bukkit.createInventory(this, 54, "⚖ Player Appeals");
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
        appealSlots.clear();
        
        List<Appeal> pending = appealManager.getPendingAppeals();
        pending.sort(Comparator.comparing(Appeal::getTimestamp).reversed());

        int start = page * 45;
        for (int i = 0; i < Math.min(45, pending.size() - start); i++) {
            Appeal appeal = pending.get(start + i);
            inventory.setItem(i, ItemBuilder.material(Material.PLAYER_HEAD)
                    .skull(appeal.getPlayerName())
                    .name("§6Appeal: §e" + appeal.getPlayerName())
                    .lore(List.of(
                            "§7Level: §c" + appeal.getPunishmentLevel(),
                            "§7Date: §f" + appeal.getFormattedTimestamp(),
                            "",
                            "§eClick to review"
                    ))
                    .build());
            appealSlots.put(i, appeal);
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            Appeal appeal = appealSlots.get(slot);
            if (appeal != null) {
                GUINavigationStack.push(player, new AppealDetailsGUI(plugin, appealManager, appeal));
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
