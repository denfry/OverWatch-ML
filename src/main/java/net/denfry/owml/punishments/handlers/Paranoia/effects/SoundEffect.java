package net.denfry.owml.punishments.handlers.Paranoia.effects;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;

/**
 * Handles all sound-related paranoia effects
 */
public class SoundEffect {

    private final OverWatchML plugin;
    private final ConfigManager configManager;

    private final Sound[] scarySounds;

    public SoundEffect(OverWatchML plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scarySounds = initializeScarySounds();
    }

    /**
     * Initialize the array of scary sounds based on sounds that exist in the current version
     */
    private Sound[] initializeScarySounds() {
        java.util.List<Sound> sounds = new java.util.ArrayList<>();

        // Base sounds that exist in most versions (1.16.5+)
        addSoundIfExists(sounds, "ENTITY_CREEPER_PRIMED");
        addSoundIfExists(sounds, "ENTITY_ENDERMAN_SCREAM");
        addSoundIfExists(sounds, "ENTITY_GHAST_WARN");
        addSoundIfExists(sounds, "ENTITY_WITHER_AMBIENT");
        addSoundIfExists(sounds, "ENTITY_ZOMBIE_AMBIENT");
        addSoundIfExists(sounds, "ENTITY_SKELETON_AMBIENT");
        addSoundIfExists(sounds, "ENTITY_SPIDER_AMBIENT");
        addSoundIfExists(sounds, "AMBIENT_CAVE");
        addSoundIfExists(sounds, "BLOCK_STONE_BREAK");
        addSoundIfExists(sounds, "ENTITY_TNT_PRIMED");
        addSoundIfExists(sounds, "BLOCK_CHORUS_FLOWER_DEATH");

        // Version-specific sounds (1.19+, might not exist in older versions)
        addSoundIfExists(sounds, "ENTITY_WARDEN_NEARBY_CLOSER");
        addSoundIfExists(sounds, "ENTITY_WARDEN_HEARTBEAT");

        return sounds.toArray(new Sound[0]);
    }

    /**
     * Safely add a sound to the list if it exists in the current version
     */
    private void addSoundIfExists(java.util.List<Sound> soundList, String soundName) {
        try {
            // Use reflection to get the enum field without calling enum methods
            java.lang.reflect.Field field = Sound.class.getDeclaredField(soundName);
            if (field != null && field.getType() == Sound.class) {
                Sound sound = (Sound) field.get(null);
                soundList.add(sound);
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            // Sound doesn't exist in this version, skip it silently
        }
    }

    /**
     * Plays one of the defined scary sounds with randomized pitch and volume
     */
    public void playParanoiaSound(Player player) {

        Sound sound = scarySounds[ThreadLocalRandom.current().nextInt(scarySounds.length)];


        Location playerLoc = player.getLocation();
        Location soundLoc;


        if (ThreadLocalRandom.current().nextBoolean()) {

            double distance = 3 + ThreadLocalRandom.current().nextDouble() * 7;
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            soundLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
        } else {
            soundLoc = playerLoc;
        }


        float volume = 0.3f + ThreadLocalRandom.current().nextFloat() * 0.7f;
        float pitch = 0.7f + ThreadLocalRandom.current().nextFloat() * 0.6f;


        player.playSound(soundLoc, sound, volume, pitch);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Playing paranoia sound " + sound + " to " + player.getName());
        }
    }

    /**
     * Plays distant mining sounds to create paranoia
     */
    public void playDistantMiningSound(Player player) {

        Location playerLoc = player.getLocation();
        double distance = 15 + ThreadLocalRandom.current().nextDouble() * 10;
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;

        double x = playerLoc.getX() + Math.cos(angle) * distance;
        double z = playerLoc.getZ() + Math.sin(angle) * distance;

        Location soundLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Playing distant mining sounds for " + player.getName() + " at " +
                    Math.round(distance) + " blocks away");
        }


        Sound[] miningSounds = {
                Sound.BLOCK_STONE_BREAK,
                Sound.BLOCK_STONE_HIT
        };


        int soundCount = 2 + ThreadLocalRandom.current().nextInt(3);

        for (int i = 0; i < soundCount; i++) {
            Sound sound = miningSounds[ThreadLocalRandom.current().nextInt(miningSounds.length)];

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.playSound(soundLoc, sound, 0.4f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                    }
                }
            }.runTaskLater(plugin, i * 10L);
        }
    }
}
