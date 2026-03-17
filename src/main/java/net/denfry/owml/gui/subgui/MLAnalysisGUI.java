package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.ml.MLDataManager;
import net.denfry.owml.ml.ModernMLManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class MLAnalysisGUI implements OverWatchGUI {
    public static final String PERMISSION = "owml.gui_ml";
    public static final int MAX_PLAYERS = 5;
    private final OverWatchML plugin;
    private final ModernMLManager mlManager;
    private final Inventory inventory;

    public MLAnalysisGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.mlManager = plugin.getMLManager();
        this.inventory = Bukkit.createInventory(this, 54, "🤖 ML Analysis & Management");
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
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        boolean enabled = mlManager.isEnabled();
        inventory.setItem(4, ItemBuilder.material(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(enabled ? "§aML System: ENABLED" : "§cML System: DISABLED")
                .lore(List.of(mlManager.isTrained() ? "§aModel: TRAINED" : "§cModel: NOT TRAINED"))
                .build());

        inventory.setItem(13, ItemBuilder.material(enabled ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK)
                .name(enabled ? "§cDisable ML System" : "§aEnable ML System")
                .build());

        inventory.setItem(8, ItemBuilder.material(Material.REPEATER).name("§bAuto Analysis Settings").build());
        inventory.setItem(26, ItemBuilder.material(Material.SPYGLASS).name("§bSelect Player to Analyze").build());
        inventory.setItem(35, ItemBuilder.material(Material.EMERALD).name("§aSelect Normal Player for Training").build());
        inventory.setItem(44, ItemBuilder.material(Material.REDSTONE).name("§cSelect Cheater for Training").build());
        inventory.setItem(53, ItemBuilder.material(Material.PAPER).name("§bML Analysis Reports").build());
        
        inventory.setItem(49, ItemBuilder.material(Material.ARROW).name("§7Back").build());

        addActiveMonitoring();
    }

    private void addActiveMonitoring() {
        List<UUID> monitored = new ArrayList<>(mlManager.getPlayersUnderAnalysis());
        for (int i = 0; i < Math.min(monitored.size(), 7); i++) {
            UUID id = monitored.get(i);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                inventory.setItem(19 + i, ItemBuilder.material(Material.PLAYER_HEAD)
                        .skull(p.getName())
                        .name("§b" + p.getName())
                        .lore(List.of("§eAnalyzing...", "§7Score: §f" + Math.round(mlManager.getDetectionScore(id) * 100) + "%"))
                        .build());
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 13:
                boolean newState = !mlManager.isEnabled();
                actionName = (newState ? "Enable" : "Disable") + " ML System";
                mlManager.setEnabled(newState);
                refresh(player);
                break;
            case 8:
                actionName = "Open Auto Analysis Settings";
                GUINavigationStack.push(player, new AutoAnalysisGUI(plugin));
                break;
            case 26:
                actionName = "Open Player Selector (Analyze)";
                GUINavigationStack.push(player, new PlayerSelectorGUI(plugin, PlayerSelectorGUI.SelectionType.ANALYZE));
                break;
            case 35:
                actionName = "Open Player Selector (Train Normal)";
                GUINavigationStack.push(player, new PlayerSelectorGUI(plugin, PlayerSelectorGUI.SelectionType.TRAIN_NORMAL));
                break;
            case 44:
                actionName = "Open Player Selector (Train Cheater)";
                GUINavigationStack.push(player, new PlayerSelectorGUI(plugin, PlayerSelectorGUI.SelectionType.TRAIN_CHEATER));
                break;
            case 53:
                actionName = "Open ML Reports";
                GUINavigationStack.push(player, new MLReportsGUI(plugin, 0));
                break;
            case 49:
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
