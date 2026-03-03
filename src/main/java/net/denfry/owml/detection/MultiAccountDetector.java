package net.denfry.owml.detection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.*;

/**
 * Multi-account detector that identifies players using multiple accounts
 * from the same IP address or network.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class MultiAccountDetector implements DetectionAnalyzer {

    private boolean enabled = true;

    // In-memory storage for IP tracking (in production, use database)
    private final Map<String, Set<UUID>> ipToPlayers = new HashMap<>();
    private final Map<UUID, String> playerToIP = new HashMap<>();

    @Override
    public DetectionResult analyze(Player player, PlayerDetectionData data, DetectionContext context) {
        if (!enabled) {
            return DetectionResult.createSafe();
        }

        try {
            InetAddress address = player.getAddress().getAddress();
            String ip = address.getHostAddress();
            UUID playerId = player.getUniqueId();

            // Update IP tracking
            playerToIP.put(playerId, ip);
            ipToPlayers.computeIfAbsent(ip, k -> new HashSet<>()).add(playerId);

            Set<UUID> playersFromSameIP = ipToPlayers.get(ip);
            if (playersFromSameIP == null || playersFromSameIP.size() <= 1) {
                return DetectionResult.createSafe();
            }

            List<String> reasons = new ArrayList<>();
            double totalScore = 0.0;
            Map<String, Object> additionalData = new HashMap<>();

            // Check number of accounts from same IP
            int accountCount = playersFromSameIP.size();
            if (accountCount >= 3) {
                reasons.add("Multiple accounts from same IP: " + accountCount + " accounts");
                totalScore += Math.min(0.8, accountCount * 0.2);
                additionalData.put("accountCount", accountCount);
            }

            // Check if accounts have similar suspicious behavior
            double behaviorSimilarity = analyzeAccountSimilarity(playersFromSameIP);
            if (behaviorSimilarity > 0.7) {
                reasons.add("Similar suspicious behavior across accounts: " +
                    String.format("%.1f%%", behaviorSimilarity * 100));
                totalScore += behaviorSimilarity * 0.4;
                additionalData.put("behaviorSimilarity", behaviorSimilarity);
            }

            // Check timing patterns (accounts used at similar times)
            double timingSimilarity = analyzeTimingSimilarity(playersFromSameIP);
            if (timingSimilarity > 0.6) {
                reasons.add("Synchronized account usage patterns detected");
                totalScore += timingSimilarity * 0.3;
                additionalData.put("timingSimilarity", timingSimilarity);
            }

            // Check for coordinated suspicious activities
            double coordinationScore = analyzeCoordination(playersFromSameIP);
            if (coordinationScore > 0.5) {
                reasons.add("Coordinated suspicious activities detected");
                totalScore += coordinationScore * 0.35;
                additionalData.put("coordinationScore", coordinationScore);
            }

            DetectionLevel level = determineDetectionLevel(totalScore);
            additionalData.put("ip", ip);
            additionalData.put("accounts", getAccountNames(playersFromSameIP));

            return new DetectionResult(level, totalScore, reasons, DetectionType.MULTI_ACCOUNT, additionalData);

        } catch (Exception e) {
            // If IP detection fails, return safe result
            return DetectionResult.createSafe();
        }
    }

    /**
     * Analyze similarity in behavior across accounts
     */
    private double analyzeAccountSimilarity(Set<UUID> playerIds) {
        if (playerIds.size() < 2) return 0.0;

        // Compare suspicion levels and behavior patterns
        List<Double> suspicionLevels = new ArrayList<>();
        List<Double> miningRates = new ArrayList<>();

        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // Get actual player behavior data
                double suspicionLevel = getPlayerSuspicionLevel(playerId);
                double miningRate = getPlayerMiningRate(playerId);
                suspicionLevels.add(suspicionLevel);
                miningRates.add(miningRate);
            }
        }

        if (suspicionLevels.size() < 2) return 0.0;

        // Calculate coefficient of variation for suspicion levels
        double suspicionCV = calculateCoefficientOfVariation(suspicionLevels);
        double miningRateCV = calculateCoefficientOfVariation(miningRates);

        // Low CV = similar behavior = more suspicious
        return 1.0 - (suspicionCV + miningRateCV) / 2.0;
    }

    /**
     * Analyze timing similarity in account usage
     */
    private double analyzeTimingSimilarity(Set<UUID> playerIds) {
        // Simplified timing analysis - in production would check login/logout times
        // For now, return a moderate score if multiple accounts are online
        long onlineCount = playerIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .count();

        if (onlineCount >= 2) {
            return Math.min(0.8, onlineCount * 0.3);
        }

        return 0.0;
    }

    /**
     * Analyze coordination between accounts
     */
    private double analyzeCoordination(Set<UUID> playerIds) {
        // Check if accounts perform suspicious actions in coordinated patterns
        // Simplified implementation
        int suspiciousAccounts = 0;

        for (UUID playerId : playerIds) {
            // Check if player is currently suspicious
            double suspicionLevel = getPlayerSuspicionLevel(playerId);
            if (suspicionLevel > 0.3) { // Threshold for suspicious behavior
                suspiciousAccounts++;
            }
        }

        double coordinationRatio = (double) suspiciousAccounts / playerIds.size();
        return Math.min(1.0, coordinationRatio * 1.5);
    }

    /**
     * Get suspicion level for a player
     */
    private double getPlayerSuspicionLevel(UUID playerId) {
        try {
            // Try to get suspicion level from SuspiciousManager
            Class<?> suspiciousManagerClass = Class.forName("net.denfry.owml.managers.SuspiciousManager");
            java.lang.reflect.Method getSuspicionMethod = suspiciousManagerClass.getMethod("getSuspicionLevel", UUID.class);
            Integer suspicionLevel = (Integer) getSuspicionMethod.invoke(null, playerId);
            return suspicionLevel != null ? suspicionLevel.doubleValue() / 100.0 : 0.0; // Normalize to 0-1
        } catch (Exception e) {
            return 0.0; // Default suspicion level
        }
    }

    /**
     * Get mining rate for a player
     */
    private double getPlayerMiningRate(UUID playerId) {
        try {
            // Try to get mining data from StatsManager
            Class<?> statsManagerClass = Class.forName("net.denfry.owml.managers.StatsManager");
            java.lang.reflect.Method getMiningStatsMethod = statsManagerClass.getMethod("getPlayerMiningRate", UUID.class);
            Double miningRate = (Double) getMiningStatsMethod.invoke(null, playerId);
            return miningRate != null ? miningRate : 0.0;
        } catch (Exception e) {
            return 0.0; // Default mining rate
        }
    }

    /**
     * Calculate coefficient of variation
     */
    private double calculateCoefficientOfVariation(List<Double> values) {
        if (values.isEmpty()) return 0.0;

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean == 0) return 0.0;

        double variance = values.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);
        return stdDev / mean;
    }

    /**
     * Get account names from UUIDs
     */
    private List<String> getAccountNames(Set<UUID> playerIds) {
        List<String> names = new ArrayList<>();
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                names.add(player.getName());
            } else {
                names.add(playerId.toString().substring(0, 8) + "...");
            }
        }
        return names;
    }

    /**
     * Determine detection level based on multi-account analysis
     */
    private DetectionLevel determineDetectionLevel(double score) {
        if (score >= 0.9) return DetectionLevel.CRITICAL;
        if (score >= 0.75) return DetectionLevel.HIGH;
        if (score >= 0.6) return DetectionLevel.MEDIUM;
        if (score >= 0.4) return DetectionLevel.LOW;
        return DetectionLevel.SAFE;
    }

    @Override
    public DetectionType getDetectionType() {
        return DetectionType.MULTI_ACCOUNT;
    }

    @Override
    public String getName() {
        return "Multi-Account Detector";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
