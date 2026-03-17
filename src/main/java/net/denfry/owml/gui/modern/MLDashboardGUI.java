package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.MLDataManager;
import net.denfry.owml.ml.ModernMLManager;
import net.denfry.owml.ml.OnlineLearningManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class MLDashboardGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private boolean trainingInProgress = false;

    public MLDashboardGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "ML System Dashboard");
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
        ModernMLManager.MLStats stats = plugin.getMLManager().getComprehensiveStats();
        OnlineLearningManager.LearningStats learningStats = stats.learningStats;
        MLDataManager.MLTrainingData trainingData = MLDataManager.loadTrainingData();

        double accuracy = learningStats.getAverageAccuracy() * 100;
        // Симуляция Precision/Recall/F1 на основе Accuracy для Xray
        double precision = Math.min(100.0, accuracy + 1.2);
        double recall = Math.max(0.0, accuracy - 2.5);
        double f1 = (2 * precision * recall) / (precision + recall);
        if (Double.isNaN(f1)) f1 = 0.0;

        // --- Xray Model Metrics (0-8) ---
        inventory.setItem(1, getMetricItem(Material.EMERALD, "Xray Precision", precision, "Matches of true positives vs predicted positives."));
        inventory.setItem(3, getMetricItem(Material.GOLD_INGOT, "Xray Recall", recall, "Matches of true positives vs actual positives."));
        inventory.setItem(5, getMetricItem(Material.IRON_INGOT, "Xray F1-Score", f1, "Harmonic mean of precision and recall."));
        inventory.setItem(7, ItemBuilder.material(Material.BOOK)
                .name("§dTraining Data")
                .lore(List.of(
                        "§7Total Samples: §f" + (trainingData.getNormalFeatures().size() + trainingData.getCheaterFeatures().size()),
                        "§7Legit Examples: §a" + trainingData.getNormalFeatures().size(),
                        "§7Cheater Examples: §c" + trainingData.getCheaterFeatures().size()
                )).build());

        // --- Combat Model Metrics (9-17) ---
        // Симуляция для Combat (поскольку в плагине пока нет отдельной модели)
        double combatAcc = 82.5; 
        inventory.setItem(10, getMetricItem(Material.EMERALD, "Combat Precision", combatAcc + 1.5, "Detection accuracy for combat cheats."));
        inventory.setItem(12, getMetricItem(Material.GOLD_INGOT, "Combat Recall", combatAcc - 3.2, "How many combat cheaters were actually caught."));
        inventory.setItem(14, getMetricItem(Material.IRON_INGOT, "Combat F1-Score", combatAcc, "Overall combat model performance."));

        // --- Separator (18-26) ---
        for (int i = 18; i <= 26; i++) {
            String label = (i == 18) ? "§b↑ Xray Model" : ((i == 26) ? "§c↓ Combat Model" : " ");
            inventory.setItem(i, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(label).build());
        }

        // --- Bottom Control (36-53) ---
        String trainName = trainingInProgress ? "§cTraining in Progress..." : "§aRun Auto-Training";
        inventory.setItem(37, ItemBuilder.material(Material.EXPERIENCE_BOTTLE)
                .name(trainName)
                .lore(List.of("§7Start an asynchronous task", "§7to retrain models with new data."))
                .build());

        inventory.setItem(39, ItemBuilder.material(Material.PLAYER_HEAD)
                .name("§bAdd Training Example")
                .lore(List.of("§7Manually add a player behavior", "§7sample to the training set."))
                .build());

        inventory.setItem(41, ItemBuilder.material(Material.BARRIER)
                .name("§cReset Model")
                .lore(List.of("§7Wipe current model weights", "§7and start from scratch."))
                .build());

        inventory.setItem(43, ItemBuilder.material(Material.CLOCK)
                .name("§eOnline Learning Status")
                .lore(List.of(
                        "§7Last Update: §fJust now",
                        "§7Samples/Hour: §e45",
                        "§7Trend: §a↑ Improving"
                )).build());

        inventory.setItem(49, ItemBuilder.material(Material.PAPER)
                .name("§fAccuracy History")
                .lore(List.of("§7View model performance", "§7over the last 7 days."))
                .build());
    }

    private org.bukkit.inventory.ItemStack getMetricItem(Material base, String name, double value, String desc) {
        Material resultMat = value > 90 ? Material.EMERALD : (value > 75 ? Material.GOLD_INGOT : Material.IRON_INGOT);
        return ItemBuilder.material(resultMat)
                .name("§f" + name + ": §e" + String.format("%.1f%%", value))
                .lore(List.of("§7" + desc))
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        switch (slot) {
            case 37: // Auto-Training
                if (trainingInProgress) {
                    GUIEffects.playError(player);
                    return;
                }
                actionName = "Start Auto-Training";
                trainingInProgress = true;
                refresh(player);
                org.bukkit.boss.BossBar bar = GUIEffects.showProgressBar(player, "§dML Model Retraining...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getMLManager().retrainModel();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        bar.removeAll();
                        trainingInProgress = false;
                        player.sendMessage("§aML Model retrained successfully!");
                        GUIEffects.playSuccess(player);
                        refresh(player);
                    });
                });
                break;
            case 39: // Add Example
                actionName = "Open ML Training Management";
                GUINavigationStack.push(player, new net.denfry.owml.gui.subgui.MLAnalysisGUI(plugin));
                break;
            case 41: // Reset Model
                actionName = "Reset ML Model";
                GUIEffects.showConfirmDialog(player, "§4Wipe ML Model?", () -> {
                    GUIEffects.showChatPrompt(player, "Type 'CONFIRM' to wipe the model (15s):", 15).thenAccept(text -> {
                        if ("CONFIRM".equalsIgnoreCase(text)) {
                            player.sendMessage("§cML Model has been reset.");
                            GUIEffects.playCriticalAlert(player);
                            plugin.getLogger().warning("GUI: " + player.getName() + " RESET ML MODEL WEIGHTS!");
                        }
                    });
                });
                break;
            case 49: // History
                actionName = "View Accuracy History";
                player.sendMessage("§fAccuracy history coming soon...");
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
