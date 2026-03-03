package net.denfry.owml.punishments.handlers;

import static net.denfry.owml.utils.LocationUtils.formatLocation;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.AbstractPunishmentHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles the Fake Ore Veins punishment which turns valuable ores into stone sometimes.
 */
public class FakeOreVeinsHandler extends AbstractPunishmentHandler {

    public FakeOreVeinsHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {
        Material ore = block.getType();


        if (!isValuableOre(ore)) {
            return false;
        }


        if (ThreadLocalRandom.current().nextBoolean()) {

            Material stoneType = getStoneTypeForOre(ore);


            player.playEffect(block.getLocation(), Effect.STEP_SOUND, stoneType);


            block.setType(stoneType);


            player.sendMessage(Component.text("The ore vein crumbles to stone as you mine it!", NamedTextColor.RED));


            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);


            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Fake ore veins punishment triggered for " +
                        player.getName() + " at " + formatLocation(block.getLocation()) +
                        " - " + ore.name() + " turned to " + stoneType.name());
            }


            return true;
        }


        return false;
    }

    @Override
    public boolean isActive(Player player) {
        return punishmentManager.hasFakeOreVeins(player.getUniqueId());
    }

    /**
     * Get appropriate stone type for ore (deepslate for deepslate ores, stone for others)
     */
    private Material getStoneTypeForOre(Material ore) {
        if (ore.name().contains("DEEPSLATE")) {
            Material deepslate = net.denfry.owml.utils.MaterialHelper.getMaterial("DEEPSLATE");
            if (deepslate != null) {
                return deepslate;
            }
        }
        return Material.STONE; // Fallback for all versions
    }
}
