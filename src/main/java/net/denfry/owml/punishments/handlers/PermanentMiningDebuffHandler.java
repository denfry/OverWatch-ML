package net.denfry.owml.punishments.handlers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.AbstractPunishmentHandler;

/**
 * Handles the Permanent Mining Debuff punishment which applies Mining Fatigue below Y-level 0.
 */
public class PermanentMiningDebuffHandler extends AbstractPunishmentHandler {

    public PermanentMiningDebuffHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {

        if (block.getY() >= 0 || !isOre(block.getType())) {
            return false;
        }


        if (!player.hasPotionEffect(PotionEffectType.MINING_FATIGUE) ||
                player.getPotionEffect(PotionEffectType.MINING_FATIGUE).getAmplifier() < 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 1));
            player.sendMessage(Component.text("Mining below Y-level 0 is significantly slowed due to your mining debuff!").color(NamedTextColor.RED));
        }


        return false;
    }

    @Override
    public boolean isActive(Player player) {
        return punishmentManager.hasPermanentMiningDebuff(player.getUniqueId());
    }
}
