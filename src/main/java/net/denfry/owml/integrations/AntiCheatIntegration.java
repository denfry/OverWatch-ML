package net.denfry.owml.integrations;

import org.bukkit.entity.Player;

/**
 * Interface for external anti-cheat integrations.
 * All external anti-cheat integrations must implement this interface.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.9.0
 */
public interface AntiCheatIntegration {
    
    /**
     * Get the name of the anti-cheat provider (e.g., "Grim", "Vulcan").
     *
     * @return provider name
     */
    String getProviderName();

    /**
     * Get the violation level or confidence score for a specific player and cheat category.
     * The score should be normalized between 0.0 and 1.0.
     *
     * @param player the player to check
     * @param cheatCategory the category of cheat (e.g., "movement", "combat", "autoclicker", "scaffold")
     * @return violation score between 0.0 and 1.0
     */
    double getViolationLevel(Player player, String cheatCategory);
}