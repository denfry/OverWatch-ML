package net.denfry.owml.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.subgui.AlertHistoryGUI;
import net.denfry.owml.gui.subgui.OreCountersGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Панель управления алертами для staff
 * Совместим с Java 1.21+ и Minecraft 1.21+
 */
public class AlertPanel {

    private final OverWatchML plugin;
    private final StaffAlertManager alertManager;

    // Cache for ItemStacks to optimize performance
    private final Cache<String, ItemStack> itemCache = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();

    // Click frequency limit to prevent spam
    private static final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 250; // 250ms between clicks

    // GUI Constants
    public static final String PERMISSION = "owml.staff";
    public static final int INVENTORY_SIZE = 27;
    public static final String INVENTORY_TITLE = "🚨 Alert Management Panel";

    // Element slots
    private static final int GLOBAL_TOGGLE_SLOT = 10;
    private static final int STAFF_STATUS_SLOT = 12;
    private static final int ORE_COUNTERS_SLOT = 14;
    private static final int ALERT_HISTORY_SLOT = 16;
    private static final int BACK_SLOT = 22;

    public AlertPanel(OverWatchML plugin) {
        this.plugin = plugin;
        this.alertManager = plugin.getStaffAlertManager();
    }

    /**
     * Открывает панель алертов для игрока
     * @param player игрок
     */
    public void openAlertPanel(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("You don't have permission to access the alert panel.")
                .color(NamedTextColor.RED));
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
            Component.text(INVENTORY_TITLE).color(NamedTextColor.GOLD));

        fillBackground(inventory);
        addAlertControls(inventory, player);
        addStatusIndicators(inventory);
        addNavigationButtons(inventory);

        player.openInventory(inventory);
    }

    /**
     * Заполняет фон инвентаря
     */
    private void fillBackground(Inventory inventory) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    /**
     * Добавляет элементы управления алертами
     */
    private void addAlertControls(Inventory inventory, Player player) {
        // Alerts toggle for current player
        boolean alertsDisabled = alertManager.areAlertsDisabled(player);
        Material toggleMaterial = alertsDisabled ? Material.BARRIER : Material.BELL;
        String toggleName = alertsDisabled ? "§c❌ Alerts Disabled" : "§a✅ Alerts Enabled";
        NamedTextColor toggleColor = alertsDisabled ? NamedTextColor.RED : NamedTextColor.GREEN;

        List<String> toggleLore = Arrays.asList(
            "§7Your current alert status",
            "§7Click to " + (alertsDisabled ? "enable" : "disable") + " alerts",
            "",
            "§eAlerts include:",
            "§f• Ore mining detections",
            "§f• Suspicious player activities",
            "§f• System notifications"
        );

        inventory.setItem(GLOBAL_TOGGLE_SLOT, createItem(toggleMaterial, toggleName, toggleLore));
    }

    /**
     * Добавляет индикаторы статуса
     */
    private void addStatusIndicators(Inventory inventory) {
        // Online staff status
        int onlineStaff = countOnlineStaff();
        Material staffMaterial = onlineStaff > 0 ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        String staffName = "§b👥 Staff Status";
        List<String> staffLore = Arrays.asList(
            "§7Online staff with alerts enabled:",
            "§a" + onlineStaff + " §fstaff member(s)",
            "",
            "§7Staff with disabled alerts:",
            "§c" + alertManager.getDisabledAlertsCount() + " §fmember(s)"
        );

        inventory.setItem(STAFF_STATUS_SLOT, createItem(staffMaterial, staffName, staffLore));

        // Ore counters
        String counterName = "§e📊 Ore Counters";
        List<String> counterLore = Arrays.asList(
            "§7Active ore counters:",
            "§fTrack mining activity",
            "§fReal-time updates",
            "",
            "§7Click to view detailed counters"
        );

        inventory.setItem(ORE_COUNTERS_SLOT, createItem(Material.DIAMOND, counterName, counterLore));

        // Alert history
        String historyName = "§6📜 Alert History";
        List<String> historyLore = Arrays.asList(
            "§7Recent alert activity",
            "§fView past notifications",
            "§fTrack system events",
            "",
            "§7Click to view history"
        );

        inventory.setItem(ALERT_HISTORY_SLOT, createItem(Material.WRITABLE_BOOK, historyName, historyLore));
    }

    /**
     * Добавляет кнопки навигации
     */
    private void addNavigationButtons(Inventory inventory) {
        inventory.setItem(BACK_SLOT, createItem(Material.ARROW,
            "§7⬅ Back to Main Menu",
            Arrays.asList("§fReturn to main control panel")));
    }

    /**
     * Подсчитывает количество staff онлайн с включенными алертами
     */
    private int countOnlineStaff() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION) && !alertManager.areAlertsDisabled(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Создает ItemStack с заданными параметрами
     */
    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, Collections.emptyList());
    }

    /**
     * Создает ItemStack с именем и описанием (с кешированием)
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        // Create cache key
        String cacheKey = material.name() + "|" + name + "|" + lore.hashCode();

        // Check cache
        ItemStack cached = itemCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached.clone(); // Return cached copy
        }

        // Create new ItemStack
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name));
            if (!lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Component.text(line));
                }
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }

        // Store in cache
        itemCache.put(cacheKey, item.clone());

        return item;
    }

    /**
     * Обрабатывает клики в панели алертов
     * @param player игрок
     * @param slot слот
     * @return true если клик обработан
     */
    public static boolean handleAlertPanelClick(Player player, int slot, OverWatchML plugin) {
        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(player.getUniqueId());

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN_MS) {
            return true; // Ignore click but return true to prevent further processing
        }

        lastClickTime.put(player.getUniqueId(), currentTime);
        StaffAlertManager alertManager = plugin.getStaffAlertManager();

        switch (slot) {
            case GLOBAL_TOGGLE_SLOT:
                boolean alertsDisabled = alertManager.toggleOreAlert(player);
                player.sendMessage(Component.text("Ore alerts " + (alertsDisabled ? "disabled" : "enabled"))
                    .color(alertsDisabled ? NamedTextColor.RED : NamedTextColor.GREEN));
                // Reopen panel to update status
                new AlertPanel(plugin).openAlertPanel(player);
                break;

            case ORE_COUNTERS_SLOT:
                new OreCountersGUI(player, alertManager).openInventory(player);
                break;

            case ALERT_HISTORY_SLOT:
                new AlertHistoryGUI(alertManager).openInventory(player);
                break;

            case BACK_SLOT:
                new MainFrame(plugin, plugin.getConfigManager()).openMainFrame(player);
                break;

            default:
                return false; // Click not handled
        }
        return true; // Click handled
    }
}
