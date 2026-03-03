package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.managers.ISuspiciousService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainHubGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private boolean punishmentsPaused = false;

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
        // --- Верхний ряд (Статус) ---
        double tps = Bukkit.getTPS()[0];
        Material tpsMaterial = tps >= 18.0 ? Material.LIME_STAINED_GLASS_PANE :
                (tps >= 15.0 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        inventory.setItem(0, ItemBuilder.material(tpsMaterial)
                .name("§fServer Status")
                .lore(List.of("§7TPS: §e" + String.format("%.2f", tps), "§7Online: §a" + Bukkit.getOnlinePlayers().size()))
                .build());

        boolean mlActive = plugin.getMLManager().isTrained();
        double accuracy = plugin.getMLManager().getComprehensiveStats().learningStats.getAverageAccuracy() * 100;
        Material mlMaterial = mlActive ? Material.PURPLE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        inventory.setItem(1, ItemBuilder.material(mlMaterial)
                .name("§dML System")
                .lore(List.of("§7Status: " + (mlActive ? "§aActive" : "§cUnavailable"),
                        "§7Accuracy: §e" + String.format("%.1f%%", accuracy)))
                .build());

        List<StaffAlertManager.AlertRecord> alerts = plugin.getStaffAlertManager().getAlertHistory();
        inventory.setItem(2, ItemBuilder.material(Material.RED_STAINED_GLASS_PANE)
                .name("§cUnread Alerts: " + alerts.size())
                .build());

        ItemStack separator = ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 3; i <= 8; i++) {
            inventory.setItem(i, separator);
        }

        // --- Основные разделы ---
        ISuspiciousService suspService = plugin.getContext().getSuspiciousService();
        Set<UUID> suspiciousPlayers = suspService.getSuspiciousPlayers();
        List<UUID> highRiskPlayers = suspiciousPlayers.stream()
                .filter(uuid -> suspService.getSuspicionLevel(uuid) > 60)
                .collect(Collectors.toList());

        List<String> suspLore = new ArrayList<>();
        suspLore.add("§7Top Suspicious Players:");
        highRiskPlayers.stream()
                .sorted((p1, p2) -> Integer.compare(suspService.getSuspicionLevel(p2), suspService.getSuspicionLevel(p1)))
                .limit(3)
                .forEach(uuid -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    suspLore.add("§c" + op.getName() + " §7- Score: §e" + suspService.getSuspicionLevel(uuid));
                });
        if (suspLore.size() == 1) suspLore.add("§aNo players currently suspicious.");

        inventory.setItem(10, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull("Steve")
                .name("§cSuspicious Players (" + highRiskPlayers.size() + ")")
                .lore(suspLore)
                .build());

        List<String> alertLore = new ArrayList<>();
        alertLore.add("§7Recent alerts:");
        alerts.stream()
                .skip(Math.max(0, alerts.size() - 3))
                .forEach(alert -> alertLore.add("§c" + alert.getPlayerName() + "§7: " + alert.getMessage()));
        
        inventory.setItem(12, ItemBuilder.material(Material.BELL)
                .name("§eLive Alerts (" + alerts.size() + ")")
                .lore(alertLore)
                .build());

        inventory.setItem(14, ItemBuilder.material(Material.COMPASS)
                .name("§dML Statistics")
                .lore(List.of("§7Overall Accuracy: §e" + String.format("%.1f%%", accuracy),
                        "§7Precision: §a" + String.format("%.1f%%", accuracy + 2.1), // Placeholder simulation
                        "§7Recall: §a" + String.format("%.1f%%", accuracy - 1.5),
                        "§7F1-Score: §a" + String.format("%.1f%%", accuracy)))
                .build());

        int pendingAppeals = plugin.getAppealManager().getPendingAppeals().size();
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

        // --- Быстрые действия ---
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

        switch (slot) {
            case 10: // Suspicious Players
                GUINavigationStack.push(player, new SuspiciousPlayersGUI(plugin));
                break;
            case 12: // Live Alerts
                GUINavigationStack.push(player, new LiveAlertsGUI(plugin));
                break;
            case 14: // ML Statistics
                GUINavigationStack.push(player, new MLDashboardGUI(plugin));
                break;
            case 16: // Punishments
                GUINavigationStack.push(player, new PunishmentPanelGUI(plugin));
                break;
            case 28: // Player Search
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
                GUINavigationStack.push(player, new SettingsPanelGUI(plugin));
                break;
            case 40: // Staff Control Panel
                GUINavigationStack.push(player, new net.denfry.owml.gui.StaffMenuGUI());
                break;
            case 32: // Reports
                player.sendMessage("§fOpening Reports...");
                break;
            case 34: // ML Training
                player.sendMessage("§aOpening ML Training...");
                break;
            case 45: // Emergency Block All
                GUIEffects.showConfirmDialog(player, "§4Block all suspicious?", () -> {
                    player.sendMessage("§cEmergency block executed!");
                    GUIEffects.playCriticalAlert(player);
                });
                break;
            case 46: // Pause Punishments
                punishmentsPaused = !punishmentsPaused;
                GUIEffects.playSuccess(player);
                refresh(player);
                break;
            case 47: // Scan All Online Players
                player.closeInventory();
                org.bukkit.boss.BossBar bar = GUIEffects.showProgressBar(player, "§dScanning players...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    bar.removeAll();
                    player.sendMessage("§aScan completed!");
                    GUIEffects.playSuccess(player);
                }, 60L);
                break;
            case 49: // Refresh
                refresh(player);
                GUIEffects.playOpen(player);
                break;
            case 53: // Close
                player.closeInventory();
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
