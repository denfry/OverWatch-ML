package net.denfry.owml.ml.v2.listeners;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.v2.core.CheatCategory;
import net.denfry.owml.ml.v2.filters.InstantFilterRules;
import net.denfry.owml.ml.v2.pipeline.DetectionPipeline;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MLDataListener implements Listener {
    private final OverWatchML plugin;
    private final DetectionPipeline pipeline;

    private final Map<UUID, PlayerSessionState> sessionStates = new ConcurrentHashMap<>();

    public MLDataListener(OverWatchML plugin, DetectionPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
    }

    private PlayerSessionState getState(UUID uuid) {
        return sessionStates.computeIfAbsent(uuid, PlayerSessionState::new);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        UUID uuid = player.getUniqueId();
        PlayerSessionState state = getState(uuid);

        state.blocksBroken++;
        if (isOre(block.getType())) {
            state.oresFound++;
            
            double[] features = new double[32];
            long now = System.currentTimeMillis();
            
            double timeSinceLastOre = (now - state.lastOreTime) / 1000.0;
            features[0] = timeSinceLastOre;
            features[1] = state.oresFound / (Math.max(1, (now - state.sessionStart) / 60000.0));
            
            double dist = 0;
            if (state.lastOreLoc != null && state.lastOreLoc.getWorld() != null && 
                state.lastOreLoc.getWorld().equals(block.getWorld())) {
                dist = block.getLocation().distance(state.lastOreLoc);
            }
            features[2] = dist;
            features[3] = block.getY();
            
            double oreRatio = (double) state.oresFound / Math.max(1, state.blocksBroken);
            features[4] = oreRatio;
            
            pipeline.serverContext.updateServerOreRatio(oreRatio);
            double zScore = pipeline.serverContext.getZScore(oreRatio, pipeline.serverContext.getServerOreRatio(), 0.05);
            features[5] = zScore;

            boolean isDecoy = plugin.getContext().getDecoyService().isDecoy(block.getLocation());
            double lookDeviationDegrees = calculateLookDeviation(player, block);
            int visibleOresBypassed = countVisibleOresBypassed(player, block);
            double effectiveMiningSpeed = calculateEffectiveMiningSpeed(player);
            
            double adjustedOreRatioThreshold = 0.15;
            if (effectiveMiningSpeed >= 3.0) {
                adjustedOreRatioThreshold = 0.35;
            }
            if (effectiveMiningSpeed >= 5.0) {
                adjustedOreRatioThreshold = 0.50;
            }
            
            int points = InstantFilterRules.evaluateXray(
                isDecoy,
                lookDeviationDegrees,
                visibleOresBypassed,
                oreRatio,
                pipeline.serverContext.getServerOreRatio(),
                adjustedOreRatioThreshold
            );

            pipeline.processInstantFilter(player, CheatCategory.XRAY, points, "BlockBreak Rules");

            pipeline.triggerTemporalAnalysis(player, CheatCategory.XRAY, features);

            state.lastOreTime = now;
            state.lastOreLoc = block.getLocation();
        }
    }

    private double calculateLookDeviation(Player player, Block targetBlock) {
        Location eyeLocation = player.getEyeLocation();
        Vector lookDirection = eyeLocation.getDirection();
        Vector directionToBlock = targetBlock.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eyeLocation.toVector()).normalize();
        
        double angleRadians = lookDirection.angle(directionToBlock);
        return Math.toDegrees(angleRadians);
    }

    private int countVisibleOresBypassed(Player player, Block brokenBlock) {
        int count = 0;
        Block playerBlock = player.getLocation().getBlock();
        
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        
        for (BlockFace face : faces) {
            for (int i = 1; i <= 5; i++) {
                Block checkBlock = playerBlock.getRelative(face, i);
                if (isOre(checkBlock.getType()) && !checkBlock.getLocation().equals(brokenBlock.getLocation())) {
                    count++;
                }
            }
        }
        return count;
    }

    private double calculateEffectiveMiningSpeed(Player player) {
        double baseSpeed = 1.0;
        
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.hasItemMeta()) {
            Map<org.bukkit.enchantments.Enchantment, Integer> enchantments = item.getItemMeta().getEnchants();
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchantments.entrySet()) {
                if (entry.getKey().getKey().getKey().equals("efficiency")) {
                    baseSpeed += entry.getValue();
                    break;
                }
            }
        }
        
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().getKey().getKey().equals("haste")) {
                baseSpeed += (effect.getAmplifier() + 1) * 0.5;
            }
        }
        
        return baseSpeed;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        UUID uuid = attacker.getUniqueId();
        PlayerSessionState state = getState(uuid);
        long now = System.currentTimeMillis();

        if (attacker.getWorld() == null || target.getWorld() == null || !attacker.getWorld().equals(target.getWorld())) {
            return;
        }

        double distance = attacker.getLocation().distance(target.getLocation());
        int ping = attacker.getPing();
        double maxReach = 3.0;

        Vector eyeToTarget = target.getLocation().toVector().subtract(attacker.getEyeLocation().toVector());
        Vector lookDirection = attacker.getEyeLocation().getDirection();
        float angleDiff = lookDirection.angle(eyeToTarget);
        
        double deltaYaw = Math.abs(attacker.getLocation().getYaw() - state.lastYaw);

        int points = InstantFilterRules.evaluateCombat(
            deltaYaw, distance, maxReach, ping, state.ticksInAir, false, 5.0, state.speed, 0.4
        );
        
        pipeline.processInstantFilter(attacker, CheatCategory.COMBAT_MOVEMENT, points, "Combat Rules");

        double[] features = new double[40];
        features[0] = angleDiff;
        features[1] = distance;
        features[2] = ping;
        features[3] = (now - state.lastAttackTime);
        features[4] = deltaYaw;
        features[5] = state.speed;

        pipeline.triggerTemporalAnalysis(attacker, CheatCategory.COMBAT_MOVEMENT, features);

        state.lastAttackTime = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerSessionState state = getState(uuid);

        state.lastYaw = event.getFrom().getYaw();
        
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        state.speed = Math.sqrt(dx * dx + dz * dz);
        
        if (!player.isOnGround()) {
            state.ticksInAir++;
        } else {
            state.ticksInAir = 0;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionStates.remove(event.getPlayer().getUniqueId());
    }

    private boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type.name().equals("ANCIENT_DEBRIS");
    }

    private static class PlayerSessionState {
        UUID uuid;
        long sessionStart = System.currentTimeMillis();
        long lastOreTime = System.currentTimeMillis();
        long lastAttackTime = System.currentTimeMillis();
        int blocksBroken = 0;
        int oresFound = 0;
        Location lastOreLoc = null;
        
        float lastYaw = 0;
        int ticksInAir = 0;
        double speed = 0;

        PlayerSessionState(UUID uuid) {
            this.uuid = uuid;
        }
    }
}
