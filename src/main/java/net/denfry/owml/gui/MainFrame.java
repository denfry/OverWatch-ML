package net.denfry.owml.gui;

import java.time.Duration;
import java.util.ArrayList;
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
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.subgui.AppealGUI;
import net.denfry.owml.gui.subgui.ConfigSettingsGUI;
import net.denfry.owml.gui.subgui.MLAnalysisGUI;
import net.denfry.owml.gui.subgui.OreConfigGUI;
import net.denfry.owml.gui.subgui.PlayerStatsMainGUI;
import net.denfry.owml.gui.subgui.PunishmentSettingsGUI;
import net.denfry.owml.gui.subgui.SuspiciousPlayersGUI;
import net.denfry.owml.gui.subgui.WebhookSettingsGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * РћСЃРЅРѕРІРЅРѕР№ С„СЂРµР№Рј GUI РґР»СЏ OverWatch-ML
 * РЎРѕРІРјРµСЃС‚РёРј СЃ Java 1.21+ Рё Minecraft 1.21+
 */
public class MainFrame {

    private final OverWatchML plugin;
    private final ConfigManager configManager;

    // Cache for ItemStacks to optimize performance
    private final Cache<String, ItemStack> itemCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    // Click frequency limit to prevent spam
    private static final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200; // 200ms between clicks

    // GUI Constants
    public static final String PERMISSION = "owml.staff";
    public static final int INVENTORY_SIZE = 54;
    public static final String INVENTORY_TITLE = "рџ”§ OverWatchML Control Panel";

    // Element slots
    private static final int STATUS_SLOT = 4;
    private static final int PLAYER_STATS_SLOT = 19;
    private static final int SUSPICIOUS_PLAYERS_SLOT = 21;
    private static final int PUNISHMENT_SYSTEM_SLOT = 23;
    private static final int PLUGIN_CONFIG_SLOT = 25;
    private static final int ORE_MANAGEMENT_SLOT = 28;
    private static final int DISCORD_WEBHOOK_SLOT = 30;
    private static final int APPEAL_SYSTEM_SLOT = 32;
    private static final int ML_ANALYSIS_SLOT = 34;
    private static final int ALERTS_TOGGLE_SLOT = 47;
    private static final int CLOSE_SLOT = 49;

    public MainFrame(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * РћС‚РєСЂС‹РІР°РµС‚ РѕСЃРЅРѕРІРЅРѕР№ GUI РґР»СЏ РёРіСЂРѕРєР°
     * @param player РёРіСЂРѕРє
     */
    public void openMainFrame(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("You don't have permission to access the OverWatchML control panel.")
                .color(NamedTextColor.RED));
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
            Component.text(INVENTORY_TITLE).color(NamedTextColor.DARK_AQUA));

        fillBackground(inventory);
        addStatusIndicator(inventory);
        addMainButtons(inventory);
        addUtilityButtons(inventory, player);

        player.openInventory(inventory);
    }

    /**
     * Р—Р°РїРѕР»РЅСЏРµС‚ С„РѕРЅ РёРЅРІРµРЅС‚Р°СЂСЏ
     */
    private void fillBackground(Inventory inventory) {
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    /**
     * Р”РѕР±Р°РІР»СЏРµС‚ РёРЅРґРёРєР°С‚РѕСЂ СЃС‚Р°С‚СѓСЃР° СЃРёСЃС‚РµРјС‹
     */
    private void addStatusIndicator(Inventory inventory) {
        boolean isEnabled = plugin.isEnabled();
        Material material = isEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String statusText = isEnabled ? "System Online" : "System Offline";
        NamedTextColor color = isEnabled ? NamedTextColor.GREEN : NamedTextColor.RED;

        List<String> lore = new ArrayList<>();
        lore.add("В§7Plugin Status: " + (isEnabled ? "В§aActive" : "В§cInactive"));
        lore.add("В§7Version: В§e" + plugin.getDescription().getVersion());
        lore.add("");
        lore.add("В§7Click to refresh status");

        inventory.setItem(STATUS_SLOT, createItem(material, "В§l" + statusText, lore));
    }

    /**
     * Р”РѕР±Р°РІР»СЏРµС‚ РѕСЃРЅРѕРІРЅС‹Рµ РєРЅРѕРїРєРё С„СѓРЅРєС†РёРѕРЅР°Р»Р°
     */
    private void addMainButtons(Inventory inventory) {
        inventory.setItem(PLAYER_STATS_SLOT, createItem(Material.PLAYER_HEAD,
            "В§bрџ“Љ Player Statistics",
            List.of("В§eView detailed mining statistics", "В§eTrack player behavior patterns", "", "В§7Click to open player stats")));

        inventory.setItem(SUSPICIOUS_PLAYERS_SLOT, createItem(Material.DETECTOR_RAIL,
            "В§cрџљЁ Suspicious Players",
            List.of("В§eMonitor players flagged by the system", "В§eView suspicion levels and reasons", "", "В§7Click to view suspicious players")));

        inventory.setItem(PUNISHMENT_SYSTEM_SLOT, createItem(Material.ANVIL,
            "В§4вљ– Punishment System",
            List.of("В§eConfigure automatic punishments", "В§eSet up paranoia effects", "", "В§7Click to manage punishments")));

        inventory.setItem(PLUGIN_CONFIG_SLOT, createItem(Material.COMMAND_BLOCK,
            "В§6вљ™ Plugin Configuration",
            List.of("В§eModify plugin settings", "В§eAdjust detection parameters", "", "В§7Click to configure plugin")));

        inventory.setItem(ORE_MANAGEMENT_SLOT, createItem(Material.DIAMOND_ORE,
            "В§eв›Џ Ore Management",
            List.of("В§eConfigure ore detection", "В§eSet up decoy blocks", "", "В§7Click to manage ores")));

        inventory.setItem(DISCORD_WEBHOOK_SLOT, createItem(Material.BELL,
            "В§9рџ“ў Discord Webhooks",
            List.of("В§eConfigure Discord notifications", "В§eSet up alert channels", "", "В§7Click to configure webhooks")));

        inventory.setItem(APPEAL_SYSTEM_SLOT, createItem(Material.WRITABLE_BOOK,
            "В§aрџ“‹ Appeal System",
            List.of("В§eManage player appeals", "В§eReview punishment cases", "", "В§7Click to handle appeals")));

        inventory.setItem(ML_ANALYSIS_SLOT, createItem(Material.BRAIN_CORAL_BLOCK,
            "В§dрџ¤– ML Analysis",
            List.of("В§eMachine learning tools", "В§eTrain detection models", "", "В§7Click for ML management")));
    }

    /**
     * Р”РѕР±Р°РІР»СЏРµС‚ РІСЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Рµ РєРЅРѕРїРєРё
     */
    private void addUtilityButtons(Inventory inventory, Player player) {
        // Alerts toggle
        boolean alertsDisabled = plugin.getStaffAlertManager().areAlertsDisabled(player);
        Material alertMaterial = alertsDisabled ? Material.BARRIER : Material.BELL;
        String alertStatus = alertsDisabled ? "В§cAlerts Disabled" : "В§aAlerts Enabled";
        NamedTextColor alertColor = alertsDisabled ? NamedTextColor.RED : NamedTextColor.GREEN;

        inventory.setItem(ALERTS_TOGGLE_SLOT, createItem(alertMaterial,
            alertStatus,
            List.of("В§7Current: " + (alertsDisabled ? "В§cDisabled" : "В§aEnabled"),
                   "В§7Click to toggle ore alerts")));

        // Close button
        inventory.setItem(CLOSE_SLOT, createItem(Material.BARRIER,
            "В§cвњ• Close Menu",
            List.of("В§7Click to close this menu")));
    }

    /**
     * РЎРѕР·РґР°РµС‚ ItemStack СЃ Р·Р°РґР°РЅРЅС‹РјРё РїР°СЂР°РјРµС‚СЂР°РјРё
     */
    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, List.of());
    }

    /**
     * РЎРѕР·РґР°РµС‚ ItemStack СЃ РёРјРµРЅРµРј Рё РѕРїРёСЃР°РЅРёРµРј (СЃ РєРµС€РёСЂРѕРІР°РЅРёРµРј)
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
     * РћР±СЂР°Р±Р°С‚С‹РІР°РµС‚ РєР»РёРєРё РІ РіР»Р°РІРЅРѕРј РјРµРЅСЋ
     * @param player РёРіСЂРѕРє
     * @param slot СЃР»РѕС‚
     * @return true РµСЃР»Рё РєР»РёРє РѕР±СЂР°Р±РѕС‚Р°РЅ
     */
    public static boolean handleMainFrameClick(Player player, int slot, OverWatchML plugin) {
        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(player.getUniqueId());

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN_MS) {
            return true; // Ignore click but return true to prevent further processing
        }

        lastClickTime.put(player.getUniqueId(), currentTime);

        // Periodically clean old click records (every minute)
        if (lastClickTime.size() > 50) { // If many players online
            long cutoffTime = currentTime - 60000; // 1 minute
            lastClickTime.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
        }

        switch (slot) {
            case STATUS_SLOT:
                // Refresh status - simply reopen menu
                new MainFrame(plugin, plugin.getConfigManager()).openMainFrame(player);
                break;

            case PLAYER_STATS_SLOT:
                new PlayerStatsMainGUI(0).openInventory(player);
                break;

            case SUSPICIOUS_PLAYERS_SLOT:
                new SuspiciousPlayersGUI(0).openInventory(player);
                break;

            case PUNISHMENT_SYSTEM_SLOT:
                new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                break;

            case PLUGIN_CONFIG_SLOT:
                new ConfigSettingsGUI(plugin.getConfigManager(), plugin).openInventory(player);
                break;

            case ORE_MANAGEMENT_SLOT:
                new OreConfigGUI(plugin.getConfigManager()).openInventory(player);
                break;

            case DISCORD_WEBHOOK_SLOT:
                new WebhookSettingsGUI(plugin.getConfigManager(), plugin).openInventory(player);
                break;

            case APPEAL_SYSTEM_SLOT:
                new AppealGUI(plugin, plugin.getAppealManager(), 0).openInventory(player);
                break;

            case ML_ANALYSIS_SLOT:
                new MLAnalysisGUI(plugin).openInventory(player);
                break;

            case ALERTS_TOGGLE_SLOT:
                boolean alertsDisabled = plugin.getStaffAlertManager().toggleOreAlert(player);
                player.sendMessage(Component.text("Ore alerts " + (alertsDisabled ? "disabled" : "enabled"))
                    .color(alertsDisabled ? NamedTextColor.RED : NamedTextColor.GREEN));
                // Reopen menu to update status
                new MainFrame(plugin, plugin.getConfigManager()).openMainFrame(player);
                break;

            case CLOSE_SLOT:
                player.closeInventory();
                break;

            default:
                return false; // Click not handled
        }
        return true; // Click handled
    }
}
