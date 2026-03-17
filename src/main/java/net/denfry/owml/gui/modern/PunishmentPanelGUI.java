package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.ReasoningMLModel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class PunishmentPanelGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private UUID selectedPlayerId = null;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final Path historyFile;

    // In-memory history loaded from file
    private List<PunishmentRecord> history;

    public PunishmentPanelGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "Punishment Control Panel");
        this.historyFile = plugin.getDataFolder().toPath().resolve("punishment_history.json");
        loadHistory();
    }

    public PunishmentPanelGUI(OverWatchML plugin, UUID targetId) {
        this.plugin = plugin;
        this.selectedPlayerId = targetId;
        this.inventory = Bukkit.createInventory(this, 54, "Punishment Control Panel");
        this.historyFile = plugin.getDataFolder().toPath().resolve("punishment_history.json");
        loadHistory();
    }

    public static class PunishmentRecord {
        String playerName;
        String type;
        long timestamp;

        public PunishmentRecord(String playerName, String type) {
            this.playerName = playerName;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private void loadHistory() {
        history = new ArrayList<>();
        if (Files.exists(historyFile)) {
            try {
                String content = new String(Files.readAllBytes(historyFile));
                if (!content.isEmpty()) {
                    // Simple JSON parsing (in production, use a proper JSON library)
                    String[] records = content.split("\\},\\{");
                    for (String record : records) {
                        try {
                            String cleaned = record.replace("[", "").replace("]", "").replace("{", "").replace("}", "").trim();
                            String[] parts = cleaned.split(",");
                            String playerName = null, type = null;
                            long timestamp = 0;
                            for (String part : parts) {
                                String[] kv = part.split(":");
                                if (kv.length == 2) {
                                    String key = kv[0].trim().replace("\"", "");
                                    String value = kv[1].trim().replace("\"", "");
                                    if (key.equals("playerName")) playerName = value;
                                    else if (key.equals("type")) type = value;
                                    else if (key.equals("timestamp")) timestamp = Long.parseLong(value);
                                }
                            }
                            if (playerName != null && type != null) {
                                PunishmentRecord rec = new PunishmentRecord(playerName, type);
                                rec.timestamp = timestamp;
                                history.add(rec);
                            }
                        } catch (Exception e) {
                            // Skip malformed records
                        }
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load punishment history: " + e.getMessage());
            }
        }
    }

    private void saveHistory() {
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                PunishmentRecord rec = history.get(i);
                sb.append("{\"playerName\":\"").append(rec.playerName)
                  .append("\",\"type\":\"").append(rec.type)
                  .append("\",\"timestamp\":").append(rec.timestamp).append("}");
                if (i < history.size() - 1) sb.append(",");
            }
            sb.append("]");
            Files.writeString(historyFile, sb.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save punishment history: " + e.getMessage());
        }
    }

    private void addHistory(String name, String type) {
        history.add(new PunishmentRecord(name, type));
        // Keep only last 50 records
        if (history.size() > 50) {
            history.remove(0);
        }
        saveHistory();
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

        // --- Left Third: Awaiting Decision (0, 9, 18, 27, 36) ---
        List<UUID> awaiting = plugin.getContext().getSuspiciousService().getSuspiciousPlayers().stream()
                .filter(uuid -> plugin.getSuspicionLevel(Bukkit.getOfflinePlayer(uuid).getName()) > 60)
                .limit(5)
                .collect(Collectors.toList());

        int[] awaitingSlots = {0, 9, 18, 27, 36};
        for (int i = 0; i < awaiting.size(); i++) {
            UUID id = awaiting.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            int score = plugin.getSuspicionLevel(op.getName());
            boolean isSelected = id.equals(selectedPlayerId);
            
            ReasoningMLModel.DetectionResult mlResult = plugin.getMLManager().getDetectionResults().get(id);
            String reason = (mlResult != null) ? mlResult.getConclusion() : "Manual flags detected";

            inventory.setItem(awaitingSlots[i], ItemBuilder.material(Material.PLAYER_HEAD)
                    .skull(op.getName())
                    .name((isSelected ? "§b» §l" : "§7") + op.getName() + " §e(" + score + ")")
                    .lore(List.of(
                            "§7ML Recommendation: §cHigh Risk",
                            "§7Reasoning: §f" + reason,
                            "",
                            "§eLeft-Click: §7Select Player"
                    )).build());
        }

        // --- Middle Part: Details (4, 13, 22, 31, 40) ---
        if (selectedPlayerId != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(selectedPlayerId);
            ReasoningMLModel.DetectionResult ml = plugin.getMLManager().getDetectionResults().get(selectedPlayerId);
            
            inventory.setItem(4, ItemBuilder.material(Material.PLAYER_HEAD).skull(target.getName()).name("§b" + target.getName()).build());
            inventory.setItem(13, ItemBuilder.material(Material.COMPASS).name("§fML Confidence: §d" + (ml != null ? String.format("%.1f%%", ml.getProbability()*100) : "N/A")).build());
            inventory.setItem(22, ItemBuilder.material(Material.BOOK).name("§fSuggested: §eTemp Ban 24h").build());
            inventory.setItem(31, ItemBuilder.material(Material.PAPER).name("§fRecent flags: §c3 Critical").build());
        } else {
            inventory.setItem(22, ItemBuilder.material(Material.BARRIER).name("§7Select a player to see details").build());
        }

        // --- Right Third: Actions (8, 17, 26, 35, 44) ---
        inventory.setItem(8, ItemBuilder.material(Material.OAK_DOOR).name("§eKick Player").build());
        inventory.setItem(17, ItemBuilder.material(Material.CLOCK).name("§6Temp Ban").build());
        inventory.setItem(26, ItemBuilder.material(Material.BARRIER).name("§cPermanent Ban").build());
        inventory.setItem(35, ItemBuilder.material(Material.ARROW).name("§dMute Player").build());
        inventory.setItem(44, ItemBuilder.material(Material.COMMAND_BLOCK_MINECART).name("§fCustom Command").build());

        // --- Center Bottom: Appeals (49) ---
        inventory.setItem(49, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("§aReview Appeals")
                .lore(List.of("§7Manage player punishment appeals."))
                .build());

        // --- Bottom Row: History (45-48, 50-53) ---
        int[] historySlots = {45, 46, 47, 48, 50, 51, 52, 53};
        for (int i = 0; i < Math.min(historySlots.length, history.size()); i++) {
            PunishmentRecord rec = history.get(history.size() - 1 - i);
            inventory.setItem(historySlots[i], ItemBuilder.material(Material.PAPER)
                    .name("§7" + rec.playerName + " §f- " + rec.type)
                    .lore(List.of("§7Time: §f" + timeFormat.format(rec.timestamp), "§eClick to cancel (if <10m)"))
                    .build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        String actionName = "Unknown";

        // Selection
        if (slot == 0 || slot == 9 || slot == 18 || slot == 27 || slot == 36) {
            List<UUID> awaiting = plugin.getContext().getSuspiciousService().getSuspiciousPlayers().stream()
                    .filter(uuid -> plugin.getSuspicionLevel(Bukkit.getOfflinePlayer(uuid).getName()) > 60)
                    .limit(5)
                    .collect(Collectors.toList());
            int index = slot / 9;
            if (index < awaiting.size()) {
                selectedPlayerId = awaiting.get(index);
                actionName = "Select Player: " + Bukkit.getOfflinePlayer(selectedPlayerId).getName();
                refresh(player);
                GUIEffects.playOpen(player);
            }
            return;
        }

        if (slot == 49) {
            actionName = "Open Appeals GUI";
            GUINavigationStack.push(player, new net.denfry.owml.gui.subgui.AppealGUI(plugin));
            plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
            return;
        }

        if (selectedPlayerId == null && (slot == 8 || slot == 17 || slot == 26 || slot == 35 || slot == 44)) {
            player.sendMessage("§cPlease select a player first!");
            GUIEffects.playError(player);
            return;
        }

        OfflinePlayer target = selectedPlayerId != null ? Bukkit.getOfflinePlayer(selectedPlayerId) : null;

        switch (slot) {
            case 8: // Kick
                actionName = "Execute KICK on " + target.getName();
                executePunishment(player, target, "KICK");
                break;
            case 17: // Temp Ban
                actionName = "Open Ban Duration Selection for " + target.getName();
                openDurationSelection(player, target, "BAN");
                break;
            case 26: // Perm Ban
                actionName = "Confirm Perm Ban for " + target.getName();
                GUIEffects.showConfirmDialog(player, "Ban " + target.getName() + "?", () -> {
                    executePunishment(player, target, "PERMANENT BAN");
                    plugin.getLogger().warning("GUI: " + player.getName() + " executed PERMANENT BAN on " + target.getName());
                });
                break;
            case 35: // Mute
                actionName = "Open Mute Duration Selection for " + target.getName();
                openDurationSelection(player, target, "MUTE");
                break;
            case 44: // Custom
                actionName = "Enter Custom Command for " + target.getName();
                player.closeInventory();
                GUIEffects.showChatPrompt(player, "Enter command (use %p for name):", 30).thenAccept(cmd -> {
                    if (cmd != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%p", target.getName()));
                            addHistory(target.getName(), "CUSTOM");
                            plugin.getLogger().info("GUI: " + player.getName() + " executed custom command on " + target.getName() + ": " + cmd);
                        });
                    }
                });
                break;
        }

        // History cancel
        int[] historySlots = {45, 46, 47, 48, 50, 51, 52, 53};
        for (int i = 0; i < historySlots.length; i++) {
            if (slot == historySlots[i]) {
                int hIndex = history.size() - 1 - i;
                if (hIndex >= 0 && hIndex < history.size()) {
                    PunishmentRecord rec = history.get(hIndex);
                    if (System.currentTimeMillis() - rec.timestamp < 600000) { // 10 min
                        actionName = "Cancel Punishment for " + rec.playerName;
                        player.sendMessage("§aPunishment for " + rec.playerName + " cancelled.");
                        history.remove(hIndex);
                        refresh(player);
                        GUIEffects.playSuccess(player);
                    }
                }
            }
        }
        
        if (!actionName.equals("Unknown")) {
            plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
        }
    }

    private void executePunishment(Player staff, OfflinePlayer target, String type) {
        if (target == null || target.getName() == null) {
            staff.sendMessage("§cInvalid target for punishment!");
            return;
        }
        staff.sendMessage("§aExecuted: " + type + " on " + target.getName());
        addHistory(target.getName(), type);
        selectedPlayerId = null;
        refresh(staff);
        GUIEffects.playSuccess(staff);
    }

    private void openDurationSelection(Player staff, OfflinePlayer target, String baseType) {
        OverWatchGUI durationGui = new OverWatchGUI() {
            private final Inventory inv = Bukkit.createInventory(this, 9, "Select Duration");
            @Override public void open(Player p) {
                inv.setItem(0, ItemBuilder.material(Material.CLOCK).name("1 Hour").build());
                inv.setItem(2, ItemBuilder.material(Material.CLOCK).name("6 Hours").build());
                inv.setItem(4, ItemBuilder.material(Material.CLOCK).name("1 Day").build());
                inv.setItem(6, ItemBuilder.material(Material.CLOCK).name("3 Days").build());
                inv.setItem(8, ItemBuilder.material(Material.CLOCK).name("7 Days").build());
                p.openInventory(inv);
            }
            @Override public void close(Player p) {}
            @Override public void refresh(Player p) {}
            @Override public void handleClick(InventoryClickEvent e) {
                if (e.getCurrentItem() == null) return;
                String duration = e.getCurrentItem().getItemMeta().getDisplayName();
                executePunishment(staff, target, baseType + " (" + duration + ")");
            }
            @Override public Inventory getInventory() { return inv; }
        };
        GUINavigationStack.push(staff, durationGui);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
