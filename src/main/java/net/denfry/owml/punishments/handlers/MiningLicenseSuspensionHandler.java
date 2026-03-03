package net.denfry.owml.punishments.handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.AbstractPunishmentHandler;

/**
 * Handles the Mining License Suspension punishment which prevents mining valuable ores.
 */
public class MiningLicenseSuspensionHandler extends AbstractPunishmentHandler {

    public MiningLicenseSuspensionHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {
        Material ore = block.getType();

        
        if (!isValuableOre(ore)) {
            return false;
        }

        
        long remainingTime = punishmentManager.getMiningLicenseSuspensionTime(player.getUniqueId()) / (60 * 1000);

        
        player.sendMessage(Component.text("Your mining license for valuable ores is suspended!").color(NamedTextColor.RED));
        player.sendMessage(Component.text("Time remaining: " + remainingTime + " minutes").color(NamedTextColor.YELLOW));

        
        return true;
    }

    @Override
    public boolean isActive(Player player) {
        return punishmentManager.hasMiningLicenseSuspension(player.getUniqueId());
    }
}
