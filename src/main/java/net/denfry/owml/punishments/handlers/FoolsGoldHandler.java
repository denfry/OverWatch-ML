package net.denfry.owml.punishments.handlers;

import static net.denfry.owml.utils.LocationUtils.formatLocation;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
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
 * Handles the Fool's Gold punishment which replaces diamond ore with gold/copper.
 */
public class FoolsGoldHandler extends AbstractPunishmentHandler {

    public FoolsGoldHandler(OverWatchML plugin, ConfigManager configManager, PunishmentManager punishmentManager) {
        super(plugin, configManager, punishmentManager);
    }

    @Override
    public boolean processBlockBreak(Player player, Block block) {
        Material ore = block.getType();


        if (!isDiamondOre(ore)) {
            return false;
        }


        Collection<ItemStack> originalDrops = block.getDrops(player.getInventory().getItemInMainHand());


        for (ItemStack drop : originalDrops) {
            if (drop.getType() == Material.DIAMOND) {

                Material fakeMaterial = getFakeMaterial();


                ItemStack fakeDrop = new ItemStack(fakeMaterial, drop.getAmount());
                block.getWorld().dropItemNaturally(block.getLocation(), fakeDrop);


                player.getWorld().spawnParticle(Particle.WITCH, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.4, 0.4, 0.4, 0.01);
            } else {

                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }


        if (ThreadLocalRandom.current().nextInt(2) == 0) {
            player.sendMessage(Component.text("The diamond's luster seems... different somehow.", NamedTextColor.GOLD));
        }


        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Fool's Gold punishment triggered for " + player.getName() + " at " + formatLocation(block.getLocation()) + " - Diamonds replaced with copper/gold.");
        }


        block.setType(Material.AIR);


        return true;
    }

    @Override
    public boolean isActive(Player player) {
        return punishmentManager.hasFoolsGold(player.getUniqueId());
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

    /**
     * Get appropriate fake material for version (raw ores for 1.17+, ingots for older)
     */
    private Material getFakeMaterial() {
        // Try modern raw ores first
        Material rawCopper = net.denfry.owml.utils.MaterialHelper.getMaterial("RAW_COPPER");
        if (rawCopper != null) return rawCopper;

        Material rawGold = net.denfry.owml.utils.MaterialHelper.getMaterial("RAW_GOLD");
        if (rawGold != null) return rawGold;

        // Fallback to ingots for older versions
        Material copperIngot = net.denfry.owml.utils.MaterialHelper.getMaterial("COPPER_INGOT");
        if (copperIngot != null) return copperIngot;

        Material goldIngot = Material.GOLD_INGOT; // Always exists
        return goldIngot;
    }
}
