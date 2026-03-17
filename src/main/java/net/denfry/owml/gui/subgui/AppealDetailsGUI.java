package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.AppealManager;
import net.denfry.owml.managers.AppealManager.Appeal;
import net.denfry.owml.managers.AppealManager.AppealStatus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AppealDetailsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final AppealManager appealManager;
    private final Appeal appeal;
    private final Inventory inventory;

    public AppealDetailsGUI(OverWatchML plugin, AppealManager appealManager, Appeal appeal) {
        this.plugin = plugin;
        this.appealManager = appealManager;
        this.appeal = appeal;
        this.inventory = Bukkit.createInventory(this, 54, "Appeal Details: " + appeal.getPlayerName());
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
        
        // Info
        inventory.setItem(4, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull(appeal.getPlayerName())
                .name("§b" + appeal.getPlayerName())
                .lore(List.of(
                        "§7Level: §c" + appeal.getPunishmentLevel(),
                        "§7Date: §f" + appeal.getFormattedTimestamp(),
                        "§7Status: §e" + appeal.getStatus().getDisplayName()
                ))
                .build());

        // Reason
        inventory.setItem(22, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("§6Reason")
                .lore(List.of("§f" + appeal.getReason()))
                .build());

        // Actions
        inventory.setItem(38, ItemBuilder.material(Material.LIME_WOOL).name("§aApprove").build());
        inventory.setItem(40, ItemBuilder.material(Material.ORANGE_WOOL).name("§6Mark Review").build());
        inventory.setItem(42, ItemBuilder.material(Material.RED_WOOL).name("§cDeny").build());
        
        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        switch (slot) {
            case 38: // Approve
                handleAction(player, AppealStatus.APPROVED);
                break;
            case 40: // Review
                appealManager.updateAppealStatus(appeal.getId(), AppealStatus.UNDER_REVIEW, player.getName(), "Under review");
                player.sendMessage("§6Marked as under review.");
                refresh(player);
                break;
            case 42: // Deny
                handleAction(player, AppealStatus.DENIED);
                break;
            case 49:
                GUINavigationStack.pop(player);
                break;
        }
    }

    private void handleAction(Player player, AppealStatus status) {
        player.closeInventory();
        GUIEffects.showChatPrompt(player, "Enter response for " + status.name() + " (or 'cancel'):", 60).thenAccept(response -> {
            if (response != null && !response.equalsIgnoreCase("cancel")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    appealManager.updateAppealStatus(appeal.getId(), status, player.getName(), response);
                    player.sendMessage("§aAppeal " + status.name().toLowerCase() + "ed.");
                    GUINavigationStack.pop(player);
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> open(player));
            }
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
