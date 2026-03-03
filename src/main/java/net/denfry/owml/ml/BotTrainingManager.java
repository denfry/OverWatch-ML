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
import net.denfry.owml.OverWatchML;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.utils.MessageManager;

/**
 * Bot Training Manager - creates NPC bots that automatically mine and train the ML system
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.8
 */
public class BotTrainingManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Active training bots
    private final Map<UUID, TrainingBot> activeBots = new ConcurrentHashMap<>();

    // Bot spawn locations (safe mining areas)
    private final List<Location> spawnLocations = new ArrayList<>();

    // Training session management
    private volatile boolean autoTrainingEnabled = false;
    private volatile int maxConcurrentBots = 5;
    private volatile int trainingSessionDuration = 120; // 2 minutes
    private volatile int botSpawnInterval = 30; // seconds between spawns

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
                    notifyAdmins("Р’В§a[Bot Training] Р’В§fSpawn location found after " + (attempt + 1) + " attempts");
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

                        // Keep within reasonable mining depths
                        if (surfaceLoc.getY() < -60 || surfaceLoc.getY() > 100) continue;

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
               block.getType() == Material.AIR &&
               above.getType() == Material.AIR &&
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
        MessageManager.log("info", "СЂСџВ¤вЂ“ Bot auto-training loop started - checking every 60 seconds");
        notifyAdmins("Р’В§a[Bot Training] Р’В§fAutomatic bot training started");

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (autoTrainingEnabled) {
                    if (shouldSpawnTrainingBot()) {
                        MessageManager.log("info", "СЂСџВ¤вЂ“ Auto-training: Spawning new training bot (Active: {ACTIVE}/{MAX})",
                            "ACTIVE", String.valueOf(activeBots.size()),
                            "MAX", String.valueOf(maxConcurrentBots));
                        notifyAdmins("Р’В§a[Bot Training] Р’В§fSpawning new training bot...");
                        plugin.getServer().getScheduler().runTask(plugin, this::spawnRandomTrainingBot);
                    } else if (activeBots.size() == 0 && totalBotsSpawned > 0) {
                        // Periodic status update when no bots are active
                        MessageManager.log("info", "СЂСџВ¤вЂ“ Auto-training: Waiting for conditions (Active: 0/{MAX}, Total spawned: {TOTAL})",
                            "MAX", String.valueOf(maxConcurrentBots),
                            "TOTAL", String.valueOf(totalBotsSpawned));
                    }
                }
            } catch (Exception e) {
                MessageManager.log("error", "Error in bot training loop: {ERROR}", "ERROR", e.getMessage());
                notifyAdmins("Р’В§c[Bot Training] Р’В§fError in training loop: " + e.getMessage());
            }
        }, 1200L, 1200L); // Check every minute
    }

    /**
     * Check if we should spawn a new training bot
     */
    private boolean shouldSpawnTrainingBot() {
        return activeBots.size() < maxConcurrentBots &&
               !spawnLocations.isEmpty() &&
               ThreadLocalRandom.current().nextDouble() < 0.7; // 70% chance
    }

    /**
     * Spawn a random training bot
     */
    private void spawnRandomTrainingBot() {
        MessageManager.log("info", "СЂСџВ¤вЂ“ Starting bot spawn process...");

        // Find a dynamic spawn location
        Location spawnLoc = findDynamicSpawnLocation();

        if (spawnLoc == null) {
            MessageManager.log("warning", "РІСњРЉ No spawn locations available for training bots - tried 20 different locations");
            notifyAdmins("Р’В§c[Bot Training] Р’В§fРІСњРЉ Failed to find suitable spawn location for bot (checked 20 locations)");
            return;
        }

        MessageManager.log("info", "РІСљвЂ¦ Found spawn location: {LOCATION}", "LOCATION", formatLocation(spawnLoc));

        // Choose random bot type
        BotBehaviorType botType = getRandomBotType();

        // Create and spawn bot
        TrainingBot bot = new TrainingBot(UUID.randomUUID(), botType, spawnLoc);
        activeBots.put(bot.getBotId(), bot);
        totalBotsSpawned++;

        String botId = bot.getBotId().toString().substring(0, 8);
        MessageManager.log("info", "СЂСџВ¤вЂ“ Spawned training bot {ID} ({TYPE}) at {LOCATION}",
            "ID", botId,
            "TYPE", botType.name(),
            "LOCATION", formatLocation(spawnLoc));

        notifyAdmins("Р’В§a[Bot Training] Р’В§fРІСљвЂ¦ Spawned bot Р’В§e" + botId + "Р’В§f (Р’В§b" + botType.name() + "Р’В§f) at Р’В§e" + formatLocation(spawnLoc));

        // Start bot behavior
        bot.startTrainingSession();

        // Schedule bot removal after training session
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeBot(bot.getBotId());
        }, trainingSessionDuration * 20L);

        MessageManager.log("info", "РІРЏВ° Bot {ID} scheduled for removal in {SECONDS} seconds",
            "ID", botId,
            "SECONDS", String.valueOf(trainingSessionDuration));
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
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            if (activeBots.size() >= maxConcurrentBots) {
                MessageManager.log("warning", "Maximum concurrent bots reached ({MAX}), stopping spawn",
                    "MAX", String.valueOf(maxConcurrentBots));
                break;
            }

            // Find a dynamic spawn location
            Location spawnLoc = findDynamicSpawnLocation();

            if (spawnLoc == null) {
                MessageManager.log("warning", "No spawn location found for bot {INDEX}/{COUNT} of type {TYPE}",
                    "INDEX", String.valueOf(i + 1),
                    "COUNT", String.valueOf(count),
                    "TYPE", type.name());
                notifyAdmins("Р’В§c[Bot Training] Р’В§fFailed to find location for bot " + (i + 1) + "/" + count);
                continue;
            }

            TrainingBot bot = new TrainingBot(UUID.randomUUID(), type, spawnLoc);
            activeBots.put(bot.getBotId(), bot);
            totalBotsSpawned++;
            spawned++;

            MessageManager.log("info", "Spawned training bot {TYPE} at {LOCATION}",
                "TYPE", type.name(),
                "LOCATION", formatLocation(spawnLoc));

            notifyAdmins("Р’В§a[Bot Training] Р’В§fSpawned " + type.name() + " bot at " + formatLocation(spawnLoc));

            bot.startTrainingSession();

            // Schedule removal
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                removeBot(bot.getBotId());
            }, trainingSessionDuration * 20L);
        }

        MessageManager.log("info", "Spawned {SPAWNED}/{REQUESTED} {TYPE} training bots",
            "SPAWNED", String.valueOf(spawned),
            "REQUESTED", String.valueOf(count),
            "TYPE", type.name());
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

            MessageManager.log("info", "СЂСџвЂвЂ№ Removed training bot {ID} ({TYPE}) after scheduled {SECONDS}s session (Total sessions: {TOTAL})",
                "ID", shortId,
                "TYPE", bot.getBehaviorType().name(),
                "SECONDS", String.valueOf(trainingSessionDuration),
                "TOTAL", String.valueOf(totalTrainingSessions));

            notifyAdmins("Р’В§a[Bot Training] Р’В§fСЂСџвЂвЂ№ Bot Р’В§e" + shortId + "Р’В§f removed after Р’В§b" + trainingSessionDuration + "sР’В§f session (Р’В§aTotal sessions: " + totalTrainingSessions + "Р’В§f)");
        }
    }

    /**
     * Remove all active training bots
     */
    public void removeAllBots() {
        int botCount = activeBots.size();
        if (botCount > 0) {
            MessageManager.log("info", "СЂСџВ§в„– Removing all {COUNT} active training bots...", "COUNT", String.valueOf(botCount));
            notifyAdmins("Р’В§e[Bot Training] Р’В§fСЂСџВ§в„– Removing all Р’В§c" + botCount + "Р’В§f active bots...");

            for (TrainingBot bot : activeBots.values()) {
                bot.endTrainingSession();
            }
            activeBots.clear();

            MessageManager.log("info", "РІСљвЂ¦ Successfully removed all {COUNT} training bots", "COUNT", String.valueOf(botCount));
            notifyAdmins("Р’В§a[Bot Training] Р’В§fРІСљвЂ¦ Successfully removed all Р’В§c" + botCount + "Р’В§f bots");
        } else {
            MessageManager.log("info", "РІвЂћв„–РїС‘РЏ No active training bots to remove");
            notifyAdmins("Р’В§e[Bot Training] Р’В§fРІвЂћв„–РїС‘РЏ No active bots to remove");
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

        // Try exact match first (if it's a valid UUID)
        try {
            UUID exactUUID = UUID.fromString(searchId);
            TrainingBot exactMatch = activeBots.get(exactUUID);
            if (exactMatch != null) {
                return exactMatch;
            }
        } catch (IllegalArgumentException e) {
            // Not a valid UUID, continue with partial matching
        }

        // Try partial match
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
        stats.put("spawnLocations", "dynamic"); // Now uses dynamic spawning

        // Bot type distribution
        Map<String, Integer> botTypeCounts = new HashMap<>();
        for (TrainingBot bot : activeBots.values()) {
            String type = bot.getBehaviorType().name();
            botTypeCounts.put(type, botTypeCounts.getOrDefault(type, 0) + 1);
        }
        stats.put("activeBotTypes", botTypeCounts);

        return stats;
    }

    /**
     * Enable/disable automatic bot training
     */
    public void setAutoTrainingEnabled(boolean enabled) {
        boolean wasEnabled = this.autoTrainingEnabled;
        this.autoTrainingEnabled = enabled;

        // Save to config
        plugin.getConfig().set("ml.bots.autoTraining", enabled);
        plugin.saveConfig();

        if (enabled && !wasEnabled) {
            MessageManager.log("info", "РІСљвЂ¦ Bot auto-training ENABLED - bots will spawn automatically");
            notifyAdmins("Р’В§a[Bot Training] Р’В§fРІСљвЂ¦ Automatic bot training Р’В§aENABLED");
        } else if (!enabled && wasEnabled) {
            MessageManager.log("info", "РІСњРЉ Bot auto-training DISABLED - no more automatic bot spawning");
            notifyAdmins("Р’В§c[Bot Training] Р’В§fРІСњРЉ Automatic bot training Р’В§cDISABLED");
        }
    }

    /**
     * Set maximum concurrent bots
     */
    public void setMaxConcurrentBots(int max) {
        int oldMax = this.maxConcurrentBots;
        this.maxConcurrentBots = Math.max(1, Math.min(max, 20)); // 1-20 bots max

        // Save to config
        plugin.getConfig().set("ml.bots.maxConcurrentBots", this.maxConcurrentBots);
        plugin.saveConfig();

        if (oldMax != this.maxConcurrentBots) {
            MessageManager.log("info", "СЂСџвЂќСћ Max concurrent bots changed from {OLD} to {NEW}",
                "OLD", String.valueOf(oldMax), "NEW", String.valueOf(this.maxConcurrentBots));
            notifyAdmins("Р’В§a[Bot Training] Р’В§fСЂСџвЂќСћ Max concurrent bots changed: Р’В§c" + oldMax + "Р’В§f РІвЂ вЂ™ Р’В§a" + this.maxConcurrentBots);
        }
    }

    /**
     * Set training session duration
     */
    public void setTrainingSessionDuration(int duration) {
        int oldDuration = this.trainingSessionDuration;
        this.trainingSessionDuration = Math.max(30, Math.min(duration, 600)); // 30s - 10min

        // Save to config
        plugin.getConfig().set("ml.bots.trainingSessionDuration", this.trainingSessionDuration);
        plugin.saveConfig();

        if (oldDuration != this.trainingSessionDuration) {
            MessageManager.log("info", "РІРЏВ° Training session duration changed from {OLD}s to {NEW}s",
                "OLD", String.valueOf(oldDuration), "NEW", String.valueOf(this.trainingSessionDuration));
            notifyAdmins("Р’В§a[Bot Training] Р’В§fРІРЏВ° Training session duration changed: Р’В§c" + oldDuration + "sР’В§f РІвЂ вЂ™ Р’В§a" + this.trainingSessionDuration + "s");
        }
    }

    /**
     * Format location for logging
     */
    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)",
            loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Bot behavior types
     */
    public enum BotBehaviorType {
        NORMAL_MINER,      // Mines legitimately, explores caves, branches out
        XRAY_CHEATER,      // Directly tunnels to valuable ores
        TUNNEL_MINER,      // Creates long straight tunnels
        RANDOM_MINER,      // Mines randomly without pattern
        EFFICIENT_MINER,   // Mines efficiently but legitimately
        SURFACE_MINER      // Mines near surface, avoids deep mining
    }

    /**
     * Training Bot class
     */
    public class TrainingBot {
        private final UUID botId;
        private final BotBehaviorType behaviorType;
        private final Location spawnLocation;
        private final FakePlayer fakePlayer;
        private BukkitTask miningTask;
        private long sessionStartTime;
        private int actionCount = 0;
        private long lastStatusLog = 0;
        private int blocksMined = 0;

        public TrainingBot(UUID botId, BotBehaviorType behaviorType, Location spawnLocation) {
            this.botId = botId;
            this.behaviorType = behaviorType;
            this.spawnLocation = spawnLocation.clone();
            this.fakePlayer = new FakePlayer(botId, "Bot_" + behaviorType.name().substring(0, 3) + "_" + totalBotsSpawned);
        }

        public void startTrainingSession() {
            sessionStartTime = System.currentTimeMillis();
            String botId = this.botId.toString().substring(0, 8);

            MessageManager.log("info", "СЂСџР‹Р‡ Bot {ID} starting training session ({TYPE}) at {LOCATION}",
                "ID", botId,
                "TYPE", behaviorType.name(),
                "LOCATION", formatLocation(spawnLocation));

                    notifyAdmins("Р’В§a[Bot Training] Р’В§fСЂСџР‹Р‡ Bot Р’В§e" + botId + "Р’В§f starts training (Р’В§b" + behaviorType.name() + "Р’В§f)");

            // Create fake player at spawn location
            fakePlayer.spawn(spawnLocation);

            // Start mining behavior based on type
            startMiningBehavior();
        }

        public void endTrainingSession() {
            // Stop mining task
            if (miningTask != null) {
                miningTask.cancel();
                miningTask = null;
            }

            // Remove fake player
            fakePlayer.despawn();

            // Submit training data to ML system
            submitTrainingData();

            long sessionDuration = (System.currentTimeMillis() - sessionStartTime) / 1000;
            String botId = this.botId.toString().substring(0, 8);

            MessageManager.log("info", "СЂСџРЏРѓ Bot {ID} training session completed after {SECONDS}s ({TYPE})",
                "ID", botId,
                "SECONDS", String.valueOf(sessionDuration),
                "TYPE", behaviorType.name());

            notifyAdmins("Р’В§a[Bot Training] Р’В§fСЂСџРЏРѓ Bot Р’В§e" + botId + "Р’В§f completed training session (Р’В§b" + behaviorType.name() + "Р’В§f, " + sessionDuration + " sec)");
        }

        private void startMiningBehavior() {
            miningTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                try {
                    performMiningAction();
                } catch (Exception e) {
                    MessageManager.log("error", "Error in bot mining behavior: {ERROR}",
                        "ERROR", e.getMessage());
                }
            }, 20L, 40L); // Every 2 seconds
        }

        private void performMiningAction() {
            Location currentLoc = fakePlayer.getLocation();
            if (currentLoc == null) return;

            actionCount++;

            // Log status every 30 seconds (15 actions at 2-second intervals)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStatusLog > 30000) {
                String botId = this.botId.toString().substring(0, 8);
                MessageManager.log("info", "СЂСџВ¤вЂ“ Bot {ID} status: {ACTIONS} actions, {BLOCKS} blocks mined, location {LOCATION}",
                    "ID", botId,
                    "ACTIONS", String.valueOf(actionCount),
                    "BLOCKS", String.valueOf(blocksMined),
                    "LOCATION", formatLocation(currentLoc));

                // Send status to admins every 60 seconds
                if (currentTime - lastStatusLog > 60000) {
                    notifyAdmins("Р’В§a[Bot Training] Р’В§fСЂСџВ¤вЂ“ Bot Р’В§e" + botId + "Р’В§f: " + actionCount + " actions, " + blocksMined + " blocks mined");
                    lastStatusLog = currentTime;
                }
            }

            switch (behaviorType) {
                case NORMAL_MINER:
                    performNormalMining(currentLoc);
                    break;
                case XRAY_CHEATER:
                    performXrayCheating(currentLoc);
                    break;
                case TUNNEL_MINER:
                    performTunnelMining(currentLoc);
                    break;
                case RANDOM_MINER:
                    performRandomMining(currentLoc);
                    break;
                case EFFICIENT_MINER:
                    performEfficientMining(currentLoc);
                    break;
                case SURFACE_MINER:
                    performSurfaceMining(currentLoc);
                    break;
            }
        }

        private void performNormalMining(Location loc) {
            // Simulate normal mining behavior - explore and mine nearby ores
            Location targetLoc = findNearbyOre(loc, Arrays.asList(
                Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
                Material.GOLD_ORE, Material.DIAMOND_ORE, Material.STONE
            ));

            if (targetLoc != null) {
                fakePlayer.moveTowards(targetLoc);
                mineBlockAt(targetLoc);
            } else {
                // Move randomly to explore
                fakePlayer.moveRandomly();
            }
        }

        private void performXrayCheating(Location loc) {
            // Simulate X-ray behavior - tunnel directly to valuable ores
            Location diamondLoc = findOreInDirection(loc, Material.DIAMOND_ORE, 50);
            if (diamondLoc != null) {
                fakePlayer.moveTowards(diamondLoc);
                // Create tunnel to diamond
                createTunnelTo(loc, diamondLoc);
                mineBlockAt(diamondLoc);
            } else {
                // Look for other valuable ores
                Location goldLoc = findOreInDirection(loc, Material.GOLD_ORE, 30);
                if (goldLoc != null) {
                    fakePlayer.moveTowards(goldLoc);
                    createTunnelTo(loc, goldLoc);
                    mineBlockAt(goldLoc);
                } else {
                    fakePlayer.moveRandomly();
                }
            }
        }

        private void performTunnelMining(Location loc) {
            // Create long straight tunnels
            Location tunnelEnd = loc.clone().add(
                ThreadLocalRandom.current().nextInt(-20, 21),
                ThreadLocalRandom.current().nextInt(-5, 6),
                ThreadLocalRandom.current().nextInt(-20, 21)
            );

            createTunnelTo(loc, tunnelEnd);
            fakePlayer.moveTowards(tunnelEnd);
        }

        private void performRandomMining(Location loc) {
            // Mine completely randomly
            Location randomLoc = loc.clone().add(
                ThreadLocalRandom.current().nextInt(-10, 11),
                ThreadLocalRandom.current().nextInt(-5, 6),
                ThreadLocalRandom.current().nextInt(-10, 11)
            );

            if (randomLoc.getBlock().getType().isSolid()) {
                mineBlockAt(randomLoc);
            }
            fakePlayer.moveRandomly();
        }

        private void performEfficientMining(Location loc) {
            // Efficient but legitimate mining - branch mining pattern
            Location branchTarget = findBestBranchLocation(loc);
            if (branchTarget != null) {
                fakePlayer.moveTowards(branchTarget);
                mineBlockAt(branchTarget);
            } else {
                fakePlayer.moveRandomly();
            }
        }

        private void performSurfaceMining(Location loc) {
            // Mine near surface, avoid deep mining
            if (loc.getY() > 20) {
                // Go down to mining level
                Location miningLoc = loc.clone();
                miningLoc.setY(10);
                fakePlayer.moveTowards(miningLoc);
            } else {
                // Mine at current level
                performNormalMining(loc);
            }
        }

        private Location findNearbyOre(Location center, List<Material> oreTypes) {
            World world = center.getWorld();
            if (world == null) return null;

            // Search in 5x5x5 area instead of 11x11x11 to save performance
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Location checkLoc = center.clone().add(x, y, z);
                        Material material = checkLoc.getBlock().getType();
                        if (oreTypes.contains(material)) {
                            return checkLoc;
                        }
                    }
                }
            }
            return null;
        }

        private Location findOreInDirection(Location start, Material oreType, int maxDistance) {
            World world = start.getWorld();
            if (world == null) return null;

            // Cast "X-ray" in random directions
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                double distance = ThreadLocalRandom.current().nextInt(10, maxDistance);

                int dx = (int)(Math.cos(angle) * distance);
                int dz = (int)(Math.sin(angle) * distance);
                int dy = ThreadLocalRandom.current().nextInt(-20, 21);

                Location checkLoc = start.clone().add(dx, dy, dz);
                if (checkLoc.getBlock().getType() == oreType) {
                    return checkLoc;
                }
            }
            return null;
        }

        private void createTunnelTo(Location from, Location to) {
            World world = from.getWorld();
            if (world == null) return;

            // Create a simple tunnel (break blocks in path)
            int steps = (int)from.distance(to);
            for (int i = 1; i < steps; i++) {
                double ratio = (double)i / steps;
                Location tunnelLoc = from.clone().add(
                    (to.getX() - from.getX()) * ratio,
                    (to.getY() - from.getY()) * ratio,
                    (to.getZ() - from.getZ()) * ratio
                );

                Block block = tunnelLoc.getBlock();
                if (block.getType().isSolid()) {
                    // Simulate mining
                    fakePlayer.recordBlockBreak(block.getType(), tunnelLoc);
                }
            }
        }

        private void mineBlockAt(Location loc) {
            Block block = loc.getBlock();
            if (block.getType().isSolid()) {
                fakePlayer.recordBlockBreak(block.getType(), loc);
                // Simulate block breaking
                block.setType(Material.AIR);
                blocksMined++;

                // Log individual mining actions occasionally
                if (blocksMined % 50 == 0) {
                    String botId = this.botId.toString().substring(0, 8);
                    MessageManager.log("info", "РІвЂєРЏРїС‘РЏ Bot {ID} mined {BLOCKS} blocks total ({TYPE} at {LOCATION})",
                        "ID", botId,
                        "BLOCKS", String.valueOf(blocksMined),
                        "TYPE", block.getType().name(),
                        "LOCATION", formatLocation(loc));
                }
            }
        }

        private Location findBestBranchLocation(Location center) {
            // Simple branch mining pattern - mine every 3 blocks
            World world = center.getWorld();
            if (world == null) return null;

            // Check current tunnel for ores
            for (int offset = 3; offset <= 9; offset += 3) {
                Location[] checkLocations = {
                    center.clone().add(offset, 0, 0),
                    center.clone().add(-offset, 0, 0),
                    center.clone().add(0, 0, offset),
                    center.clone().add(0, 0, -offset)
                };

                for (Location checkLoc : checkLocations) {
                    Material material = checkLoc.getBlock().getType();
                    if (material.name().contains("ORE")) {
                        return checkLoc;
                    }
                }
            }
            return null;
        }

        private void submitTrainingData() {
            String botId = this.botId.toString().substring(0, 8);
            MessageManager.log("info", "СЂСџвЂњР‰ Bot {ID} submitting training data...", "ID", botId);

            // Submit collected data to ML system
            Map<String, Double> features = fakePlayer.getMiningFeatures();
            boolean isCheater = behaviorType == BotBehaviorType.XRAY_CHEATER;

            MessageManager.log("info", "СЂСџвЂњв‚¬ Bot {ID} collected {FEATURES} features, labeled as {TYPE}",
                "ID", botId,
                "FEATURES", String.valueOf(features.size()),
                "TYPE", isCheater ? "CHEATER" : "NORMAL");

            if (!features.isEmpty()) {
                // Get ML manager and submit data
                ModernMLManager mlManager = plugin.getMLManager();
                if (mlManager != null) {
                    // Create fake player data for training
                    PlayerMiningData trainingData = fakePlayer.generateTrainingData(isCheater, behaviorType);
                    if (trainingData != null) {
                        try {
                            MessageManager.log("info", "СЂСџвЂ™С• Saving bot {ID} training data to ML system...", "ID", botId);
                            MLDataManager.saveTrainingData(trainingData);

                            MessageManager.log("info", "СЂСџвЂќвЂћ Triggering ML model retraining with bot {ID} data...", "ID", botId);
                            // Trigger model retraining
                            mlManager.retrainModel();

                            MessageManager.log("info", "РІСљвЂ¦ Bot {ID} training data successfully submitted and model retrained", "ID", botId);
                            notifyAdmins("Р’В§a[Bot Training] Р’В§fРІСљвЂ¦ Bot Р’В§e" + botId + "Р’В§f completed training (Р’В§b" + behaviorType.name() + "Р’В§f, " + features.size() + " features)");

                        } catch (Exception e) {
                            MessageManager.log("error", "РІСњРЉ Failed to save bot {ID} training data: {ERROR}",
                                "ID", botId, "ERROR", e.getMessage());
                            notifyAdmins("Р’В§c[Bot Training] Р’В§fРІСњРЉ Error saving bot Р’В§e" + botId + "Р’В§f data: " + e.getMessage());
                        }
                    } else {
                        MessageManager.log("warning", "РІС™В РїС‘РЏ Bot {ID} generated no training data", "ID", botId);
                        notifyAdmins("Р’В§e[Bot Training] Р’В§fРІС™В РїС‘РЏ Bot Р’В§e" + botId + "Р’В§f generated no training data");
                    }
                } else {
                    MessageManager.log("error", "РІСњРЉ ML Manager not available for bot {ID}", "ID", botId);
                }
            } else {
                MessageManager.log("warning", "РІС™В РїС‘РЏ Bot {ID} has no mining features to submit", "ID", botId);
                notifyAdmins("Р’В§e[Bot Training] Р’В§fРІС™В РїС‘РЏ Bot Р’В§e" + botId + "Р’В§f collected no mining data");
            }
        }

        public UUID getBotId() { return botId; }
        public BotBehaviorType getBehaviorType() { return behaviorType; }
        public FakePlayer getFakePlayer() { return fakePlayer; }
    }

    /**
     * Simple Fake Player implementation for bot training
     */
    public static class FakePlayer {
        private final UUID playerId;
        private final String playerName;
        private Location currentLocation;
        private final Map<String, Integer> blocksBroken = new HashMap<>();
        private final List<Long> miningTimestamps = new ArrayList<>();
        private long lastMoveTime = 0;

        public FakePlayer(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }

        public void spawn(Location location) {
            this.currentLocation = location.clone();
            MessageManager.log("info", "Spawned fake player {NAME} at {LOCATION}",
                "NAME", playerName,
                "LOCATION", formatLocation(location));
        }

        public void despawn() {
            MessageManager.log("info", "Despawned fake player {NAME}",
                "NAME", playerName);
        }

        public void moveTowards(Location target) {
            if (currentLocation == null) return;

            // Simple movement simulation
            double dx = target.getX() - currentLocation.getX();
            double dy = target.getY() - currentLocation.getY();
            double dz = target.getZ() - currentLocation.getZ();

            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (distance > 0) {
                double speed = 2.0; // blocks per move
                double moveX = dx / distance * Math.min(speed, distance);
                double moveY = dy / distance * Math.min(speed, distance);
                double moveZ = dz / distance * Math.min(speed, distance);

                currentLocation.add(moveX, moveY, moveZ);
            }

            lastMoveTime = System.currentTimeMillis();
        }

        public void moveRandomly() {
            if (currentLocation == null) return;

            // Random movement in +/- 5 block radius
            double moveX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 10;
            double moveY = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
            double moveZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 10;

            Location newLoc = currentLocation.clone().add(moveX, moveY, moveZ);

            // Keep within reasonable mining depths
            if (newLoc.getY() < -60) newLoc.setY(-60);
            if (newLoc.getY() > 50) newLoc.setY(50);

            currentLocation = newLoc;
            lastMoveTime = System.currentTimeMillis();
        }

        public void recordBlockBreak(Material material, Location location) {
            String materialName = material.name();
            blocksBroken.put(materialName, blocksBroken.getOrDefault(materialName, 0) + 1);
            miningTimestamps.add(System.currentTimeMillis());
        }

        public Map<String, Double> getMiningFeatures() {
            Map<String, Double> features = new HashMap<>();

            if (miningTimestamps.isEmpty()) return features;

            long sessionDuration = miningTimestamps.get(miningTimestamps.size() - 1) - miningTimestamps.get(0);
            if (sessionDuration <= 0) return features;

            double minutes = sessionDuration / 60000.0;
            int totalBlocks = blocksBroken.values().stream().mapToInt(Integer::intValue).sum();

            // Basic mining features
            features.put("blocks_per_minute", totalBlocks / minutes);
            features.put("unique_ores_mined", (double) blocksBroken.size());
            features.put("diamond_percentage", (double) blocksBroken.getOrDefault("DIAMOND_ORE", 0) / totalBlocks);
            features.put("stone_percentage", (double) blocksBroken.getOrDefault("STONE", 0) / totalBlocks);

            // Movement patterns (simplified)
            features.put("mining_depth", currentLocation != null ? -currentLocation.getY() : 0);

            // Mining efficiency metrics
            double valuableOres = blocksBroken.getOrDefault("DIAMOND_ORE", 0) +
                                 blocksBroken.getOrDefault("GOLD_ORE", 0) +
                                 blocksBroken.getOrDefault("ANCIENT_DEBRIS", 0);
            features.put("valuable_ore_ratio", totalBlocks > 0 ? valuableOres / totalBlocks : 0);

            return features;
        }

        public PlayerMiningData generateTrainingData(boolean isCheater, BotBehaviorType behaviorType) {
            if (blocksBroken.isEmpty()) return null;

            // Create synthetic player mining data
            PlayerMiningData data = new PlayerProtocolData(playerName, isCheater);

            // Populate features map directly with synthetic data
            Map<String, Double> features = data.getFeatures();

            // Basic mining statistics
            int totalBlocks = blocksBroken.values().stream().mapToInt(Integer::intValue).sum();
            features.put("total_blocks_broken", (double) totalBlocks);

            // Calculate mining time (simulate realistic session duration)
            long miningTimeMs = Math.max(120000, totalBlocks * 2000L); // At least 2 minutes, 2 seconds per block
            features.put("total_mining_time_seconds", miningTimeMs / 1000.0);

            // Ore-specific features
            int valuableOres = 0;
            int diamondOres = 0;
            int goldOres = 0;

            for (Map.Entry<String, Integer> entry : blocksBroken.entrySet()) {
                String materialName = entry.getKey();
                int count = entry.getValue();

                // Add ore count feature
                features.put("ore_count_" + materialName.toLowerCase(), (double) count);

                // Track valuable ores
                if (materialName.contains("DIAMOND")) {
                    diamondOres += count;
                    valuableOres += count;
                } else if (materialName.contains("GOLD")) {
                    goldOres += count;
                    valuableOres += count;
                } else if (materialName.contains("ANCIENT_DEBRIS") || materialName.contains("EMERALD")) {
                    valuableOres += count;
                }
            }

            features.put("total_ores_mined", (double) valuableOres);

            // Mining efficiency metrics
            if (totalBlocks > 0) {
                features.put("diamond_percentage", diamondOres * 100.0 / totalBlocks);
                features.put("gold_percentage", goldOres * 100.0 / totalBlocks);
                features.put("valuable_ore_ratio", valuableOres * 1.0 / totalBlocks);
            }

            // Depth and movement patterns (synthetic)
            double avgDepth = currentLocation != null ? -currentLocation.getY() : -20;
            features.put("average_mining_depth", avgDepth);

            // Mining speed (blocks per minute)
            double blocksPerMinute = totalBlocks / (miningTimeMs / 60000.0);
            features.put("mining_speed_blocks_per_minute", blocksPerMinute);

            // Pattern detection features (synthetic based on bot type)
            if (behaviorType == BotBehaviorType.XRAY_CHEATER) {
                features.put("straight_line_mining_ratio", 0.9); // High straight line ratio for X-ray
                features.put("branch_mining_ratio", 0.1);       // Low branch mining
                features.put("random_mining_ratio", 0.0);      // No random mining
            } else if (behaviorType == BotBehaviorType.TUNNEL_MINER) {
                features.put("straight_line_mining_ratio", 0.95);
                features.put("branch_mining_ratio", 0.05);
                features.put("random_mining_ratio", 0.0);
            } else if (behaviorType == BotBehaviorType.RANDOM_MINER) {
                features.put("straight_line_mining_ratio", 0.1);
                features.put("branch_mining_ratio", 0.1);
                features.put("random_mining_ratio", 0.8);
            } else {
                // Normal/Efficient/Surface miners
                features.put("straight_line_mining_ratio", 0.3);
                features.put("branch_mining_ratio", 0.6);
                features.put("random_mining_ratio", 0.1);
            }

            return data;
        }

        public Location getLocation() { return currentLocation; }

        private String formatLocation(Location loc) {
            return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
        }
    }
}
