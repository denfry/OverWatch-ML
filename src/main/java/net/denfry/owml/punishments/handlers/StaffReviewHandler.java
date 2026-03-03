package net.denfry.owml.punishments.handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.AbstractPunishmentHandler;

/**
 * Handles the Staff Review Requirement which prevents mining ores below Y-level 25 until approved.
 */
public class StaffReviewHandler extends AbstractPunishmentHandler {

    public StaffReviewHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {
        
        if (block.getY() >= 25 || !isOre(block.getType())) {
            return false;
        }

        player.sendMessage(Component.text("You require staff approval to mine ores below Y-level 25!").color(NamedTextColor.RED));
        player.sendMessage(Component.text("Contact a staff member to request mining access.").color(NamedTextColor.YELLOW));

        
        return true;
    }

    @Override
    public boolean isActive(Player player) {
        return player.hasMetadata("requires_staff_review");
    }
}
