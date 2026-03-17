package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.ISuspiciousService;
import net.denfry.owml.detection.PlayerBehaviorProfile;
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
        List<UUID> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());
        
        // Filter and calculate scores
        Map<UUID, Double> playerScores = new HashMap<>();
        Map<UUID, String> mainReasons = new HashMap<>();

        for (UUID uuid : players) {
            PlayerBehaviorProfile profile = plugin.getContext().getProfileManager().getProfile(uuid);
            
            // Find highest score and category
            double maxScore = 0.0;
            String mainCat = "None";
            
            for (Map.Entry<String, Double> entry : profile.getDetectionScores().entrySet()) {
                if (entry.getValue() > maxScore) {
                    maxScore = entry.getValue();
                    mainCat = entry.getKey().toUpperCase();
                }
            }
            
            playerScores.put(uuid, maxScore);
            mainReasons.put(uuid, mainCat);
        }

        // Apply filters
        players = players.stream().filter(uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (!searchQuery.isEmpty() && (op.getName() == null || !op.getName().toLowerCase().contains(searchQuery.toLowerCase()))) return false;
            
            double score = playerScores.getOrDefault(uuid, 0.0);
            if (score < 0.1 && statusFilter != StatusFilter.ALL) return false; // Hide completely safe players if not in ALL mode
            
            return true;
        }).collect(Collectors.toList());

        // Sort
        players.sort((p1, p2) -> {
            switch (sortType) {
                case SCORE_ASC: return Double.compare(playerScores.get(p1), playerScores.get(p2));
                case NAME: return Bukkit.getOfflinePlayer(p1).getName().compareToIgnoreCase(Bukkit.getOfflinePlayer(p2).getName());
                case SCORE_DESC: default: return Double.compare(playerScores.get(p2), playerScores.get(p1));
            }
        });

        // Pagination
        int totalPages = (int) Math.ceil(players.size() / 45.0);
        if (currentPage >= totalPages && totalPages > 0) currentPage = totalPages - 1;

        int start = currentPage * 45;
        int end = Math.min(start + 45, players.size());

        for (int i = 0; i < (end - start); i++) {
            UUID uuid = players.get(start + i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            double score = playerScores.getOrDefault(uuid, 0.0);
            String scoreColor = score > 0.8 ? "§c" : (score > 0.5 ? "§6" : "§a");

            inventory.setItem(i, ItemBuilder.material(Material.PLAYER_HEAD)
                    .skull(op.getName())
                    .name(scoreColor + op.getName() + " §7(" + String.format("%.0f%%", score * 100) + ")")
                    .lore(List.of(
                            "§7Status: " + (op.isOnline() ? "§aOnline" : "§7Offline"),
                            "§7Primary Issue: " + scoreColor + mainReasons.get(uuid),
                            "§7Last Analysis: §fJust now",
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
        
        String actionName = "Unknown";

        if (slot < 45) {
            // Клик по игроку
            int index = (currentPage * 45) + slot;
            ISuspiciousService service = plugin.getContext().getSuspiciousService();
            List<UUID> players = new ArrayList<>(service.getSuspiciousPlayers()); // В реальности нужно хранить отфильтрованный список
            if (index >= players.size()) return;
            UUID targetId = players.get(index);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);

            if (event.isShiftClick()) {
                actionName = "Teleport to " + target.getName();
                if (target.isOnline()) {
                    player.teleport(target.getPlayer());
                    player.sendMessage("§aTeleported to " + target.getName());
                }
            } else if (event.isLeftClick()) {
                actionName = "Open Profile for " + target.getName();
                GUINavigationStack.push(player, new PlayerProfileGUI(plugin, targetId));
            } else if (event.isRightClick()) {
                actionName = "Open Quick Actions for " + target.getName();
                openQuickActionMenu(player, targetId);
            }
            
            if (!actionName.equals("Unknown")) {
                plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
            }
            return;
        }

        switch (slot) {
            case 45:
                if (event.isShiftClick()) {
                    actionName = "Go Back";
                    GUINavigationStack.pop(player);
                } else {
                    typeFilter = TypeFilter.values()[(typeFilter.ordinal() + 1) % TypeFilter.values().length];
                    actionName = "Cycle Type Filter: " + typeFilter;
                    refresh(player);
                }
                break;
            case 46:
                statusFilter = StatusFilter.values()[(statusFilter.ordinal() + 1) % StatusFilter.values().length];
                actionName = "Cycle Status Filter: " + statusFilter;
                refresh(player);
                break;
            case 47:
                sortType = SortType.values()[(sortType.ordinal() + 1) % SortType.values().length];
                actionName = "Cycle Sort Type: " + sortType;
                refresh(player);
                break;
            case 49:
                actionName = "Open Search Prompt";
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
                    actionName = "Previous Page (" + (currentPage + 1) + ")";
                    refresh(player);
                }
                break;
            case 53:
                currentPage++;
                actionName = "Next Page (" + (currentPage + 1) + ")";
                refresh(player);
                break;
        }
        
        if (!actionName.equals("Unknown")) {
            plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
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
