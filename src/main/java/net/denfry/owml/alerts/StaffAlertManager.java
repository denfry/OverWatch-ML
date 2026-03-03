package net.denfry.owml.alerts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.denfry.owml.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Управляет алертами для staff и счетчиками руды
 * Совместим с Java 1.21+
 */
public class StaffAlertManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    // Store disabled alerts for players
    private final Set<UUID> disabledAlerts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Ore counters for players (player -> ore -> count)
    private final Map<UUID, Map<Material, OreCounter>> playerOreCounters = new ConcurrentHashMap<>();

    // Alert history storage
    public static class AlertRecord {
        private final String message;
        private final Location location;
        private final String playerName;
        private final long timestamp;

        public AlertRecord(String message, Location location, String playerName) {
            this.message = message;
            this.location = location;
            this.playerName = playerName;
            this.timestamp = System.currentTimeMillis();
        }

        public String getMessage() { return message; }
        public Location getLocation() { return location; }
        public String getPlayerName() { return playerName; }
        public long getTimestamp() { return timestamp; }
    }

    private final List<AlertRecord> alertHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 100;

    public StaffAlertManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Переключает алерты для игрока
     * @param player игрок
     * @return true если алерты отключены, false если включены
     */
    public boolean toggleOreAlert(Player player) {
        UUID playerId = player.getUniqueId();
        if (disabledAlerts.contains(playerId)) {
            disabledAlerts.remove(playerId);
            return false; // alerts enabled
        } else {
            disabledAlerts.add(playerId);
            return true; // alerts disabled
        }
    }

    /**
     * Проверяет, отключены ли алерты для игрока
     * @param player игрок
     * @return true если алерты отключены
     */
    public boolean areAlertsDisabled(Player player) {
        return disabledAlerts.contains(player.getUniqueId());
    }

    /**
     * Отправляет алерт staff с возможностью телепортации
     * @param player игрок, вызвавший алерт
     * @param location локация события
     * @param message сообщение алерта
     */
    public void alertStaffWithTeleport(Player player, Location location, String message) {
        // Store alert in history
        alertHistory.add(new AlertRecord(message, location.clone(), player.getName()));
        if (alertHistory.size() > MAX_HISTORY_SIZE) {
            alertHistory.remove(0); // Remove oldest
        }

        // Create clickable message for teleportation
        Component alertMessage = Component.text()
            .append(Component.text("[OverWatch-ML] ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(message, NamedTextColor.YELLOW))
            .append(Component.text(" [TELEPORT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tp " + player.getName() + " " +
                    location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to location", NamedTextColor.GRAY))))
            .build();

        // Send to all players with staff permissions
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("owml.staff") && !areAlertsDisabled(staff)) {
                staff.sendMessage(alertMessage);
            }
        }

        // Log to console
        plugin.getLogger().info("[ALERT] " + message + " at " +
            location.getWorld().getName() + " " + location.getBlockX() + "," +
            location.getBlockY() + "," + location.getBlockZ());
    }

    /**
     * Логирует событие с приманкой
     * @param message сообщение для логирования
     */
    public void logDecoyEvent(String message) {
        plugin.getLogger().info("[DECOY] " + message);
    }

    /**
     * Обновляет счетчик руды для игрока
     * @param player игрок
     * @param ore тип руды
     * @param location локация
     */
    public void updateStaffOreCounter(Player player, Material ore, Location location) {
        UUID playerId = player.getUniqueId();
        Map<Material, OreCounter> oreCounters = playerOreCounters.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        OreCounter counter = oreCounters.computeIfAbsent(ore, k -> new OreCounter());
        counter.increment();

        // Send counter update to staff players
        Component counterMessage = Component.text()
            .append(Component.text("[COUNTER] ", NamedTextColor.AQUA))
            .append(Component.text(player.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" mined ", NamedTextColor.GRAY))
            .append(Component.text(counter.getCount() + "x ", NamedTextColor.GREEN))
            .append(Component.text(formatMaterialName(ore), NamedTextColor.WHITE))
            .build();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("owml.staff") && !areAlertsDisabled(staff)) {
                staff.sendMessage(counterMessage);
            }
        }
    }

    /**
     * Получает счетчик руды для игрока
     * @param playerId UUID игрока
     * @param ore тип руды
     * @return счетчик или 0 если не найдено
     */
    public int getOreCount(UUID playerId, Material ore) {
        Map<Material, OreCounter> oreCounters = playerOreCounters.get(playerId);
        if (oreCounters == null) return 0;

        OreCounter counter = oreCounters.get(ore);
        return counter != null ? counter.getCount() : 0;
    }

    /**
     * Получает все счетчики руды для игрока
     * @param playerId UUID игрока
     * @return карта руда -> счетчик или пустая карта если нет данных
     */
    public Map<Material, OreCounter> getAllOreCounters(UUID playerId) {
        return playerOreCounters.getOrDefault(playerId, Collections.emptyMap());
    }

    /**
     * Очищает счетчики для игрока
     * @param playerId UUID игрока
     */
    public void clearOreCounters(UUID playerId) {
        playerOreCounters.remove(playerId);
    }

    /**
     * Получает историю алертов
     * @return список последних алертов (от новых к старым)
     */
    public List<AlertRecord> getAlertHistory() {
        synchronized (alertHistory) {
            return new ArrayList<>(alertHistory);
        }
    }

    /**
     * Очищает историю алертов
     */
    public void clearAlertHistory() {
        alertHistory.clear();
    }

    /**
     * Очищает все счетчики (для обслуживания)
     */
    public void clearAllOreCounters() {
        playerOreCounters.clear();
    }

    /**
     * Получает количество игроков с отключенными алертами
     * @return количество игроков
     */
    public int getDisabledAlertsCount() {
        return disabledAlerts.size();
    }

    /**
     * Форматирует имя материала для отображения
     * @param material материал
     * @return отформатированное имя
     */
    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    /**
     * Внутренний класс для подсчета руды
     */
    public static class OreCounter {
        private int count = 0;

        public void increment() {
            count++;
        }

        public int getCount() {
            return count;
        }

        public void reset() {
            count = 0;
        }
    }
}
