package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.denfry.owml.managers.StatsManager;

import java.util.*;

public class PlayerStatsMainGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final int itemsPerPage = 45;
    private final Map<Integer, UUID> playerSlots = new HashMap<>();
    private final Inventory inv;
    private int currentPage;

    public PlayerStatsMainGUI(OverWatchML plugin, int currentPage) {
        this.plugin = plugin;
        this.currentPage = currentPage;
        this.inv = Bukkit.createInventory(this, 54, "📊 Player Analytics");
    }

    public PlayerStatsMainGUI(OverWatchML plugin) {
        this(plugin, 0);
    }

    @Override
    public void open(Player player) {
        refresh(player);
        player.openInventory(inv);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        inv.clear();
        playerSlots.clear();
        
        List<OfflinePlayer> playersWithStats = getPlayersWithStats();
        int totalPages = Math.max(1, (int) Math.ceil((double) playersWithStats.size() / itemsPerPage));
        
        if (currentPage >= totalPages) currentPage = totalPages - 1;
        if (currentPage < 0) currentPage = 0;

        setNavigationItems(totalPages);

        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playersWithStats.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= 45) break;

            OfflinePlayer p = playersWithStats.get(i);
            inv.setItem(slot, createPlayerHead(p));
            playerSlots.put(slot, p.getUniqueId());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player staff = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot < 45) {
            UUID targetId = playerSlots.get(slot);
            if (targetId != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
                GUINavigationStack.push(staff, new PlayerMiningStatsGUI(plugin, targetId, op.getName() != null ? op.getName() : "Unknown"));
            }
        } else if (slot == 45 && inv.getItem(45).getType() == Material.ARROW) {
            currentPage--;
            refresh(staff);
        } else if (slot == 53 && inv.getItem(53).getType() == Material.ARROW) {
            currentPage++;
            refresh(staff);
        } else if (slot == 47) {
            GUINavigationStack.pop(staff);
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private List<OfflinePlayer> getPlayersWithStats() {
        List<OfflinePlayer> players = new ArrayList<>();
        for (UUID playerId : StatsManager.getAllPlayerIds()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            if (offlinePlayer != null && offlinePlayer.getName() != null) {
                players.add(offlinePlayer);
            }
        }
        return players;
    }

    private void setNavigationItems(int totalPages) {
        if (currentPage > 0) {
            inv.setItem(45, createNavItem(Material.ARROW, "Previous Page", NamedTextColor.GREEN));
        } else {
            inv.setItem(45, createNavItem(Material.BARRIER, "No Previous Page", NamedTextColor.RED));
        }

        inv.setItem(47, createNavItem(Material.ARROW, "Back", NamedTextColor.RED));
        inv.setItem(49, createNavItem(Material.PAPER, "Page " + (currentPage + 1) + " / " + totalPages, NamedTextColor.YELLOW));

        if (currentPage < totalPages - 1) {
            inv.setItem(53, createNavItem(Material.ARROW, "Next Page", NamedTextColor.GREEN));
        } else {
            inv.setItem(53, createNavItem(Material.BARRIER, "No Next Page", NamedTextColor.RED));
        }
    }

    private ItemStack createNavItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(color));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName() != null ? player.getName() : "Unknown").color(NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to view mining stats").color(NamedTextColor.GRAY));
        boolean isOnline = player.isOnline();
        lore.add(Component.text("Status: " + (isOnline ? "Online" : "Offline")).color(isOnline ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }
}
