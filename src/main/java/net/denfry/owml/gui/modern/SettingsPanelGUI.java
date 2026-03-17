package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class SettingsPanelGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager config;
    private final Inventory inventory;

    // Temporary values for editing before saving
    private int warningThreshold;
    private int autoPunishThreshold;
    private int minDataPoints;
    private boolean autoPunishEnabled;
    private String autoPunishType;
    private boolean discordAlerts;
    private int analysisInterval;
    private int maxQueueSize;

    public SettingsPanelGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 54, "OverWatch-ML Settings");
        loadCurrentSettings();
    }

    private void loadCurrentSettings() {
        // Load from real config manager
        this.warningThreshold = plugin.getConfig().getInt("ml.thresholds.warning", 60);
        this.autoPunishThreshold = plugin.getConfig().getInt("ml.thresholds.auto-punish", 90);
        this.minDataPoints = plugin.getConfig().getInt("ml.min-data-points", 50);
        this.autoPunishEnabled = plugin.getConfig().getBoolean("automation.auto-punish.enabled", false);
        this.autoPunishType = plugin.getConfig().getString("automation.auto-punish.type", "KICK");
        this.discordAlerts = plugin.getConfig().getBoolean("integrations.discord.enabled", false);
        this.analysisInterval = plugin.getConfig().getInt("performance.analysis-interval", 100);
        this.maxQueueSize = plugin.getConfig().getInt("performance.max-queue", 20);
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

        // --- ML Thresholds (10-16) ---
        inventory.setItem(1, ItemBuilder.material(Material.NETHER_STAR).name("§b§lML Thresholds").build());
        
        inventory.setItem(10, ItemBuilder.material(Material.YELLOW_DYE)
                .name("§eWarning Score: §f" + warningThreshold)
                .lore(List.of("§7Current: §e" + warningThreshold, "§7Left: -5 | Right: +5", "§7Shift: Reset to 60"))
                .build());

        inventory.setItem(11, ItemBuilder.material(Material.RED_DYE)
                .name("§cAuto-Punish Score: §f" + autoPunishThreshold)
                .lore(List.of("§7Current: §c" + autoPunishThreshold, "§7Left: -5 | Right: +5", "§7Shift: Reset to 90"))
                .build());

        inventory.setItem(12, ItemBuilder.material(Material.PAPER)
                .name("§fMin Data Points: §e" + minDataPoints)
                .lore(List.of("§7Current: §f" + minDataPoints, "§7Left: -10 | Right: +10"))
                .build());

        // --- Automation (19-25) ---
        inventory.setItem(18, ItemBuilder.material(Material.REPEATER).name("§a§lAutomation").build());

        Material punishMat = autoPunishEnabled ? Material.LIME_WOOL : Material.RED_WOOL;
        inventory.setItem(19, ItemBuilder.material(punishMat)
                .name("§fAuto-Punishment: " + (autoPunishEnabled ? "§aENABLED" : "§cDISABLED"))
                .lore(List.of("§7Click to toggle automatic actions"))
                .build());

        inventory.setItem(20, ItemBuilder.material(Material.ENCHANTED_BOOK)
                .name("§fAuto-Punish Type: §b" + autoPunishType)
                .lore(List.of("§7Cycle through types:", "§7KICK, TEMPBAN_1H, BAN"))
                .build());

        Material discordMat = discordAlerts ? Material.CYAN_WOOL : Material.GRAY_WOOL;
        inventory.setItem(21, ItemBuilder.material(discordMat)
                .name("§fDiscord Alerts: " + (discordAlerts ? "§aENABLED" : "§cDISABLED"))
                .build());

        // --- Performance (28-34) ---
        inventory.setItem(27, ItemBuilder.material(Material.COMPARATOR).name("§e§lPerformance").build());

        inventory.setItem(28, ItemBuilder.material(Material.CLOCK)
                .name("§fAnalysis Interval: §e" + analysisInterval + " ticks")
                .lore(List.of("§7Left: -20 | Right: +20"))
                .build());

        inventory.setItem(29, ItemBuilder.material(Material.HOPPER)
                .name("§fMax Queue Size: §e" + maxQueueSize)
                .lore(List.of("§7Left: -1 | Right: +1"))
                .build());

        // --- Bottom Row (45-53) ---
        inventory.setItem(45, ItemBuilder.material(Material.BARRIER).name("§cReset All to Defaults").build());
        inventory.setItem(49, ItemBuilder.material(Material.LIME_DYE).name("§aSave Changes").build());
        inventory.setItem(53, ItemBuilder.material(Material.RED_DYE).name("§cCancel").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 10: // Warning Threshold
                actionName = "Adjust Warning Threshold";
                if (event.isShiftClick()) warningThreshold = 60;
                else if (event.isLeftClick()) warningThreshold = Math.max(0, warningThreshold - 5);
                else if (event.isRightClick()) warningThreshold = Math.min(100, warningThreshold + 5);
                break;
            case 11: // Auto-Punish Threshold
                actionName = "Adjust Auto-Punish Threshold";
                if (event.isShiftClick()) autoPunishThreshold = 90;
                else if (event.isLeftClick()) autoPunishThreshold = Math.max(0, autoPunishThreshold - 5);
                else if (event.isRightClick()) autoPunishThreshold = Math.min(100, autoPunishThreshold + 5);
                break;
            case 12: // Min Data Points
                actionName = "Adjust Min Data Points";
                if (event.isLeftClick()) minDataPoints = Math.max(10, minDataPoints - 10);
                else if (event.isRightClick()) minDataPoints = Math.min(500, minDataPoints + 10);
                break;
            case 19: // Toggle Auto-Punish
                actionName = "Toggle Auto-Punish";
                autoPunishEnabled = !autoPunishEnabled;
                break;
            case 20: // Cycle Punish Type
                actionName = "Cycle Punish Type";
                List<String> types = List.of("KICK", "TEMPBAN_1H", "TEMPBAN_24H", "BAN");
                int idx = (types.indexOf(autoPunishType) + 1) % types.size();
                autoPunishType = types.get(idx);
                break;
            case 21: // Discord
                actionName = "Toggle Discord Alerts";
                discordAlerts = !discordAlerts;
                break;
            case 28: // Interval
                actionName = "Adjust Analysis Interval";
                if (event.isLeftClick()) analysisInterval = Math.max(20, analysisInterval - 20);
                else if (event.isRightClick()) analysisInterval = Math.min(1200, analysisInterval + 20);
                break;
            case 29: // Queue
                actionName = "Adjust Max Queue Size";
                if (event.isLeftClick()) maxQueueSize = Math.max(1, maxQueueSize - 1);
                else if (event.isRightClick()) maxQueueSize = Math.min(50, maxQueueSize + 1);
                break;
            case 45: // Reset
                actionName = "Reset Settings to Default";
                GUIEffects.showConfirmDialog(player, "Reset to defaults?", () -> {
                    loadCurrentSettings(); 
                    refresh(player);
                    plugin.getLogger().info("GUI: " + player.getName() + " reset settings to defaults.");
                });
                return;
            case 49: // Save
                actionName = "Save Settings";
                saveSettings(player);
                plugin.getLogger().info("GUI: " + player.getName() + " saved new configuration settings.");
                return;
            case 53: // Cancel
                actionName = "Cancel Changes";
                GUINavigationStack.pop(player);
                break;
        }
        
        if (!actionName.equals("Unknown")) {
            plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
        }
        
        refresh(player);
        GUIEffects.playOpen(player);
    }

    private void saveSettings(Player player) {
        // Validate settings before saving
        if (warningThreshold < 0 || warningThreshold > 100) {
            player.sendMessage("§cWarning threshold must be between 0 and 100!");
            GUIEffects.playError(player);
            return;
        }

        if (autoPunishThreshold < 0 || autoPunishThreshold > 100) {
            player.sendMessage("§cAuto-punish threshold must be between 0 and 100!");
            GUIEffects.playError(player);
            return;
        }

        if (minDataPoints < 10 || minDataPoints > 1000) {
            player.sendMessage("§cMin data points must be between 10 and 1000!");
            GUIEffects.playError(player);
            return;
        }

        if (analysisInterval < 20 || analysisInterval > 1200) {
            player.sendMessage("§cAnalysis interval must be between 20 and 1200 ticks!");
            GUIEffects.playError(player);
            return;
        }

        if (maxQueueSize < 1 || maxQueueSize > 50) {
            player.sendMessage("§cMax queue size must be between 1 and 50!");
            GUIEffects.playError(player);
            return;
        }

        // Validate auto-punish type
        List<String> validTypes = List.of("KICK", "TEMPBAN_1H", "TEMPBAN_24H", "BAN");
        if (!validTypes.contains(autoPunishType)) {
            player.sendMessage("§cInvalid auto-punish type! Resetting to KICK.");
            autoPunishType = "KICK";
        }

        plugin.getConfig().set("ml.thresholds.warning", warningThreshold);
        plugin.getConfig().set("ml.thresholds.auto-punish", autoPunishThreshold);
        plugin.getConfig().set("ml.min-data-points", minDataPoints);
        plugin.getConfig().set("automation.auto-punish.enabled", autoPunishEnabled);
        plugin.getConfig().set("automation.auto-punish.type", autoPunishType);
        plugin.getConfig().set("integrations.discord.enabled", discordAlerts);
        plugin.getConfig().set("performance.analysis-interval", analysisInterval);
        plugin.getConfig().set("performance.max-queue", maxQueueSize);
        plugin.saveConfig();

        player.sendMessage("§aSettings saved successfully!");
        GUIEffects.playSuccess(player);
        player.closeInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
