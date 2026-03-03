package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;

/**
 * Handles environment interaction effects including cave-ins, teleport illusions,
 * fake ore appearances, and more
 */
public class EnvironmentEffect {

    private final OverWatchML plugin;
    private final ConfigManager configManager;

    public EnvironmentEffect(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Simulates a small cave-in above the player's head
     */
    public void simulateCaveIn(Player player, Block brokenBlock) {
        World world = player.getWorld();
        Location playerLoc = player.getLocation();


        List<Block> potentialFallingBlocks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 3; y++) {
                    Block block = world.getBlockAt(brokenBlock.getX() + x, brokenBlock.getY() + y, brokenBlock.getZ() + z);


                    if (isNaturalStone(block.getType())) {
                        potentialFallingBlocks.add(block);
                    }
                }
            }
        }

        if (potentialFallingBlocks.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Cave-in effect cancelled for " + player.getName() + " - no suitable blocks found");
            }
            return;
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Simulating cave-in effect for " + player.getName() + " with " + Math.min(5, potentialFallingBlocks.size()) + " falling blocks");
        }


        player.playSound(playerLoc, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.5f);
        player.playSound(playerLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);


        int fallingCount = Math.min(5, potentialFallingBlocks.size());


        for (int i = 0; i < fallingCount; i++) {

            int index = ThreadLocalRandom.current().nextInt(potentialFallingBlocks.size());
            Block fallingBlock = potentialFallingBlocks.get(index);
            potentialFallingBlocks.remove(index);


            FallingBlock visual = world.spawnFallingBlock(fallingBlock.getLocation().add(0.5, 0, 0.5), fallingBlock.getBlockData());


            visual.setDropItem(false);
            visual.setCancelDrop(true);


            visual.setMetadata("visual_only", new FixedMetadataValue(plugin, true));


            new BukkitRunnable() {
                @Override
                public void run() {
                    if (visual != null && !visual.isDead()) {
                        visual.remove();
                    }
                }
            }.runTaskLater(plugin, 100L);
        }


        world.spawnParticle(Particle.CLOUD, brokenBlock.getLocation().add(0.5, 1.5, 0.5), 30, 1.0, 1.0, 1.0, 0.1);
    }

    /**
     * Applies a fake damage effect without actually hurting the player
     */
    public void applyFakeDamage(Player player) {

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);


        player.playHurtAnimation(0.0f);


        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false, false));


        String[] damageSources = {"You feel something sharp slice against your back!", "Something stabs at your leg from the darkness!", "A searing pain shoots through your body!", "You feel the sting of ghostly claws!", "Something unseen strikes you from behind!", "A choking sensation tightens around your throat for a moment!"};

        player.sendMessage(ChatColor.RED + damageSources[ThreadLocalRandom.current().nextInt(damageSources.length)]);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Applied fake damage effect to " + player.getName());
        }
    }

    /**
     * Creates a brief illusion of valuable ore that quickly reverts to stone
     */
    public void createFakeOreIllusion(Player player, Block sourceBlock) {

        Material[] oreTypes = getValuableOreTypes();


        World world = player.getWorld();
        Location sourceLoc = sourceBlock.getLocation();
        List<Block> stoneBlocks = new ArrayList<>();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = world.getBlockAt(sourceLoc.getBlockX() + x, sourceLoc.getBlockY() + y, sourceLoc.getBlockZ() + z);

                    if (isNaturalStone(block.getType())) {
                        stoneBlocks.add(block);
                    }
                }
            }
        }

        if (stoneBlocks.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Fake ore illusion cancelled for " + player.getName() + " - no suitable blocks found");
            }
            return;
        }


        int illusionCount = 1 + ThreadLocalRandom.current().nextInt(3);
        illusionCount = Math.min(illusionCount, stoneBlocks.size());

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Creating fake ore illusion for " + player.getName() + " with " + illusionCount + " fake ores");
        }

        for (int i = 0; i < illusionCount; i++) {

            int index = ThreadLocalRandom.current().nextInt(stoneBlocks.size());
            Block targetBlock = stoneBlocks.get(index);
            stoneBlocks.remove(index);


            Material originalType = targetBlock.getType();


            Material oreType;
            if (originalType == getMaterialIfExists("DEEPSLATE")) {
                oreType = oreTypes[ThreadLocalRandom.current().nextInt(2) + 1];
            } else {
                oreType = oreTypes[ThreadLocalRandom.current().nextInt(2)];
            }


            player.sendBlockChange(targetBlock.getLocation(), Bukkit.createBlockData(oreType));


            int revertDelay = 20 + ThreadLocalRandom.current().nextInt(20);

            new BukkitRunnable() {
                @Override
                public void run() {

                    player.sendBlockChange(targetBlock.getLocation(), targetBlock.getBlockData());


                    if (ThreadLocalRandom.current().nextBoolean()) {
                        world.spawnParticle(Particle.SMOKE, targetBlock.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
                    }
                }
            }.runTaskLater(plugin, revertDelay);
        }


        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    /**
     * Creates an illusion of teleportation without actually moving the player
     * Enhanced with longer duration and more effects
     */
    public void createTeleportIllusion(Player player) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Creating teleport illusion for " + player.getName());
        }

        Location originalLoc = player.getLocation();


        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, false));


        player.playSound(originalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);


        player.getWorld().spawnParticle(Particle.PORTAL, originalLoc.add(0, 1, 0), 60, 0.5, 1, 0.5, 0.01);


        player.setVelocity(new Vector((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2, 0.2, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2));


        if (ThreadLocalRandom.current().nextBoolean()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.5f, 0.7f);
        }


        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {

                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);


                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.01);


                    player.setVelocity(new Vector((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.15, 0.1, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.15));


                    if (ThreadLocalRandom.current().nextInt(100) < 70) {
                        String[] messages = {"Where am I?", "This isn't where I was...", "Something pulled me through the void...", "The darkness... it moved me...", "I can feel eyes watching me..."};
                        player.sendMessage(ChatColor.DARK_PURPLE + messages[ThreadLocalRandom.current().nextInt(messages.length)]);
                    }
                }
            }
        }.runTaskLater(plugin, 25L);


        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {

                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);


                    if (ThreadLocalRandom.current().nextInt(100) < 30) {

                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double distance = 20;
                        Location soundLoc = player.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                        player.playSound(soundLoc, Sound.ENTITY_ENDERMAN_AMBIENT, 0.4f, 0.7f);
                    }
                }
            }
        }.runTaskLater(plugin, 50L);
    }

    /**
     * Creates fake explosion effect
     */
    public void createFakeExplosion(Player player) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Creating fake explosion effect for " + player.getName());
        }
        Location loc = player.getLocation();
        World world = loc.getWorld();


        player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);


        world.spawnParticle(Particle.EXPLOSION, loc.add(ThreadLocalRandom.current().nextDouble(-3, 3), ThreadLocalRandom.current().nextDouble(-2, 2), ThreadLocalRandom.current().nextDouble(-3, 3)), 1, 0, 0, 0, 0);


        Vector knockback = player.getLocation().getDirection().multiply(-0.3);
        knockback.setY(0.2);
        player.setVelocity(knockback);


        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false, false));
    }

    /**
     * Applies a tripwire effect (brief slowness)
     */
    public void applyTripwireEffect(Player player) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Applied tripwire slowness effect to " + player.getName());
        }


        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, false, false, false));


        player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.6f, 1.2f);


        player.setVelocity(new Vector((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02, 0.05, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02));


        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 0.1, 0), 10, 0.3, 0.05, 0.3, 0.02);
    }

    /**
     * Check if material is a natural stone type that can be replaced
     */
    private boolean isNaturalStone(Material material) {
        // Always available stones
        if (material == Material.STONE || material == Material.ANDESITE ||
            material == Material.GRANITE || material == Material.DIORITE) {
            return true;
        }

        // Check for deepslate if it exists
        Material deepslate = net.denfry.owml.utils.MaterialHelper.getMaterial("DEEPSLATE");
        return deepslate != null && material == deepslate;
    }

    /**
     * Get appropriate stone material for replacement
     */
    private Material getStoneTypeForReplacement(Material original) {
        // If original was deepslate and deepslate exists, keep it
        Material deepslate = net.denfry.owml.utils.MaterialHelper.getMaterial("DEEPSLATE");
        if (deepslate != null && original == deepslate) {
            return deepslate;
        }

        // Otherwise use regular stone
        return Material.STONE;
    }

    /**
     * Check if material is a valuable ore (only if exists in version)
     */
    private boolean isValuableOre(Material material) {
        Material[] valuableOres = {
            getMaterialIfExists("DIAMOND_ORE"), getMaterialIfExists("DEEPSLATE_DIAMOND_ORE"),
            getMaterialIfExists("EMERALD_ORE"), getMaterialIfExists("DEEPSLATE_EMERALD_ORE"),
            getMaterialIfExists("ANCIENT_DEBRIS")
        };

        for (Material ore : valuableOres) {
            if (ore != null && material == ore) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get material if exists, null otherwise
     */
    private Material getMaterialIfExists(String materialName) {
        return net.denfry.owml.utils.MaterialHelper.getMaterial(materialName);
    }

    /**
     * Get array of valuable ore types that exist in current version
     */
    private Material[] getValuableOreTypes() {
        java.util.List<Material> ores = new java.util.ArrayList<>();
        addIfExists(ores, "DIAMOND_ORE");
        addIfExists(ores, "DEEPSLATE_DIAMOND_ORE");
        addIfExists(ores, "EMERALD_ORE");
        addIfExists(ores, "DEEPSLATE_EMERALD_ORE");
        addIfExists(ores, "ANCIENT_DEBRIS");
        return ores.toArray(new Material[0]);
    }

    /**
     * Add material to list if it exists
     */
    private void addIfExists(java.util.List<Material> list, String materialName) {
        Material material = getMaterialIfExists(materialName);
        if (material != null) {
            list.add(material);
        }
    }
}
