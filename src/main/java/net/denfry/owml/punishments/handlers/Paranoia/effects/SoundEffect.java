package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundEffect {
   private final OverWatchML plugin;
   private final ConfigManager configManager;
   private final Sound[] scarySounds;

   public SoundEffect(OverWatchML plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
      this.scarySounds = this.initializeScarySounds();
   }

   private Sound[] initializeScarySounds() {
      List<Sound> sounds = new ArrayList<>();
      this.addSoundIfExists(sounds, "ENTITY_CREEPER_PRIMED");
      this.addSoundIfExists(sounds, "ENTITY_ENDERMAN_SCREAM");
      this.addSoundIfExists(sounds, "ENTITY_GHAST_WARN");
      this.addSoundIfExists(sounds, "ENTITY_WITHER_AMBIENT");
      this.addSoundIfExists(sounds, "ENTITY_ZOMBIE_AMBIENT");
      this.addSoundIfExists(sounds, "ENTITY_SKELETON_AMBIENT");
      this.addSoundIfExists(sounds, "ENTITY_SPIDER_AMBIENT");
      this.addSoundIfExists(sounds, "AMBIENT_CAVE");
      this.addSoundIfExists(sounds, "BLOCK_STONE_BREAK");
      this.addSoundIfExists(sounds, "ENTITY_TNT_PRIMED");
      this.addSoundIfExists(sounds, "BLOCK_CHORUS_FLOWER_DEATH");
      this.addSoundIfExists(sounds, "ENTITY_WARDEN_NEARBY_CLOSER");
      this.addSoundIfExists(sounds, "ENTITY_WARDEN_HEARTBEAT");
      return sounds.toArray(new Sound[0]);
   }

   private void addSoundIfExists(List<Sound> soundList, String soundName) {
      try {
         Field field = Sound.class.getDeclaredField(soundName);
         if (field != null && field.getType() == Sound.class) {
            Sound sound = (Sound)field.get(null);
            soundList.add(sound);
         }
      } catch (IllegalAccessException | ClassCastException | NoSuchFieldException var5) {
      }
   }

   public void playParanoiaSound(Player player) {
      Sound sound = this.scarySounds[ThreadLocalRandom.current().nextInt(this.scarySounds.length)];
      Location playerLoc = player.getLocation();
      Location soundLoc;
      if (ThreadLocalRandom.current().nextBoolean()) {
         double distance = 3.0 + ThreadLocalRandom.current().nextDouble() * 7.0;
         double angle = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
         double x = playerLoc.getX() + Math.cos(angle) * distance;
         double z = playerLoc.getZ() + Math.sin(angle) * distance;
         soundLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
      } else {
         soundLoc = playerLoc;
      }

      float volume = 0.3F + ThreadLocalRandom.current().nextFloat() * 0.7F;
      float pitch = 0.7F + ThreadLocalRandom.current().nextFloat() * 0.6F;
      player.playSound(soundLoc, sound, volume, pitch);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Playing paranoia sound " + sound + " to " + player.getName());
      }
   }

   public void playDistantMiningSound(final Player player) {
      Location playerLoc = player.getLocation();
      double distance = 15.0 + ThreadLocalRandom.current().nextDouble() * 10.0;
      double angle = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
      double x = playerLoc.getX() + Math.cos(angle) * distance;
      double z = playerLoc.getZ() + Math.sin(angle) * distance;
      final Location soundLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
      if (this.configManager.isDebugEnabled()) {
         this.plugin.getLogger().info("Playing distant mining sounds for " + player.getName() + " at " + Math.round(distance) + " blocks away");
      }

      Sound[] miningSounds = new Sound[]{Sound.BLOCK_STONE_BREAK, Sound.BLOCK_STONE_HIT};
      int soundCount = 2 + ThreadLocalRandom.current().nextInt(3);

      for (int i = 0; i < soundCount; i++) {
         final Sound sound = miningSounds[ThreadLocalRandom.current().nextInt(miningSounds.length)];
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               if (player.isOnline()) {
                  player.playSound(soundLoc, sound, 0.4F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F);
               }
            }
         }).entityLater(player, i * 10L);
      }
   }
}
