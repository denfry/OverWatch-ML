package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.ml.ModernMLManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSelectorGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final SelectionType type;
    private final Inventory inventory;
    private int page = 0;

    public enum SelectionType { ANALYZE, TRAIN_NORMAL, TRAIN_CHEATER }

    public PlayerSelectorGUI(OverWatchML plugin, SelectionType type) {
        this.plugin = plugin;
        this.type = type;
        this.inventory = Bukkit.createInventory(this, 54, "Select Player: " + type.name());
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
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        
        int start = page * 45;
        for (int i = 0; i < Math.min(45, players.size() - start); i++) {
            Player target = players.get(start + i);
            inventory.setItem(i, ItemBuilder.material(Material.PLAYER_HEAD)
                    .skull(target.getName())
                    .name("§e" + target.getName())
                    .lore(List.of("§7Click to select"))
                    .build());
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            int index = (page * 45) + slot;
            if (index < players.size()) {
                Player target = players.get(index);
                handleSelection(player, target);
            }
        } else if (slot == 49) {
            GUINavigationStack.pop(player);
        }
    }

    private void handleSelection(Player staff, Player target) {
        ModernMLManager ml = plugin.getMLManager();
        switch (type) {
            case ANALYZE:
                ml.startAnalysis(target);
                staff.sendMessage("§aStarted analysis for " + target.getName());
                GUINavigationStack.pop(staff);
                break;
            case TRAIN_NORMAL:
                ml.startTraining(target, false);
                staff.sendMessage("§aStarted normal training for " + target.getName());
                GUINavigationStack.pop(staff);
                break;
            case TRAIN_CHEATER:
                ml.startTraining(target, true);
                staff.sendMessage("§cStarted cheater training for " + target.getName());
                GUINavigationStack.pop(staff);
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
