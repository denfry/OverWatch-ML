package net.denfry.owml.punishments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.SchedulerUtils;

/**
 * Advanced temporal punishment system with time-based effects and escalation.
 *
 * Features:
 * - Time-based punishment effects
 * - Automatic escalation/de-escalation
 * - Punishment stacking and combination
 * - Grace periods and warnings
 * - Punishment history tracking
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.2
 */
public class TemporalPunishmentSystem {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Active punishments tracking
    private final Map<UUID, List<ActivePunishment>> activePunishments = new ConcurrentHashMap<>();

    // Punishment history
    private final Map<UUID, PunishmentHistory> punishmentHistory = new ConcurrentHashMap<>();

    // Punishment escalation rules
    private final Map<Integer, PunishmentLevel> punishmentLevels = new HashMap<>();

    public TemporalPunishmentSystem() {
        initializePunishmentLevels();

        // Cleanup task will be started later to avoid scheduler issues during initialization

        MessageManager.log("info", "Temporal Punishment System initialized with {LEVELS} punishment levels",
            "LEVELS", String.valueOf(punishmentLevels.size()));
    }

    /**
     * Initialize punishment levels with escalating effects
     */
    private void initializePunishmentLevels() {
        // Level 1: Warning
        punishmentLevels.put(1, new PunishmentLevel(1, "Warning",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.MESSAGE, 0, "Р’В§eРІС™В  You have been warned for suspicious mining activity!")
            ),
            5 * 60 * 1000L // 5 minutes
        ));

        // Level 2: Mining Debuff
        punishmentLevels.put(2, new PunishmentLevel(2, "Mining Debuff",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.MINING_DEBUFF, 30, "Р’В§cРІвЂєРЏ Your mining speed has been reduced!")
            ),
            15 * 60 * 1000L // 15 minutes
        ));

        // Level 3: Inventory Restriction
        punishmentLevels.put(3, new PunishmentLevel(3, "Inventory Restriction",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.INVENTORY_LOCK, 60, "Р’В§cСЂСџвЂњВ¦ Your inventory has been temporarily locked!"),
                new PunishmentEffect(PunishmentEffect.Type.MINING_DEBUFF, 50, "Р’В§cРІвЂєРЏ Mining speed further reduced!")
            ),
            30 * 60 * 1000L // 30 minutes
        ));

        // Level 4: Movement Restriction
        punishmentLevels.put(4, new PunishmentLevel(4, "Movement Restriction",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.SLOWNESS, 120, "Р’В§cСЂСџС’РЉ You have been slowed down!"),
                new PunishmentEffect(PunishmentEffect.Type.INVENTORY_LOCK, 90, "Р’В§cСЂСџвЂњВ¦ Inventory remains locked!"),
                new PunishmentEffect(PunishmentEffect.Type.MINING_DEBUFF, 75, "Р’В§cРІвЂєРЏ Mining heavily debuffed!")
            ),
            60 * 60 * 1000L // 1 hour
        ));

        // Level 5: Paranoia Effects
        punishmentLevels.put(5, new PunishmentLevel(5, "Paranoia",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.PARANOIA, 180, "Р’В§4СЂСџвЂВ» Paranoia effects activated!"),
                new PunishmentEffect(PunishmentEffect.Type.SLOWNESS, 150, "Р’В§cСЂСџС’РЉ Movement severely restricted!"),
                new PunishmentEffect(PunishmentEffect.Type.INVENTORY_LOCK, 120, "Р’В§cСЂСџвЂњВ¦ Inventory locked!"),
                new PunishmentEffect(PunishmentEffect.Type.MINING_DEBUFF, 90, "Р’В§cРІвЂєРЏ Mining disabled!")
            ),
            120 * 60 * 1000L // 2 hours
        ));

        // Level 6: Severe Paranoia
        punishmentLevels.put(6, new PunishmentLevel(6, "Severe Paranoia",
            Arrays.asList(
                new PunishmentEffect(PunishmentEffect.Type.SEVERE_PARANOIA, 300, "Р’В§4СЂСџвЂ™Р‚ Severe paranoia activated!"),
                new PunishmentEffect(PunishmentEffect.Type.SLOWNESS, 200, "Р’В§cСЂСџС’РЉ Movement critically restricted!"),
                new PunishmentEffect(PunishmentEffect.Type.INVENTORY_LOCK, 180, "Р’В§cСЂСџвЂњВ¦ Inventory permanently locked!"),
                new PunishmentEffect(PunishmentEffect.Type.MINING_DEBUFF, 100, "Р’В§cРІвЂєРЏ Mining completely disabled!")
            ),
            240 * 60 * 1000L // 4 hours
        ));
    }

    /**
     * Apply punishment to a player with automatic escalation
     */
    public void applyPunishment(@NotNull UUID playerId, int level, @NotNull String reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        PunishmentHistory history = punishmentHistory.computeIfAbsent(playerId, k -> new PunishmentHistory(playerId));

        // Check for escalation based on recent punishments
        int effectiveLevel = calculateEffectiveLevel(playerId, level, history);

        PunishmentLevel punishmentLevel = punishmentLevels.get(effectiveLevel);
        if (punishmentLevel == null) {
            MessageManager.log("error", "Invalid punishment level: {LEVEL}", "LEVEL", String.valueOf(effectiveLevel));
            return;
        }

        // Create active punishment
        ActivePunishment activePunishment = new ActivePunishment(
            playerId, effectiveLevel, reason, System.currentTimeMillis(), punishmentLevel.duration
        );

        // Apply effects
        applyPunishmentEffects(player, punishmentLevel.effects);

        // Store active punishment
        activePunishments.computeIfAbsent(playerId, k -> new ArrayList<>()).add(activePunishment);

        // Update history
        history.addPunishment(effectiveLevel, reason, System.currentTimeMillis());

        // Schedule removal
        schedulePunishmentRemoval(playerId, activePunishment);

        // Notify player and staff
        notifyPunishment(player, punishmentLevel, reason, effectiveLevel);

        MessageManager.log("info", "Applied punishment level {LEVEL} to {PLAYER}: {REASON}",
            "LEVEL", String.valueOf(effectiveLevel), "PLAYER", player.getName(), "REASON", reason);
    }

    /**
     * Calculate effective punishment level based on history and escalation rules
     */
    private int calculateEffectiveLevel(UUID playerId, int baseLevel, PunishmentHistory history) {
        long now = System.currentTimeMillis();
        long recentWindow = 60 * 60 * 1000L; // 1 hour window

        // Count recent punishments
        int recentPunishments = history.getPunishmentsInTimeframe(now - recentWindow, now);

        // Escalation rules
        if (recentPunishments >= 5) {
            return Math.min(6, baseLevel + 2); // Major escalation
        } else if (recentPunishments >= 3) {
            return Math.min(6, baseLevel + 1); // Moderate escalation
        } else if (recentPunishments >= 1 && baseLevel == 1) {
            return Math.min(6, baseLevel + 1); // Quick escalation from warnings
        }

        return Math.min(6, Math.max(1, baseLevel));
    }

    /**
     * Apply punishment effects to player
     */
    private void applyPunishmentEffects(Player player, List<PunishmentEffect> effects) {
        for (PunishmentEffect effect : effects) {
            applySingleEffect(player, effect);
        }
    }

    /**
     * Apply a single punishment effect
     */
    private void applySingleEffect(Player player, PunishmentEffect effect) {
        UUID playerId = player.getUniqueId();

        switch (effect.type) {
            case MESSAGE -> {
                MessageManager.send(player, "punishment.applied",
                    "PUNISHMENT", effect.description,
                    "PLAYER", player.getName(),
                    "LEVEL", "Dynamic");
            }

            case MINING_DEBUFF -> {
                // Apply mining speed debuff (would integrate with existing systems)
                applyMiningDebuff(player, effect.intensity);
            }

            case INVENTORY_LOCK -> {
                // Lock inventory (would integrate with existing systems)
                applyInventoryLock(player, effect.intensity);
            }

            case SLOWNESS -> {
                // Apply slowness effect
                applySlownessEffect(player, effect.intensity);
            }

            case PARANOIA -> {
                // Apply paranoia effects (would integrate with existing ParanoiaHandler)
                applyParanoiaEffects(player, effect.intensity);
            }

            case SEVERE_PARANOIA -> {
                // Apply severe paranoia effects
                applySevereParanoiaEffects(player, effect.intensity);
            }
        }
    }

    /**
     * Schedule automatic punishment removal
     */
    private void schedulePunishmentRemoval(UUID playerId, ActivePunishment punishment) {
        long remainingTime = punishment.getRemainingTime();

        if (remainingTime > 0) {
            SchedulerUtils.runTaskLater(plugin, () -> {
                removePunishment(playerId, punishment);
            }, remainingTime / 50); // Convert to ticks
        }
    }

    /**
     * Remove a specific punishment
     */
    public void removePunishment(UUID playerId, ActivePunishment punishment) {
        List<ActivePunishment> playerPunishments = activePunishments.get(playerId);
        if (playerPunishments != null) {
            playerPunishments.remove(punishment);

            // Remove effects if no other punishments active
            if (playerPunishments.isEmpty()) {
                removeAllEffects(Bukkit.getPlayer(playerId));
            }
        }
    }

    /**
     * Remove all punishments from a player
     */
    public void removeAllPunishments(UUID playerId) {
        List<ActivePunishment> punishments = activePunishments.remove(playerId);
        if (punishments != null && !punishments.isEmpty()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeAllEffects(player);
                MessageManager.send(player, "punishment.removed", "PLAYER", player.getName());
            }
        }
    }

    /**
     * Notify player and staff about punishment
     */
    private void notifyPunishment(Player player, PunishmentLevel level, String reason, int effectiveLevel) {
        // Notify player
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", level.name,
            "PLAYER", player.getName(),
            "LEVEL", String.valueOf(effectiveLevel));

        // Notify staff
        MessageManager.broadcastToPermission("owml.staff", "punishment.applied",
            "PUNISHMENT", level.name,
            "PLAYER", player.getName(),
            "LEVEL", String.valueOf(effectiveLevel));

        // Show remaining time
        long durationMinutes = level.duration / (60 * 1000);
        MessageManager.send(player, "punishment.cooldown",
            "TIME", durationMinutes + " minutes");
    }

    /**
     * Start periodic cleanup task
     */
    public void startPunishmentCleanupTask() {
        SchedulerUtils.runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            // Clean up expired punishments
            for (Map.Entry<UUID, List<ActivePunishment>> entry : activePunishments.entrySet()) {
                UUID playerId = entry.getKey();
                List<ActivePunishment> punishments = entry.getValue();

                punishments.removeIf(punishment -> {
                    if (punishment.isExpired(now)) {
                        // Punishment expired
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            removeSingleEffect(player, punishment.level);
                        }
                        return true;
                    }
                    return false;
                });

                // Remove empty lists
                if (punishments.isEmpty()) {
                    activePunishments.remove(playerId);
                }
            }
        }, 1200L, 1200L); // Every minute
    }

    // ===== EFFECT IMPLEMENTATIONS =====
    // These would integrate with existing punishment handlers

    private void applyMiningDebuff(Player player, int intensity) {
        // Integration with existing mining debuff systems
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Mining Debuff (" + intensity + "%)",
            "PLAYER", player.getName(),
            "LEVEL", "Dynamic");
    }

    private void applyInventoryLock(Player player, int intensity) {
        // Integration with existing inventory lock systems
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Inventory Lock (" + intensity + " seconds)",
            "PLAYER", player.getName(),
            "LEVEL", "Dynamic");
    }

    private void applySlownessEffect(Player player, int intensity) {
        // Apply potion effect
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOWNESS, intensity * 20, 2));
    }

    private void applyParanoiaEffects(Player player, int intensity) {
        // Integration with existing ParanoiaHandler
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Paranoia Effects",
            "PLAYER", player.getName(),
            "LEVEL", "Dynamic");
    }

    private void applySevereParanoiaEffects(Player player, int intensity) {
        // Integration with existing severe paranoia systems
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Severe Paranoia Effects",
            "PLAYER", player.getName(),
            "LEVEL", "Dynamic");
    }

    private void removeAllEffects(Player player) {
        if (player != null) {
            // Remove all potion effects related to punishments
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            // Remove other effects...
        }
    }

    private void removeSingleEffect(Player player, int level) {
        // Remove effects for specific level
        PunishmentLevel punishmentLevel = punishmentLevels.get(level);
        if (punishmentLevel != null) {
            // Reverse effects (simplified)
            if (player != null) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
        }
    }

    // ===== PUBLIC API =====

    /**
     * Check if player has active punishments
     */
    public boolean hasActivePunishments(UUID playerId) {
        List<ActivePunishment> punishments = activePunishments.get(playerId);
        return punishments != null && !punishments.isEmpty();
    }

    /**
     * Get active punishments for player
     */
    public List<ActivePunishment> getActivePunishments(UUID playerId) {
        return new ArrayList<>(activePunishments.getOrDefault(playerId, Collections.emptyList()));
    }

    /**
     * Get punishment history for player
     */
    public PunishmentHistory getPunishmentHistory(UUID playerId) {
        return punishmentHistory.get(playerId);
    }

    /**
     * Get punishment statistics
     */
    public PunishmentStats getStats() {
        int totalActive = activePunishments.values().stream()
            .mapToInt(List::size)
            .sum();

        int totalHistorical = punishmentHistory.values().stream()
            .mapToInt(history -> history.getTotalPunishments())
            .sum();

        return new PunishmentStats(totalActive, totalHistorical, activePunishments.size());
    }

    // ===== DATA CLASSES =====

    /**
     * Active punishment instance
     */
    public static class ActivePunishment {
        public final UUID playerId;
        public final int level;
        public final String reason;
        public final long startTime;
        public final long duration;

        public ActivePunishment(UUID playerId, int level, String reason, long startTime, long duration) {
            this.playerId = playerId;
            this.level = level;
            this.reason = reason;
            this.startTime = startTime;
            this.duration = duration;
        }

        public boolean isExpired(long currentTime) {
            return (currentTime - startTime) >= duration;
        }

        public long getRemainingTime() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.max(0, duration - elapsed);
        }
    }

    /**
     * Punishment level configuration
     */
    public static class PunishmentLevel {
        public final int level;
        public final String name;
        public final List<PunishmentEffect> effects;
        public final long duration;

        public PunishmentLevel(int level, String name, List<PunishmentEffect> effects, long duration) {
            this.level = level;
            this.name = name;
            this.effects = effects;
            this.duration = duration;
        }
    }

    /**
     * Individual punishment effect
     */
    public static class PunishmentEffect {
        public enum Type {
            MESSAGE, MINING_DEBUFF, INVENTORY_LOCK, SLOWNESS, PARANOIA, SEVERE_PARANOIA
        }

        public final Type type;
        public final int intensity;
        public final String description;

        public PunishmentEffect(Type type, int intensity, String description) {
            this.type = type;
            this.intensity = intensity;
            this.description = description;
        }
    }

    /**
     * Punishment history tracking
     */
    public static class PunishmentHistory {
        private final UUID playerId;
        private final List<PunishmentRecord> records = new ArrayList<>();

        public PunishmentHistory(UUID playerId) {
            this.playerId = playerId;
        }

        public void addPunishment(int level, String reason, long timestamp) {
            records.add(new PunishmentRecord(level, reason, timestamp));
        }

        public int getTotalPunishments() {
            return records.size();
        }

        public int getPunishmentsInTimeframe(long startTime, long endTime) {
            return (int) records.stream()
                .filter(record -> record.timestamp >= startTime && record.timestamp <= endTime)
                .count();
        }

        public List<PunishmentRecord> getRecentPunishments(int count) {
            int start = Math.max(0, records.size() - count);
            return new ArrayList<>(records.subList(start, records.size()));
        }
    }

    /**
     * Individual punishment record
     */
    public static class PunishmentRecord {
        public final int level;
        public final String reason;
        public final long timestamp;

        public PunishmentRecord(int level, String reason, long timestamp) {
            this.level = level;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }

    /**
     * Punishment statistics
     */
    public static class PunishmentStats {
        public final int totalActive;
        public final int totalHistorical;
        public final int affectedPlayers;

        public PunishmentStats(int totalActive, int totalHistorical, int affectedPlayers) {
            this.totalActive = totalActive;
            this.totalHistorical = totalHistorical;
            this.affectedPlayers = affectedPlayers;
        }
    }
}
