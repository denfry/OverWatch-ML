package net.denfry.owml.integrations;

import org.bukkit.entity.Player;
import java.util.List;

/**
 * Combines internal ML detection scores with external anti-cheat provider scores.
 * Uses provider-specific weights according to project specifications.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.9.0
 */
public class CorrelationEngine {

    private final List<AntiCheatIntegration> integrations;

    public CorrelationEngine(List<AntiCheatIntegration> integrations) {
        this.integrations = integrations;
    }

    /**
     * Calculates the combined score based on internal ML score and external anti-cheat integrations.
     * 
     * Formula: combinedScore = internalScore * 0.6 + externalCorrelationScore * 0.4
     * If cross-provider consensus is 2 or more, multiply combinedScore by 1.3 (capped at 1.0).
     *
     * @param player        The player being checked.
     * @param internalScore The internal ML detection score (0.0 to 1.0).
     * @param cheatCategory The category of cheat (e.g., "movement", "combat", "autoclicker", "scaffold").
     * @return The final combined score capped at 1.0.
     */
    public double calculateCombinedScore(Player player, double internalScore, String cheatCategory) {
        double externalCorrelationScore = 0.0;
        int consensusCount = 0;

        for (AntiCheatIntegration integration : integrations) {
            double providerScore = integration.getViolationLevel(player, cheatCategory);
            if (providerScore > 0) {
                consensusCount++;
                double weight = getWeight(integration.getProviderName(), cheatCategory);
                // Ensure the external score factor is bounded by the provider score and weight
                externalCorrelationScore += (providerScore * weight);
            }
        }

        // Normalize external correlation score if multiple providers triggered
        if (consensusCount > 0) {
            externalCorrelationScore /= consensusCount;
            // Cap at 1.0 to ensure predictable calculations
            externalCorrelationScore = Math.min(externalCorrelationScore, 1.0);
        }

        double combinedScore = (internalScore * 0.6) + (externalCorrelationScore * 0.4);

        if (getCrossProviderConsensus(player, cheatCategory) >= 2) {
            combinedScore *= 1.3;
        }

        return Math.min(combinedScore, 1.0);
    }

    /**
     * Returns the number of external providers that have flagged the player for the given category.
     *
     * @param player        The player.
     * @param cheatCategory The cheat category.
     * @return Number of providers with violation level > 0.
     */
    public int getCrossProviderConsensus(Player player, String cheatCategory) {
        int count = 0;
        for (AntiCheatIntegration integration : integrations) {
            if (integration.getViolationLevel(player, cheatCategory) > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the specific weight for a provider based on the cheat category.
     * Provider weights: Grim 1.4 for movement 1.2 otherwise, 
     * Vulcan 1.3 for combat 1.1 otherwise, 
     * Matrix 1.3 for AutoClicker 1.1 otherwise, 
     * Spartan 1.2 for scaffold 1.0 otherwise.
     */
    private double getWeight(String provider, String category) {
        if (provider == null || category == null) return 1.0;
        
        return switch (provider.toLowerCase()) {
            case "grim" -> category.equalsIgnoreCase("movement") ? 1.4 : 1.2;
            case "vulcan" -> category.equalsIgnoreCase("combat") ? 1.3 : 1.1;
            case "matrix" -> category.equalsIgnoreCase("autoclicker") ? 1.3 : 1.1;
            case "spartan" -> category.equalsIgnoreCase("scaffold") ? 1.2 : 1.0;
            default -> 1.0;
        };
    }
}