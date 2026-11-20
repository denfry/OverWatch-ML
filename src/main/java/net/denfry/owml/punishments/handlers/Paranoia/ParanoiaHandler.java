package net.denfry.owml.punishments.handlers.Paranoia;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.punishments.handlers.Paranoia.cooldown.CooldownManager;
import net.denfry.owml.punishments.handlers.Paranoia.effects.EnvironmentEffect;
import net.denfry.owml.punishments.handlers.Paranoia.effects.GhostEntityEffect;
import net.denfry.owml.punishments.handlers.Paranoia.effects.MessageEffect;
import net.denfry.owml.punishments.handlers.Paranoia.effects.NightmareEffect;
import net.denfry.owml.punishments.handlers.Paranoia.effects.SoundEffect;
import net.denfry.owml.punishments.handlers.Paranoia.effects.VisualEffect;
import net.denfry.owml.punishments.handlers.Paranoia.util.ParanoiaDataHolder;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class ParanoiaHandler implements Listener {
   private final OverWatchML plugin;
   private final PunishmentManager punishmentManager;
   private final ConfigManager configManager;
   private final CooldownManager cooldownManager;
   private final GhostEntityEffect ghostEntityEffect;
   private final MessageEffect messageEffect;
   private final NightmareEffect nightmareEffect;
   private final SoundEffect soundEffect;
   private final VisualEffect visualEffect;
   private final EnvironmentEffect environmentEffect;
   private final ParanoiaDataHolder dataHolder;

   public ParanoiaHandler(OverWatchML plugin, PunishmentManager punishmentManager, ConfigManager configManager) {
      this.plugin = plugin;
      this.punishmentManager = punishmentManager;
      this.configManager = configManager;
      this.cooldownManager = new CooldownManager();
      this.dataHolder = new ParanoiaDataHolder();
      this.ghostEntityEffect = new GhostEntityEffect(plugin, configManager, this.dataHolder);
      this.messageEffect = new MessageEffect(plugin, configManager);
      this.nightmareEffect = new NightmareEffect(plugin, configManager, this.dataHolder);
      this.soundEffect = new SoundEffect(plugin, configManager);
      this.visualEffect = new VisualEffect(plugin, configManager);
      this.environmentEffect = new EnvironmentEffect(plugin, configManager);
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   public void processBlockBreak(Player player, Block block) {
      UUID playerId = player.getUniqueId();
      boolean hasParanoiaMode = this.punishmentManager.hasParanoiaMode(playerId);
      boolean isBelowThreshold = player.getLocation().getY() < 30.0;
      if (hasParanoiaMode && isBelowThreshold) {
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Processing block break paranoia for " + player.getName() + " at Y:" + player.getLocation().getBlockY());
         }

         int y = block.getY();
         int baseChance = 5;
         int depthBonus = Math.max(0, (64 - y) / 5);
         int totalChance = Math.min(20, baseChance + depthBonus);
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Block break paranoia for " + player.getName() + " at Y:" + y + " with " + totalChance + "% chance");
         }

         if (ThreadLocalRandom.current().nextInt(100) < totalChance) {
            if (this.configManager.isDebugEnabled()) {
               this.plugin.getLogger().info("Paranoia effect triggered for " + player.getName() + " from block break");
            }

            this.applyRandomParanoiaEffect(player, block);
         }
      }
   }

   private void applyRandomParanoiaEffect(Player player, Block block) {
      int effect = ThreadLocalRandom.current().nextInt(10);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Applying random paranoia effect #" + effect + " for " + player.getName());
      }

      switch (effect) {
         case 0:
            if (!this.cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.CooldownType.SOUND)) {
               this.soundEffect.playParanoiaSound(player);
               this.cooldownManager.setCooldown(player.getUniqueId(), CooldownManager.CooldownType.SOUND);
            }
            break;
         case 1:
            if (!this.cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.CooldownType.GHOST_MOB)) {
               this.ghostEntityEffect.spawnGhostMob(player, block.getLocation());
               this.cooldownManager.setCooldown(player.getUniqueId(), CooldownManager.CooldownType.GHOST_MOB);
            }
            break;
         case 2:
            if (!this.cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.CooldownType.MESSAGE)) {
               this.messageEffect.sendFakeMessage(player);
               this.cooldownManager.setCooldown(player.getUniqueId(), CooldownManager.CooldownType.MESSAGE);
            }
            break;
         case 3:
            if (!this.cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.CooldownType.VISUAL)) {
               this.visualEffect.createVisualEffect(player, block.getLocation());
               this.cooldownManager.setCooldown(player.getUniqueId(), CooldownManager.CooldownType.VISUAL);
            }
            break;
         case 4:
            this.environmentEffect.simulateCaveIn(player, block);
            break;
         case 5:
            if (!this.cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.CooldownType.FAKE_DAMAGE)) {
               this.environmentEffect.applyFakeDamage(player);
               this.cooldownManager.setCooldown(player.getUniqueId(), CooldownManager.CooldownType.FAKE_DAMAGE);
            }
            break;
         case 6:
            this.environmentEffect.createFakeOreIllusion(player, block);
            break;
         case 7:
            this.visualEffect.applyBlindness(player);
            break;
         case 8:
            this.environmentEffect.createTeleportIllusion(player);
            break;
         case 9:
            this.environmentEffect.createFakeExplosion(player);
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (!(event.getFrom().distanceSquared(event.getTo()) < 0.01)) {
         boolean hasParanoiaMode = this.punishmentManager.hasParanoiaMode(playerId);
         boolean isBelowThreshold = player.getLocation().getY() < 30.0;
         if (hasParanoiaMode && isBelowThreshold) {
            if (ThreadLocalRandom.current().nextInt(1000) < 5) {
               if (this.configManager.isDebugEnabled()) {
                  this.plugin.getLogger().info("Movement paranoia effect triggered for " + player.getName() + " at Y:" + player.getLocation().getBlockY());
               }

               this.applyMovementParanoiaEffect(player);
            }
         }
      }
   }

   private void applyMovementParanoiaEffect(Player player) {
      int effect = ThreadLocalRandom.current().nextInt(5);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Applying movement paranoia effect #" + effect + " for " + player.getName());
      }

      switch (effect) {
         case 0:
            this.ghostEntityEffect.spawnPeripheralGhostMob(player);
            break;
         case 1:
            this.soundEffect.playDistantMiningSound(player);
            break;
         case 2:
            this.visualEffect.applyCameraShake(player);
            break;
         case 3:
            this.environmentEffect.applyTripwireEffect(player);
            break;
         case 4:
            this.ghostEntityEffect.createFakePlayerSighting(player);
      }
   }

   private void tickParanoiaPlayer(Player player, Map<UUID, Integer> failedNightmareRolls) {
      if (player.isOnline()) {
         UUID playerId = player.getUniqueId();
         boolean hasParanoiaMode = this.punishmentManager.hasParanoiaMode(playerId);
         boolean isBelowThreshold = player.getLocation().getY() < 30.0;
         if (hasParanoiaMode && isBelowThreshold) {
            int playerY = player.getLocation().getBlockY();
            if (playerY < 0) {
               boolean isOnCooldown = this.cooldownManager.isOnCooldown(playerId, CooldownManager.CooldownType.NIGHTMARE);
               boolean wasOnCooldownBefore = false;
               if (isOnCooldown) {
                  wasOnCooldownBefore = true;
               } else {
                  wasOnCooldownBefore = player.hasMetadata("nightmare_was_on_cooldown");
               }

               if (isOnCooldown) {
                  long remainingCooldown = this.cooldownManager.getRemainingCooldown(playerId, CooldownManager.CooldownType.NIGHTMARE);
                  if (this.configManager.isDebugEnabled()) {
                     this.plugin
                        .getLogger()
                        .info("Nightmare check skipped for " + player.getName() + " - still on cooldown for " + remainingCooldown / 1000L + " seconds");
                  }

                  if (failedNightmareRolls.containsKey(playerId)) {
                     if (this.configManager.isDebugEnabled()) {
                        this.plugin.getLogger().info("Nightmare tension reset for " + player.getName() + " - still on cooldown");
                     }

                     failedNightmareRolls.remove(playerId);
                  }

                  player.setMetadata("nightmare_was_on_cooldown", new FixedMetadataValue(this.plugin, true));
                  return;
               }

               if (wasOnCooldownBefore) {
                  if (this.configManager.isDebugEnabled()) {
                     this.plugin.getLogger().info("в\u0098… Nightmare cooldown expired for " + player.getName() + " - starting fresh tension building");
                  }

                  player.removeMetadata("nightmare_was_on_cooldown", this.plugin);
                  failedNightmareRolls.remove(playerId);
               }

               int baseChance = 5;
               int depthBonus = Math.abs(playerY) * 3;
               int consecutiveFailedRolls = failedNightmareRolls.getOrDefault(playerId, 0);
               int failedBonus = consecutiveFailedRolls * 5;
               int nightmareChance = Math.min(500, baseChance + depthBonus + failedBonus);
               if (this.configManager.isDebugEnabled()) {
                  this.plugin
                     .getLogger()
                     .info(
                        "Nightmare roll for "
                           + player.getName()
                           + " at Y:"
                           + playerY
                           + " with "
                           + nightmareChance / 10.0
                           + "% chance (base: "
                           + baseChance / 10.0
                           + "%, depth: "
                           + depthBonus / 10.0
                           + "%, tension: "
                           + failedBonus / 10.0
                           + "%, after "
                           + consecutiveFailedRolls
                           + " failed rolls)"
                     );
               }

               if (ThreadLocalRandom.current().nextInt(3000) < nightmareChance) {
                  if (this.configManager.isDebugEnabled()) {
                     this.plugin
                        .getLogger()
                        .info(
                           "в\u0098…в\u0098…в\u0098… NIGHTMARE EVENT triggered for "
                              + player.getName()
                              + " at Y:"
                              + playerY
                              + " after "
                              + consecutiveFailedRolls
                              + " failed rolls"
                        );
                  }

                  failedNightmareRolls.remove(playerId);
                  this.nightmareEffect.triggerNightmareEvent(player);
                  this.cooldownManager.setCooldown(playerId, CooldownManager.CooldownType.NIGHTMARE);
                  return;
               }

               failedNightmareRolls.put(playerId, consecutiveFailedRolls + 1);
               if (this.configManager.isDebugEnabled() && consecutiveFailedRolls > 3) {
                  this.plugin
                     .getLogger()
                     .info("Nightmare tension building for " + player.getName() + " - now at " + (consecutiveFailedRolls + 1) + " failed rolls");
               }
            } else if (failedNightmareRolls.containsKey(playerId)) {
               if (this.configManager.isDebugEnabled()) {
                  this.plugin.getLogger().info("Nightmare tension reset for " + player.getName() + " - moved above Y=0");
               }

               failedNightmareRolls.remove(playerId);
            }

            if (ThreadLocalRandom.current().nextInt(100) < 5) {
               int effect = ThreadLocalRandom.current().nextInt(3);
               if (this.configManager.isDebugEnabled()) {
                  this.plugin
                     .getLogger()
                     .info("Ambient paranoia effect #" + effect + " triggered for " + player.getName() + " at Y:" + player.getLocation().getBlockY());
               }

               switch (effect) {
                  case 0:
                     if (!this.cooldownManager.isOnCooldown(playerId, CooldownManager.CooldownType.SOUND)) {
                        this.soundEffect.playParanoiaSound(player);
                        this.cooldownManager.setCooldown(playerId, CooldownManager.CooldownType.SOUND);
                     }
                     break;
                  case 1:
                     if (ThreadLocalRandom.current().nextInt(3) == 0 && !this.cooldownManager.isOnCooldown(playerId, CooldownManager.CooldownType.MESSAGE)) {
                        this.messageEffect.sendFakeMessage(player);
                        this.cooldownManager.setCooldown(playerId, CooldownManager.CooldownType.MESSAGE);
                     }
                     break;
                  case 2:
                     if (ThreadLocalRandom.current().nextInt(5) == 0 && !this.cooldownManager.isOnCooldown(playerId, CooldownManager.CooldownType.VISUAL)) {
                        this.visualEffect.createVisualEffect(player, player.getLocation());
                        this.cooldownManager.setCooldown(playerId, CooldownManager.CooldownType.VISUAL);
                     }
               }
            }
         }
      }
   }

   public void startParanoiaTask() {
      final Map<UUID, Integer> failedNightmareRolls = new ConcurrentHashMap<>();
      (new Schedulers.Repeating() {
            @Override
            public void run() {
               int affectedPlayers = 0;
               int effectsTriggered = 0;
               int nightmareRolls = 0;

               for (Player player : Bukkit.getOnlinePlayers()) {
                  if (ParanoiaHandler.this.punishmentManager.hasParanoiaMode(player.getUniqueId())) {
                     affectedPlayers++;
                     Schedulers.entity(player, () -> ParanoiaHandler.this.tickParanoiaPlayer(player, failedNightmareRolls));
                  }
               }

               for (Player playerx : Bukkit.getOnlinePlayers()) {
                  if (ParanoiaHandler.this.dataHolder.getAllGhostEntities().containsKey(playerx.getUniqueId())) {
                     Schedulers.entity(playerx, () -> ParanoiaHandler.this.ghostEntityEffect.trackAndUpdateGhostMobs(playerx));
                  }
               }

               if (ParanoiaHandler.this.configManager.isDebugEnabled() && affectedPlayers > 0) {
                  ParanoiaHandler.this.plugin
                     .getLogger()
                     .info("Paranoia task: " + affectedPlayers + " players affected, " + effectsTriggered + " ambient effects triggered");
               }
            }
         })
         .globalTimer(60L, 60L);
   }

   public void triggerNightmare(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.cooldownManager.isOnCooldown(playerId, CooldownManager.CooldownType.NIGHTMARE)) {
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Manual nightmare trigger skipped for " + player.getName() + " - still on cooldown");
         }
      } else {
         this.nightmareEffect.triggerNightmareEvent(player);
         this.cooldownManager.setCooldown(playerId, CooldownManager.CooldownType.NIGHTMARE);
      }
   }

   public void cleanup() {
      this.ghostEntityEffect.cleanup();
      this.nightmareEffect.cleanup();
   }

   public long getRemainingCooldown(UUID playerId, CooldownManager.CooldownType type) {
      return this.cooldownManager.getRemainingCooldown(playerId, type);
   }

   public boolean isOnCooldown(UUID playerId, CooldownManager.CooldownType type) {
      return this.cooldownManager.isOnCooldown(playerId, type);
   }
}
