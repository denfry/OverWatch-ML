package net.denfry.owml.punishments;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive punishment engine that learns from player behavior and punishment effectiveness.
 * Dynamically adjusts punishment severity and type based on historical data.
 *
 * Features:
 * - Learning from punishment outcomes
 * - Player-specific adaptation
 * - Contextual punishment selection
 * - Effectiveness tracking
 * - Escalation/de-escalation logic
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.8
 */
public class AdaptivePunishmentEngine {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Punishment effectiveness tracking
    private final Map<UUID, PlayerPunishmentHistory> playerHistories = new ConcurrentHashMap<>();
    private final Map<String, PunishmentEffectiveness> punishmentStats = new ConcurrentHashMap<>();

    // Adaptive parameters
    private final Map<String, AdaptiveParameter> adaptiveParameters = new ConcurrentHashMap<>();

    // Learning parameters
    private static final double LEARNING_RATE = 0.1;
    private static final double FORGETTING_FACTOR = 0.95; // How much past performance matters
    private static final int MIN_HISTORY_SIZE = 3;
    private static final int MAX_HISTORY_SIZE = 50;

    // Statistics
    private final AtomicLong totalPunishments = new AtomicLong(0);
    private final AtomicLong successfulPunishments = new AtomicLong(0);

    public AdaptivePunishmentEngine() {
        initializeAdaptiveParameters();
        initializePunishmentStats();

        MessageManager.log("info", "Adaptive Punishment Engine initialized");
    }

    /**
     * Initialize adaptive parameters
     */
    private void initializeAdaptiveParameters() {
        // Escalation factors based on repeated offenses
        adaptiveParameters.put("escalation_factor", new AdaptiveParameter(1.0, 0.05, 0.5, 3.0));

        // Forgiveness factors for good behavior
        adaptiveParameters.put("forgiveness_factor", new AdaptiveParameter(0.8, 0.02, 0.1, 1.0));

        // Context sensitivity
        adaptiveParameters.put("context_sensitivity", new AdaptiveParameter(1.2, 0.03, 0.5, 2.0));

        // Player type adaptation (casual vs hardcore players)
        adaptiveParameters.put("player_type_factor", new AdaptiveParameter(1.0, 0.01, 0.3, 2.0));
    }

    /**
     * Initialize punishment effectiveness tracking
     */
    private void initializePunishmentStats() {
        // Common punishment types
        String[] punishmentTypes = {
            "warning", "kick", "temp_ban_1h", "temp_ban_24h", "temp_ban_7d",
            "perm_ban", "mining_debuff", "inventory_lock", "speed_reduction",
            "fake_ore_veins", "paranoia_effects", "cursed_pickaxe"
        };

        for (String type : punishmentTypes) {
            punishmentStats.put(type, new PunishmentEffectiveness(type));
        }
    }

    /**
     * Select optimal punishment for player based on their history and current offense
     */
    public PunishmentRecommendation recommendPunishment(UUID playerId, String offenseType,
                                                      double offenseSeverity, Map<String, Object> context) {
        totalPunishments.incrementAndGet();

        PlayerPunishmentHistory history = playerHistories.computeIfAbsent(playerId,
            k -> new PlayerPunishmentHistory(playerId));

        // Analyze player behavior patterns
        PlayerBehaviorProfile profile = analyzePlayerProfile(history, context);

        // Calculate base punishment severity
        double baseSeverity = calculateBaseSeverity(offenseType, offenseSeverity, profile);

        // Apply adaptive modifications
        double adaptedSeverity = applyAdaptiveModifications(baseSeverity, history, profile, context);

        // Select punishment type based on effectiveness and player response
        String punishmentType = selectOptimalPunishmentType(adaptedSeverity, history, profile);

        // Calculate duration/intensity
        double intensity = calculatePunishmentIntensity(adaptedSeverity, punishmentType, profile);

        return new PunishmentRecommendation(punishmentType, adaptedSeverity, intensity, profile, context);
    }

    /**
     * Record punishment outcome for learning
     */
    public void recordPunishmentOutcome(UUID playerId, String punishmentType, boolean wasEffective,
                                       Map<String, Object> outcomeContext) {
        PlayerPunishmentHistory history = playerHistories.computeIfAbsent(playerId,
            k -> new PlayerPunishmentHistory(playerId));

        // Record the punishment
        history.addPunishment(punishmentType, wasEffective, outcomeContext);

        // Update punishment effectiveness statistics
        PunishmentEffectiveness stats = punishmentStats.get(punishmentType);
        if (stats != null) {
            stats.recordOutcome(wasEffective);
        }

        // Update success counter
        if (wasEffective) {
            successfulPunishments.incrementAndGet();
        }

        // Learn from the outcome
        learnFromOutcome(playerId, punishmentType, wasEffective, outcomeContext);
    }

    /**
     * Analyze player punishment history and behavior
     */
    private PlayerBehaviorProfile analyzePlayerProfile(PlayerPunishmentHistory history,
                                                     Map<String, Object> context) {
        PlayerBehaviorProfile profile = new PlayerBehaviorProfile();

        // Analyze punishment response patterns
        List<PunishmentRecord> recentPunishments = history.getRecentPunishments(10);

        if (recentPunishments.size() >= MIN_HISTORY_SIZE) {
            // Calculate deterrence effectiveness
            double deterrenceScore = calculateDeterrenceScore(recentPunishments);
            profile.setDeterrenceScore(deterrenceScore);

            // Identify punishment resistance patterns
            double resistanceScore = calculateResistanceScore(recentPunishments);
            profile.setResistanceScore(resistanceScore);

            // Determine player type (casual vs persistent offender)
            PlayerType playerType = classifyPlayerType(recentPunishments, context);
            profile.setPlayerType(playerType);

            // Calculate recidivism probability
            double recidivismProb = calculateRecidivismProbability(recentPunishments);
            profile.setRecidivismProbability(recidivismProb);
        }

        // Analyze contextual factors
        analyzeContextFactors(profile, context);

        return profile;
    }

    /**
     * Calculate base punishment severity
     */
    private double calculateBaseSeverity(String offenseType, double offenseSeverity,
                                       PlayerBehaviorProfile profile) {
        // Base severity from offense type
        double baseSeverity = getOffenseBaseSeverity(offenseType) * offenseSeverity;

        // Adjust based on player type
        switch (profile.getPlayerType()) {
            case CASUAL:
                baseSeverity *= 0.7; // More lenient for casual players
                break;
            case PERSISTENT:
                baseSeverity *= 1.3; // Stricter for persistent offenders
                break;
            case FIRST_TIME:
                baseSeverity *= 0.5; // Very lenient for first offenses
                break;
        }

        return Math.max(0.0, Math.min(1.0, baseSeverity));
    }

    /**
     * Apply adaptive modifications based on history
     */
    private double applyAdaptiveModifications(double baseSeverity, PlayerPunishmentHistory history,
                                            PlayerBehaviorProfile profile, Map<String, Object> context) {
        double modifiedSeverity = baseSeverity;

        // Escalation for repeated offenses
        if (profile.getRecidivismProbability() > 0.7) {
            AdaptiveParameter escalation = adaptiveParameters.get("escalation_factor");
            modifiedSeverity *= escalation.getValue();
        }

        // De-escalation for good response to previous punishments
        if (profile.getDeterrenceScore() > 0.8) {
            AdaptiveParameter forgiveness = adaptiveParameters.get("forgiveness_factor");
            modifiedSeverity *= forgiveness.getValue();
        }

        // Context-based adjustments
        AdaptiveParameter contextSensitivity = adaptiveParameters.get("context_sensitivity");
        double contextFactor = calculateContextFactor(context);
        modifiedSeverity *= (1.0 + contextFactor * (contextSensitivity.getValue() - 1.0));

        return Math.max(0.0, Math.min(1.0, modifiedSeverity));
    }

    /**
     * Select optimal punishment type based on history and effectiveness
     */
    private String selectOptimalPunishmentType(double severity, PlayerPunishmentHistory history,
                                             PlayerBehaviorProfile profile) {
        List<String> candidateTypes = getCandidatePunishmentTypes(severity);

        if (candidateTypes.isEmpty()) {
            return "warning"; // Fallback
        }

        // Score each candidate based on predicted effectiveness
        String bestType = candidateTypes.get(0);
        double bestScore = 0.0;

        for (String type : candidateTypes) {
            double effectiveness = predictPunishmentEffectiveness(type, profile, history);
            double resistance = predictResistance(type, profile);

            // Combined score: effectiveness minus resistance
            double score = effectiveness - resistance * 0.3;

            if (score > bestScore) {
                bestScore = score;
                bestType = type;
            }
        }

        return bestType;
    }

    /**
     * Calculate punishment intensity/duration
     */
    private double calculatePunishmentIntensity(double severity, String punishmentType,
                                              PlayerBehaviorProfile profile) {
        double baseIntensity = severity;

        // Adjust based on player resistance
        if (profile.getResistanceScore() > 0.7) {
            baseIntensity *= 1.5; // Stronger punishment for resistant players
        }

        // Adjust based on punishment type effectiveness
        PunishmentEffectiveness stats = punishmentStats.get(punishmentType);
        if (stats != null && stats.getTotalApplications() > 5) {
            double effectiveness = stats.getEffectivenessRate();
            if (effectiveness < 0.5) {
                baseIntensity *= 1.3; // Increase intensity for ineffective punishments
            } else if (effectiveness > 0.8) {
                baseIntensity *= 0.9; // Can be slightly less intense for very effective punishments
            }
        }

        return Math.max(0.1, Math.min(5.0, baseIntensity));
    }

    /**
     * Learn from punishment outcomes
     */
    private void learnFromOutcome(UUID playerId, String punishmentType, boolean wasEffective,
                                Map<String, Object> outcomeContext) {
        // Update adaptive parameters based on outcome
        for (AdaptiveParameter param : adaptiveParameters.values()) {
            param.update(wasEffective ? 0.01 : -0.01); // Small adjustments
        }

        // Update punishment effectiveness models
        PunishmentEffectiveness stats = punishmentStats.get(punishmentType);
        if (stats != null) {
            stats.updateEffectivenessModel(wasEffective, outcomeContext);
        }
    }

    // ===== HELPER METHODS =====

    private double getOffenseBaseSeverity(String offenseType) {
        switch (offenseType.toLowerCase()) {
            case "minor_xray": return 0.3;
            case "moderate_xray": return 0.6;
            case "severe_xray": return 0.8;
            case "pattern_abuse": return 0.5;
            case "speed_hack": return 0.7;
            case "inventory_hack": return 0.9;
            default: return 0.4;
        }
    }

    private double calculateDeterrenceScore(List<PunishmentRecord> punishments) {
        if (punishments.isEmpty()) return 1.0;

        int effectiveCount = 0;
        for (PunishmentRecord record : punishments) {
            if (record.wasEffective) {
                effectiveCount++;
            }
        }

        return (double) effectiveCount / punishments.size();
    }

    private double calculateResistanceScore(List<PunishmentRecord> punishments) {
        if (punishments.size() < 2) return 0.0;

        // Calculate how often player returns after punishment
        int returnCount = 0;
        for (int i = 1; i < punishments.size(); i++) {
            PunishmentRecord current = punishments.get(i);
            PunishmentRecord previous = punishments.get(i - 1);

            // Check if this punishment came shortly after the previous one
            long timeDiff = current.timestamp - previous.timestamp;
            if (timeDiff < 24 * 60 * 60 * 1000) { // Within 24 hours
                returnCount++;
            }
        }

        return (double) returnCount / (punishments.size() - 1);
    }

    private PlayerType classifyPlayerType(List<PunishmentRecord> punishments, Map<String, Object> context) {
        if (punishments.isEmpty()) {
            return PlayerType.FIRST_TIME;
        }

        long totalPlayTime = ((Number) context.getOrDefault("total_play_time", 3600000L)).longValue(); // Default 1 hour
        int punishmentCount = punishments.size();

        // Calculate punishment frequency
        double punishmentFrequency = (double) punishmentCount / Math.max(1, totalPlayTime / (24 * 60 * 60 * 1000.0)); // Per day

        if (punishmentFrequency > 2.0) {
            return PlayerType.PERSISTENT;
        } else if (punishmentFrequency < 0.1) {
            return PlayerType.CASUAL;
        } else {
            return PlayerType.REGULAR;
        }
    }

    private double calculateRecidivismProbability(List<PunishmentRecord> punishments) {
        if (punishments.size() < 2) return 0.0;

        int recidivismCount = 0;
        for (int i = 1; i < punishments.size(); i++) {
            // Check if this offense came after a punishment
            if (!punishments.get(i - 1).wasEffective) {
                recidivismCount++;
            }
        }

        return (double) recidivismCount / (punishments.size() - 1);
    }

    private void analyzeContextFactors(PlayerBehaviorProfile profile, Map<String, Object> context) {
        // Time of day factor
        Integer hourOfDay = (Integer) context.get("hour_of_day");
        if (hourOfDay != null) {
            if (hourOfDay >= 22 || hourOfDay <= 6) { // Late night/early morning
                profile.addContextFactor("night_time", 1.2);
            }
        }

        // Server load factor
        Integer playerCount = (Integer) context.get("online_players");
        if (playerCount != null && playerCount > 100) {
            profile.addContextFactor("high_server_load", 1.1);
        }

        // Recent server events
        Boolean recentRestart = (Boolean) context.get("recent_restart");
        if (Boolean.TRUE.equals(recentRestart)) {
            profile.addContextFactor("post_restart", 0.9); // Slightly more lenient
        }
    }

    private double calculateContextFactor(Map<String, Object> context) {
        // Calculate how much context should influence the punishment
        double factor = 0.0;
        int factorCount = 0;

        Boolean isFirstOffense = (Boolean) context.get("first_offense");
        if (Boolean.TRUE.equals(isFirstOffense)) {
            factor += 0.8; // Reduce severity for first offenses
            factorCount++;
        }

        Boolean hasAppealHistory = (Boolean) context.get("has_appeal_history");
        if (Boolean.TRUE.equals(hasAppealHistory)) {
            factor += 0.9; // Slightly reduce for players who appeal legitimately
            factorCount++;
        }

        String offenseTime = (String) context.get("offense_time_category");
        if ("peak_hours".equals(offenseTime)) {
            factor += 1.1; // Slightly increase during peak hours
            factorCount++;
        }

        return factorCount > 0 ? factor / factorCount : 1.0;
    }

    private List<String> getCandidatePunishmentTypes(double severity) {
        List<String> candidates = new ArrayList<>();

        if (severity < 0.3) {
            candidates.addAll(Arrays.asList("warning", "mining_debuff"));
        } else if (severity < 0.6) {
            candidates.addAll(Arrays.asList("kick", "temp_ban_1h", "inventory_lock"));
        } else if (severity < 0.8) {
            candidates.addAll(Arrays.asList("temp_ban_24h", "speed_reduction", "fake_ore_veins"));
        } else {
            candidates.addAll(Arrays.asList("temp_ban_7d", "perm_ban", "paranoia_effects", "cursed_pickaxe"));
        }

        return candidates;
    }

    private double predictPunishmentEffectiveness(String punishmentType, PlayerBehaviorProfile profile,
                                                PlayerPunishmentHistory history) {
        PunishmentEffectiveness stats = punishmentStats.get(punishmentType);
        if (stats == null) return 0.5; // Default

        double baseEffectiveness = stats.getEffectivenessRate();

        // Adjust based on player profile
        if (profile.getPlayerType() == PlayerType.CASUAL && punishmentType.contains("ban")) {
            baseEffectiveness *= 0.8; // Bans less effective for casual players
        }

        if (profile.getResistanceScore() > 0.7) {
            baseEffectiveness *= 0.9; // Less effective for resistant players
        }

        // Check historical effectiveness for this player
        List<PunishmentRecord> playerRecords = history.getPunishmentsByType(punishmentType);
        if (!playerRecords.isEmpty()) {
            double playerSpecificEffectiveness = playerRecords.stream()
                .mapToInt(r -> r.wasEffective ? 1 : 0)
                .average()
                .orElse(0.5);

            // Blend global and player-specific effectiveness
            baseEffectiveness = baseEffectiveness * 0.7 + playerSpecificEffectiveness * 0.3;
        }

        return baseEffectiveness;
    }

    private double predictResistance(String punishmentType, PlayerBehaviorProfile profile) {
        // Predict how resistant the player will be to this punishment type
        double baseResistance = profile.getResistanceScore();

        // Some punishment types are inherently more resistible
        if (punishmentType.contains("debuff") || punishmentType.contains("speed")) {
            baseResistance *= 0.8; // Players can often work around debuffs
        }

        if (punishmentType.contains("ban")) {
            baseResistance *= 1.2; // Bans are harder to resist
        }

        return Math.max(0.0, Math.min(1.0, baseResistance));
    }

    /**
     * Get adaptive punishment statistics
     */
    public Map<String, Object> getAdaptiveStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPunishments", totalPunishments.get());
        stats.put("successfulPunishments", successfulPunishments.get());
        stats.put("successRate", totalPunishments.get() > 0 ?
            (double) successfulPunishments.get() / totalPunishments.get() : 0.0);
        stats.put("trackedPlayers", playerHistories.size());

        // Punishment effectiveness stats
        Map<String, Double> effectivenessStats = new HashMap<>();
        for (Map.Entry<String, PunishmentEffectiveness> entry : punishmentStats.entrySet()) {
            effectivenessStats.put(entry.getKey(), entry.getValue().getEffectivenessRate());
        }
        stats.put("punishmentEffectiveness", effectivenessStats);

        // Adaptive parameters
        Map<String, Double> paramStats = new HashMap<>();
        for (Map.Entry<String, AdaptiveParameter> entry : adaptiveParameters.entrySet()) {
            paramStats.put(entry.getKey(), entry.getValue().getValue());
        }
        stats.put("adaptiveParameters", paramStats);

        return stats;
    }

    // ===== INNER CLASSES =====

    public static class PunishmentRecommendation {
        public final String punishmentType;
        public final double severity;
        public final double intensity;
        public final PlayerBehaviorProfile playerProfile;
        public final Map<String, Object> context;

        public PunishmentRecommendation(String punishmentType, double severity, double intensity,
                                      PlayerBehaviorProfile playerProfile, Map<String, Object> context) {
            this.punishmentType = punishmentType;
            this.severity = severity;
            this.intensity = intensity;
            this.playerProfile = playerProfile;
            this.context = context;
        }

        public String getReasoning() {
            return String.format("Type: %s, Severity: %.2f, Intensity: %.2f, Player Type: %s",
                punishmentType, severity, intensity, playerProfile.getPlayerType());
        }
    }

    public static class PlayerBehaviorProfile {
        private double deterrenceScore = 0.5;
        private double resistanceScore = 0.0;
        private PlayerType playerType = PlayerType.REGULAR;
        private double recidivismProbability = 0.0;
        private final Map<String, Double> contextFactors = new HashMap<>();

        // Getters and setters
        public double getDeterrenceScore() { return deterrenceScore; }
        public void setDeterrenceScore(double score) { this.deterrenceScore = score; }

        public double getResistanceScore() { return resistanceScore; }
        public void setResistanceScore(double score) { this.resistanceScore = score; }

        public PlayerType getPlayerType() { return playerType; }
        public void setPlayerType(PlayerType type) { this.playerType = type; }

        public double getRecidivismProbability() { return recidivismProbability; }
        public void setRecidivismProbability(double prob) { this.recidivismProbability = prob; }

        public void addContextFactor(String factor, double value) {
            contextFactors.put(factor, value);
        }

        public Map<String, Double> getContextFactors() { return new HashMap<>(contextFactors); }
    }

    public enum PlayerType {
        FIRST_TIME, CASUAL, REGULAR, PERSISTENT
    }

    public static class PunishmentRecord {
        public final String punishmentType;
        public final boolean wasEffective;
        public final long timestamp;
        public final Map<String, Object> context;

        public PunishmentRecord(String punishmentType, boolean wasEffective,
                              long timestamp, Map<String, Object> context) {
            this.punishmentType = punishmentType;
            this.wasEffective = wasEffective;
            this.timestamp = timestamp;
            this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        }
    }

    public static class PlayerPunishmentHistory {
        private final UUID playerId;
        private final List<PunishmentRecord> punishmentRecords = new ArrayList<>();

        public PlayerPunishmentHistory(UUID playerId) {
            this.playerId = playerId;
        }

        public void addPunishment(String punishmentType, boolean wasEffective, Map<String, Object> context) {
            punishmentRecords.add(new PunishmentRecord(punishmentType, wasEffective,
                System.currentTimeMillis(), context));

            // Maintain history size limit
            if (punishmentRecords.size() > MAX_HISTORY_SIZE) {
                punishmentRecords.remove(0);
            }
        }

        public List<PunishmentRecord> getRecentPunishments(int count) {
            int start = Math.max(0, punishmentRecords.size() - count);
            return new ArrayList<>(punishmentRecords.subList(start, punishmentRecords.size()));
        }

        public List<PunishmentRecord> getPunishmentsByType(String type) {
            List<PunishmentRecord> matching = new ArrayList<>();
            for (PunishmentRecord record : punishmentRecords) {
                if (record.punishmentType.equals(type)) {
                    matching.add(record);
                }
            }
            return matching;
        }

        public int getTotalPunishments() {
            return punishmentRecords.size();
        }
    }

    public static class PunishmentEffectiveness {
        private final String punishmentType;
        private int totalApplications = 0;
        private int successfulApplications = 0;

        // Effectiveness model parameters
        private double effectivenessRate = 0.5;
        private final Map<String, Double> contextFactors = new HashMap<>();

        public PunishmentEffectiveness(String punishmentType) {
            this.punishmentType = punishmentType;
        }

        public void recordOutcome(boolean wasEffective) {
            totalApplications++;
            if (wasEffective) {
                successfulApplications++;
            }

            // Update effectiveness rate with smoothing
            double newRate = (double) successfulApplications / totalApplications;
            effectivenessRate = effectivenessRate * FORGETTING_FACTOR + newRate * (1 - FORGETTING_FACTOR);
        }

        public void updateEffectivenessModel(boolean wasEffective, Map<String, Object> context) {
            // Learn from context factors
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String factor = entry.getKey();
                if (entry.getValue() instanceof Number) {
                    double value = ((Number) entry.getValue()).doubleValue();
                    double currentFactor = contextFactors.getOrDefault(factor, 1.0);

                    // Adjust factor based on outcome
                    double adjustment = wasEffective ? 1.01 : 0.99;
                    contextFactors.put(factor, currentFactor * adjustment);
                }
            }
        }

        public double getEffectivenessRate() {
            return effectivenessRate;
        }

        public int getTotalApplications() {
            return totalApplications;
        }

        public int getSuccessfulApplications() {
            return successfulApplications;
        }
    }

    public static class AdaptiveParameter {
        private double currentValue;
        private final double learningRate;
        private final double minValue;
        private final double maxValue;

        public AdaptiveParameter(double initialValue, double learningRate, double minValue, double maxValue) {
            this.currentValue = initialValue;
            this.learningRate = learningRate;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        public void update(double delta) {
            currentValue += delta * learningRate;
            currentValue = Math.max(minValue, Math.min(maxValue, currentValue));
        }

        public double getValue() {
            return currentValue;
        }

        public void setValue(double value) {
            this.currentValue = Math.max(minValue, Math.min(maxValue, value));
        }
    }
}