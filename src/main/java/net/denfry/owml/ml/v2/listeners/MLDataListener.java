package net.denfry.owml.ml.v2.listeners;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.v2.core.CheatCategory;
import net.denfry.owml.ml.v2.filters.InstantFilterRules;
import net.denfry.owml.ml.v2.pipeline.DetectionPipeline;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MLDataListener implements Listener {
    private final OverWatchML plugin;
    private final DetectionPipeline pipeline;

    // РЎС‚РµР№С‚-С‚СЂРµРєРµСЂС‹ РґР»СЏ СЃР±РѕСЂР° С„РёС‡РµР№
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
            
            // XRAY FEATURE EXTRACTION
            double[] features = new double[32];
            long now = System.currentTimeMillis();
            
            // 1. Р’СЂРµРјРµРЅРЅС‹Рµ РїСЂРёР·РЅР°РєРё
            double timeSinceLastOre = (now - state.lastOreTime) / 1000.0;
            features[0] = timeSinceLastOre;
            features[1] = state.oresFound / (Math.max(1, (now - state.sessionStart) / 60000.0)); // Ores per min
            
            // 2. Spatial features
            double dist = 0;
            if (state.lastOreLoc != null && state.lastOreLoc.getWorld() != null && 
                state.lastOreLoc.getWorld().equals(block.getWorld())) {
                dist = block.getLocation().distance(state.lastOreLoc);
            }
            features[2] = dist;
            features[3] = block.getY(); // Y-level variance can be calculated later, storing Y for now
            
            // 3. Ore-specific
            double oreRatio = (double) state.oresFound / Math.max(1, state.blocksBroken);
            features[4] = oreRatio;
            
            // 4. Server context normalization
            pipeline.serverContext.updateServerOreRatio(oreRatio);
            double zScore = pipeline.serverContext.getZScore(oreRatio, pipeline.serverContext.getServerOreRatio(), 0.05); // pseudo std-dev
            features[5] = zScore;

            // ... РѕСЃС‚Р°Р»СЊРЅР°СЏ 32-РјРµСЂРЅР°СЏ Р»РѕРіРёРєР° Р·Р°РїРѕР»РЅСЏРµС‚СЃСЏ 0.0 РєР°Рє РґРµС„РѕР»С‚РЅС‹РјРё Р·РЅР°С‡РµРЅРёСЏРјРё РґР»СЏ РїСЂРёРјРµСЂР°
            // Р’ СЂРµР°Р»СЊРЅРѕРј РїСЂРѕРґРµ Р·РґРµСЃСЊ РёРґРµС‚ Р·Р°РїРѕР»РЅРµРЅРёРµ РІСЃРµС… 32 С„РёС‡.

            // TIER 1: INSTANT FILTER
            boolean isDecoy = plugin.getContext().getDecoyService().isDecoy(block.getLocation());
            int points = InstantFilterRules.evaluateXray(
                isDecoy,
                10.0, // Look deviation mock
                0, // Bypassed ores mock
                oreRatio,
                pipeline.serverContext.getServerOreRatio()
            );

            pipeline.processInstantFilter(player, CheatCategory.XRAY, points, "BlockBreak Rules");

            // TIER 2: TEMPORAL ANOMALY
            pipeline.triggerTemporalAnalysis(player, CheatCategory.XRAY, features);

            state.lastOreTime = now;
            state.lastOreLoc = block.getLocation();
        }
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
        double maxReach = 3.0; // Base reach

        // TIER 1: INSTANT FILTER
        Vector eyeToTarget = target.getLocation().toVector().subtract(attacker.getEyeLocation().toVector());
        Vector lookDirection = attacker.getEyeLocation().getDirection();
        float angleDiff = lookDirection.angle(eyeToTarget);
        
        // РџСЂРёРјРµСЂ: СѓРіРѕР» РїРѕРІРѕСЂРѕС‚Р° РіРѕР»РѕРІС‹ РІ С‚РёРєРµ (Р·РґРµСЃСЊ Р·Р°РіР»СѓС€РєР° РґР»СЏ deltaYaw)
        double deltaYaw = Math.abs(attacker.getLocation().getYaw() - state.lastYaw);

        int points = InstantFilterRules.evaluateCombat(
            deltaYaw, distance, maxReach, ping, state.ticksInAir, false, 5.0, state.speed, 0.4
        );
        
        pipeline.processInstantFilter(attacker, CheatCategory.COMBAT_MOVEMENT, points, "Combat Rules");

        // COMBAT FEATURE EXTRACTION (40 features)
        double[] features = new double[40];
        features[0] = angleDiff;
        features[1] = distance;
        features[2] = ping;
        features[3] = (now - state.lastAttackTime); // TTK/Attack speed
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
        
        // Movement tracking
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        state.speed = Math.sqrt(dx * dx + dz * dz);
        
        if (!((org.bukkit.entity.Entity) player).isOnGround()) {
            state.ticksInAir++;
        } else {
            state.ticksInAir = 0;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionStates.remove(event.getPlayer().getUniqueId());
        // РџСЂРѕС„РёР»СЊ РЅРµ СѓРґР°Р»СЏРµРј СЃСЂР°Р·Сѓ РёР· РїР°Р№РїР»Р°Р№РЅР°, С‚Р°Рє РєР°Рє РјРѕР¶РµС‚ РёРґС‚Рё РѕС‚Р»РѕР¶РµРЅРЅС‹Р№ Р°РЅР°Р»РёР·
    }

    private boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type.name().equals("ANCIENT_DEBRIS");
    }

    /**
     * Р›РѕРєР°Р»СЊРЅС‹Р№ СЃС‚РµР№С‚ РёРіСЂРѕРєР° РґР»СЏ СЃР±РѕСЂР° С„РёС‡РµР№, РЅРµ СЏРІР»СЏРµС‚СЃСЏ РїРѕСЃС‚РѕСЏРЅРЅС‹Рј ML РїСЂРѕС„РёР»РµРј
     */
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
