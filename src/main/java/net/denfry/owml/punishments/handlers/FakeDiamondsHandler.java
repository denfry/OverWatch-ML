package net.denfry.owml.punishments.handlers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.AbstractPunishmentHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles the Fake Diamonds punishment which replaces diamond ore with coal.
 */
public class FakeDiamondsHandler extends AbstractPunishmentHandler {

    public FakeDiamondsHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {
        Material ore = block.getType();


        if (!isDiamondOre(ore)) {
            return false;
        }


        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.COAL, 1));
        player.sendMessage(Component.text("The diamond crumbles to coal in your hands!", NamedTextColor.RED));


        punishmentManager.decrementFakeDiamonds(player.getUniqueId());



        block.setType(Material.AIR);


        return true;
    }

    @Override
    public boolean isActive(Player player) {
        return punishmentManager.hasFakeDiamonds(player.getUniqueId());
    }

    /**
     * Check if material is a diamond ore variant (only if exists in version)
     */
    private boolean isDiamondOre(Material material) {
        Material diamondOre = net.denfry.owml.utils.MaterialHelper.getMaterial("DIAMOND_ORE");
        Material deepslateDiamondOre = net.denfry.owml.utils.MaterialHelper.getMaterial("DEEPSLATE_DIAMOND_ORE");

        return (diamondOre != null && material == diamondOre) ||
               (deepslateDiamondOre != null && material == deepslateDiamondOre);
    }
}
