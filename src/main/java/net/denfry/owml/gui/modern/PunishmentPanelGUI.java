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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class PunishmentPanelGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private UUID selectedPlayerId = null;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    // Static history for prototype
    private static final List<PunishmentRecord> history = new ArrayList<>();

    public PunishmentPanelGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "Punishment Control Panel");
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

        // --- Bottom Row: History (45-53) ---
        int historyStartSlot = 45;
        for (int i = 0; i < Math.min(9, history.size()); i++) {
            PunishmentRecord rec = history.get(history.size() - 1 - i);
            inventory.setItem(historyStartSlot + i, ItemBuilder.material(Material.PAPER)
                    .name("§7" + rec.playerName + " §f- " + rec.type)
                    .lore(List.of("§7Time: §f" + timeFormat.format(rec.timestamp), "§eClick to cancel (if <10m)"))
                    .build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Selection
        if (slot == 0 || slot == 9 || slot == 18 || slot == 27 || slot == 36) {
            List<UUID> awaiting = plugin.getContext().getSuspiciousService().getSuspiciousPlayers().stream()
                    .filter(uuid -> plugin.getSuspicionLevel(Bukkit.getOfflinePlayer(uuid).getName()) > 60)
                    .limit(5)
                    .collect(Collectors.toList());
            int index = slot / 9;
            if (index < awaiting.size()) {
                selectedPlayerId = awaiting.get(index);
                refresh(player);
                GUIEffects.playOpen(player);
            }
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
                executePunishment(player, target, "KICK");
                break;
            case 17: // Temp Ban
                openDurationSelection(player, target, "BAN");
                break;
            case 26: // Perm Ban
                GUIEffects.showConfirmDialog(player, "Ban " + target.getName() + "?", () -> executePunishment(player, target, "PERMANENT BAN"));
                break;
            case 35: // Mute
                openDurationSelection(player, target, "MUTE");
                break;
            case 44: // Custom
                player.closeInventory();
                GUIEffects.showChatPrompt(player, "Enter command (use %p for name):", 30).thenAccept(cmd -> {
                    if (cmd != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%p", target.getName()));
                            addHistory(target.getName(), "CUSTOM");
                        });
                    }
                });
                break;
        }

        // History cancel
        if (slot >= 45 && slot <= 53) {
            int hIndex = history.size() - 1 - (slot - 45);
            if (hIndex >= 0 && hIndex < history.size()) {
                PunishmentRecord rec = history.get(hIndex);
                if (System.currentTimeMillis() - rec.timestamp < 600000) { // 10 min
                    player.sendMessage("§aPunishment for " + rec.playerName + " cancelled.");
                    history.remove(hIndex);
                    refresh(player);
                    GUIEffects.playSuccess(player);
                }
            }
        }
    }

    private void executePunishment(Player staff, OfflinePlayer target, String type) {
        staff.sendMessage("§aExecuted: " + type + " on " + target.getName());
        addHistory(target.getName(), type);
        selectedPlayerId = null;
        refresh(staff);
        GUIEffects.playSuccess(staff);
    }

    private void addHistory(String name, String type) {
        history.add(new PunishmentRecord(name, type));
        if (history.size() > 9) history.remove(0);
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
