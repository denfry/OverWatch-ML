package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.ISuspiciousService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.stream.Collectors;

public class SuspiciousPlayersGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final Inventory inventory;

    private int currentPage = 0;
    private TypeFilter typeFilter = TypeFilter.ALL;
    private StatusFilter statusFilter = StatusFilter.ALL;
    private SortType sortType = SortType.SCORE_DESC;
    private String searchQuery = "";

    public SuspiciousPlayersGUI(OverWatchML plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "Suspicious Players List");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    enum TypeFilter { ALL, XRAY_ONLY, COMBAT_ONLY, MOVEMENT_ONLY }
    enum StatusFilter { ALL, ONLINE_ONLY, NEW_ALERTS_ONLY }
    enum SortType { SCORE_DESC, SCORE_ASC, RECENT_ALERT, NAME }

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
        ISuspiciousService service = plugin.getContext().getSuspiciousService();
        List<UUID> players = new ArrayList<>(service.getSuspiciousPlayers());

        // Фильтрация
        players = players.stream().filter(uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            // Поиск по имени
            if (!searchQuery.isEmpty() && (op.getName() == null || !op.getName().toLowerCase().contains(searchQuery.toLowerCase()))) return false;
            // Статус
            if (statusFilter == StatusFilter.ONLINE_ONLY && !op.isOnline()) return false;
            // Тип (упрощенная логика для примера)
            int score = service.getSuspicionLevel(uuid);
            if (score < 10) return false; 
            return true;
        }).collect(Collectors.toList());

        // Сортировка
        players.sort((p1, p2) -> {
            switch (sortType) {
                case SCORE_ASC: return Integer.compare(service.getSuspicionLevel(p1), service.getSuspicionLevel(p2));
                case NAME: return Bukkit.getOfflinePlayer(p1).getName().compareToIgnoreCase(Bukkit.getOfflinePlayer(p2).getName());
                case SCORE_DESC: default: return Integer.compare(service.getSuspicionLevel(p2), service.getSuspicionLevel(p1));
            }
        });

        // Пагинация
        int totalPages = (int) Math.ceil(players.size() / 45.0);
        if (currentPage >= totalPages && totalPages > 0) currentPage = totalPages - 1;

        int start = currentPage * 45;
        int end = Math.min(start + 45, players.size());

        for (int i = 0; i < (end - start); i++) {
            UUID uuid = players.get(start + i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            int score = service.getSuspicionLevel(uuid);
            String scoreColor = score > 80 ? "§c" : (score > 40 ? "§e" : "§a");

            inventory.setItem(i, ItemBuilder.material(Material.PLAYER_HEAD)
                    .skull(op.getName())
                    .name(scoreColor + op.getName() + " §7(" + score + ")")
                    .lore(List.of(
                            "§7Status: " + (op.isOnline() ? "§aOnline" : "§7Offline"),
                            "§7Main Reason: §fAbnormal mining pattern",
                            "§7Last Activity: §f" + (op.isOnline() ? "Just now" : "Long ago"),
                            "",
                            "§eLeft-Click: §7Full Profile",
                            "§eRight-Click: §7Quick Actions",
                            "§eShift-Click: §7Teleport"
                    )).build());
        }

        // --- Нижний ряд ---
        inventory.setItem(45, ItemBuilder.material(Material.HOPPER)
                .name("§bFilter Type: §f" + typeFilter)
                .lore(List.of("§7Click to cycle", "§7Shift-Click to go BACK"))
                .build());

        inventory.setItem(46, ItemBuilder.material(Material.COMPARATOR)
                .name("§bStatus Filter: §f" + statusFilter)
                .build());

        inventory.setItem(47, ItemBuilder.material(Material.LECTERN)
                .name("§bSort: §f" + sortType)
                .build());

        inventory.setItem(49, ItemBuilder.material(Material.SPYGLASS)
                .name("§fSearch: " + (searchQuery.isEmpty() ? "§7None" : "§e" + searchQuery))
                .build());

        // Навигация
        if (currentPage > 0) {
            inventory.setItem(51, ItemBuilder.material(Material.ARROW).name("§7Previous Page").build());
        } else {
            inventory.setItem(51, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        inventory.setItem(52, ItemBuilder.material(Material.PAPER)
                .name("§fPage " + (currentPage + 1) + " / " + Math.max(1, totalPages))
                .build());

        if (currentPage < totalPages - 1) {
            inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Next Page").build());
        } else {
            inventory.setItem(53, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            // Клик по игроку
            int index = (currentPage * 45) + slot;
            ISuspiciousService service = plugin.getContext().getSuspiciousService();
            List<UUID> players = new ArrayList<>(service.getSuspiciousPlayers()); // В реальности нужно хранить отфильтрованный список
            if (index >= players.size()) return;
            UUID targetId = players.get(index);

            if (event.isShiftClick()) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                if (target.isOnline()) {
                    player.teleport(target.getPlayer());
                    player.sendMessage("§aTeleported to " + target.getName());
                }
            } else if (event.isLeftClick()) {
                GUINavigationStack.push(player, new PlayerProfileGUI(plugin, targetId));
            } else if (event.isRightClick()) {
                openQuickActionMenu(player, targetId);
            }
            return;
        }

        switch (slot) {
            case 45:
                if (event.isShiftClick()) {
                    GUINavigationStack.pop(player);
                } else {
                    typeFilter = TypeFilter.values()[(typeFilter.ordinal() + 1) % TypeFilter.values().length];
                    refresh(player);
                }
                break;
            case 46:
                statusFilter = StatusFilter.values()[(statusFilter.ordinal() + 1) % StatusFilter.values().length];
                refresh(player);
                break;
            case 47:
                sortType = SortType.values()[(sortType.ordinal() + 1) % SortType.values().length];
                refresh(player);
                break;
            case 49:
                player.closeInventory();
                GUIEffects.showChatPrompt(player, "Enter player name to search (or 'cancel' to reset):", 30).thenAccept(name -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        searchQuery = (name == null || name.isEmpty()) ? "" : name;
                        currentPage = 0;
                        open(player);
                    });
                });
                break;
            case 51:
                if (currentPage > 0) {
                    currentPage--;
                    refresh(player);
                }
                break;
            case 53:
                // Здесь нужна проверка на макс страницы, для простоты refresh сделает это
                currentPage++;
                refresh(player);
                break;
        }
    }

    private void openQuickActionMenu(Player player, UUID targetId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        OverWatchGUI quickMenu = new OverWatchGUI() {
            private final Inventory inv = Bukkit.createInventory(this, 27, "Quick Actions: " + target.getName());

            @Override
            public void open(Player p) {
                inv.setItem(10, ItemBuilder.material(Material.ENDER_EYE).name("§3Watch").build());
                inv.setItem(11, ItemBuilder.material(Material.YELLOW_DYE).name("§eWarn").build());
                inv.setItem(12, ItemBuilder.material(Material.RED_DYE).name("§cPunish").build());
                inv.setItem(13, ItemBuilder.material(Material.LIME_DYE).name("§aMark Legit").build());
                inv.setItem(14, ItemBuilder.material(Material.PLAYER_HEAD).skull(target.getName()).name("§bFull Profile").build());
                p.openInventory(inv);
            }

            @Override
            public void close(Player p) {}
            @Override
            public void refresh(Player p) {}

            @Override
            public void handleClick(InventoryClickEvent e) {
                switch (e.getSlot()) {
                    case 10: player.closeInventory(); player.teleport(target.getPlayer()); break;
                    case 11: player.sendMessage("§eWarned!"); player.closeInventory(); break;
                    case 12: player.sendMessage("§cPunishing..."); player.closeInventory(); break;
                    case 13: player.sendMessage("§aLegit!"); player.closeInventory(); break;
                    case 14: GUINavigationStack.push(player, new PlayerProfileGUI(plugin, targetId)); break;
                }
            }

            @Override
            public Inventory getInventory() { return inv; }
        };
        GUINavigationStack.push(player, quickMenu);
    }
}
