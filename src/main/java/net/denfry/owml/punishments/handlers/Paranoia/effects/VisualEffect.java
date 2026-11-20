package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.punishments.handlers.Paranoia.util.LocationUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class VisualEffect {
   private final OverWatchML plugin;
   private final ConfigManager configManager;

   public VisualEffect(OverWatchML plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
   }

   public void createVisualEffect(final Player player, Location location) {
      final int effectType = ThreadLocalRandom.current().nextInt(5);
      final World world = location.getWorld();
      final int burstCount = 4 + ThreadLocalRandom.current().nextInt(3);

      for (int burst = 0; burst < burstCount; burst++) {
         final int finalBurst = burst;
         (new Schedulers.Repeating() {
               @Override
               public void run() {
                  if (player.isOnline()) {
                     switch (effectType) {
                        case 0:
                           for (int i = 0; i < 3; i++) {
                              Location particleLoc = LocationUtils.getRandomNearbyLocation(player.getLocation(), 5.0);
                              world.spawnParticle(Particle.PORTAL, particleLoc, 50, 0.5, 0.5, 0.5, 0.1);
                              if (finalBurst == burstCount - 1 && i == 0) {
                                 player.playSound(particleLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4F, 0.7F);
                              }
                           }
                           break;
                        case 1:
                           Location bloodLoc = LocationUtils.getRandomNearbyLocation(player.getLocation(), 3.0);
                           float size = 1.0F + finalBurst % 2 * 0.5F;
                           DustOptions dustOptions = new DustOptions(Color.RED, size);
                           world.spawnParticle(Particle.DUST, bloodLoc, 30, 0.5, 0.1, 0.5, dustOptions);
                           if (finalBurst % 2 == 0) {
                              world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, bloodLoc.clone().add(0.0, 0.5, 0.0), 10, 0.3, 0.1, 0.3, 0.01);
                           }
                           break;
                        case 2:
                           double angle = finalBurst * (Math.PI / 8);
                           double radius = 3.0;
                           double x = player.getLocation().getX() + Math.cos(angle) * radius;
                           double z = player.getLocation().getZ() + Math.sin(angle) * radius;
                           Location shadowLoc = new Location(world, x, player.getLocation().getY(), z);
                           DustOptions shadowDust = new DustOptions(Color.BLACK, 2.0F);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(0.0, 1.8, 0.0), 8, 0.1, 0.1, 0.1, shadowDust);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(0.0, 1.4, 0.0), 15, 0.1, 0.3, 0.1, shadowDust);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(-0.2, 1.4, 0.0), 8, 0.1, 0.3, 0.1, shadowDust);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(0.2, 1.4, 0.0), 8, 0.1, 0.3, 0.1, shadowDust);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(-0.1, 0.8, 0.0), 8, 0.1, 0.4, 0.1, shadowDust);
                           world.spawnParticle(Particle.DUST, shadowLoc.clone().add(0.1, 0.8, 0.0), 8, 0.1, 0.4, 0.1, shadowDust);
                           if (finalBurst == burstCount - 1) {
                              DustOptions eyeDust = new DustOptions(Color.RED, 0.8F);
                              world.spawnParticle(Particle.DUST, shadowLoc.clone().add(-0.1, 1.8, 0.2), 3, 0.05, 0.05, 0.05, eyeDust);
                              world.spawnParticle(Particle.DUST, shadowLoc.clone().add(0.1, 1.8, 0.2), 3, 0.05, 0.05, 0.05, eyeDust);
                              player.playSound(shadowLoc, Sound.ENTITY_GHAST_WARN, 0.2F, 0.5F);
                           }
                           break;
                        case 3:
                           for (int i = 0; i < 5; i++) {
                              double fogAngle = finalBurst * 0.5 + i * 2 * Math.PI / 5.0;
                              double fogRadius = 3.0 + finalBurst * 0.5;
                              double fogX = player.getLocation().getX() + Math.cos(fogAngle) * fogRadius;
                              double fogZ = player.getLocation().getZ() + Math.sin(fogAngle) * fogRadius;
                              Location fogLoc = new Location(world, fogX, player.getLocation().getY(), fogZ);
                              world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, fogLoc, 10, 0.2, 0.2, 0.2, 0.02);
                           }

                           float volume = 0.3F + finalBurst * 0.1F;
                           player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, volume, 0.5F);
                           break;
                        case 4:
                           int eyePairs = 1 + finalBurst / 2;

                           for (int i = 0; i < eyePairs; i++) {
                              Location eyesLoc = LocationUtils.getRandomNearbyLocation(player.getLocation(), 8.0);
                              DustOptions glowOptions = new DustOptions(Color.fromRGB(255, 0, 0), 0.7F);
                              world.spawnParticle(Particle.DUST, eyesLoc.clone().add(-0.2, 0.0, 0.0), 3, 0.05, 0.05, 0.05, glowOptions);
                              world.spawnParticle(Particle.DUST, eyesLoc.clone().add(0.2, 0.0, 0.0), 3, 0.05, 0.05, 0.05, glowOptions);
                           }

                           if (finalBurst == burstCount - 1) {
                              player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.4F, 0.5F);
                           }
                     }

                     if (ThreadLocalRandom.current().nextInt(100) < 40) {
                        player.setVelocity(
                           new Vector(
                              (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02,
                              (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02,
                              (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02
                           )
                        );
                     }
                  }
               }
            })
            .entityLater(player, burst * 20L);
      }

      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Created visual effect type " + effectType + " for " + player.getName() + " with " + burstCount + " bursts");
      }
   }

   public void applyBlindness(final Player player) {
      int duration = 40 + ThreadLocalRandom.current().nextInt(40);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Applying blindness effect to " + player.getName() + " for " + duration / 20 + " seconds");
      }

      player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0, false, false, false));
      player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.7F);
      (new Schedulers.Repeating() {
         int count = 0;

         @Override
         public void run() {
            if (player.isOnline() && player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
               for (int i = 0; i < 3; i++) {
                  double angle = this.count * 0.5 + i * 2 * Math.PI / 3.0;
                  double x = Math.cos(angle) * 1.5;
                  double z = Math.sin(angle) * 1.5;
                  Location particleLoc = player.getLocation().add(x, 1.0, z);
                  player.spawnParticle(Particle.PORTAL, particleLoc, 5, 0.1, 0.1, 0.1, 0.02);
               }

               this.count++;
            } else {
               this.cancel();
            }
         }
      }).entityTimer(player, 0L, 5L);
      player.sendMessage(ChatColor.DARK_GRAY + "A wave of darkness washes over your vision...");
      if (ThreadLocalRandom.current().nextInt(100) < 40) {
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (player.isOnline() && player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                  String[] whispers = new String[]{"behind you...", "watch out...", "they're coming...", "run...", "too late..."};
                  String whisper = whispers[ThreadLocalRandom.current().nextInt(whispers.length)];
                  player.sendMessage("" + ChatColor.DARK_GRAY + ChatColor.ITALIC + whisper);
                  player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.5F);
               }
            }
         }).entityLater(player, duration / 2);
      }
   }

   public void applyCameraShake(final Player player) {
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Applying camera shake effect to " + player.getName());
      }

      player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false, false));

      for (int i = 0; i < 5; i++) {
         (new Schedulers.Repeating() {
               @Override
               public void run() {
                  if (player.isOnline()) {
                     player.setVelocity(
                        new Vector(
                           (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                           (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                           (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03
                        )
                     );
                  }
               }
            })
            .entityLater(player, i * 2L);
      }

      player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.5F, 0.7F);
      if (ThreadLocalRandom.current().nextBoolean()) {
         player.sendMessage(ChatColor.GRAY + "You feel the cave trembling slightly around you...");
      }
   }
}
