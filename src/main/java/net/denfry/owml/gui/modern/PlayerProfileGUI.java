package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.PlayerBehaviorProfile;
import net.denfry.owml.detection.PlayerEventBuffer;
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
import java.util.ArrayList;

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
        PlayerBehaviorProfile profile = plugin.getContext().getProfileManager().getProfile(targetId);
        PlayerEventBuffer buffer = plugin.getContext().getProfileManager().getEventBuffer(targetId);

        if (profile == null || buffer == null) {
            player.sendMessage("§cNo data available for player " + (op.getName() != null ? op.getName() : "Unknown"));
            return;
        }

        String status = op.isOnline() ? "§aOnline" : "§7Offline";
        String lastAnalysis = dateFormat.format(new Date(profile.getLastAnalysisTime()));

        // --- Header (0-8) ---
        inventory.setItem(4, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull(op.getName())
                .name("§b" + (op.getName() != null ? op.getName() : "Unknown"))
                .lore(LoreFormatter.format(List.of(
                        "§7Status: " + status,
                        "§7Last Analysis: §f" + lastAnalysis,
                        "§7Overall Suspicion: " + getOverallSuspicion(profile),
                        "§7First Seen: §f" + dateFormat.format(new Date(op.getFirstPlayed()))
                ))).build());

        // Category Scores
        inventory.setItem(10, getCategoryItem(Material.DIAMOND_ORE, "Xray", profile.getDetectionScore("xray")));
        inventory.setItem(12, getCategoryItem(Material.IRON_SWORD, "Combat", profile.getDetectionScore("combat")));
        inventory.setItem(14, getCategoryItem(Material.FEATHER, "Movement", profile.getDetectionScore("movement")));
        inventory.setItem(16, getCategoryItem(Material.SCAFFOLDING, "World/Scaffold", profile.getDetectionScore("world")));

        // --- Recent Events (Lower Section) ---
        List<PlayerEventBuffer.BehavioralEvent> events = buffer.getEvents();
        int eventSlot = 28;
        int maxEvents = 16;

        inventory.setItem(19, ItemBuilder.material(Material.BOOK).name("§eRecent Behavioral Events").build());

        if (events != null) {
            for (int i = 0; i < Math.min(events.size(), maxEvents); i++) {
                PlayerEventBuffer.BehavioralEvent event = events.get(events.size() - 1 - i); // Newest first
                inventory.setItem(eventSlot + i + (i/8)*1, ItemBuilder.material(getEventMaterial(event.eventType()))
                        .name("§f" + event.eventType().toUpperCase())
                        .lore(List.of(
                                "§7Time: §f" + new SimpleDateFormat("HH:mm:ss").format(new Date(event.timestamp())),
                                "§7Value: §e" + event.value(),
                                "§7Context: §7" + event.context().toString()
                        )).build());
            }
        }

        // --- Actions (Bottom) ---
        inventory.setItem(45, ItemBuilder.material(Material.PAPER).name("§fFull Statistics").build());
        inventory.setItem(47, ItemBuilder.material(Material.ENDER_EYE).name("§3Teleport to Player").build());
        inventory.setItem(49, ItemBuilder.material(Material.YELLOW_DYE).name("§eWarn Player").build());
        inventory.setItem(51, ItemBuilder.material(Material.RED_DYE).name("§cPunish Player").build());
        inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Back").build());
    }

    private String getOverallSuspicion(PlayerBehaviorProfile profile) {
        double maxScore = profile.getDetectionScores().values().stream().max(Double::compare).orElse(0.0);
        String color = maxScore > 0.8 ? "§c" : (maxScore > 0.5 ? "§6" : "§a");
        return color + String.format("%.0f%%", maxScore * 100);
    }

    private org.bukkit.inventory.ItemStack getCategoryItem(Material mat, String name, double score) {
        String color = score > 0.8 ? "§c" : (score > 0.5 ? "§6" : "§a");
        return ItemBuilder.material(mat)
                .name(color + name + " Detection")
                .lore(List.of(
                        "§7Current Score: " + color + String.format("%.1f%%", score * 100),
                        "§7Status: " + (score > 0.8 ? "§4CRITICAL" : (score > 0.5 ? "§6SUSPICIOUS" : "§aSAFE"))
                )).build();
    }

    private Material getEventMaterial(String type) {
        return switch (type.toLowerCase()) {
            case "move" -> Material.FEATHER;
            case "combat_hit" -> Material.REDSTONE;
            case "place" -> Material.BRICKS;
            case "interact" -> Material.LEVER;
            default -> Material.PAPER;
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);

        switch (slot) {
            case 47: // Teleport
                if (op.isOnline()) {
                    player.closeInventory();
                    player.teleport(op.getPlayer());
                    player.sendMessage("§aTeleported to " + op.getName());
                    GUIEffects.playSuccess(player);
                } else {
                    GUIEffects.playError(player);
                    player.sendMessage("§cPlayer is offline.");
                }
                break;
            case 49: // Warn
                GUIEffects.showConfirmDialog(player, "Warn " + op.getName() + "?", () -> {
                    player.sendMessage("§ePlayer warned for suspicious behavior.");
                    GUIEffects.playSuccess(player);
                });
                break;
            case 51: // Punish
                player.sendMessage("§cOpening Punishment Panel...");
                GUINavigationStack.push(player, new PunishmentPanelGUI(plugin, targetId));
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
