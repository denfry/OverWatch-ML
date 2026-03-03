package net.denfry.owml.punishments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.SchedulerUtils;

/**
 * Composite punishment engine that combines multiple punishment types
 * for more effective and varied consequences.
 *
 * Features:
 * - Punishment combinations
 * - Dynamic effect stacking
 * - Punishment chains (sequential effects)
 * - Adaptive punishment scaling
 * - Effect conflict resolution
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class CompositePunishmentEngine {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Active composite punishments
    private final Map<UUID, CompositePunishment> activeComposites = new ConcurrentHashMap<>();

    // Punishment templates
    private final Map<String, PunishmentTemplate> templates = new ConcurrentHashMap<>();

    // Adaptive punishment engine
    private final AdaptivePunishmentEngine adaptiveEngine = new AdaptivePunishmentEngine();

    public CompositePunishmentEngine() {
        initializeTemplates();

        // Cleanup task will be started later to avoid scheduler issues during initialization

        MessageManager.log("info", "Composite Punishment Engine initialized with {TEMPLATES} templates",
            "TEMPLATES", String.valueOf(templates.size()));
    }

    /**
     * Initialize punishment templates
     */
    private void initializeTemplates() {
        // Light combination: Warning + minor debuff
        templates.put("light_combo", new PunishmentTemplate(
            "Light Combination",
            Arrays.asList(
                new PunishmentComponent(PunishmentType.MESSAGE, 1, 30000), // 30 seconds
                new PunishmentComponent(PunishmentType.MINING_DEBUFF, 1, 60000) // 1 minute
            ),
            PunishmentChain.SEQUENTIAL
        ));

        // Medium combination: Multiple debuffs + effects
        templates.put("medium_combo", new PunishmentTemplate(
            "Medium Combination",
            Arrays.asList(
                new PunishmentComponent(PunishmentType.MINING_DEBUFF, 2, 180000), // 3 minutes
                new PunishmentComponent(PunishmentType.INVENTORY_RESTRICTION, 1, 120000), // 2 minutes
                new PunishmentComponent(PunishmentType.MESSAGE, 2, 30000) // 30 seconds
            ),
            PunishmentChain.PARALLEL
        ));

        // Heavy combination: Severe effects
        templates.put("heavy_combo", new PunishmentTemplate(
            "Heavy Combination",
            Arrays.asList(
                new PunishmentComponent(PunishmentType.MINING_DEBUFF, 3, 300000), // 5 minutes
                new PunishmentComponent(PunishmentType.INVENTORY_RESTRICTION, 2, 240000), // 4 minutes
                new PunishmentComponent(PunishmentType.MOVEMENT_DEBUFF, 2, 180000), // 3 minutes
                new PunishmentComponent(PunishmentType.PARANOIA_EFFECT, 1, 120000) // 2 minutes
            ),
            PunishmentChain.CASCADE
        ));

        // Escalation template: Progressive worsening
        templates.put("escalation", new PunishmentTemplate(
            "Progressive Escalation",
            Arrays.asList(
                new PunishmentComponent(PunishmentType.MESSAGE, 1, 20000), // 20s
                new PunishmentComponent(PunishmentType.MINING_DEBUFF, 1, 60000), // +40s
                new PunishmentComponent(PunishmentType.INVENTORY_RESTRICTION, 1, 80000), // +20s
                new PunishmentComponent(PunishmentType.MOVEMENT_DEBUFF, 1, 100000), // +20s
                new PunishmentComponent(PunishmentType.PARANOIA_EFFECT, 1, 120000) // +20s
            ),
            PunishmentChain.CASCADE
        ));

        // Random mix template
        templates.put("random_mix", new PunishmentTemplate(
            "Random Mix",
            Arrays.asList(
                new PunishmentComponent(PunishmentType.random(), 1, 90000),
                new PunishmentComponent(PunishmentType.random(), 2, 120000),
                new PunishmentComponent(PunishmentType.random(), 1, 60000)
            ),
            PunishmentChain.SEQUENTIAL
        ));
    }

    /**
     * Apply adaptive punishment based on player behavior and history
     *
     * @param playerId        The UUID of the player to punish
     * @param offenseType     The type of offense committed
     * @param offenseSeverity The severity of the offense (0.0-1.0)
     * @param context         Additional context information
     * @param reason          The reason for the punishment
     */
    public void applyAdaptivePunishment(@NotNull UUID playerId, @NotNull String offenseType,
                                       double offenseSeverity, @NotNull Map<String, Object> context,
                                       @NotNull String reason) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Get adaptive punishment recommendation
        AdaptivePunishmentEngine.PunishmentRecommendation recommendation =
            adaptiveEngine.recommendPunishment(playerId, offenseType, offenseSeverity, context);

        // Convert recommendation to template name
        String templateName = mapRecommendationToTemplate(recommendation);

        // Apply the recommended punishment
        applyCompositePunishment(playerId, templateName, reason + " (Adaptive: " + recommendation.getReasoning() + ")");

        // Schedule outcome tracking
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            trackPunishmentOutcome(playerId, recommendation);
        }, 20 * 60 * 5); // Check after 5 minutes
    }

    /**
     * Map adaptive recommendation to punishment template
     */
    private String mapRecommendationToTemplate(AdaptivePunishmentEngine.PunishmentRecommendation recommendation) {
        String punishmentType = recommendation.punishmentType;
        double intensity = recommendation.intensity;

        // Map punishment types to templates based on intensity
        if (intensity < 0.5) {
            switch (punishmentType) {
                case "warning": return "light";
                case "kick": return "light";
                case "mining_debuff": return "light";
                default: return "light";
            }
        } else if (intensity < 0.8) {
            switch (punishmentType) {
                case "temp_ban_1h": return "medium";
                case "inventory_lock": return "medium";
                case "speed_reduction": return "medium";
                default: return "medium";
            }
        } else if (intensity < 1.2) {
            switch (punishmentType) {
                case "temp_ban_24h": return "heavy";
                case "fake_ore_veins": return "heavy";
                case "paranoia_effects": return "heavy";
                default: return "heavy";
            }
        } else {
            switch (punishmentType) {
                case "temp_ban_7d": return "severe";
                case "perm_ban": return "severe";
                case "cursed_pickaxe": return "severe";
                default: return "severe";
            }
        }
    }

    /**
     * Track punishment outcome for learning
     */
    private void trackPunishmentOutcome(UUID playerId, AdaptivePunishmentEngine.PunishmentRecommendation recommendation) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;

        // Simple outcome tracking: check if player is still online and not causing issues
        boolean wasEffective = player.isOnline() && !hasRecentDetections(playerId);

        Map<String, Object> outcomeContext = new HashMap<>();
        outcomeContext.put("player_online", player.isOnline());
        outcomeContext.put("time_since_punishment", 5 * 60); // 5 minutes
        outcomeContext.put("has_recent_detections", hasRecentDetections(playerId));

        // Record outcome for learning
        adaptiveEngine.recordPunishmentOutcome(playerId, recommendation.punishmentType,
            wasEffective, outcomeContext);
    }

    /**
     * Check if player has recent detections (simplified)
     */
    private boolean hasRecentDetections(UUID playerId) {
        // This would integrate with the detection system
        // For now, return false (assume punishment was effective)
        return false;
    }

    /**
     * Apply composite punishment using template
     */
    public void applyCompositePunishment(@NotNull UUID playerId, @NotNull String templateName, @NotNull String reason) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        PunishmentTemplate template = templates.get(templateName);
        if (template == null) {
            MessageManager.log("error", "Unknown punishment template: {TEMPLATE}", "TEMPLATE", templateName);
            return;
        }

        // Check for existing composite punishment
        CompositePunishment existing = activeComposites.get(playerId);
        if (existing != null && !existing.isExpired()) {
            // Merge with existing punishment
            mergePunishments(player, existing, template, reason);
            return;
        }

        // Create new composite punishment
        CompositePunishment composite = new CompositePunishment(
            playerId, template, reason, System.currentTimeMillis()
        );

        activeComposites.put(playerId, composite);

        // Apply punishment based on chain type
        applyPunishmentChain(player, composite);

        // Notify player and staff
        notifyCompositePunishment(player, template, reason);

        MessageManager.log("info", "Applied composite punishment '{TEMPLATE}' to {PLAYER}: {REASON}",
            "TEMPLATE", templateName, "PLAYER", player.getName(), "REASON", reason);
    }

    /**
     * Apply punishment based on chain type
     */
    private void applyPunishmentChain(Player player, CompositePunishment composite) {
        PunishmentTemplate template = composite.template;

        switch (template.chainType) {
            case SEQUENTIAL -> applySequentialChain(player, composite);
            case PARALLEL -> applyParallelChain(player, composite);
            case CASCADE -> applyCascadeChain(player, composite);
            case RANDOM -> applyRandomChain(player, composite);
        }
    }

    /**
     * Apply punishments sequentially (one after another)
     */
    private void applySequentialChain(Player player, CompositePunishment composite) {
        PunishmentTemplate template = composite.template;
        long currentDelay = 0;

        for (PunishmentComponent component : template.components) {
            SchedulerUtils.runTaskLater(plugin, () -> {
                applyPunishmentComponent(player, component);
            }, currentDelay / 50); // Convert to ticks

            currentDelay += component.duration;
        }

        composite.totalDuration = currentDelay;
    }

    /**
     * Apply punishments in parallel (all at once)
     */
    private void applyParallelChain(Player player, CompositePunishment composite) {
        PunishmentTemplate template = composite.template;
        long maxDuration = 0;

        for (PunishmentComponent component : template.components) {
            applyPunishmentComponent(player, component);
            maxDuration = Math.max(maxDuration, component.duration);
        }

        composite.totalDuration = maxDuration;
    }

    /**
     * Apply punishments in cascade (each adds to previous)
     */
    private void applyCascadeChain(Player player, CompositePunishment composite) {
        PunishmentTemplate template = composite.template;
        long cumulativeDelay = 0;
        long totalDuration = 0;

        for (PunishmentComponent component : template.components) {
            SchedulerUtils.runTaskLater(plugin, () -> {
                applyPunishmentComponent(player, component);
            }, cumulativeDelay / 50);

            cumulativeDelay += component.delay;
            totalDuration += component.duration;
        }

        composite.totalDuration = totalDuration;
    }

    /**
     * Apply punishments in random order with random delays
     */
    private void applyRandomChain(Player player, CompositePunishment composite) {
        PunishmentTemplate template = composite.template;
        List<PunishmentComponent> shuffled = new ArrayList<>(template.components);
        Collections.shuffle(shuffled);

        long maxDuration = 0;
        Random random = new Random();

        for (PunishmentComponent component : shuffled) {
            long delay = random.nextInt(10000); // Random delay up to 10 seconds
            SchedulerUtils.runTaskLater(plugin, () -> {
                applyPunishmentComponent(player, component);
            }, delay / 50);

            maxDuration = Math.max(maxDuration, delay + component.duration);
        }

        composite.totalDuration = maxDuration;
    }

    /**
     * Apply individual punishment component
     */
    private void applyPunishmentComponent(Player player, PunishmentComponent component) {
        switch (component.type) {
            case MESSAGE -> applyMessageEffect(player, component);
            case MINING_DEBUFF -> applyMiningDebuff(player, component);
            case INVENTORY_RESTRICTION -> applyInventoryRestriction(player, component);
            case MOVEMENT_DEBUFF -> applyMovementDebuff(player, component);
            case PARANOIA_EFFECT -> applyParanoiaEffect(player, component);
            case VISUAL_EFFECT -> applyVisualEffect(player, component);
            case SOUND_EFFECT -> applySoundEffect(player, component);
        }
    }

    /**
     * Merge new punishment with existing one
     */
    private void mergePunishments(Player player, CompositePunishment existing,
                                PunishmentTemplate newTemplate, String reason) {
        // Create merged template
        List<PunishmentComponent> mergedComponents = new ArrayList<>(existing.template.components);

        // Add new components (avoid duplicates)
        for (PunishmentComponent newComponent : newTemplate.components) {
            boolean exists = mergedComponents.stream()
                .anyMatch(component -> component.type == newComponent.type);

            if (!exists) {
                mergedComponents.add(newComponent);
            }
        }

        PunishmentTemplate mergedTemplate = new PunishmentTemplate(
            "Merged: " + existing.template.name + " + " + newTemplate.name,
            mergedComponents,
            PunishmentChain.PARALLEL // Merged punishments run in parallel
        );

        // Replace existing punishment
        CompositePunishment merged = new CompositePunishment(
            player.getUniqueId(), mergedTemplate, reason + " (merged)", System.currentTimeMillis()
        );

        activeComposites.put(player.getUniqueId(), merged);
        applyPunishmentChain(player, merged);

        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Merged punishment effects",
            "PLAYER", player.getName(),
            "LEVEL", "Enhanced");
    }

    /**
     * Notify about composite punishment
     */
    private void notifyCompositePunishment(Player player, PunishmentTemplate template, String reason) {
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", template.name,
            "PLAYER", player.getName(),
            "LEVEL", "Composite");

        MessageManager.broadcastToPermission("owml.staff", "punishment.applied",
            "PUNISHMENT", template.name + " (" + template.components.size() + " effects)",
            "PLAYER", player.getName(),
            "LEVEL", "Composite");
    }

    /**
     * Start cleanup task for expired punishments
     */
    public void startCompositeCleanupTask() {
        SchedulerUtils.runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            activeComposites.entrySet().removeIf(entry -> {
                CompositePunishment punishment = entry.getValue();
                if (punishment.isExpired(now)) {
                    // Remove effects from player
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player != null) {
                        removeCompositeEffects(player, punishment);
                    }
                    return true;
                }
                return false;
            });
        }, 1200L, 1200L); // Every minute
    }

    /**
     * Remove composite punishment effects
     */
    private void removeCompositeEffects(Player player, CompositePunishment punishment) {
        // Remove all effects from the punishment
        for (PunishmentComponent component : punishment.template.components) {
            removePunishmentComponent(player, component);
        }

        MessageManager.send(player, "punishment.removed", "PLAYER", player.getName());
    }

    // ===== EFFECT IMPLEMENTATIONS =====

    private void applyMessageEffect(Player player, PunishmentComponent component) {
        String message = "Р’В§eРІС™В  Punishment Effect: " + component.type.name() + " (Level " + component.intensity + ")";
        player.sendMessage(message);
    }

    private void applyMiningDebuff(Player player, PunishmentComponent component) {
        // Integration with existing mining debuff systems
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Mining Debuff (Level " + component.intensity + ")",
            "PLAYER", player.getName(),
            "LEVEL", "Composite");
    }

    private void applyInventoryRestriction(Player player, PunishmentComponent component) {
        // Integration with existing inventory systems
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Inventory Restriction (Level " + component.intensity + ")",
            "PLAYER", player.getName(),
            "LEVEL", "Composite");
    }

    private void applyMovementDebuff(Player player, PunishmentComponent component) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOWNESS, (int)(component.duration / 50), component.intensity - 1));
    }

    private void applyParanoiaEffect(Player player, PunishmentComponent component) {
        // Integration with ParanoiaHandler
        MessageManager.send(player, "punishment.applied",
            "PUNISHMENT", "Paranoia Effect (Level " + component.intensity + ")",
            "PLAYER", player.getName(),
            "LEVEL", "Composite");
    }

    private void applyVisualEffect(Player player, PunishmentComponent component) {
        // Apply visual distortion effects
        player.sendMessage("Р’В§cСЂСџвЂРѓ Visual effects applied!");
    }

    private void applySoundEffect(Player player, PunishmentComponent component) {
        // Play disturbing sounds
        player.sendMessage("Р’В§4СЂСџвЂќР‰ Audio effects applied!");
    }

    private void removePunishmentComponent(Player player, PunishmentComponent component) {
        switch (component.type) {
            case MOVEMENT_DEBUFF -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
            case MESSAGE, MINING_DEBUFF, INVENTORY_RESTRICTION, PARANOIA_EFFECT, VISUAL_EFFECT, SOUND_EFFECT -> {
                // These effects don't need explicit removal or are handled elsewhere
            }
        }
    }

    // ===== PUBLIC API =====

    /**
     * Check if player has active composite punishment
     */
    public boolean hasActiveCompositePunishment(UUID playerId) {
        CompositePunishment punishment = activeComposites.get(playerId);
        return punishment != null && !punishment.isExpired();
    }

    /**
     * Get active composite punishment for player
     */
    public CompositePunishment getActiveCompositePunishment(UUID playerId) {
        return activeComposites.get(playerId);
    }

    /**
     * Remove composite punishment from player
     */
    public void removeCompositePunishment(UUID playerId) {
        CompositePunishment punishment = activeComposites.remove(playerId);
        if (punishment != null) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                removeCompositeEffects(player, punishment);
            }
        }
    }

    /**
     * Get available punishment templates
     */
    public Set<String> getAvailableTemplates() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Get composite punishment statistics
     */
    public CompositeStats getStats() {
        int activeCount = (int) activeComposites.values().stream()
            .filter(p -> !p.isExpired())
            .count();

        return new CompositeStats(
            activeComposites.size(),
            activeCount,
            templates.size(),
            System.currentTimeMillis()
        );
    }

    // ===== DATA CLASSES =====

    /**
     * Active composite punishment
     */
    public static class CompositePunishment {
        public final UUID playerId;
        public final PunishmentTemplate template;
        public final String reason;
        public final long startTime;
        public long totalDuration;

        public CompositePunishment(UUID playerId, PunishmentTemplate template, String reason, long startTime) {
            this.playerId = playerId;
            this.template = template;
            this.reason = reason;
            this.startTime = startTime;
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long currentTime) {
            return (currentTime - startTime) >= totalDuration;
        }

        public long getRemainingTime() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.max(0, totalDuration - elapsed);
        }
    }

    /**
     * Punishment template definition
     */
    public static class PunishmentTemplate {
        public final String name;
        public final List<PunishmentComponent> components;
        public final PunishmentChain chainType;

        public PunishmentTemplate(String name, List<PunishmentComponent> components, PunishmentChain chainType) {
            this.name = name;
            this.components = components;
            this.chainType = chainType;
        }
    }

    /**
     * Individual punishment component
     */
    public static class PunishmentComponent {
        public final PunishmentType type;
        public final int intensity;
        public final long duration;
        public final long delay; // For cascade chains

        public PunishmentComponent(PunishmentType type, int intensity, long duration) {
            this(type, intensity, duration, 0);
        }

        public PunishmentComponent(PunishmentType type, int intensity, long duration, long delay) {
            this.type = type;
            this.intensity = intensity;
            this.duration = duration;
            this.delay = delay;
        }
    }

    /**
     * Punishment types
     */
    public enum PunishmentType {
        MESSAGE, MINING_DEBUFF, INVENTORY_RESTRICTION, MOVEMENT_DEBUFF,
        PARANOIA_EFFECT, VISUAL_EFFECT, SOUND_EFFECT;

        public static PunishmentType random() {
            return values()[new Random().nextInt(values().length)];
        }
    }

    /**
     * Punishment chain types
     */
    public enum PunishmentChain {
        SEQUENTIAL, // One after another
        PARALLEL,   // All at once
        CASCADE,    // Progressive addition
        RANDOM      // Random order and timing
    }

    /**
     * Composite punishment statistics
     */
    public static class CompositeStats {
        public final int totalPunishments;
        public final int activePunishments;
        public final int availableTemplates;
        public final long lastUpdate;

        public CompositeStats(int totalPunishments, int activePunishments, int availableTemplates, long lastUpdate) {
            this.totalPunishments = totalPunishments;
            this.activePunishments = activePunishments;
            this.availableTemplates = availableTemplates;
            this.lastUpdate = lastUpdate;
        }
    }

    /**
     * Get adaptive punishment engine statistics
     */
    public Map<String, Object> getAdaptiveStats() {
        return adaptiveEngine.getAdaptiveStats();
    }
}
