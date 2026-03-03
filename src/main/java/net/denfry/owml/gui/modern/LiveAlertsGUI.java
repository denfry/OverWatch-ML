package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LiveAlertsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private AlertTypeFilter typeFilter = AlertTypeFilter.ALL;
    private int currentPage = 0;

    enum AlertTypeFilter { ALL, CRITICAL, WARNING, INFO }

    public LiveAlertsGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "Live Alerts Panel");
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
        List<StaffAlertManager.AlertRecord> allAlerts = new ArrayList<>(plugin.getStaffAlertManager().getAlertHistory());
        Collections.reverse(allAlerts); // Сначала новые

        // Фильтрация
        List<StaffAlertManager.AlertRecord> filteredAlerts = allAlerts.stream().filter(alert -> {
            if (typeFilter == AlertTypeFilter.ALL) return true;
            String msg = alert.getMessage().toLowerCase();
            if (typeFilter == AlertTypeFilter.CRITICAL) return msg.contains("critical") || msg.contains("high");
            if (typeFilter == AlertTypeFilter.WARNING) return msg.contains("suspicious") || msg.contains("warn");
            return true;
        }).collect(Collectors.toList());

        int totalPages = (int) Math.ceil(filteredAlerts.size() / 45.0);
        int start = currentPage * 45;
        int end = Math.min(start + 45, filteredAlerts.size());

        for (int i = 0; i < (end - start); i++) {
            StaffAlertManager.AlertRecord alert = filteredAlerts.get(start + i);
            Material mat = alert.getMessage().toLowerCase().contains("critical") ? Material.RED_CONCRETE :
                          (alert.getMessage().toLowerCase().contains("suspicious") ? Material.YELLOW_CONCRETE : Material.BLUE_CONCRETE);

            inventory.setItem(i, ItemBuilder.material(mat)
                    .name("§e" + alert.getPlayerName() + " §7- " + alert.getMessage())
                    .lore(List.of(
                            "§7Time: §f" + timeFormat.format(alert.getTimestamp()),
                            "§7Location: §f" + alert.getLocation().getBlockX() + ", " + alert.getLocation().getBlockY() + ", " + alert.getLocation().getBlockZ(),
                            "§7Confidence: §d85%", // Simulation
                            "",
                            "§eLeft-Click: §7View Profile",
                            "§eRight-Click: §7Mark Read (Dismiss)",
                            "§eShift-Click: §7Teleport & Dismiss"
                    )).build());
        }

        // --- Верхняя строка (Фильтры) ---
        inventory.setItem(45, ItemBuilder.material(Material.HOPPER)
                .name("§bFilter: §f" + typeFilter)
                .build());

        inventory.setItem(49, ItemBuilder.material(Material.BELL)
                .name("§eUnread Alerts: " + filteredAlerts.size())
                .enchanted()
                .build());

        inventory.setItem(46, ItemBuilder.material(Material.LAVA_BUCKET)
                .name("§cMark All Read")
                .lore(List.of("§7Right-click to clear all alerts"))
                .build());

        inventory.setItem(53, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("§6Export Today's Log")
                .build());

        // Навигация
        if (currentPage > 0) inventory.setItem(51, ItemBuilder.material(Material.ARROW).name("§7Prev").build());
        inventory.setItem(52, ItemBuilder.material(Material.PAPER).name("§fPage " + (currentPage + 1)).build());
        if (currentPage < totalPages - 1) inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Next").build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            List<StaffAlertManager.AlertRecord> allAlerts = new ArrayList<>(plugin.getStaffAlertManager().getAlertHistory());
            Collections.reverse(allAlerts);
            int index = (currentPage * 45) + slot;
            if (index >= allAlerts.size()) return;
            StaffAlertManager.AlertRecord alert = allAlerts.get(index);

            if (event.isLeftClick() && !event.isShiftClick()) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(alert.getPlayerName());
                GUINavigationStack.push(player, new PlayerProfileGUI(plugin, target.getUniqueId()));
            } else if (event.isRightClick()) {
                dismissAlert(alert);
                refresh(player);
                GUIEffects.playOpen(player);
            } else if (event.isShiftClick()) {
                player.teleport(alert.getLocation());
                dismissAlert(alert);
                player.sendMessage("§aTeleported to alert location and dismissed.");
                player.closeInventory();
            }
            return;
        }

        switch (slot) {
            case 45: // Filter
                typeFilter = AlertTypeFilter.values()[(typeFilter.ordinal() + 1) % AlertTypeFilter.values().length];
                refresh(player);
                break;
            case 46: // Mark All Read
                if (event.isRightClick()) {
                    plugin.getStaffAlertManager().getAlertHistory().clear();
                    player.sendMessage("§aAll alerts cleared!");
                    refresh(player);
                    GUIEffects.playSuccess(player);
                }
                break;
            case 51: if (currentPage > 0) { currentPage--; refresh(player); } break;
            case 53: currentPage++; refresh(player); break; // simple logic for demo
        }
    }

    private void dismissAlert(StaffAlertManager.AlertRecord alert) {
        plugin.getStaffAlertManager().getAlertHistory().remove(alert);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
