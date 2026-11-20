package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.punishments.handlers.Paranoia.util.LocationUtils;
import net.denfry.owml.punishments.handlers.Paranoia.util.ParanoiaDataHolder;
import net.denfry.owml.utils.SchedulerUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class GhostEntityEffect {
   private final OverWatchML plugin;
   private final ConfigManager configManager;
   private final ParanoiaDataHolder dataHolder;

   public GhostEntityEffect(OverWatchML plugin, ConfigManager configManager, ParanoiaDataHolder dataHolder) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.dataHolder = dataHolder;
   }

   public void spawnGhostMob(final Player player, Location location) {
      final UUID playerId = player.getUniqueId();
      EntityType[] hostileMobs = new EntityType[]{
         EntityType.CREEPER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.WARDEN
      };
      EntityType mobType = hostileMobs[ThreadLocalRandom.current().nextInt(hostileMobs.length)];
      Location spawnLoc = LocationUtils.findSafeLocationNearby(location);
      if (spawnLoc != null) {
         final Entity ghostMob = spawnLoc.getWorld().spawnEntity(spawnLoc, mobType);
         ghostMob.setPersistent(false);
         ghostMob.setMetadata("ghost_mob", new FixedMetadataValue(this.plugin, true));
         if (ghostMob instanceof LivingEntity livingMob) {
            boolean willCharge = ThreadLocalRandom.current().nextInt(100) < 40;
            if (willCharge) {
               livingMob.setAI(true);
               if (livingMob instanceof Monster) {
                  ((Monster)livingMob).setTarget(player);
               }

               if (mobType == EntityType.CREEPER) {
                  Creeper creeper = (Creeper)livingMob;
                  creeper.setPowered(ThreadLocalRandom.current().nextBoolean());
                  if (ThreadLocalRandom.current().nextInt(100) < 30) {
                     player.playSound(creeper.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 1.0F);
                  }
               } else if (mobType == EntityType.ENDERMAN) {
                  Enderman enderman = (Enderman)livingMob;
                  enderman.setTarget(player);
               } else if (mobType == EntityType.SKELETON) {
                  player.playSound(livingMob.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 0.5F, 0.8F);
               }
            } else {
               livingMob.setAI(false);
               Vector direction = player.getLocation().toVector().subtract(livingMob.getLocation().toVector()).normalize();
               Location lookLocation = livingMob.getLocation().setDirection(direction);
               livingMob.teleportAsync(lookLocation);
            }

            if (mobType == EntityType.ENDERMAN) {
               Enderman enderman = (Enderman)livingMob;
               enderman.setTarget(player);
            }
         }

         this.dataHolder.addGhostEntity(playerId, ghostMob);
         String var13 = mobType.name();

         player.playSound(ghostMob.getLocation(), switch (var13) {
            case "CREEPER" -> Sound.ENTITY_CREEPER_PRIMED;
            case "ZOMBIE" -> Sound.ENTITY_ZOMBIE_AMBIENT;
            case "SKELETON" -> Sound.ENTITY_SKELETON_AMBIENT;
            case "SPIDER" -> Sound.ENTITY_SPIDER_AMBIENT;
            case "ENDERMAN" -> Sound.ENTITY_ENDERMAN_STARE;
            case "WITCH" -> Sound.ENTITY_WITCH_AMBIENT;
            case "WARDEN" -> Sound.ENTITY_WARDEN_ANGRY;
            default -> Sound.ENTITY_GENERIC_EXPLODE;
         }, 0.8F, 0.7F);
         int disappearTime = 80 + ThreadLocalRandom.current().nextInt(60);
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (!ghostMob.isDead()) {
                  ghostMob.getWorld().spawnParticle(Particle.SMOKE, ghostMob.getLocation().add(0.0, 1.0, 0.0), 30, 0.3, 0.5, 0.3, 0.05);
                  ghostMob.getWorld().spawnParticle(Particle.PORTAL, ghostMob.getLocation().add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.05);
                  player.playSound(ghostMob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 0.5F);
                  ghostMob.remove();
               }

               GhostEntityEffect.this.dataHolder.removeGhostEntity(playerId, ghostMob);
            }
         }).entityLater(player, disappearTime);
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Spawned ghost " + mobType.name() + " for " + player.getName());
         }
      }
   }

   public void spawnPeripheralGhostMob(Player player) {
      Location playerLoc = player.getLocation();
      Vector playerDir = playerLoc.getDirection();
      boolean leftSide = ThreadLocalRandom.current().nextBoolean();
      double angle = Math.toRadians(90 + ThreadLocalRandom.current().nextInt(30));
      if (leftSide) {
         angle = -angle;
      }

      double x = playerDir.getX();
      double z = playerDir.getZ();
      double newX = x * Math.cos(angle) - z * Math.sin(angle);
      double newZ = x * Math.sin(angle) + z * Math.cos(angle);
      Vector direction = new Vector(newX, 0.0, newZ).normalize();
      double distance = 8.0 + ThreadLocalRandom.current().nextDouble() * 4.0;
      Location spawnLoc = playerLoc.clone().add(direction.multiply(distance));
      spawnLoc.setY(playerLoc.getY());
      spawnLoc = LocationUtils.findSafeLocationNearby(spawnLoc);
      if (spawnLoc == null) {
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Peripheral ghost mob cancelled for " + player.getName() + " - no valid location found");
         }
      } else {
         EntityType[] hostileMobs = new EntityType[]{EntityType.CREEPER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ENDERMAN};
         EntityType mobType = hostileMobs[ThreadLocalRandom.current().nextInt(hostileMobs.length)];
         if (this.configManager.isDebugEnabled()) {
            this.plugin
               .getLogger()
               .info("Spawning peripheral ghost " + mobType.name() + " for " + player.getName() + " at distance " + player.getLocation().distance(spawnLoc));
         }

         final Entity ghostMob = spawnLoc.getWorld().spawnEntity(spawnLoc, mobType);
         ghostMob.setPersistent(false);
         ghostMob.setMetadata("ghost_mob", new FixedMetadataValue(this.plugin, true));
         final UUID playerId = player.getUniqueId();
         this.dataHolder.addGhostEntity(playerId, ghostMob);
         if (ghostMob instanceof LivingEntity livingMob) {
            livingMob.setAI(false);
            if (mobType == EntityType.ENDERMAN) {
               Enderman enderman = (Enderman)livingMob;
               enderman.setTarget(player);
            }
         }

         int disappearTime = 40 + ThreadLocalRandom.current().nextInt(40);
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (!ghostMob.isDead() && ghostMob.isValid()) {
                  ghostMob.getWorld().spawnParticle(Particle.SMOKE, ghostMob.getLocation().add(0.0, 1.0, 0.0), 30, 0.3, 0.5, 0.3, 0.05);
                  ghostMob.remove();
               }

               GhostEntityEffect.this.dataHolder.removeGhostEntity(playerId, ghostMob);
            }
         }).entityLater(player, disappearTime);
      }
   }

   public void createFakePlayerSighting(final Player player) {
      Location playerLoc = player.getLocation();
      Vector direction = playerLoc.getDirection().normalize();
      direction.add(
            new Vector(
               (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
               (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1,
               (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3
            )
         )
         .normalize();
      double distance = 15.0 + ThreadLocalRandom.current().nextDouble() * 10.0;
      Location spawnLoc = playerLoc.clone().add(direction.multiply(distance));
      spawnLoc = LocationUtils.findSafeLocationNearby(spawnLoc);
      if (spawnLoc == null) {
         if (this.configManager.isDebugEnabled()) {
            this.plugin.getLogger().info("Fake player sighting cancelled for " + player.getName() + " - no valid location found");
         }
      } else {
         if (this.configManager.isDebugEnabled()) {
            this.plugin
               .getLogger()
               .info("Creating fake player sighting for " + player.getName() + " at distance " + Math.round(player.getLocation().distance(spawnLoc)));
         }

         final ArmorStand stand = (ArmorStand)spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
         stand.setPersistent(false);
         stand.setVisible(false);
         final UUID playerId = player.getUniqueId();
         this.dataHolder.addGhostEntity(playerId, stand);

         for (int i = 0; i < 3; i++) {
            (new Schedulers.Repeating() {
                  @Override
                  public void run() {
                     if (player.isOnline() && !stand.isDead()) {
                        player.playSound(stand.getLocation(), Sound.BLOCK_STONE_HIT, 0.4F, 1.0F);
                        player.getWorld()
                           .spawnParticle(
                              Particle.FALLING_DUST, stand.getLocation().add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, 0.05, Bukkit.createBlockData(Material.STONE)
                           );
                     }
                  }
               })
               .entityLater(player, i * 10L);
         }

         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (!stand.isDead()) {
                  stand.remove();
               }

               GhostEntityEffect.this.dataHolder.removeGhostEntity(playerId, stand);
            }
         }).entityLater(player, 40L);
      }
   }

   public void trackAndUpdateGhostMobs() {
      this.trackAndUpdateGhostMobs(null);
   }

   public void trackAndUpdateGhostMobs(Player only) {
      int totalEntities = 0;
      int facingUpdates = 0;
      int pathUpdates = 0;
      Map<EntityType, Integer> entityTypeStats = new HashMap<>();

      for (List<Entity> entities : this.dataHolder.getAllGhostEntities().values()) {
         totalEntities += entities.size();
      }

      if (totalEntities != 0) {
         if (this.configManager.isDebugEnabled()) {
            this.plugin
               .getLogger()
               .info(
                  "trackAndUpdateGhostMobs running - tracking "
                     + totalEntities
                     + " ghost entities for "
                     + this.dataHolder.getAllGhostEntities().size()
                     + " players"
               );
         }

         int updatedEntities = 0;
         int activatedEntities = 0;

         for (final UUID playerId : new ArrayList<>(this.dataHolder.getAllGhostEntities().keySet())) {
            if (only == null || only.getUniqueId().equals(playerId)) {
               final Player player = Bukkit.getPlayer(playerId);
               if (player != null && player.isOnline()) {
                  List<Entity> playerEntities = this.dataHolder.getPlayerGhostEntities(playerId);
                  if (this.configManager.isDebugEnabled() && !playerEntities.isEmpty()) {
                     this.plugin.getLogger().info("Processing " + playerEntities.size() + " ghost entities for player " + player.getName());
                  }

                  for (final Entity entity : new ArrayList<>(playerEntities)) {
                     if (entity == null || entity.isDead()) {
                        this.dataHolder.removeGhostEntity(playerId, entity);
                     } else if (Schedulers.isOwned(entity)) {
                        if (this.configManager.isDebugEnabled()) {
                           entityTypeStats.put(entity.getType(), entityTypeStats.getOrDefault(entity.getType(), 0) + 1);
                        }

                        if (ThreadLocalRandom.current().nextBoolean()) {
                           updatedEntities++;
                           if (entity instanceof LivingEntity livingEntity) {
                              if (livingEntity.hasAI()) {
                                 if (livingEntity instanceof Monster) {
                                    ((Monster)livingEntity).setTarget(player);
                                    pathUpdates++;
                                    if (this.configManager.isDebugEnabled()) {
                                       this.plugin
                                          .getLogger()
                                          .info(
                                             "Updated target path for "
                                                + entity.getType()
                                                + " ghost entity chasing "
                                                + player.getName()
                                                + ", distance: "
                                                + String.format("%.1f", entity.getLocation().distance(player.getLocation()))
                                          );
                                    }
                                 }
                              } else {
                                 Vector direction = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                                 Location lookLoc = entity.getLocation().setDirection(direction);
                                 entity.teleportAsync(lookLoc);
                                 facingUpdates++;
                                 if (this.configManager.isDebugEnabled() && ThreadLocalRandom.current().nextInt(5) == 0) {
                                    this.plugin
                                       .getLogger()
                                       .info("Updated facing direction for " + entity.getType() + " ghost entity to look at " + player.getName());
                                 }

                                 if (ThreadLocalRandom.current().nextInt(100) < 10 && entity instanceof Monster && !livingEntity.hasAI()) {
                                    livingEntity.setAI(true);
                                    ((Monster)livingEntity).setTarget(player);
                                    activatedEntities++;
                                    if (this.configManager.isDebugEnabled()) {
                                       this.plugin
                                          .getLogger()
                                          .info(
                                             "в\u0098… JUMP-SCARE: Activated "
                                                + entity.getType()
                                                + " ghost entity to charge at "
                                                + player.getName()
                                                + " from distance "
                                                + String.format("%.1f", entity.getLocation().distance(player.getLocation()))
                                          );
                                    }

                                    if (entity instanceof Creeper) {
                                       player.playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 1.0F);
                                    } else if (entity instanceof Zombie) {
                                       player.playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0F, 0.7F);
                                    } else {
                                       player.playSound(entity.getLocation(), Sound.ENTITY_HOSTILE_HURT, 0.8F, 0.6F);
                                    }

                                    (new Schedulers.Repeating() {
                                          @Override
                                          public void run() {
                                             if (!entity.isDead()) {
                                                if (GhostEntityEffect.this.configManager.isDebugEnabled()) {
                                                   GhostEntityEffect.this.plugin
                                                      .getLogger()
                                                      .info("Removing charging " + entity.getType() + " ghost entity after jump-scare for " + player.getName());
                                                }

                                                entity.getWorld()
                                                   .spawnParticle(Particle.SMOKE, entity.getLocation().add(0.0, 1.0, 0.0), 30, 0.3, 0.5, 0.3, 0.05);
                                                entity.getWorld()
                                                   .spawnParticle(Particle.PORTAL, entity.getLocation().add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.05);
                                                entity.remove();
                                                GhostEntityEffect.this.dataHolder.removeGhostEntity(playerId, entity);
                                             }
                                          }
                                       })
                                       .entityLater(player, 40L);
                                 }
                              }
                           }
                        }
                     }
                  }
               } else if (this.configManager.isDebugEnabled()) {
                  this.plugin.getLogger().info("Skipping ghost update for offline player UUID: " + playerId);
               }
            }
         }

         this.dataHolder.removeDeadEntities();
         if (this.configManager.isDebugEnabled() && (updatedEntities > 0 || activatedEntities > 0)) {
            StringBuilder statsLog = new StringBuilder();
            statsLog.append("Ghost tracking stats: ");
            statsLog.append(updatedEntities).append(" entities updated (");
            statsLog.append(pathUpdates).append(" paths, ");
            statsLog.append(facingUpdates).append(" facings), ");
            statsLog.append(activatedEntities).append(" jump-scares activated");
            if (!entityTypeStats.isEmpty()) {
               statsLog.append(" - Types: ");

               for (Entry<EntityType, Integer> entry : entityTypeStats.entrySet()) {
                  statsLog.append(entry.getKey().name()).append("(").append(entry.getValue()).append(") ");
               }
            }

            this.plugin.getLogger().info(statsLog.toString());
         }
      }
   }

   public void cleanup() {
      int removedEntities = 0;
      this.dataHolder.cleanupAllGhostEntities();
      if (!SchedulerUtils.isFolia()) {
         for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
               if (entity.hasMetadata("nightmare_ghost") || entity.hasMetadata("ghost_mob")) {
                  entity.remove();
                  removedEntities++;
               }
            }
         }
      }

      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("GhostEntityEffect cleanup: removed " + removedEntities + " remaining entities");
      }
   }
}
