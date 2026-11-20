package net.denfry.owml.ml;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.SchedulerUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BotTrainingManager {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, BotTrainingManager.TrainingBot> activeBots = new ConcurrentHashMap<>();
   private volatile boolean autoTrainingEnabled = false;
   private volatile int maxConcurrentBots = 5;
   private volatile int trainingSessionDuration = 120;
   private volatile int botSpawnInterval = 30;
   private volatile long lastSpawnTime = 0L;
   private volatile int totalBotsSpawned = 0;
   private volatile int totalTrainingSessions = 0;

   public BotTrainingManager() {
      this.loadConfigSettings();
      this.initializeSpawnLocations();
      if (SchedulerUtils.isFolia()) {
         this.autoTrainingEnabled = false;
         MessageManager.log("info", "Bot training disabled on Folia/Tessera (unsafe world access)");
      } else {
         this.startAutoTrainingLoop();
      }
   }

   private void loadConfigSettings() {
      try {
         this.autoTrainingEnabled = plugin.getConfig().getBoolean("ml.bots.autoTraining", false);
         this.maxConcurrentBots = plugin.getConfig().getInt("ml.bots.maxConcurrentBots", 5);
         this.trainingSessionDuration = plugin.getConfig().getInt("ml.bots.trainingSessionDuration", 120);
         this.botSpawnInterval = plugin.getConfig().getInt("ml.bots.spawnInterval", 30);
         MessageManager.log(
            "info",
            "Bot training config loaded: auto={AUTO}, max={MAX}, duration={DURATION}s, interval={INTERVAL}s",
            "AUTO",
            String.valueOf(this.autoTrainingEnabled),
            "MAX",
            String.valueOf(this.maxConcurrentBots),
            "DURATION",
            String.valueOf(this.trainingSessionDuration),
            "INTERVAL",
            String.valueOf(this.botSpawnInterval)
         );
      } catch (Exception var2) {
         MessageManager.log("warning", "Failed to load bot training config, using defaults: {ERROR}", "ERROR", var2.getMessage());
      }
   }

   private void initializeSpawnLocations() {
      MessageManager.log("info", "Bot training system initialized - spawn locations will be found dynamically");
   }

   private Location findDynamicSpawnLocation() {
      World world = (World)Bukkit.getWorlds().get(0);
      if (world == null) {
         return null;
      } else {
         for (int attempt = 0; attempt < 20; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-10000, 10000);
            int z = ThreadLocalRandom.current().nextInt(-10000, 10000);
            int[] yLevels = new int[]{-50, -30, -10, 10, 30};

            for (int baseY : yLevels) {
               Location center = new Location(world, x, baseY, z);
               Location safeLoc = this.findSafeSpawnLocation(center, 100);
               if (safeLoc != null) {
                  return safeLoc;
               }
            }
         }

         return null;
      }
   }

   private Location findSafeSpawnLocation(Location center, int searchRadius) {
      World world = center.getWorld();
      if (world == null) {
         return null;
      } else {
         for (int radius = 10; radius <= searchRadius; radius += 10) {
            for (int x = -radius; x <= radius; x += 5) {
               for (int z = -radius; z <= radius; z += 5) {
                  if (Math.abs(x) >= radius - 5 || Math.abs(z) >= radius - 5) {
                     Location testLoc = center.clone().add(x, 0.0, z);
                     int surfaceY = world.getHighestBlockYAt(testLoc.getBlockX(), testLoc.getBlockZ());

                     for (int yOffset = -5; yOffset <= 5; yOffset += 2) {
                        Location surfaceLoc = new Location(world, testLoc.getX(), surfaceY + yOffset, testLoc.getZ());
                        if (!(surfaceLoc.getY() < -64.0) && !(surfaceLoc.getY() > 320.0) && this.isSafeLocation(surfaceLoc)) {
                           return surfaceLoc;
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private boolean isSafeLocation(Location location) {
      World world = location.getWorld();
      if (world == null) {
         return false;
      } else {
         Block block = location.getBlock();
         Block below = location.clone().subtract(0.0, 1.0, 0.0).getBlock();
         Block above = location.clone().add(0.0, 1.0, 0.0).getBlock();
         return below.getType().isSolid() && block.getType().isAir() && above.getType().isAir() && !this.isDangerousLocation(location);
      }
   }

   private boolean isDangerousLocation(Location location) {
      World world = location.getWorld();
      if (world == null) {
         return true;
      } else {
         for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  Block block = location.clone().add(x, y, z).getBlock();
                  Material type = block.getType();
                  if (type == Material.LAVA || type == Material.FIRE || type.name().contains("MAGMA") || type.name().contains("CACTUS")) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private void notifyAdmins(String message) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission("owml.ml.train") || player.hasPermission("owml.admin")) {
            player.sendMessage(message);
         }
      }
   }

   private void startAutoTrainingLoop() {
      MessageManager.log("info", "\ud83e\udd16 Bot auto-training loop started - checking every 60 seconds");
      Schedulers.asyncTimer(() -> {
         try {
            if (this.autoTrainingEnabled && this.shouldSpawnTrainingBot()) {
               Schedulers.global(this::spawnRandomTrainingBot);
            }
         } catch (Exception var2) {
            MessageManager.log("error", "Error in bot training loop: {ERROR}", "ERROR", var2.getMessage());
         }
      }, 1200L, 1200L);
   }

   private boolean shouldSpawnTrainingBot() {
      long currentTime = System.currentTimeMillis();
      return this.activeBots.size() < this.maxConcurrentBots
         && currentTime - this.lastSpawnTime >= this.botSpawnInterval * 1000L
         && ThreadLocalRandom.current().nextDouble() < 0.7;
   }

   private void spawnRandomTrainingBot() {
      Schedulers.async(
         () -> {
            Location spawnLoc = this.findDynamicSpawnLocation();
            if (spawnLoc != null) {
               Schedulers.global(
                  () -> {
                     this.lastSpawnTime = System.currentTimeMillis();
                     BotTrainingManager.BotBehaviorType botType = this.getRandomBotType();
                     BotTrainingManager.TrainingBot bot = new BotTrainingManager.TrainingBot(UUID.randomUUID(), botType, spawnLoc);
                     this.activeBots.put(bot.getBotId(), bot);
                     this.totalBotsSpawned++;
                     String botId = bot.getBotId().toString().substring(0, 8);
                     MessageManager.log(
                        "info",
                        "\ud83e\udd16 Spawned training bot {ID} ({TYPE}) at {LOCATION}",
                        "ID",
                        botId,
                        "TYPE",
                        botType.name(),
                        "LOCATION",
                        this.formatLocation(spawnLoc)
                     );
                     this.notifyAdmins("§a[Bot Training] §f✔ Spawned bot §e" + botId + "§f (§b" + botType.name() + "§f) at §e" + this.formatLocation(spawnLoc));
                     bot.startTrainingSession();
                     Schedulers.globalLater(() -> this.removeBot(bot.getBotId()), this.trainingSessionDuration * 20L);
                  }
               );
            }
         }
      );
   }

   private BotTrainingManager.BotBehaviorType getRandomBotType() {
      BotTrainingManager.BotBehaviorType[] types = BotTrainingManager.BotBehaviorType.values();
      return types[ThreadLocalRandom.current().nextInt(types.length)];
   }

   public void spawnTrainingBot(BotTrainingManager.BotBehaviorType type, int count) {
      if (SchedulerUtils.isFolia()) {
         this.notifyAdmins("§c[Bot Training] Недоступно на Folia/Tessera.");
      } else {
         Schedulers.async(() -> {
            int spawned = 0;

            for (int i = 0; i < count && this.activeBots.size() < this.maxConcurrentBots; i++) {
               Location spawnLoc = this.findDynamicSpawnLocation();
               if (spawnLoc != null) {
                  Schedulers.global(() -> {
                     this.lastSpawnTime = System.currentTimeMillis();
                     BotTrainingManager.TrainingBot bot = new BotTrainingManager.TrainingBot(UUID.randomUUID(), type, spawnLoc);
                     this.activeBots.put(bot.getBotId(), bot);
                     this.totalBotsSpawned++;
                     this.notifyAdmins("§a[Bot Training] §fSpawned " + type.name() + " bot at " + this.formatLocation(spawnLoc));
                     bot.startTrainingSession();
                     Schedulers.globalLater(() -> this.removeBot(bot.getBotId()), this.trainingSessionDuration * 20L);
                  });
                  spawned++;
               }
            }
         });
      }
   }

   private void removeBot(UUID botId) {
      BotTrainingManager.TrainingBot bot = this.activeBots.remove(botId);
      if (bot != null) {
         String shortId = botId.toString().substring(0, 8);
         bot.endTrainingSession();
         this.totalTrainingSessions++;
         MessageManager.log(
            "info",
            "\ud83d\udc4b Removed training bot {ID} ({TYPE}) after {SECONDS}s",
            "ID",
            shortId,
            "TYPE",
            bot.getBehaviorType().name(),
            "SECONDS",
            String.valueOf(this.trainingSessionDuration)
         );
         this.notifyAdmins("§a[Bot Training] §f\ud83d\udc4b Bot §e" + shortId + "§f removed (§aTotal sessions: " + this.totalTrainingSessions + "§f)");
      }
   }

   public void removeAllBots() {
      int botCount = this.activeBots.size();
      if (botCount > 0) {
         for (BotTrainingManager.TrainingBot bot : this.activeBots.values()) {
            bot.endTrainingSession();
         }

         this.activeBots.clear();
         this.notifyAdmins("§a[Bot Training] §f✔ Removed all §c" + botCount + "§f bots");
      }
   }

   public BotTrainingManager.TrainingBot findBotById(String partialId) {
      if (partialId != null && !partialId.trim().isEmpty()) {
         String searchId = partialId.toLowerCase().trim();

         for (Entry<UUID, BotTrainingManager.TrainingBot> entry : this.activeBots.entrySet()) {
            String botIdStr = entry.getKey().toString();
            if (botIdStr.toLowerCase().startsWith(searchId) || botIdStr.toLowerCase().contains(searchId)) {
               return entry.getValue();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public Map<UUID, BotTrainingManager.TrainingBot> getActiveBots() {
      return new HashMap<>(this.activeBots);
   }

   public Map<String, Object> getTrainingStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("activeBots", this.activeBots.size());
      stats.put("totalBotsSpawned", this.totalBotsSpawned);
      stats.put("totalTrainingSessions", this.totalTrainingSessions);
      stats.put("autoTrainingEnabled", this.autoTrainingEnabled);
      stats.put("maxConcurrentBots", this.maxConcurrentBots);
      stats.put("sessionDuration", this.trainingSessionDuration);
      return stats;
   }

   public void setAutoTrainingEnabled(boolean enabled) {
      if (SchedulerUtils.isFolia()) {
         this.notifyAdmins("§c[Bot Training] Недоступно на Folia/Tessera.");
      } else {
         this.autoTrainingEnabled = enabled;
         plugin.getConfig().set("ml.bots.autoTraining", enabled);
         plugin.saveConfig();
         this.notifyAdmins("§a[Bot Training] §f✔ Automatic bot training " + (enabled ? "§aENABLED" : "§cDISABLED"));
      }
   }

   public void setMaxConcurrentBots(int max) {
      this.maxConcurrentBots = Math.max(1, Math.min(max, 20));
      plugin.getConfig().set("ml.bots.maxConcurrentBots", this.maxConcurrentBots);
      plugin.saveConfig();
   }

   private String formatLocation(Location loc) {
      return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
   }

   public static enum BotBehaviorType {
      NORMAL_MINER,
      XRAY_CHEATER,
      TUNNEL_MINER,
      RANDOM_MINER,
      EFFICIENT_MINER,
      SURFACE_MINER;
   }

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
         this.recordPosition();
      }

      public void despawn() {
      }

      public void moveTowards(Location target) {
         if (this.location != null) {
            Vector dir = target.toVector().subtract(this.location.toVector());
            if (dir.lengthSquared() > 0.0) {
               dir.normalize().multiply(1.5);
               this.location.add(dir);
            }

            this.recordPosition();
         }
      }

      public void moveRandomly() {
         if (this.location != null) {
            this.location
               .add(
                  ThreadLocalRandom.current().nextDouble(-2.0, 2.0),
                  ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                  ThreadLocalRandom.current().nextDouble(-2.0, 2.0)
               );
            if (this.location.getY() < -64.0) {
               this.location.setY(-64.0);
            }

            if (this.location.getY() > 320.0) {
               this.location.setY(320.0);
            }

            this.recordPosition();
         }
      }

      public void recordBlockBreak(Block block) {
         this.data.recordBlockBreak(block, this.location.getDirection());
         this.recordPosition();
      }

      private void recordPosition() {
         this.data.recordPlayerPosition(this.location.toVector());
      }

      public PlayerProtocolData getTrainingData() {
         return this.data;
      }

      public Location getLocation() {
         return this.location;
      }
   }

   public class TrainingBot {
      private final UUID botId;
      private final BotTrainingManager.BotBehaviorType behaviorType;
      private final Location spawnLocation;
      private final BotTrainingManager.FakePlayer fakePlayer;
      private Schedulers.Task behaviorTask;
      private long sessionStartTime;
      private int blocksMined = 0;

      public TrainingBot(UUID botId, BotTrainingManager.BotBehaviorType behaviorType, Location spawnLocation) {
         this.botId = botId;
         this.behaviorType = behaviorType;
         this.spawnLocation = spawnLocation.clone();
         boolean isCheater = behaviorType == BotTrainingManager.BotBehaviorType.XRAY_CHEATER;
         this.fakePlayer = new BotTrainingManager.FakePlayer(botId, "Bot_" + botId.toString().substring(0, 4), isCheater);
      }

      public void startTrainingSession() {
         this.sessionStartTime = System.currentTimeMillis();
         this.fakePlayer.spawn(this.spawnLocation);
         this.startBehavior();
      }

      public void endTrainingSession() {
         if (this.behaviorTask != null) {
            this.behaviorTask.cancel();
            this.behaviorTask = null;
         }

         this.fakePlayer.despawn();
         this.submitTrainingData();
      }

      private void startBehavior() {
         this.behaviorTask = Schedulers.globalTimer(() -> {
            try {
               this.performAction();
            } catch (Exception var2) {
               MessageManager.log("error", "Error in bot behavior: {ERROR}", "ERROR", var2.getMessage());
            }
         }, 20L, 40L);
      }

      private void performAction() {
         Location loc = this.fakePlayer.getLocation();
         if (loc != null) {
            switch (this.behaviorType) {
               case NORMAL_MINER:
                  this.performNormalMining(loc);
                  break;
               case XRAY_CHEATER:
                  this.performXrayCheating(loc);
                  break;
               case TUNNEL_MINER:
                  this.performTunnelMining(loc);
                  break;
               case RANDOM_MINER:
                  this.performRandomMining(loc);
                  break;
               case EFFICIENT_MINER:
                  this.performEfficientMining(loc);
                  break;
               case SURFACE_MINER:
                  this.performSurfaceMining(loc);
            }
         }
      }

      private void performNormalMining(Location loc) {
         Location target = this.findNearbyOre(loc, Arrays.asList(Material.IRON_ORE, Material.COAL_ORE, Material.STONE));
         if (target != null) {
            this.fakePlayer.moveTowards(target);
            this.mineBlockAt(target);
         } else {
            this.fakePlayer.moveRandomly();
         }
      }

      private void performXrayCheating(Location loc) {
         Location target = this.findOreInDirection(loc, Material.DIAMOND_ORE, 30);
         if (target == null) {
            target = this.findOreInDirection(loc, Material.GOLD_ORE, 20);
         }

         if (target != null) {
            this.fakePlayer.moveTowards(target);
            this.mineBlockAt(target);
         } else {
            this.fakePlayer.moveRandomly();
         }
      }

      private void performTunnelMining(Location loc) {
         Vector dir = loc.getDirection();
         Location target = loc.clone().add(dir.multiply(2));
         this.fakePlayer.moveTowards(target);
         this.mineBlockAt(target);
      }

      private void performRandomMining(Location loc) {
         this.fakePlayer.moveRandomly();
         this.mineBlockAt(loc.clone().add(0.0, 1.0, 0.0));
      }

      private void performEfficientMining(Location loc) {
         this.performNormalMining(loc);
      }

      private void performSurfaceMining(Location loc) {
         if (loc.getY() > 40.0) {
            this.performNormalMining(loc);
         } else {
            Location up = loc.clone().add(0.0, 10.0, 0.0);
            this.fakePlayer.moveTowards(up);
         }
      }

      private Location findNearbyOre(Location center, List<Material> ores) {
         World world = center.getWorld();

         for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
               for (int z = -2; z <= 2; z++) {
                  Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                  if (ores.contains(b.getType())) {
                     return b.getLocation();
                  }
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
            if (b.getType() == ore) {
               return b.getLocation();
            }
         }

         return null;
      }

      private void mineBlockAt(Location loc) {
         Block b = loc.getBlock();
         if (b.getType().isSolid()) {
            this.fakePlayer.recordBlockBreak(b);
            this.blocksMined++;
         }
      }

      private void submitTrainingData() {
         PlayerProtocolData data = this.fakePlayer.getTrainingData();
         if (data != null) {
            data.calculateDerivedFeatures();
            ModernMLManager mlManager = BotTrainingManager.plugin.getMLManager();
            if (mlManager != null) {
               MLDataManager.saveTrainingData(data);
               mlManager.retrainModel();
            }
         }
      }

      public UUID getBotId() {
         return this.botId;
      }

      public BotTrainingManager.BotBehaviorType getBehaviorType() {
         return this.behaviorType;
      }

      public BotTrainingManager.FakePlayer getFakePlayer() {
         return this.fakePlayer;
      }
   }
}
