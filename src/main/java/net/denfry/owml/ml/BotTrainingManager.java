package net.denfry.owml.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.utils.MessageManager;

/**
 * Bot Training Manager - creates NPC bots that automatically mine and train the ML system
 *
 * @author OverWatch Team
 * @version 1.1.0
 * @since 1.8.8
 */
public class BotTrainingManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Active training bots
    private final Map<UUID, TrainingBot> activeBots = new ConcurrentHashMap<>();

    // Training session management
    private volatile boolean autoTrainingEnabled = false;
    private volatile int maxConcurrentBots = 5;
    private volatile int trainingSessionDuration = 120; // 2 minutes
    private volatile int botSpawnInterval = 30; // seconds between spawns
    private volatile long lastSpawnTime = 0;

    public BotTrainingManager() {
        loadConfigSettings();
        initializeSpawnLocations();
        startAutoTrainingLoop();
    }

    /**
     * Load bot training settings from config
     */
    private void loadConfigSettings() {
        try {
            autoTrainingEnabled = plugin.getConfig().getBoolean("ml.bots.autoTraining", false);
            maxConcurrentBots = plugin.getConfig().getInt("ml.bots.maxConcurrentBots", 5);
            trainingSessionDuration = plugin.getConfig().getInt("ml.bots.trainingSessionDuration", 120);
            botSpawnInterval = plugin.getConfig().getInt("ml.bots.spawnInterval", 30);

            MessageManager.log("info", "Bot training config loaded: auto={AUTO}, max={MAX}, duration={DURATION}s, interval={INTERVAL}s",
                "AUTO", String.valueOf(autoTrainingEnabled),
                "MAX", String.valueOf(maxConcurrentBots),
                "DURATION", String.valueOf(trainingSessionDuration),
                "INTERVAL", String.valueOf(botSpawnInterval));
        } catch (Exception e) {
            MessageManager.log("warning", "Failed to load bot training config, using defaults: {ERROR}", "ERROR", e.getMessage());
        }
    }

    // Statistics
    private volatile int totalBotsSpawned = 0;
    private volatile int totalTrainingSessions = 0;


    /**
     * Initialize spawn location search parameters
     */
    private void initializeSpawnLocations() {
        // We'll find spawn locations dynamically when needed
        // This allows for more flexible spawning across the world
        MessageManager.log("info", "Bot training system initialized - spawn locations will be found dynamically");
    }

    /**
     * Find a dynamic spawn location for bot
     */
    private Location findDynamicSpawnLocation() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;

        // Try multiple random locations across the world
        for (int attempt = 0; attempt < 20; attempt++) { // Try up to 20 different locations
            // Generate random coordinates (avoid spawn area)
            int x = ThreadLocalRandom.current().nextInt(-10000, 10000);
            int z = ThreadLocalRandom.current().nextInt(-10000, 10000);

            // Try different Y levels for mining depths
            int[] yLevels = {-50, -30, -10, 10, 30};
            for (int baseY : yLevels) {
                Location center = new Location(world, x, baseY, z);
                Location safeLoc = findSafeSpawnLocation(center, 100); // Search in 100x100 area

                if (safeLoc != null) {
                    return safeLoc;
                }
            }
        }

        return null;
    }

    /**
     * Find a safe location for bot spawning with expanded search
     */
    private Location findSafeSpawnLocation(Location center, int searchRadius) {
        World world = center.getWorld();
        if (world == null) return null;

        // Search in expanding squares around center
        for (int radius = 10; radius <= searchRadius; radius += 10) {
            for (int x = -radius; x <= radius; x += 5) { // Check every 5 blocks for performance
                for (int z = -radius; z <= radius; z += 5) {
                    if (Math.abs(x) < radius - 5 && Math.abs(z) < radius - 5) continue; // Only check perimeter

                    Location testLoc = center.clone().add(x, 0, z);

                    // Find surface/mining level
                    int surfaceY = world.getHighestBlockYAt(testLoc.getBlockX(), testLoc.getBlockZ());

                    // Try a few Y levels around the surface
                    for (int yOffset = -5; yOffset <= 5; yOffset += 2) {
                        Location surfaceLoc = new Location(world, testLoc.getX(), surfaceY + yOffset, testLoc.getZ());

                        // Keep within reasonable mining depths (Paper 1.18+ support)
                        if (surfaceLoc.getY() < -64 || surfaceLoc.getY() > 320) continue;

                        if (isSafeLocation(surfaceLoc)) {
                            return surfaceLoc;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if a location is safe for bot spawning
     */
    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        Block block = location.getBlock();
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        Block above = location.clone().add(0, 1, 0).getBlock();

        // Must have solid ground below and air above
        // Also check for dangerous blocks nearby
        return below.getType().isSolid() &&
               block.getType().isAir() &&
               above.getType().isAir() &&
               !isDangerousLocation(location);
    }

    /**
     * Check if location has dangerous blocks nearby
     */
    private boolean isDangerousLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return true;

        // Check 3x3x3 area around location for dangerous blocks
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    Material type = block.getType();

                    // Dangerous blocks that could harm the bot
                    if (type == Material.LAVA ||
                        type == Material.FIRE ||
                        type.name().contains("MAGMA") ||
                        type.name().contains("CACTUS")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Notify all online admins about bot training events
     */
    private void notifyAdmins(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("owml.ml.train") || player.hasPermission("owml.admin")) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Start automatic bot training loop
     */
    private void startAutoTrainingLoop() {
        MessageManager.log("info", "🤖 Bot auto-training loop started - checking every 60 seconds");

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (autoTrainingEnabled) {
                    if (shouldSpawnTrainingBot()) {
                        plugin.getServer().getScheduler().runTask(plugin, this::spawnRandomTrainingBot);
                    }
                }
            } catch (Exception e) {
                MessageManager.log("error", "Error in bot training loop: {ERROR}", "ERROR", e.getMessage());
            }
        }, 1200L, 1200L); // Check every minute
    }

    /**
     * Check if we should spawn a new training bot
     */
    private boolean shouldSpawnTrainingBot() {
        long currentTime = System.currentTimeMillis();
        return activeBots.size() < maxConcurrentBots &&
               (currentTime - lastSpawnTime >= botSpawnInterval * 1000L) &&
               ThreadLocalRandom.current().nextDouble() < 0.7; // 70% chance
    }

    /**
     * Spawn a random training bot
     */
    private void spawnRandomTrainingBot() {
        // Find a dynamic spawn location - search async to prevent lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Location spawnLoc = findDynamicSpawnLocation();

            if (spawnLoc == null) {
                return;
            }

            // Return to main thread to actually spawn the bot
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                lastSpawnTime = System.currentTimeMillis();

                // Choose random bot type
                BotBehaviorType botType = getRandomBotType();

                // Create and spawn bot
                TrainingBot bot = new TrainingBot(UUID.randomUUID(), botType, spawnLoc);
                activeBots.put(bot.getBotId(), bot);
                totalBotsSpawned++;

                String botId = bot.getBotId().toString().substring(0, 8);
                MessageManager.log("info", "🤖 Spawned training bot {ID} ({TYPE}) at {LOCATION}",
                    "ID", botId,
                    "TYPE", botType.name(),
                    "LOCATION", formatLocation(spawnLoc));

                notifyAdmins("§a[Bot Training] §f✔ Spawned bot §e" + botId + "§f (§b" + botType.name() + "§f) at §e" + formatLocation(spawnLoc));

                // Start bot behavior
                bot.startTrainingSession();

                // Schedule bot removal after training session
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    removeBot(bot.getBotId());
                }, trainingSessionDuration * 20L);
            });
        });
    }

    /**
     * Get random bot behavior type
     */
    private BotBehaviorType getRandomBotType() {
        BotBehaviorType[] types = BotBehaviorType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    /**
     * Spawn specific type of training bot
     */
    public void spawnTrainingBot(BotBehaviorType type, int count) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int spawned = 0;

            for (int i = 0; i < count; i++) {
                if (activeBots.size() >= maxConcurrentBots) {
                    break;
                }

                // Find a dynamic spawn location
                Location spawnLoc = findDynamicSpawnLocation();

                if (spawnLoc == null) {
                    continue;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    lastSpawnTime = System.currentTimeMillis();
                    TrainingBot bot = new TrainingBot(UUID.randomUUID(), type, spawnLoc);
                    activeBots.put(bot.getBotId(), bot);
                    totalBotsSpawned++;

                    notifyAdmins("§a[Bot Training] §fSpawned " + type.name() + " bot at " + formatLocation(spawnLoc));

                    bot.startTrainingSession();

                    // Schedule removal
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        removeBot(bot.getBotId());
                    }, trainingSessionDuration * 20L);
                });
                spawned++;
            }
        });
    }

    /**
     * Remove a training bot
     */
    private void removeBot(UUID botId) {
        TrainingBot bot = activeBots.remove(botId);
        if (bot != null) {
            String shortId = botId.toString().substring(0, 8);
            bot.endTrainingSession();
            totalTrainingSessions++;

            MessageManager.log("info", "👋 Removed training bot {ID} ({TYPE}) after {SECONDS}s",
                "ID", shortId,
                "TYPE", bot.getBehaviorType().name(),
                "SECONDS", String.valueOf(trainingSessionDuration));

            notifyAdmins("§a[Bot Training] §f👋 Bot §e" + shortId + "§f removed (§aTotal sessions: " + totalTrainingSessions + "§f)");
        }
    }

    /**
     * Remove all active training bots
     */
    public void removeAllBots() {
        int botCount = activeBots.size();
        if (botCount > 0) {
            for (TrainingBot bot : activeBots.values()) {
                bot.endTrainingSession();
            }
            activeBots.clear();
            notifyAdmins("§a[Bot Training] §f✔ Removed all §c" + botCount + "§f bots");
        }
    }

    /**
     * Find bot by partial ID match
     */
    public TrainingBot findBotById(String partialId) {
        if (partialId == null || partialId.trim().isEmpty()) {
            return null;
        }

        String searchId = partialId.toLowerCase().trim();

        for (Map.Entry<UUID, TrainingBot> entry : activeBots.entrySet()) {
            String botIdStr = entry.getKey().toString();
            if (botIdStr.toLowerCase().startsWith(searchId) ||
                botIdStr.toLowerCase().contains(searchId)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Get all active bots
     */
    public Map<UUID, TrainingBot> getActiveBots() {
        return new HashMap<>(activeBots);
    }

    /**
     * Get training statistics
     */
    public Map<String, Object> getTrainingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeBots", activeBots.size());
        stats.put("totalBotsSpawned", totalBotsSpawned);
        stats.put("totalTrainingSessions", totalTrainingSessions);
        stats.put("autoTrainingEnabled", autoTrainingEnabled);
        stats.put("maxConcurrentBots", maxConcurrentBots);
        stats.put("sessionDuration", trainingSessionDuration);
        return stats;
    }

    /**
     * Enable/disable automatic bot training
     */
    public void setAutoTrainingEnabled(boolean enabled) {
        this.autoTrainingEnabled = enabled;
        plugin.getConfig().set("ml.bots.autoTraining", enabled);
        plugin.saveConfig();
        notifyAdmins("§a[Bot Training] §f✔ Automatic bot training " + (enabled ? "§aENABLED" : "§cDISABLED"));
    }

    /**
     * Set maximum concurrent bots
     */
    public void setMaxConcurrentBots(int max) {
        this.maxConcurrentBots = Math.max(1, Math.min(max, 20));
        plugin.getConfig().set("ml.bots.maxConcurrentBots", this.maxConcurrentBots);
        plugin.saveConfig();
    }

    /**
     * Format location for logging
     */
    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Bot behavior types
     */
    public enum BotBehaviorType {
        NORMAL_MINER,
        XRAY_CHEATER,
        TUNNEL_MINER,
        RANDOM_MINER,
        EFFICIENT_MINER,
        SURFACE_MINER
    }

    /**
     * Training Bot class - manages individual bot lifecycle and behavior
     */
    public class TrainingBot {
        private final UUID botId;
        private final BotBehaviorType behaviorType;
        private final Location spawnLocation;
        private final FakePlayer fakePlayer;
        private BukkitTask behaviorTask;
        private long sessionStartTime;
        private int blocksMined = 0;

        public TrainingBot(UUID botId, BotBehaviorType behaviorType, Location spawnLocation) {
            this.botId = botId;
            this.behaviorType = behaviorType;
            this.spawnLocation = spawnLocation.clone();
            boolean isCheater = behaviorType == BotBehaviorType.XRAY_CHEATER;
            this.fakePlayer = new FakePlayer(botId, "Bot_" + botId.toString().substring(0, 4), isCheater);
        }

        public void startTrainingSession() {
            sessionStartTime = System.currentTimeMillis();
            fakePlayer.spawn(spawnLocation);
            startBehavior();
        }

        public void endTrainingSession() {
            if (behaviorTask != null) {
                behaviorTask.cancel();
                behaviorTask = null;
            }
            fakePlayer.despawn();
            submitTrainingData();
        }

        private void startBehavior() {
            behaviorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                try {
                    performAction();
                } catch (Exception e) {
                    MessageManager.log("error", "Error in bot behavior: {ERROR}", "ERROR", e.getMessage());
                }
            }, 20L, 40L); // Every 2 seconds
        }

        private void performAction() {
            Location loc = fakePlayer.getLocation();
            if (loc == null) return;

            switch (behaviorType) {
                case NORMAL_MINER -> performNormalMining(loc);
                case XRAY_CHEATER -> performXrayCheating(loc);
                case TUNNEL_MINER -> performTunnelMining(loc);
                case RANDOM_MINER -> performRandomMining(loc);
                case EFFICIENT_MINER -> performEfficientMining(loc);
                case SURFACE_MINER -> performSurfaceMining(loc);
            }
        }

        private void performNormalMining(Location loc) {
            Location target = findNearbyOre(loc, Arrays.asList(Material.IRON_ORE, Material.COAL_ORE, Material.STONE));
            if (target != null) {
                fakePlayer.moveTowards(target);
                mineBlockAt(target);
            } else {
                fakePlayer.moveRandomly();
            }
        }

        private void performXrayCheating(Location loc) {
            // Directly look for valuable ores regardless of visibility
            Location target = findOreInDirection(loc, Material.DIAMOND_ORE, 30);
            if (target == null) target = findOreInDirection(loc, Material.GOLD_ORE, 20);

            if (target != null) {
                fakePlayer.moveTowards(target);
                mineBlockAt(target);
            } else {
                fakePlayer.moveRandomly();
            }
        }

        private void performTunnelMining(Location loc) {
            // Move in straight line
            Vector dir = loc.getDirection();
            Location target = loc.clone().add(dir.multiply(2));
            fakePlayer.moveTowards(target);
            mineBlockAt(target);
        }

        private void performRandomMining(Location loc) {
            fakePlayer.moveRandomly();
            mineBlockAt(loc.clone().add(0, 1, 0));
        }

        private void performEfficientMining(Location loc) {
            // branch mining simulation
            performNormalMining(loc);
        }

        private void performSurfaceMining(Location loc) {
            if (loc.getY() > 40) performNormalMining(loc);
            else {
                Location up = loc.clone().add(0, 10, 0);
                fakePlayer.moveTowards(up);
            }
        }

        private Location findNearbyOre(Location center, List<Material> ores) {
            World world = center.getWorld();
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                        if (ores.contains(b.getType())) return b.getLocation();
                    }
                }
            }
            return null;
        }

        private Location findOreInDirection(Location start, Material ore, int dist) {
            World world = start.getWorld();
            for (int attempt = 0; attempt < 5; attempt++) {
                int dx = ThreadLocalRandom.current().nextInt(-dist, dist);
                int dy = ThreadLocalRandom.current().nextInt(-10, 10);
                int dz = ThreadLocalRandom.current().nextInt(-dist, dist);
                Block b = world.getBlockAt(start.getBlockX() + dx, start.getBlockY() + dy, start.getBlockZ() + dz);
                if (b.getType() == ore) return b.getLocation();
            }
            return null;
        }

        private void mineBlockAt(Location loc) {
            Block b = loc.getBlock();
            if (b.getType().isSolid()) {
                fakePlayer.recordBlockBreak(b);
                // In a real environment we'd break it, here we just simulate
                blocksMined++;
            }
        }

        private void submitTrainingData() {
            PlayerProtocolData data = fakePlayer.getTrainingData();
            if (data != null) {
                data.calculateDerivedFeatures();
                ModernMLManager mlManager = plugin.getMLManager();
                if (mlManager != null) {
                    MLDataManager.saveTrainingData(data);
                    mlManager.retrainModel();
                }
            }
        }

        public UUID getBotId() { return botId; }
        public BotBehaviorType getBehaviorType() { return behaviorType; }
        public FakePlayer getFakePlayer() { return fakePlayer; }
    }

    /**
     * FakePlayer records actions into real PlayerProtocolData structure
     */
    public static class FakePlayer {
        private final UUID id;
        private final String name;
        private final PlayerProtocolData data;
        private Location location;

        public FakePlayer(UUID id, String name, boolean isCheater) {
            this.id = id;
            this.name = name;
            this.data = new PlayerProtocolData(name, isCheater);
        }

        public void spawn(Location loc) {
            this.location = loc.clone();
            recordPosition();
        }

        public void despawn() {
            // Finalize data if needed
        }

        public void moveTowards(Location target) {
            if (location == null) return;
            Vector dir = target.toVector().subtract(location.toVector());
            if (dir.lengthSquared() > 0) {
                dir.normalize().multiply(1.5);
                location.add(dir);
            }
            recordPosition();
        }

        public void moveRandomly() {
            if (location == null) return;
            location.add(ThreadLocalRandom.current().nextDouble(-2, 2),
                         ThreadLocalRandom.current().nextDouble(-1, 1),
                         ThreadLocalRandom.current().nextDouble(-2, 2));
            
            // Keep in world bounds
            if (location.getY() < -64) location.setY(-64);
            if (location.getY() > 320) location.setY(320);
            
            recordPosition();
        }

        public void recordBlockBreak(Block block) {
            data.recordBlockBreak(block, location.getDirection());
            // Also record position at time of break
            recordPosition();
        }

        private void recordPosition() {
            data.recordPlayerPosition(location.toVector());
        }

        public PlayerProtocolData getTrainingData() {
            return data;
        }

        public Location getLocation() {
            return location;
        }
    }
}
