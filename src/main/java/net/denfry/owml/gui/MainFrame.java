package net.denfry.owml.gui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.gui.subgui.AppealGUI;
import net.denfry.owml.gui.subgui.ConfigSettingsGUI;
import net.denfry.owml.gui.subgui.OreConfigGUI;
import net.denfry.owml.gui.subgui.PlayerStatsMainGUI;
import net.denfry.owml.gui.subgui.PunishmentSettingsGUI;
import net.denfry.owml.gui.subgui.WebhookSettingsGUI;
import net.denfry.owml.gui.modern.SuspiciousPlayersGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class MainFrame implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public MainFrame(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.inventory = Bukkit.createInventory(this, 54, "OverWatchML Control Panel");
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
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        inventory.setItem(10, ItemBuilder.material(Material.PLAYER_HEAD).name("§bPlayer Statistics").build());
        inventory.setItem(12, ItemBuilder.material(Material.DETECTOR_RAIL).name("§cSuspicious Players").build());
        inventory.setItem(14, ItemBuilder.material(Material.ANVIL).name("§4Punishment System").build());
        inventory.setItem(16, ItemBuilder.material(Material.COMMAND_BLOCK).name("§6Plugin Configuration").build());
        inventory.setItem(28, ItemBuilder.material(Material.DIAMOND_ORE).name("§eOre Management").build());
        inventory.setItem(30, ItemBuilder.material(Material.BELL).name("§9Discord Webhooks").build());
        inventory.setItem(32, ItemBuilder.material(Material.WRITABLE_BOOK).name("§aAppeal System").build());
        inventory.setItem(34, ItemBuilder.material(Material.NETHER_STAR).name("§dStaff Menu").build());

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cClose").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 10:
                actionName = "Open Player Stats";
                GUINavigationStack.push(player, new PlayerStatsMainGUI(plugin));
                break;
            case 12:
                actionName = "Open Suspicious Players";
                GUINavigationStack.push(player, new SuspiciousPlayersGUI(plugin));
                break;
            case 14:
                actionName = "Open Punishment Settings";
                GUINavigationStack.push(player, new PunishmentSettingsGUI(plugin));
                break;
            case 16:
                actionName = "Open Config Settings";
                GUINavigationStack.push(player, new ConfigSettingsGUI(plugin));
                break;
            case 28:
                actionName = "Open Ore Config";
                GUINavigationStack.push(player, new OreConfigGUI(plugin));
                break;
            case 30:
                actionName = "Open Webhook Settings";
                GUINavigationStack.push(player, new WebhookSettingsGUI(plugin));
                break;
            case 32:
                actionName = "Open Appeal System";
                GUINavigationStack.push(player, new AppealGUI(plugin));
                break;
            case 34:
                actionName = "Open Staff Menu";
                GUINavigationStack.push(player, new StaffMenuGUI(plugin));
                break;
            case 49:
                actionName = "Close MainFrame";
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
