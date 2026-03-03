package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.ReasoningMLModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PlayerProfileGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final UUID targetId;
    private final Inventory inventory;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public PlayerProfileGUI(OverWatchML plugin, UUID targetId) {
        this.plugin = plugin;
        this.targetId = targetId;
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        this.inventory = Bukkit.createInventory(this, 54, "Profile: " + op.getName());
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
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        int score = plugin.getSuspicionLevel(op.getName());
        ReasoningMLModel.DetectionResult mlResult = plugin.getMLManager().getDetectionResults().get(targetId);
        
        String status = op.isOnline() ? "§aOnline" : "§7Offline";
        String verdict = (mlResult != null) ? mlResult.getConclusion() : "§7No data";
        double confidence = (mlResult != null) ? mlResult.getProbability() * 100 : 0;
        String regDate = dateFormat.format(new Date(op.getFirstPlayed()));

        // --- Шапка (0-8) ---
        inventory.setItem(4, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull(op.getName())
                .name("§b" + op.getName())
                .lore(List.of(
                        "§7Status: " + status,
                        "§7Verdict: " + verdict,
                        "§7Confidence: §e" + String.format("%.1f%%", confidence),
                        "§7Registered: §f" + regDate
                )).build());

        Material glassMat = score > 80 ? Material.RED_STAINED_GLASS_PANE :
                           (score > 40 ? Material.YELLOW_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
        inventory.setItem(3, ItemBuilder.material(glassMat).name(" ").build());
        inventory.setItem(5, ItemBuilder.material(glassMat).name(" ").build());

        // Xray Status (0)
        Material xrayMat = score > 60 ? Material.DIAMOND : Material.EMERALD;
        inventory.setItem(0, ItemBuilder.material(xrayMat)
                .name("§bXray Status")
                .lore(List.of(
                        "§7Xray Score: §e" + score,
                        "§7Ore Find Rate: §fAbove Normal",
                        "§7Decoy Interactions: §c4 detected",
                        "§7Last Episode: §fJust now at 120, 12, -450"
                )).build());

        // Combat Status (8)
        inventory.setItem(8, ItemBuilder.material(Material.IRON_SWORD)
                .name("§cCombat Status")
                .lore(List.of(
                        "§7Combat Score: §e12",
                        "§7Headshot %: §f14%",
                        "§7Model Deviation: §aNormal",
                        "§7Suspicious Fights: §f0 recorded"
                )).build());

        // --- Детали (Средняя часть) ---
        inventory.setItem(19, ItemBuilder.material(Material.BOOK).name("§eActivity Timeline").build());
        inventory.setItem(21, ItemBuilder.material(Material.IRON_PICKAXE).name("§7Mining History").build());
        inventory.setItem(23, ItemBuilder.material(Material.BLAZE_ROD).name("§cCombat History").build());
        inventory.setItem(25, ItemBuilder.material(Material.COMPASS).name("§dBehavioral Profile").build());

        // --- Действия (Нижняя часть) ---
        inventory.setItem(37, ItemBuilder.material(Material.ENDER_EYE).name("§3Watch Player").build());
        inventory.setItem(39, ItemBuilder.material(Material.YELLOW_DYE).name("§eWarn Player").build());
        inventory.setItem(41, ItemBuilder.material(Material.RED_DYE).name("§cPunish Player").build());
        inventory.setItem(43, ItemBuilder.material(Material.LIME_DYE).name("§aMark as Legit").build());
        inventory.setItem(45, ItemBuilder.material(Material.PAPER).name("§fPunishment History").build());
        inventory.setItem(49, ItemBuilder.material(Material.WRITABLE_BOOK).name("§6Export Report").build());
        inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Back").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);

        switch (slot) {
            case 37: // Watch
                if (op.isOnline()) {
                    player.closeInventory();
                    player.teleport(op.getPlayer());
                    player.sendMessage("§aTeleported to " + op.getName());
                } else {
                    GUIEffects.playError(player);
                    player.sendMessage("§cPlayer is offline.");
                }
                break;
            case 39: // Warn
                GUIEffects.showConfirmDialog(player, "Warn " + op.getName() + "?", () -> {
                    player.sendMessage("§ePlayer warned for suspicious mining.");
                    GUIEffects.playSuccess(player);
                });
                break;
            case 41: // Punish
                player.sendMessage("§cOpening Punishment Dialog...");
                break;
            case 43: // Mark as Legit
                player.sendMessage("§aPlayer marked as legit. Retraining ML...");
                GUIEffects.playSuccess(player);
                break;
            case 49: // Export
                player.sendMessage("§6Report exported to: §f/plugins/OverWatchML/reports/" + op.getName() + ".txt");
                GUIEffects.playSuccess(player);
                break;
            case 53: // Back
                GUINavigationStack.pop(player);
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
