package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.punishments.handlers.Paranoia.util.LocationUtils;
import net.denfry.owml.punishments.handlers.Paranoia.util.ParanoiaDataHolder;
import net.denfry.owml.utils.SchedulerUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class NightmareEffect {
   private final OverWatchML plugin;
   private final ConfigManager configManager;
   private final ParanoiaDataHolder dataHolder;
   private final String[] creepyMessages = new String[]{
      "HE IS WATCHING YOU",
      "YOU CANNOT ESCAPE MY GAZE",
      "I SEE EVERYTHING YOU MINE",
      "YOUR SINS ARE KNOWN TO ME",
      "NOWHERE TO HIDE",
      "I AM ALWAYS WATCHING",
      "THE DARKNESS REMEMBERS",
      "THE VOID CLAIMS ALL CHEATERS",
      "YOUR MINING PATTERNS HAVE BEEN RECORDED"
   };

   public NightmareEffect(OverWatchML plugin, ConfigManager configManager, ParanoiaDataHolder dataHolder) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.dataHolder = dataHolder;
   }

   public void triggerNightmareEvent(final Player player) {
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Starting nightmare event for " + player.getName());
      }

      Location playerLoc = player.getLocation().clone();
      final World world = player.getWorld();
      final LocationUtils.LocationHolder wardenLocationHolder = new LocationUtils.LocationHolder();
      Vector direction = player.getLocation().getDirection().normalize();
      double spawnDistance = 10.0 + ThreadLocalRandom.current().nextDouble() * 5.0;
      Location possibleLocation = player.getEyeLocation().add(direction.multiply(spawnDistance));
      Location safeLocation = LocationUtils.findSafeLocationNearby(possibleLocation);
      if (safeLocation == null) {
         safeLocation = LocationUtils.findValidSpawnLocationInSight(player.getLocation(), world, 8.0, 16.0);
         if (safeLocation == null) {
            if (this.configManager.isDebugEnabled()) {
               this.plugin.getLogger().info("Cancelled Nightmare Event for " + player.getName() + " - no valid spawn location");
            }

            return;
         }
      }

      wardenLocationHolder.location = safeLocation;
      if (this.configManager.isDebugEnabled()) {
         double distance = wardenLocationHolder.location.distance(player.getLocation());
         this.plugin.getLogger().info("Nightmare Event for " + player.getName() + " - Warden spawning " + distance + " blocks away");
      }

      int[] delays = new int[]{40, 35, 30, 25, 22, 20, 18, 16, 14, 12, 10, 8, 6, 5, 4, 3, 2, 1};
      final Sound[] breakingSounds = new Sound[]{Sound.BLOCK_STONE_BREAK, Sound.BLOCK_GRAVEL_BREAK, Sound.BLOCK_WOOD_BREAK};
      player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0F, 0.5F);
      player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 220, 0, false, false, false));
      player.playSound(wardenLocationHolder.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3F, 0.6F);

      for (int i = 0; i < delays.length; i++) {
         final int index = i;
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (player.isOnline()) {
                  double angle = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
                  double distance = 1.0 + ThreadLocalRandom.current().nextDouble() * 3.0;
                  double x = wardenLocationHolder.location.getX() + Math.cos(angle) * distance;
                  double z = wardenLocationHolder.location.getZ() + Math.sin(angle) * distance;
                  Location soundLoc = new Location(world, x, wardenLocationHolder.location.getY(), z);
                  Sound sound = breakingSounds[ThreadLocalRandom.current().nextInt(breakingSounds.length)];
                  float volume = 0.3F + index * 0.04F;
                  float pitch = 0.8F - index * 0.03F;
                  player.playSound(soundLoc, sound, volume, pitch);
                  if (index > 8) {
                     world.spawnParticle(Particle.FALLING_DUST, soundLoc, 10, 0.5, 0.5, 0.5, 0.05, Bukkit.createBlockData(Material.SCULK));
                  }

                  if (index > 12) {
                     world.spawnParticle(Particle.SOUL, wardenLocationHolder.location, 20, 1.0, 0.5, 1.0, 0.01);
                     world.spawnParticle(Particle.PORTAL, wardenLocationHolder.location, 15, 0.7, 0.3, 0.7, 0.01);
                     if (index % 3 == 0) {
                        player.playSound(soundLoc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 0.5F);
                     }
                  }
               }
            }
         }).entityLater(player, i * delays[i]);
      }

      (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (player.isOnline()) {
                  Vector lookDirection = wardenLocationHolder.location.clone().add(0.0, 1.0, 0.0).subtract(player.getEyeLocation()).toVector().normalize();
                  Location playerLookLoc = player.getLocation().clone();
                  playerLookLoc.setDirection(lookDirection);
                  player.teleportAsync(playerLookLoc);
                  world.spawnParticle(Particle.SOUL, wardenLocationHolder.location, 20, 1.0, 0.5, 1.0, 0.01);
                  world.spawnParticle(Particle.PORTAL, wardenLocationHolder.location, 15, 0.7, 0.3, 0.7, 0.01);
                  player.playSound(wardenLocationHolder.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0F, 0.6F);
                  player.playSound(wardenLocationHolder.location, Sound.ENTITY_WARDEN_EMERGE, 1.0F, 1.0F);
                  (new Schedulers.Repeating() {
                        int count = 0;

                        @Override
                        public void run() {
                           if (player.isOnline() && this.count < 3) {
                              player.setVelocity(
                                 new Vector(
                                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03, 0.05, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03
                                 )
                              );

                              for (int i = 0; i < 3; i++) {
                                 double angle = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
                                 double dist = ThreadLocalRandom.current().nextDouble() * 1.5;
                                 Location particleLoc = wardenLocationHolder.location.clone().add(Math.cos(angle) * dist, 0.1, Math.sin(angle) * dist);
                                 world.spawnParticle(Particle.FALLING_DUST, particleLoc, 15, 0.5, 0.1, 0.5, 0.05, Bukkit.createBlockData(Material.SCULK));
                              }

                              this.count++;
                           } else {
                              this.cancel();
                           }
                        }
                     })
                     .entityTimer(player, 0L, 5L);
                  (new Schedulers.Repeating() {
                     @Override
                     public void run() {
                        final Warden warden = (Warden)world.spawnEntity(wardenLocationHolder.location, EntityType.WARDEN);
                        warden.setPersistent(false);
                        warden.setMetadata("nightmare_ghost", new FixedMetadataValue(NightmareEffect.this.plugin, true));
                        warden.setAI(true);
                        warden.setAnger(player, 150);

                        for (int i = 0; i < 5; i++) {
                           (new Schedulers.Repeating() {
                              @Override
                              public void run() {
                                 if (player.isOnline() && !warden.isDead()) {
                                    world.spawnParticle(Particle.SOUL, warden.getLocation().add(0.0, 0.5, 0.0), 15, 0.7, 0.5, 0.7, 0.01);
                                    DustOptions dustOptions = new DustOptions(Color.fromRGB(15, 82, 186), 1.0F);
                                    world.spawnParticle(Particle.DUST, warden.getLocation().add(0.0, 0.1, 0.0), 20, 0.7, 0.1, 0.7, 0.05, dustOptions);
                                 }
                              }
                           }).entityLater(player, i * 8L);
                        }

                        (new Schedulers.Repeating() {
                           @Override
                           public void run() {
                              if (player.isOnline() && !warden.isDead()) {
                                 player.playSound(warden.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 1.0F, 0.6F);
                                 warden.setAI(false);
                                 NightmareEffect.this.handleWardenStaring(player, warden);
                              }
                           }
                        }).entityLater(player, 40L);
                     }
                  }).entityLater(player, 20L);
               }
            }
         })
         .entityLater(player, 200L);
   }

   private void handleWardenStaring(final Player player, final Warden warden) {
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Starting Warden staring phase for " + player.getName());
      }

      int STARING_DURATION_TICKS = 100;
      (new Schedulers.Repeating() {
            int ticksRemaining = 100;

            @Override
            public void run() {
               if (player.isOnline() && warden != null && !warden.isDead()) {
                  Vector direction = warden.getLocation().clone().add(0.0, 2.0, 0.0).subtract(player.getEyeLocation()).toVector().normalize();
                  Location playerLoc = player.getLocation();
                  playerLoc.setDirection(direction);
                  player.teleportAsync(playerLoc);
                  if (this.ticksRemaining % 20 == 0) {
                     Sound[] wardenSounds = new Sound[]{
                        Sound.ENTITY_WARDEN_HEARTBEAT, Sound.ENTITY_WARDEN_LISTENING, Sound.ENTITY_WARDEN_LISTENING_ANGRY, Sound.ENTITY_WARDEN_SNIFF
                     };
                     Sound sound = wardenSounds[ThreadLocalRandom.current().nextInt(wardenSounds.length)];
                     player.playSound(warden.getLocation(), sound, 1.0F, 0.5F);
                     if (this.ticksRemaining < 50) {
                        for (int i = 0; i < 8; i++) {
                           double angle = i * Math.PI / 4.0;
                           Location particleLoc = player.getEyeLocation().add(Math.cos(angle) * 0.5, Math.sin(angle) * 0.5, 0.0);
                           DustOptions darkDust;
                           if (i % 2 == 0) {
                              darkDust = new DustOptions(Color.fromRGB(0, 0, 50), 2.0F);
                           } else {
                              darkDust = new DustOptions(Color.BLACK, 2.0F);
                           }

                           player.spawnParticle(Particle.DUST, particleLoc, 1, 0.0, 0.0, 0.0, 0.0, darkDust);
                        }
                     }
                  }

                  if (this.ticksRemaining % 5 == 0) {
                     Vector wardenLookDir = player.getEyeLocation().subtract(warden.getLocation()).toVector().normalize();
                     Location wardenLookLoc = warden.getLocation().clone();
                     wardenLookLoc.setDirection(wardenLookDir);
                     warden.teleportAsync(wardenLookLoc);
                     if (this.ticksRemaining < 50 && this.ticksRemaining % 15 == 0) {
                        warden.getWorld().spawnParticle(Particle.EXPLOSION, warden.getLocation().add(0.0, 2.0, 0.0), 2, 0.1, 0.1, 0.1, 0.0);
                        warden.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, warden.getLocation().add(0.0, 2.0, 0.0), 15, 0.3, 0.3, 0.3, 0.05);
                     }
                  }

                  if (this.ticksRemaining < 40 && this.ticksRemaining % 10 == 0 && warden.getLocation().distance(player.getLocation()) > 4.0) {
                     Vector moveVector = player.getLocation().toVector().subtract(warden.getLocation().toVector()).normalize().multiply(1.0);
                     Location newLoc = warden.getLocation().add(moveVector);
                     warden.teleportAsync(newLoc);
                     player.playSound(warden.getLocation(), Sound.ENTITY_WARDEN_STEP, 1.0F, 0.7F);
                     DustOptions sculkDust = new DustOptions(Color.fromRGB(15, 82, 186), 1.0F);
                     warden.getWorld().spawnParticle(Particle.DUST, warden.getLocation(), 15, 0.5, 0.1, 0.5, 0.05, sculkDust);
                     warden.getWorld().spawnParticle(Particle.SOUL, warden.getLocation(), 8, 0.3, 0.1, 0.3, 0.01);
                  }

                  player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 6, false, false, false));
                  player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20, 128, false, false, false));
                  this.ticksRemaining--;
                  if (this.ticksRemaining == 10) {
                     player.playSound(warden.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0F, 0.8F);
                     warden.getWorld().spawnParticle(Particle.EXPLOSION, warden.getLocation().add(0.0, 2.0, 0.0), 2, 0.1, 0.1, 0.1, 0.0);
                     warden.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, warden.getLocation().add(0.0, 2.0, 0.0), 15, 0.3, 0.3, 0.3, 0.05);
                  }

                  if (this.ticksRemaining <= 0) {
                     if (NightmareEffect.this.configManager.isDebugEnabled()) {
                        NightmareEffect.this.plugin
                           .getLogger()
                           .info("Warden staring phase completed for " + player.getName() + " - moving to sonic boom phase");
                     }

                     warden.setAI(true);
                     warden.setAnger(player, 150);
                     player.playSound(warden.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0F, 0.8F);
                     (new Schedulers.Repeating() {
                           int count = 0;

                           @Override
                           public void run() {
                              if (this.count < 10 && player.isOnline() && !warden.isDead()) {
                                 player.setVelocity(
                                    new Vector(
                                       (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.05, 0.02, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.05
                                    )
                                 );
                                 if (this.count % 2 == 0) {
                                    warden.getWorld().spawnParticle(Particle.EXPLOSION, warden.getLocation().add(0.0, 1.5, 0.0), 1, 0.1, 0.1, 0.1, 0.0);
                                 }

                                 warden.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, warden.getLocation().add(0.0, 2.0, 0.0), 10, 0.3, 0.3, 0.3, 0.05);
                                 this.count++;
                              } else {
                                 this.cancel();
                              }
                           }
                        })
                        .entityTimer(player, 0L, 2L);
                     (new Schedulers.Repeating() {
                        @Override
                        public void run() {
                           if (player.isOnline() && !warden.isDead()) {
                              NightmareEffect.this.handleBlindnessPhase(player, warden);
                           }
                        }
                     }).entityLater(player, 35L);
                  }
               } else {
                  if (NightmareEffect.this.configManager.isDebugEnabled()) {
                     NightmareEffect.this.plugin.getLogger().info("Warden staring phase cancelled early for " + player.getName());
                  }

                  this.cancel();
               }
            }
         })
         .entityTimer(player, 0L, 1L);
   }

   private void handleBlindnessPhase(final Player player, Entity ghost) {
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Starting blindness phase for " + player.getName());
      }

      player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
      ghost.getWorld().spawnParticle(Particle.EXPLOSION, ghost.getLocation(), 3, 0.5, 1.0, 0.5, 0.01);
      ghost.getWorld().spawnParticle(Particle.PORTAL, ghost.getLocation(), 50, 0.5, 1.0, 0.5, 0.01);
      player.playSound(ghost.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 1.0F, 0.5F);
      player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0F, 0.5F);
      ghost.remove();
      (new Schedulers.Repeating() {
         @Override
         public void run() {
            if (!player.isOnline()) {
               if (NightmareEffect.this.configManager.isDebugEnabled()) {
                  NightmareEffect.this.plugin.getLogger().info("Blindness phase message cancelled - player offline: " + player.getName());
               }
            } else {
               String message = NightmareEffect.this.creepyMessages[ThreadLocalRandom.current().nextInt(NightmareEffect.this.creepyMessages.length)];
               if (NightmareEffect.this.configManager.isDebugEnabled()) {
                  NightmareEffect.this.plugin.getLogger().info("Sending nightmare message to " + player.getName() + ": " + message);
               }

               player.sendMessage("");
               player.sendMessage("" + ChatColor.DARK_RED + ChatColor.BOLD + message);
               player.sendMessage("");
               player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 0.5F);
               player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0, false, false, false));
            }
         }
      }).entityLater(player, 40L);
   }

   public void cleanup() {
      int removedEntities = 0;
      if (!SchedulerUtils.isFolia()) {
         for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
               if (entity.hasMetadata("nightmare_ghost")) {
                  entity.remove();
                  removedEntities++;
               }
            }
         }
      }

      if (this.configManager.isDebugEnabled() && removedEntities > 0) {
         this.plugin.getLogger().info("NightmareEffect cleanup: removed " + removedEntities + " remaining nightmare entities");
      }
   }
}
