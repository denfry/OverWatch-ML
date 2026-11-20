package net.denfry.owml.ml.v2.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.v2.core.CheatCategory;
import net.denfry.owml.ml.v2.filters.InstantFilterRules;
import net.denfry.owml.ml.v2.pipeline.DetectionPipeline;
import net.denfry.owml.utils.Locs;
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

public class MLDataListener implements Listener {
   private final OverWatchML plugin;
   private final DetectionPipeline pipeline;
   private final Map<UUID, MLDataListener.PlayerSessionState> sessionStates = new ConcurrentHashMap<>();

   public MLDataListener(OverWatchML plugin, DetectionPipeline pipeline) {
      this.plugin = plugin;
      this.pipeline = pipeline;
   }

   private MLDataListener.PlayerSessionState getState(UUID uuid) {
      return this.sessionStates.computeIfAbsent(uuid, MLDataListener.PlayerSessionState::new);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("owml.bypass")) {
         Block block = event.getBlock();
         UUID uuid = player.getUniqueId();
         MLDataListener.PlayerSessionState state = this.getState(uuid);
         state.blocksBroken++;
         if (this.isOre(block.getType())) {
            state.oresFound++;
            double[] features = new double[32];
            long now = System.currentTimeMillis();
            double timeSinceLastOre = (now - state.lastOreTime) / 1000.0;
            features[0] = timeSinceLastOre;
            features[1] = state.oresFound / Math.max(1.0, (now - state.sessionStart) / 60000.0);
            double dist = 0.0;
            if (state.lastOreLoc != null && state.lastOreLoc.getWorld() != null && state.lastOreLoc.getWorld().equals(block.getWorld())) {
               dist = Locs.distance(block.getLocation(), state.lastOreLoc);
            }

            features[2] = dist;
            features[3] = block.getY();
            double oreRatio = (double)state.oresFound / Math.max(1, state.blocksBroken);
            features[4] = oreRatio;
            this.pipeline.serverContext.updateServerOreRatio(oreRatio);
            double zScore = this.pipeline.serverContext.getZScore(oreRatio, this.pipeline.serverContext.getServerOreRatio(), 0.05);
            features[5] = zScore;
            boolean isDecoy = this.plugin.getContext().getDecoyService().isDecoy(block.getLocation());
            if (isValuableOre(block.getType())) {
               state.valuableOres++;
            }

            int points = InstantFilterRules.evaluateXray(
               isDecoy,
               state.valuableOres,
               state.blocksBroken,
               this.plugin.getConfig().getInt("ml.xray.min-blocks", 500),
               this.plugin.getConfig().getDouble("ml.xray.max-valuable-ratio", 0.02)
            );
            this.pipeline.processInstantFilter(player, CheatCategory.XRAY, points, "BlockBreak Rules");
            this.pipeline.triggerTemporalAnalysis(player, CheatCategory.XRAY, features);
            state.lastOreTime = now;
            state.lastOreLoc = block.getLocation();
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player attacker) {
         if (event.getEntity() instanceof Player target) {
            UUID var21 = attacker.getUniqueId();
            MLDataListener.PlayerSessionState state = this.getState(var21);
            long now = System.currentTimeMillis();
            if (attacker.getWorld() != null && target.getWorld() != null && attacker.getWorld().equals(target.getWorld())) {
               double distance = Locs.distance(attacker.getLocation(), target.getLocation());
               int ping = attacker.getPing();
               double maxReach = 3.0;
               Vector eyeToTarget = target.getLocation().toVector().subtract(attacker.getEyeLocation().toVector());
               Vector lookDirection = attacker.getEyeLocation().getDirection();
               float angleDiff = lookDirection.angle(eyeToTarget);
               double deltaYaw = Math.abs(attacker.getLocation().getYaw() - state.lastYaw);
               int points = InstantFilterRules.evaluateCombat(deltaYaw, distance, maxReach, ping, state.ticksInAir, false, 5.0, state.speed, 0.4);
               this.pipeline.processInstantFilter(attacker, CheatCategory.COMBAT_MOVEMENT, points, "Combat Rules");
               double[] features = new double[40];
               features[0] = angleDiff;
               features[1] = distance;
               features[2] = ping;
               features[3] = now - state.lastAttackTime;
               features[4] = deltaYaw;
               features[5] = state.speed;
               this.pipeline.triggerTemporalAnalysis(attacker, CheatCategory.COMBAT_MOVEMENT, features);
               state.lastAttackTime = now;
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      MLDataListener.PlayerSessionState state = this.getState(uuid);
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
      this.sessionStates.remove(event.getPlayer().getUniqueId());
   }

   private boolean isOre(Material type) {
      return type.name().endsWith("_ORE") || type.name().equals("ANCIENT_DEBRIS");
   }

   static boolean isValuableOre(Material type) {
      return switch (type) {
         case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE, ANCIENT_DEBRIS -> true;
         default -> false;
      };
   }

   private static class PlayerSessionState {
      UUID uuid;
      long sessionStart = System.currentTimeMillis();
      long lastOreTime = System.currentTimeMillis();
      long lastAttackTime = System.currentTimeMillis();
      int blocksBroken = 0;
      int oresFound = 0;
      int valuableOres = 0;
      Location lastOreLoc = null;
      float lastYaw = 0.0F;
      int ticksInAir = 0;
      double speed = 0.0;

      PlayerSessionState(UUID uuid) {
         this.uuid = uuid;
      }
   }
}
