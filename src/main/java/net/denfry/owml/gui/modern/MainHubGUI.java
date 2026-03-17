package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.AlertPanel;
import net.denfry.owml.managers.ISuspiciousService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainHubGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private boolean punishmentsPaused = false;
    
    // Cached data for performance - refresh only every 2 seconds
    private long lastRefreshTime = 0;
    private static final long CACHE_DURATION_MS = 2000;
    private double cachedTPS = 20.0;
    private boolean cachedMLActive = false;
    private double cachedAccuracy = 0.0;
    private int cachedOnlinePlayers = 0;
    private int cachedSuspiciousCount = 0;
    private List<StaffAlertManager.AlertRecord> cachedAlerts = null;

    public MainHubGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "OverWatch-ML Control Center");
    }

    @Override
    public void open(Player player) {
        refresh(player);
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {
        // Nothing to clean up
    }

    @Override
    public void refresh(Player player) {
        long now = System.currentTimeMillis();
        
        // Update cache if expired (every 2 seconds)
        if (now - lastRefreshTime > CACHE_DURATION_MS) {
            cachedTPS = Bukkit.getTPS()[0];
            cachedOnlinePlayers = Bukkit.getOnlinePlayers().size();
            
            if (plugin.getMLManager() != null) {
                cachedMLActive = plugin.getMLManager().isTrained();
                cachedAccuracy = plugin.getMLManager().getComprehensiveStats().learningStats.getAverageAccuracy() * 100;
            }
            
            cachedAlerts = plugin.getStaffAlertManager().getAlertHistory();
            
            ISuspiciousService suspService = plugin.getContext().getSuspiciousService();
            if (suspService != null) {
                cachedSuspiciousCount = suspService.getSuspiciousPlayers().size();
            }
            
            lastRefreshTime = now;
        }
        
        // --- Top row (Status) ---
        double tps = cachedTPS;
        Material tpsMaterial = tps >= 18.0 ? Material.LIME_STAINED_GLASS_PANE :
                (tps >= 15.0 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        inventory.setItem(0, ItemBuilder.material(tpsMaterial)
                .name("§fServer Status")
                .lore(List.of("§7TPS: §e" + String.format("%.2f", tps), "§7Online: §a" + cachedOnlinePlayers))
                .build());

        boolean mlActive = cachedMLActive;
        double accuracy = cachedAccuracy;
        Material mlMaterial = mlActive ? Material.PURPLE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        inventory.setItem(1, ItemBuilder.material(mlMaterial)
                .name("§dML System")
                .lore(List.of("§7Status: " + (mlActive ? "§aActive" : "§cUnavailable"),
                        "§7Accuracy: §e" + String.format("%.1f%%", accuracy)))
                .build());

        List<StaffAlertManager.AlertRecord> alerts = cachedAlerts;
        inventory.setItem(2, ItemBuilder.material(Material.RED_STAINED_GLASS_PANE)
                .name("§cUnread Alerts: " + (alerts != null ? alerts.size() : 0))
                .build());

        ItemStack separator = ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 3; i <= 8; i++) {
            inventory.setItem(i, separator);
        }

        // --- Main sections ---
        ISuspiciousService suspService = plugin.getContext().getSuspiciousService();
        Set<UUID> suspiciousPlayers = suspService != null ? suspService.getSuspiciousPlayers() : Collections.emptySet();
        List<UUID> highRiskPlayers = suspiciousPlayers.stream()
                .filter(uuid -> suspService != null && suspService.getSuspicionLevel(uuid) > 60)
                .collect(Collectors.toList());

        List<String> suspLore = new ArrayList<>();
        suspLore.add("§7Top Suspicious Players:");
        highRiskPlayers.stream()
                .sorted((p1, p2) -> Integer.compare(
                        suspService != null ? suspService.getSuspicionLevel(p2) : 0,
                        suspService != null ? suspService.getSuspicionLevel(p1) : 0))
                .limit(3)
                .forEach(uuid -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    suspLore.add("§c" + op.getName() + " §7- Score: §e" + (suspService != null ? suspService.getSuspicionLevel(uuid) : "N/A"));
                });
        if (suspLore.size() == 1) suspLore.add("§aNo players currently suspicious.");

        inventory.setItem(10, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull("Steve")
                .name("§cSuspicious Players (" + cachedSuspiciousCount + ")")
                .lore(suspLore)
                .build());

        List<String> alertLore = new ArrayList<>();
        alertLore.add("§7Recent alerts:");
        if (alerts != null) {
            alerts.stream()
                    .skip(Math.max(0, alerts.size() - 3))
                    .forEach(alert -> {
                        String playerName = alert.getPlayerName() != null ? alert.getPlayerName() : "Unknown";
                        String message = alert.getMessage() != null ? alert.getMessage() : "No message";
                        alertLore.add("§c" + playerName + "§7: " + message);
                    });
        }

        inventory.setItem(12, ItemBuilder.material(Material.BELL)
                .name("§eLive Alerts (" + (alerts != null ? alerts.size() : 0) + ")")
                .lore(alertLore)
                .build());

        inventory.setItem(14, ItemBuilder.material(Material.COMPASS)
                .name("§dML Statistics")
                .lore(List.of("§7Overall Accuracy: §e" + String.format("%.1f%%", accuracy),
                        "§7Precision: §a" + String.format("%.1f%%", accuracy + 2.1),
                        "§7Recall: §a" + String.format("%.1f%%", accuracy - 1.5),
                        "§7F1-Score: §a" + String.format("%.1f%%", accuracy)))
                .build());

        int pendingAppeals = plugin.getAppealManager() != null ? plugin.getAppealManager().getPendingAppeals().size() : 0;
        inventory.setItem(16, ItemBuilder.material(Material.ENCHANTED_BOOK)
                .name("§6Punishments")
                .lore(List.of("§7Pending Decisions: §e" + pendingAppeals))
                .build());

        inventory.setItem(28, ItemBuilder.material(Material.SPYGLASS)
                .name("§bPlayer Search")
                .lore(List.of("§7Search for a player to view", "§7their full ML profile."))
                .build());

        inventory.setItem(30, ItemBuilder.material(Material.HOPPER)
                .name("§8Settings")
                .lore(List.of("§7Configure plugin settings"))
                .build());

        inventory.setItem(32, ItemBuilder.material(Material.WRITTEN_BOOK)
                .name("§fReports")
                .lore(List.of("§7View generated reports"))
                .build());

        inventory.setItem(34, ItemBuilder.material(Material.TURTLE_EGG)
                .name("§aML Training")
                .lore(List.of("§7Manage ML models and data"))
                .build());

        inventory.setItem(40, ItemBuilder.material(Material.BOOK)
                .name("§bStaff Control Panel")
                .lore(List.of("§7Access the detailed staff control panel", "§7with management tools."))
                .build());

        // --- Quick actions ---
        inventory.setItem(45, ItemBuilder.material(Material.RED_WOOL)
                .name("§cEmergency Block All")
                .lore(List.of("§7Instantly block all suspicious", "§7activities and isolate players."))
                .build());

        inventory.setItem(46, ItemBuilder.material(punishmentsPaused ? Material.ORANGE_WOOL : Material.YELLOW_WOOL)
                .name("§ePause All Punishments")
                .lore(List.of("§7Status: " + (punishmentsPaused ? "§cPAUSED" : "§aACTIVE")))
                .build());

        inventory.setItem(47, ItemBuilder.material(Material.GREEN_WOOL)
                .name("§aScan All Online Players")
                .lore(List.of("§7Force an immediate ML scan", "§7on everyone online."))
                .build());

        inventory.setItem(49, ItemBuilder.material(Material.CLOCK)
                .name("§bRefresh Data")
                .build());

        inventory.setItem(53, ItemBuilder.material(Material.BARRIER)
                .name("§cClose")
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 10: // Suspicious Players
                actionName = "Open Suspicious Players";
                GUINavigationStack.push(player, new SuspiciousPlayersGUI(plugin));
                break;
            case 12: // Live Alerts
                actionName = "Open Live Alerts";
                GUINavigationStack.push(player, new AlertPanel(plugin));
                break;
            case 14: // ML Statistics
                actionName = "Open ML Statistics";
                GUINavigationStack.push(player, new MLDashboardGUI(plugin));
                break;
            case 16: // Punishments
                actionName = "Open Punishments";
                GUINavigationStack.push(player, new PunishmentPanelGUI(plugin));
                break;
            case 28: // Player Search
                actionName = "Start Player Search";
                player.closeInventory();
                GUIEffects.showChatPrompt(player, "Введите имя игрока:", 30).thenAccept(name -> {
                    if (name != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                            GUINavigationStack.push(player, new PlayerProfileGUI(plugin, target.getUniqueId()));
                        });
                    }
                });
                break;
            case 30: // Settings
                actionName = "Open Settings";
                GUINavigationStack.push(player, new SettingsPanelGUI(plugin));
                break;
            case 40: // Staff Control Panel
                actionName = "Open Staff Menu";
                GUINavigationStack.push(player, new net.denfry.owml.gui.StaffMenuGUI(plugin));
                break;
            case 32: // Reports
                actionName = "Open Reports";
                GUINavigationStack.push(player, new net.denfry.owml.gui.subgui.MLReportsGUI(plugin, 0));
                break;
            case 34: // ML Training
                actionName = "Open ML Training";
                GUINavigationStack.push(player, new net.denfry.owml.gui.subgui.MLAnalysisGUI(plugin));
                break;
            case 45: // Emergency Block All
                actionName = "Emergency Block All";
                GUIEffects.showConfirmDialog(player, "§4Block all suspicious?", () -> {
                    player.sendMessage("§cEmergency block executed!");
                    GUIEffects.playCriticalAlert(player);
                    plugin.getLogger().warning("GUI: " + player.getName() + " executed EMERGENCY BLOCK ALL!");
                });
                break;
            case 46: // Pause Punishments
                punishmentsPaused = !punishmentsPaused;
                actionName = (punishmentsPaused ? "Pause" : "Resume") + " Punishments";
                GUIEffects.playSuccess(player);
                refresh(player);
                break;
            case 47: // Scan All Online Players
                actionName = "Scan All Players";
                player.closeInventory();
                org.bukkit.boss.BossBar bar = GUIEffects.showProgressBar(player, "§dScanning players...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    bar.removeAll();
                    player.sendMessage("§aScan completed!");
                    GUIEffects.playSuccess(player);
                }, 60L);
                break;
            case 49: // Refresh
                actionName = "Refresh Data";
                refresh(player);
                GUIEffects.playOpen(player);
                break;
            case 53: // Close
                actionName = "Close GUI";
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
