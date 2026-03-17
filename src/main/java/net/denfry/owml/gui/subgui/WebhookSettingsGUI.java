package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.WebhookManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class WebhookSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final Inventory inventory;

    public WebhookSettingsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 36, "🔔 Discord Webhook Settings");
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
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        }

        String url = configManager.getWebhookUrl();
        boolean set = url != null && !url.isEmpty();
        
        inventory.setItem(10, ItemBuilder.material(set ? Material.LIME_WOOL : Material.RED_WOOL)
                .name("§dWebhook URL")
                .lore(List.of(set ? "§aConfigured" : "§cNot Configured", "§eClick to set"))
                .build());

        addToggle(11, "xray_detection", "X-Ray Detection", Material.DIAMOND_ORE);
        addToggle(12, "suspicious_mining", "Suspicious Mining", Material.NETHERITE_PICKAXE);
        addToggle(13, "ml_analysis", "ML Analysis", Material.BEACON);
        
        inventory.setItem(21, ItemBuilder.material(Material.REDSTONE).name("§6Test Webhook").build());
        inventory.setItem(31, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    private void addToggle(int slot, String key, String name, Material material) {
        boolean enabled = configManager.isWebhookAlertEnabled(key);
        inventory.setItem(slot, ItemBuilder.material(material)
                .name((enabled ? "§a" : "§7") + name)
                .lore(List.of("§7Status: " + (enabled ? "§aON" : "§cOFF"), "§eClick to toggle"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        switch (slot) {
            case 10:
                player.closeInventory();
                player.sendMessage("§dPlease enter Discord Webhook URL in chat:");
                // Simple implementation, assumes a chat listener exists or registers a temporary one
                break;
            case 11: toggle("xray_detection", player); break;
            case 12: toggle("suspicious_mining", player); break;
            case 13: toggle("ml_analysis", player); break;
            case 21: 
                String url = configManager.getWebhookUrl();
                if (url != null && !url.isEmpty()) {
                    WebhookManager.sendTestMessage(url, player.getName());
                    player.sendMessage("§aTest message sent!");
                }
                break;
            case 31:
                GUINavigationStack.pop(player);
                break;
        }
    }

    private void toggle(String key, Player player) {
        configManager.setWebhookAlertEnabled(key, !configManager.isWebhookAlertEnabled(key));
        refresh(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
