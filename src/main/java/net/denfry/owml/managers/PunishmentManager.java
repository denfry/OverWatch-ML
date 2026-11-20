package net.denfry.owml.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.listeners.PunishmentListener;
import net.denfry.owml.utils.AsyncExecutor;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.BanList.Type;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PunishmentManager {
   private final ConfigManager configManager;
   private final OverWatchML plugin;
   private final Map<UUID, Long> miningLicenseSuspension = new ConcurrentHashMap<>();
   private final Map<UUID, Long> restrictedAreas = new ConcurrentHashMap<>();
   private final Map<UUID, Long> resourceTaxActive = new ConcurrentHashMap<>();
   private final Map<UUID, Long> cursedPickaxeActive = new ConcurrentHashMap<>();
   private final Map<UUID, Long> stoneVisionActive = new ConcurrentHashMap<>();
   private final Map<UUID, Long> paranoiaModeActive = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> fakeDiamondsRemaining = new ConcurrentHashMap<>();
   private final Set<UUID> permanentMiningDebuff = ConcurrentHashMap.newKeySet();
   private final Set<Block> fakeOreBlocks = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> fakeOreVeinsActive = new ConcurrentHashMap<>();
   private final Map<UUID, Long> foolsGoldActive = new ConcurrentHashMap<>();
   private final Map<UUID, Entry<Component, Long>> miningReputations = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> playerPunishmentLevels = new ConcurrentHashMap<>();
   private File punishmentDataFile;
   private FileConfiguration punishmentConfig;
   private volatile Schedulers.Task autoSaveTask = null;

   public PunishmentManager(ConfigManager configManager, OverWatchML plugin) {
      this.configManager = configManager;
      this.plugin = plugin;
      this.initializeDataStorage();
      Bukkit.getPluginManager().registerEvents(new PunishmentListener(this, plugin.getDecoyManager(), configManager, plugin), plugin);
   }

   private void initializeDataStorage() {
      if (!this.plugin.getDataFolder().exists()) {
         this.plugin.getDataFolder().mkdir();
      }

      this.punishmentDataFile = new File(this.plugin.getDataFolder(), "punishment_data.yml");
      if (!this.punishmentDataFile.exists()) {
         try {
            this.punishmentDataFile.createNewFile();
         } catch (IOException var2) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create punishment_data.yml", (Throwable)var2);
         }
      }

      this.punishmentConfig = YamlConfiguration.loadConfiguration(this.punishmentDataFile);
      this.loadPunishmentData();
   }

   private void startAutoSaveTask() {
      if (this.autoSaveTask != null) {
         this.autoSaveTask.cancel();
      }

      long autoSaveInterval = this.configManager.getPunishmentAutoSaveInterval() * 1200L;
      this.autoSaveTask = Schedulers.asyncTimer(() -> {
         if (this.configManager.isPunishmentAutoSaveLoggingEnabled()) {
            this.plugin.getLogger().info("Auto-saving punishment data...");
         }

         this.savePunishmentData();
         if (this.configManager.isPunishmentAutoSaveLoggingEnabled()) {
            this.plugin.getLogger().info("Auto-save complete for punishment data");
         }
      }, autoSaveInterval, autoSaveInterval);
      if (this.configManager.isPunishmentAutoSaveLoggingEnabled()) {
         this.plugin.getLogger().info("Started auto-save task for punishment data (every " + this.configManager.getPunishmentAutoSaveInterval() + " minutes)");
      }
   }

   public void updateAutoSaveSettings() {
      if (this.configManager.isPunishmentAutoSaveEnabled() && this.autoSaveTask == null) {
         this.startAutoSaveTask();
      } else if (!this.configManager.isPunishmentAutoSaveEnabled() && this.autoSaveTask != null) {
         this.autoSaveTask.cancel();
         this.autoSaveTask = null;
         if (this.configManager.isPunishmentAutoSaveLoggingEnabled()) {
            this.plugin.getLogger().info("Auto-save for punishment data has been disabled");
         }
      } else if (this.configManager.isPunishmentAutoSaveEnabled()) {
         this.startAutoSaveTask();
      }
   }

   public void savePunishmentData() {
      this.punishmentConfig.set("punishments", null);
      this.saveLongMap("miningLicenseSuspension", this.miningLicenseSuspension);
      this.saveLongMap("restrictedAreas", this.restrictedAreas);
      this.saveLongMap("resourceTaxActive", this.resourceTaxActive);
      this.saveLongMap("cursedPickaxeActive", this.cursedPickaxeActive);
      this.saveLongMap("stoneVisionActive", this.stoneVisionActive);
      this.saveLongMap("paranoiaModeActive", this.paranoiaModeActive);
      this.saveLongMap("fakeOreVeinsActive", this.fakeOreVeinsActive);
      this.saveLongMap("foolsGoldActive", this.foolsGoldActive);
      this.saveIntegerMap("fakeDiamondsRemaining", this.fakeDiamondsRemaining);
      this.saveIntegerMap("playerPunishmentLevels", this.playerPunishmentLevels);
      this.saveUUIDSet("permanentMiningDebuff", this.permanentMiningDebuff);
      this.saveMiningReputations();
      String configString = this.punishmentConfig.saveToString();
      AsyncExecutor.submitIO(() -> {
         try {
            Files.writeString(this.punishmentDataFile.toPath(), configString);
         } catch (IOException var3) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save punishment data", (Throwable)var3);
         }

         return null;
      });
   }

   private void loadPunishmentData() {
      ConfigurationSection section = this.punishmentConfig.getConfigurationSection("punishments");
      if (section == null) {
         this.plugin.getLogger().info("No saved punishment data found.");
      } else {
         this.loadLongMap("miningLicenseSuspension", this.miningLicenseSuspension);
         this.loadLongMap("restrictedAreas", this.restrictedAreas);
         this.loadLongMap("resourceTaxActive", this.resourceTaxActive);
         this.loadLongMap("cursedPickaxeActive", this.cursedPickaxeActive);
         this.loadLongMap("stoneVisionActive", this.stoneVisionActive);
         this.loadLongMap("paranoiaModeActive", this.paranoiaModeActive);
         this.loadLongMap("fakeOreVeinsActive", this.fakeOreVeinsActive);
         this.loadLongMap("foolsGoldActive", this.foolsGoldActive);
         this.loadIntegerMap("fakeDiamondsRemaining", this.fakeDiamondsRemaining);
         this.loadIntegerMap("playerPunishmentLevels", this.playerPunishmentLevels);
         this.loadUUIDSet("permanentMiningDebuff", this.permanentMiningDebuff);
         this.loadMiningReputations();
         this.plugin.getLogger().info("Loaded punishment data successfully");
         this.cleanupExpiredPunishments();
      }
   }

   private void cleanupExpiredPunishments() {
      long currentTime = System.currentTimeMillis();
      this.cleanupExpiredMaps(currentTime);
      this.plugin.getLogger().info("Cleaned up expired punishments");
   }

   private void saveLongMap(String mapName, Map<UUID, Long> map) {
      for (Entry<UUID, Long> entry : map.entrySet()) {
         this.punishmentConfig.set("punishments." + mapName + "." + entry.getKey().toString(), entry.getValue());
      }
   }

   private void loadLongMap(String mapName, Map<UUID, Long> map) {
      map.clear();
      ConfigurationSection section = this.punishmentConfig.getConfigurationSection("punishments." + mapName);
      if (section != null) {
         for (String uuidString : section.getKeys(false)) {
            try {
               UUID uuid = UUID.fromString(uuidString);
               long value = section.getLong(uuidString);
               map.put(uuid, value);
            } catch (IllegalArgumentException var9) {
               this.plugin.getLogger().warning("Invalid UUID in punishment data: " + uuidString);
            }
         }
      }
   }

   private void saveIntegerMap(String mapName, Map<UUID, Integer> map) {
      for (Entry<UUID, Integer> entry : map.entrySet()) {
         this.punishmentConfig.set("punishments." + mapName + "." + entry.getKey().toString(), entry.getValue());
      }
   }

   private void loadIntegerMap(String mapName, Map<UUID, Integer> map) {
      map.clear();
      ConfigurationSection section = this.punishmentConfig.getConfigurationSection("punishments." + mapName);
      if (section != null) {
         for (String uuidString : section.getKeys(false)) {
            try {
               UUID uuid = UUID.fromString(uuidString);
               int value = section.getInt(uuidString);
               map.put(uuid, value);
            } catch (IllegalArgumentException var8) {
               this.plugin.getLogger().warning("Invalid UUID in punishment data: " + uuidString);
            }
         }
      }
   }

   private void saveUUIDSet(String setName, Set<UUID> set) {
      List<String> uuidStrings = new ArrayList<>();

      for (UUID uuid : set) {
         uuidStrings.add(uuid.toString());
      }

      this.punishmentConfig.set("punishments." + setName, uuidStrings);
   }

   private void loadUUIDSet(String setName, Set<UUID> set) {
      set.clear();

      for (String uuidString : this.punishmentConfig.getStringList("punishments." + setName)) {
         try {
            set.add(UUID.fromString(uuidString));
         } catch (IllegalArgumentException var7) {
            this.plugin.getLogger().warning("Invalid UUID in punishment data: " + uuidString);
         }
      }
   }

   private void saveMiningReputations() {
      for (Entry<UUID, Entry<Component, Long>> entry : this.miningReputations.entrySet()) {
         UUID uuid = entry.getKey();
         Component reputation = entry.getValue().getKey();
         Long expiry = entry.getValue().getValue();
         String serializedComponent = (String)GsonComponentSerializer.gson().serialize(reputation);
         this.punishmentConfig.set("punishments.miningReputations." + uuid.toString() + ".reputation", serializedComponent);
         this.punishmentConfig.set("punishments.miningReputations." + uuid + ".expiry", expiry);
      }
   }

   private void loadMiningReputations() {
      this.miningReputations.clear();
      ConfigurationSection section = this.punishmentConfig.getConfigurationSection("punishments.miningReputations");
      if (section != null) {
         for (String uuidString : section.getKeys(false)) {
            try {
               UUID uuid = UUID.fromString(uuidString);
               String serializedComponent = section.getString(uuidString + ".reputation");
               Long expiry = section.getLong(uuidString + ".expiry");
               Component reputation = GsonComponentSerializer.gson().deserialize(serializedComponent);
               this.miningReputations.put(uuid, new SimpleEntry<>(reputation, expiry));
            } catch (Exception var8) {
               this.plugin.getLogger().warning("Error loading mining reputation for: " + uuidString + " - " + var8.getMessage());
            }
         }
      }
   }

   public void setPunishmentLevel(Player player, int level) {
      if (!Schedulers.isOwned(player)) {
         Schedulers.entity(player, () -> this.setPunishmentLevel(player, level));
      } else {
         UUID playerId = player.getUniqueId();
         int currentLevel = this.playerPunishmentLevels.getOrDefault(playerId, 0);
         if (currentLevel > 0) {
            this.removeAllPunishmentEffects(player, currentLevel);
         }

         if (level > 0 && level <= 6) {
            this.applyPunishment(player, level);
            this.playerPunishmentLevels.put(playerId, level);
         } else if (level == 0) {
            this.playerPunishmentLevels.remove(playerId);
         }
      }
   }

   public void removePunishment(Player player) {
      if (!Schedulers.isOwned(player)) {
         Schedulers.entity(player, () -> this.removePunishment(player));
      } else {
         UUID playerId = player.getUniqueId();
         int currentLevel = this.playerPunishmentLevels.getOrDefault(playerId, 0);
         if (currentLevel > 0) {
            this.removeAllPunishmentEffects(player, currentLevel);
            this.playerPunishmentLevels.remove(playerId);
         }
      }
   }

   public void startDisabledLevelCheckTask() {
      (new Schedulers.Repeating() {
         @Override
         public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
               UUID playerId = player.getUniqueId();
               Integer currentLevel = PunishmentManager.this.playerPunishmentLevels.get(playerId);
               if (currentLevel != null && currentLevel > 0 && !PunishmentManager.this.configManager.isPunishmentEnabled(currentLevel)) {
                  int level = currentLevel;
                  Schedulers.entity(player, () -> {
                     PunishmentManager.this.removeAllPunishmentEffects(player, level);
                     PunishmentManager.this.playerPunishmentLevels.remove(playerId);
                     player.sendMessage(Component.text("Your punishment has been lifted due to a system update.", NamedTextColor.GREEN));
                  });
               }
            }
         }
      }).globalTimer(1200L, 6000L);
   }

   public void startCleanupTask() {
      (new Schedulers.Repeating() {
         private int runCount = 0;

         @Override
         public void run() {
            long currentTime = System.currentTimeMillis();
            PunishmentManager.this.cleanupExpiredMaps(currentTime);
            if (this.getRunCount() % 3 == 0) {
               PunishmentManager.this.cleanupFakeOreBlocks();
            }
         }

         private int getRunCount() {
            return this.runCount++;
         }
      }).globalTimer(1200L, 6000L);
   }

   private void cleanupExpiredMaps(long currentTime) {
      this.miningLicenseSuspension.values().removeIf(expiry -> expiry < currentTime);
      this.restrictedAreas.values().removeIf(expiry -> expiry < currentTime);
      this.resourceTaxActive.values().removeIf(expiry -> expiry < currentTime);
      this.cursedPickaxeActive.values().removeIf(expiry -> expiry < currentTime);
      this.stoneVisionActive.values().removeIf(expiry -> expiry < currentTime);
      this.paranoiaModeActive.values().removeIf(expiry -> expiry < currentTime);
      this.fakeOreVeinsActive.values().removeIf(expiry -> expiry < currentTime);
      this.foolsGoldActive.values().removeIf(expiry -> expiry < currentTime);
      this.miningReputations.entrySet().removeIf(entry -> entry.getValue().getValue() < currentTime);
   }

   private void cleanupFakeOreBlocks() {
      for (Block block : this.fakeOreBlocks) {
         if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            Schedulers.at(block.getLocation(), () -> {
               if (!this.isOre(block.getType())) {
                  this.fakeOreBlocks.remove(block);
               }
            });
         }
      }
   }

   private boolean isOre(Material material) {
      switch (material) {
         case COAL_ORE:
         case DEEPSLATE_COAL_ORE:
         case COPPER_ORE:
         case DEEPSLATE_COPPER_ORE:
         case IRON_ORE:
         case DEEPSLATE_IRON_ORE:
         case GOLD_ORE:
         case DEEPSLATE_GOLD_ORE:
         case REDSTONE_ORE:
         case DEEPSLATE_REDSTONE_ORE:
         case EMERALD_ORE:
         case DEEPSLATE_EMERALD_ORE:
         case LAPIS_ORE:
         case DEEPSLATE_LAPIS_ORE:
         case DIAMOND_ORE:
         case DEEPSLATE_DIAMOND_ORE:
         case NETHER_GOLD_ORE:
         case NETHER_QUARTZ_ORE:
         case ANCIENT_DEBRIS:
            return true;
         default:
            return false;
      }
   }

   public void resetMiningReputation(UUID playerId) {
      this.miningReputations.remove(playerId);
   }

   public void setMiningReputation(UUID playerId, Component reputation, long duration) {
      long expiry = System.currentTimeMillis() + duration;
      this.miningReputations.put(playerId, new SimpleEntry<>(reputation, expiry));
   }

   public boolean isUntrustedMiner(UUID playerId) {
      Component reputation = this.getMiningReputation(playerId);
      return reputation != null && reputation.color() == NamedTextColor.RED && reputation.toString().contains("Untrusted Miner");
   }

   public Component getMiningReputation(UUID playerId) {
      if (this.miningReputations.containsKey(playerId)) {
         Entry<Component, Long> entry = this.miningReputations.get(playerId);
         if (entry.getValue() > System.currentTimeMillis()) {
            return entry.getKey();
         }

         this.miningReputations.remove(playerId);
      }

      return Component.text("Trusted Miner");
   }

   public boolean hasMiningLicenseSuspension(UUID playerId) {
      return this.miningLicenseSuspension.containsKey(playerId) && this.miningLicenseSuspension.get(playerId) > System.currentTimeMillis();
   }

   public long getMiningLicenseSuspensionTime(UUID playerId) {
      return !this.hasMiningLicenseSuspension(playerId) ? 0L : this.miningLicenseSuspension.get(playerId) - System.currentTimeMillis();
   }

   public boolean hasAreaRestriction(UUID playerId) {
      return this.restrictedAreas.containsKey(playerId) && this.restrictedAreas.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasResourceTax(UUID playerId) {
      return this.resourceTaxActive.containsKey(playerId) && this.resourceTaxActive.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasCursedPickaxe(UUID playerId) {
      return this.cursedPickaxeActive.containsKey(playerId) && this.cursedPickaxeActive.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasStoneVision(UUID playerId) {
      return this.stoneVisionActive.containsKey(playerId) && this.stoneVisionActive.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasFoolsGold(UUID playerId) {
      return this.foolsGoldActive.containsKey(playerId) && this.foolsGoldActive.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasParanoiaMode(UUID playerId) {
      return this.paranoiaModeActive.containsKey(playerId) && this.paranoiaModeActive.get(playerId) > System.currentTimeMillis();
   }

   public boolean hasFakeDiamonds(UUID playerId) {
      return this.fakeDiamondsRemaining.containsKey(playerId) && this.fakeDiamondsRemaining.get(playerId) > 0;
   }

   public int getFakeDiamondsRemaining(UUID playerId) {
      return this.fakeDiamondsRemaining.getOrDefault(playerId, 0);
   }

   public void decrementFakeDiamonds(UUID playerId) {
      int remaining = this.fakeDiamondsRemaining.getOrDefault(playerId, 0) - 1;
      if (remaining <= 0) {
         this.fakeDiamondsRemaining.remove(playerId);
      } else {
         this.fakeDiamondsRemaining.put(playerId, remaining);
      }
   }

   public boolean hasPermanentMiningDebuff(UUID playerId) {
      return this.permanentMiningDebuff.contains(playerId);
   }

   public boolean isFakeOre(Block block) {
      return this.fakeOreBlocks.contains(block);
   }

   public boolean hasFakeOreVeins(UUID playerId) {
      return this.fakeOreVeinsActive.containsKey(playerId) && this.fakeOreVeinsActive.get(playerId) > System.currentTimeMillis();
   }

   public void registerFakeOre(final Block block) {
      if (!this.fakeOreBlocks.contains(block)) {
         this.fakeOreBlocks.add(block);
         (new Schedulers.Repeating() {
            @Override
            public void run() {
               PunishmentManager.this.fakeOreBlocks.remove(block);
            }
         }).globalLater(36000L);
      }
   }

   public void removeFakeOre(Block block) {
      this.fakeOreBlocks.remove(block);
   }

   public void checkAndPunish(Player player) {
      if (!Schedulers.isOwned(player)) {
         Schedulers.entity(player, () -> this.checkAndPunish(player));
      } else {
         UUID playerId = player.getUniqueId();
         int suspiciousCount = SuspiciousManager.getSuspiciousCounts().getOrDefault(playerId, 0);
         int level = suspiciousCount / 10;
         if (level >= 1 && level <= 6) {
            if (this.configManager.isPunishmentEnabled(level)) {
               int currentLevel = this.playerPunishmentLevels.getOrDefault(playerId, 0);
               if (currentLevel > 0 && currentLevel < level) {
                  this.removeAllPunishmentEffects(player, currentLevel);
               }

               if (currentLevel != level) {
                  this.applyPunishment(player, level);
                  this.playerPunishmentLevels.put(playerId, level);
               }
            }
         }
      }
   }

   public void onPunishmentLevelDisabled(int level, String adminName) {
      if (level >= 1 && level <= 6) {
         int affectedPlayers = 0;

         for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Integer currentLevel = this.playerPunishmentLevels.get(playerId);
            if (currentLevel != null && currentLevel == level) {
               this.playerPunishmentLevels.remove(playerId);
               Schedulers.runFor(player, () -> {
                  this.removeAllPunishmentEffects(player, level);
                  player.sendMessage(Component.text("Your punishment has been lifted due to a system update.", NamedTextColor.GREEN));
               });
               affectedPlayers++;
            }
         }

         this.plugin.getLogger().info("Admin " + adminName + " disabled punishment level " + level + ", affecting " + affectedPlayers + " players.");
         this.notifyAdminsAboutRemoval(level, adminName, affectedPlayers);
      }
   }

   private void notifyAdminsAboutRemoval(int level, String adminName, int affectedCount) {
      Component message = ((TextComponent)((TextComponent)Component.text("[OverWatch-ML] ", NamedTextColor.BLUE)
               .append(Component.text("Punishment level " + level + " was disabled by ", NamedTextColor.AQUA)))
            .append(Component.text(adminName, NamedTextColor.YELLOW)))
         .append(Component.text(", affecting " + affectedCount + " player" + (affectedCount == 1 ? "" : "s") + ".", NamedTextColor.AQUA));

      for (Player admin : Bukkit.getOnlinePlayers()) {
         if (this.plugin.wantsAlerts(admin)) {
            admin.sendMessage(message);
         }
      }
   }

   public void removeAllPunishmentEffects(Player player, int level) {
      UUID playerId = player.getUniqueId();
      switch (level) {
         case 1:
            if (this.configManager.isPunishmentOptionEnabled(level, "mining_fatigue")) {
               player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }

            this.fakeDiamondsRemaining.remove(playerId);
            if (this.configManager.isPunishmentOptionEnabled(level, "heavy_pickaxe")) {
               player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }
            break;
         case 2:
            this.fakeOreVeinsActive.remove(playerId);
            if (this.configManager.isPunishmentOptionEnabled(level, "xray_vision_blur")) {
               player.removePotionEffect(PotionEffectType.NAUSEA);
            }

            this.paranoiaModeActive.remove(playerId);
            break;
         case 3:
            this.miningLicenseSuspension.remove(playerId);
            this.resourceTaxActive.remove(playerId);
            player.removeMetadata("decoy_attraction", this.plugin);
            this.foolsGoldActive.remove(playerId);
            break;
         case 4:
            this.miningReputations.remove(playerId);
            this.restrictedAreas.remove(playerId);
            this.cursedPickaxeActive.remove(playerId);
         case 5:
            this.permanentMiningDebuff.remove(playerId);
            player.removeMetadata("requires_staff_review", this.plugin);
            this.stoneVisionActive.remove(playerId);
         case 6:
      }
   }

   private String getPunishmentName(int level) {
      switch (level) {
         case 1:
            return "Warning Phase";
         case 2:
            return "Minor Consequences";
         case 3:
            return "Moderate Punishment";
         case 4:
            return "Severe Consequences";
         case 5:
            return "Critical Response";
         case 6:
            return "Maximum Enforcement";
         default:
            return "Level " + level + " Punishment";
      }
   }

   private void applyPunishment(final Player player, int level) {
      UUID playerId = player.getUniqueId();
      if (this.plugin.getConfigManager().isWebhookAlertEnabled("punishment_applied")) {
         this.plugin.getWebhookManager().sendPunishmentAlert(player, level, this.getPunishmentName(level));
      }

      if (this.configManager.isPunishmentOptionEnabled(level, "admin_alert")) {
         this.alertAdmins(player, level);
      }

      if (this.configManager.isPunishmentOptionEnabled(level, "warning_message")) {
         this.sendWarningMessage(player, level);
      }

      switch (level) {
         case 1:
            if (this.configManager.isPunishmentOptionEnabled(level, "mining_fatigue")) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 6000, 0, false, false, false));
               player.sendMessage(Component.text("Your arms feel tired from suspicious mining techniques...", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "fake_diamonds")) {
               int fakeCount = ThreadLocalRandom.current().nextInt(1, 4);
               this.fakeDiamondsRemaining.put(playerId, fakeCount);
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "heavy_pickaxe")) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 12000, 1, false, false, false));
               player.sendMessage(Component.text("Your pickaxe feels suspiciously heavy...", NamedTextColor.RED));
            }
            break;
         case 2:
            if (this.configManager.isPunishmentOptionEnabled(level, "fake_ore_veins")) {
               this.spawnFakeOreVeins(player);
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "inventory_drop")) {
               this.dropInventoryItems(player, 25);
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "xray_vision_blur")) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 6000, 0, false, false, false));
               player.sendMessage(Component.text("Your suspicious vision is becoming blurry underground...", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "tool_damage")) {
               this.damageCurrentTool(player, 50);
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "paranoia_mode")) {
               long duration = System.currentTimeMillis() + 1800000L;
               this.paranoiaModeActive.put(playerId, duration);
               player.sendMessage(Component.text("The caves seem to be unusually active around you...", NamedTextColor.RED));
            }
            break;
         case 3:
            if (this.configManager.isPunishmentOptionEnabled(level, "temporary_kick")) {
               Component kickMessage = Component.text("❌ OverWatchML has detected suspicious X-ray patterns.\n", NamedTextColor.RED)
                  .append(Component.text("Please rejoin and mine responsibly.", NamedTextColor.YELLOW));
               Schedulers.entity(player, () -> {
                  if (player.isOnline()) {
                     player.kick(kickMessage);
                  }
               });
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "mining_license_suspension")) {
               long duration = System.currentTimeMillis() + 7200000L;
               this.miningLicenseSuspension.put(playerId, duration);
               player.sendMessage(Component.text("Your mining license for valuable ores has been suspended for 2 hours!", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "resource_tax")) {
               long duration = System.currentTimeMillis() + 10800000L;
               this.resourceTaxActive.put(playerId, duration);
               player.sendMessage(Component.text("The mining authorities have imposed a 50% resource tax on your mining!", NamedTextColor.RED));
               player.sendMessage(Component.text("All mining drops will be reduced by half (rounded down).", NamedTextColor.YELLOW));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "decoy_attraction")) {
               player.setMetadata("decoy_attraction", new FixedMetadataValue(this.plugin, true));
               player.sendMessage(Component.text("Strange... the cave walls seem to shimmer with false promises...", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "fools_gold")) {
               long duration = System.currentTimeMillis() + 1800000L;
               this.foolsGoldActive.put(playerId, duration);
               player.sendMessage(Component.text("Your treasure detection appears to be malfunctioning...", NamedTextColor.RED));
               player.sendMessage(Component.text("For the next 30 minutes, your diamonds might not be what they seem.", NamedTextColor.YELLOW));
            }
            break;
         case 4:
            if (this.configManager.isPunishmentOptionEnabled(level, "extended_ban")) {
               Component banReason = Component.text("OverWatch-ML: Suspicious mining patterns detected\n", NamedTextColor.RED)
                  .append(Component.text("Your account is suspended for 3 days.", NamedTextColor.YELLOW));
               Bukkit.getBanList(Type.NAME)
                  .addBan(player.getName(), banReason.toString(), new Date(System.currentTimeMillis() + 259200000L), "OverWatchML System");
               Component kickMessage = Component.text("OverWatch-ML: You have been banned for 3 days due to suspicious mining behavior.", NamedTextColor.RED);
               Schedulers.entity(player, () -> {
                  if (player.isOnline()) {
                     player.kick(kickMessage);
                  }
               });
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "mining_reputation")) {
               long duration = 604800000L;
               Component reputationText = Component.text("Untrusted Miner", NamedTextColor.RED);
               this.setMiningReputation(playerId, reputationText, duration);
               player.sendMessage(
                  ((TextComponent)Component.text("You have been flagged as an ", NamedTextColor.RED)
                        .append(Component.text("Untrusted Miner", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD})))
                     .append(Component.text("!", NamedTextColor.RED))
               );
               player.sendMessage(
                  ((TextComponent)((TextComponent)((TextComponent)Component.text("This status will be visible to ", NamedTextColor.YELLOW)
                              .append(Component.text("all players", NamedTextColor.GOLD)))
                           .append(Component.text(" in chat for ", NamedTextColor.YELLOW)))
                        .append(Component.text("7 days", NamedTextColor.GOLD)))
                     .append(Component.text(".", NamedTextColor.YELLOW))
               );
               player.sendMessage(Component.text("Your suspicious mining behavior will be publicly displayed.", NamedTextColor.GRAY));
               player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.5F);
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "restricted_areas")) {
               long duration = System.currentTimeMillis() + 604800000L;
               this.restrictedAreas.put(playerId, duration);
               player.sendMessage(Component.text("You are now restricted from mining below Y-level -40 for 7 days!", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "cursed_pickaxe")) {
               long duration = System.currentTimeMillis() + 604800000L;
               this.cursedPickaxeActive.put(playerId, duration);
               player.sendMessage(Component.text("Your mining tools have been cursed! They may break when mining valuable ores.", NamedTextColor.RED));
            }
            break;
         case 5:
            if (this.configManager.isPunishmentOptionEnabled(level, "long_term_ban")) {
               Component banReason = Component.text("OverWatch-ML: Critical level of suspicious activity detected\n", NamedTextColor.RED)
                  .append(Component.text("Your account is suspended for 14 days.", NamedTextColor.YELLOW));
               Bukkit.getBanList(Type.NAME)
                  .addBan(player.getName(), banReason.toString(), new Date(System.currentTimeMillis() + 1209600000L), "OverWatchML System");
               Component kickMessage = Component.text(
                  "OverWatch-ML: You have been banned for 14 days due to critical level of suspicious mining.", NamedTextColor.RED
               );
               Schedulers.entity(player, () -> {
                  if (player.isOnline()) {
                     player.kick(kickMessage);
                  }
               });
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "public_notification")) {
               player.setMetadata("public_notification", new FixedMetadataValue(this.plugin, true));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "permanent_mining_debuff")) {
               this.permanentMiningDebuff.add(playerId);
               player.sendMessage(Component.text("Your mining abilities have been permanently restricted below Y-level 0!", NamedTextColor.RED));
               player.sendMessage(Component.text("You will experience Mining Fatigue II when mining below this level.", NamedTextColor.YELLOW));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "staff_review")) {
               player.setMetadata("requires_staff_review", new FixedMetadataValue(this.plugin, true));
               player.sendMessage(Component.text("You now require staff approval to mine ores below Y-level 25!", NamedTextColor.RED));
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "stone_vision")) {
               long duration = System.currentTimeMillis() + 604800000L;
               this.stoneVisionActive.put(playerId, duration);
               player.sendMessage(Component.text("Your vision has been altered - all ores now appear as stone to you!", NamedTextColor.RED));
            }
            break;
         case 6:
            if (this.configManager.isPunishmentOptionEnabled(level, "permanent_ban")) {
               if (this.configManager.isPunishmentOptionEnabled(level, "tnt_execution")) {
                  Location loc = player.getLocation();
                  World world = loc.getWorld();
                  world.spawnParticle(Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5);
                  world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);
                  player.damage(player.getHealth() - 1.0);
                  player.sendMessage(Component.text("The OverWatch system has detected critical levels of X-ray usage!", NamedTextColor.RED));
                  (new Schedulers.Repeating() {
                        @Override
                        public void run() {
                           if (player.isOnline()) {
                              Component banReason = Component.text("OverWatch-ML: Maximum level violation detected\n", NamedTextColor.RED)
                                 .append(Component.text("Your account has been permanently banned.", NamedTextColor.YELLOW));
                              Bukkit.getBanList(Type.NAME).addBan(player.getName(), banReason.toString(), null, "OverWatchML System");
                              Component kickMessage = Component.text(
                                 "OverWatch-ML: You have been permanently banned for maximum-level X-ray detection.", NamedTextColor.RED
                              );
                              player.kick(kickMessage);
                           }
                        }
                     })
                     .entityLater(player, 60L);
               } else {
                  Component banReason = Component.text("OverWatch-ML: Maximum level violation detected\n", NamedTextColor.RED)
                     .append(Component.text("Your account has been permanently banned.", NamedTextColor.YELLOW));
                  Bukkit.getBanList(Type.NAME).addBan(player.getName(), banReason.toString(), null, "OverWatchML System");
                  Component kickMessage = Component.text(
                     "OverWatch-ML: You have been permanently banned for maximum-level X-ray detection.", NamedTextColor.RED
                  );
                  Schedulers.entity(player, () -> {
                     if (player.isOnline()) {
                        player.kick(kickMessage);
                     }
                  });
               }
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "ip_tracking")) {
               String ip = player.getAddress().getAddress().getHostAddress();
               List<String> trackedIPs = this.plugin.getConfig().getStringList("tracked_ips");
               if (!trackedIPs.contains(ip)) {
                  trackedIPs.add(ip);
                  this.plugin.getConfig().set("tracked_ips", trackedIPs);
                  AsyncExecutor.submitIO(() -> {
                     this.plugin.saveConfig();
                     return null;
                  });
               }
            }

            if (this.configManager.isPunishmentOptionEnabled(level, "security_report")) {
               this.generateSecurityReport(player);
            }
            break;
         default:
            Component kickMessage = Component.text("Punished for suspicious behavior.", NamedTextColor.RED);
            Schedulers.entity(player, () -> {
               if (player.isOnline()) {
                  player.kick(kickMessage);
               }
            });
      }
   }

   private void sendWarningMessage(Player player, int level) {
      String titleText = "⚠ WARNING ⚠";
      String subtitleText;
      TextColor titleColor;
      TextColor borderColor;
      TextColor messageColor;
      TextColor highlightColor;
      Sound alertSound;
      float volume;
      float pitch;
      Particle warningParticle;
      String message1;
      String message2;
      String message3;
      switch (level) {
         case 1:
            message1 = "Suspicious mining activity detected!";
            message2 = "This is your first warning.";
            message3 = "Further violations will be punished.";
            subtitleText = "Level 1 - First Warning";
            titleColor = TextColor.color(255, 255, 0);
            borderColor = TextColor.color(230, 126, 34);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(241, 196, 15);
            alertSound = Sound.BLOCK_NOTE_BLOCK_PLING;
            volume = 0.8F;
            pitch = 1.0F;
            warningParticle = Particle.END_ROD;
            break;
         case 2:
            message1 = "Continued suspicious mining detected!";
            message2 = "This is your second warning.";
            message3 = "Your actions are being monitored closely.";
            subtitleText = "Level 2 - Second Warning";
            titleColor = TextColor.color(255, 165, 0);
            borderColor = TextColor.color(231, 76, 60);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(230, 126, 34);
            alertSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            volume = 1.0F;
            pitch = 0.8F;
            warningParticle = Particle.SMOKE;
            break;
         case 3:
            message1 = "Multiple violations of mining policy!";
            message2 = "This is a serious offense.";
            message3 = "Further violations will result in severe action.";
            subtitleText = "Level 3 - Serious Offense";
            titleColor = TextColor.color(255, 69, 0);
            borderColor = TextColor.color(192, 57, 43);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(231, 76, 60);
            alertSound = Sound.ENTITY_ELDER_GUARDIAN_CURSE;
            volume = 0.4F;
            pitch = 1.0F;
            warningParticle = Particle.ANGRY_VILLAGER;
            break;
         case 4:
            message1 = "Severe mining violations detected!";
            message2 = "This is a major offense.";
            message3 = "Your mining privileges are now restricted.";
            subtitleText = "Level 4 - Major Offense";
            titleColor = TextColor.color(255, 0, 0);
            borderColor = TextColor.color(153, 0, 0);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(192, 57, 43);
            alertSound = Sound.BLOCK_BELL_USE;
            volume = 1.0F;
            pitch = 0.6F;
            warningParticle = Particle.FLAME;
            break;
         case 5:
            message1 = "Critical violation of server mining policy!";
            message2 = "Your actions have been flagged for review.";
            message3 = "Mining privileges severely restricted.";
            subtitleText = "Level 5 - Critical Violation";
            titleColor = TextColor.color(139, 0, 0);
            borderColor = TextColor.color(88, 0, 0);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(153, 0, 0);
            alertSound = Sound.ENTITY_WITHER_SPAWN;
            volume = 0.3F;
            pitch = 0.5F;
            warningParticle = Particle.SOUL_FIRE_FLAME;
            break;
         case 6:
            message1 = "MAXIMUM VIOLATION DETECTED!";
            message2 = "Your account is subject to permanent action.";
            message3 = "All mining activity has been logged for review.";
            subtitleText = "Level 6 - MAXIMUM VIOLATION";
            titleColor = TextColor.color(128, 0, 128);
            borderColor = TextColor.color(75, 0, 130);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(139, 0, 139);
            alertSound = Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            volume = 0.7F;
            pitch = 0.3F;
            warningParticle = Particle.SOUL;
            break;
         default:
            message1 = "Suspicious mining activity detected!";
            message2 = "This behavior is not permitted.";
            message3 = "Further violations will be punished.";
            subtitleText = "Mining Violation Detected";
            titleColor = TextColor.color(255, 255, 0);
            borderColor = TextColor.color(230, 126, 34);
            messageColor = TextColor.color(236, 240, 241);
            highlightColor = TextColor.color(241, 196, 15);
            alertSound = Sound.BLOCK_NOTE_BLOCK_BASS;
            volume = 0.8F;
            pitch = 1.0F;
            warningParticle = Particle.END_ROD;
      }

      player.showTitle(
         Title.title(
            ((TextComponent)Component.text(titleText).color(titleColor)).decoration(TextDecoration.BOLD, true),
            Component.text(subtitleText).color(highlightColor),
            Times.times(Ticks.duration(10L), Ticks.duration(70L), Ticks.duration(20L))
         )
      );
      Component topBorder = Component.text("╔═════════ " + this.getStars(level) + " ═════════╗").color(borderColor);
      Component titleLine = ((TextComponent)((TextComponent)Component.text("║ ").color(borderColor))
            .append(((TextComponent)Component.text("OverWatchML WARNING").color(titleColor)).decoration(TextDecoration.BOLD, true)))
         .append(Component.text(" ║").color(borderColor));
      Component separator = Component.text("╠════════════════════════════╣").color(borderColor);
      Component message1Line = ((TextComponent)((TextComponent)Component.text("║ ").color(borderColor)).append(Component.text(message1).color(messageColor)))
         .append(Component.text(" ║").color(borderColor));
      Component message2Line = ((TextComponent)((TextComponent)Component.text("║ ").color(borderColor)).append(Component.text(message2).color(messageColor)))
         .append(Component.text(" ║").color(borderColor));
      Component message3Line = ((TextComponent)((TextComponent)Component.text("║ ").color(borderColor)).append(Component.text(message3).color(messageColor)))
         .append(Component.text(" ║").color(borderColor));
      Component bottomBorder = Component.text("╚════════════════════════════╝").color(borderColor);
      Component appealLine = ((TextComponent)((TextComponent)Component.text("║      ").color(borderColor))
            .append(
               ((TextComponent)((TextComponent)((TextComponent)Component.text("[APPEAL]").color(TextColor.color(255, 215, 0)))
                        .decoration(TextDecoration.BOLD, true))
                     .clickEvent(ClickEvent.suggestCommand("/owml appeal ")))
                  .hoverEvent(HoverEvent.showText(Component.text("Click to appeal this warning to staff")))
            ))
         .append(Component.text("      ║").color(borderColor));
      player.sendMessage(Component.text(""));
      player.sendMessage(topBorder);
      player.sendMessage(titleLine);
      player.sendMessage(separator);
      player.sendMessage(message1Line);
      player.sendMessage(message2Line);
      player.sendMessage(message3Line);
      player.sendMessage(separator);
      player.sendMessage(appealLine);
      player.sendMessage(bottomBorder);
      player.sendMessage(Component.text(""));
      player.sendMessage(
         Component.text("If you believe this detection was incorrect, click [APPEAL] to explain your case to the staff team.")
            .color(TextColor.color(200, 200, 200))
      );
      player.playSound(player.getLocation(), alertSound, volume, pitch);
      Location loc = player.getLocation();

      for (int i = 0; i < 20; i++) {
         double angle = i * (Math.PI / 10);
         double x = Math.cos(angle) * 1.5;
         double z = Math.sin(angle) * 1.5;
         player.getWorld().spawnParticle(warningParticle, loc.getX() + x, loc.getY() + 1.0, loc.getZ() + z, 3, 0.0, 0.0, 0.0, 0.05);
      }

      Component actionBarMsg = Component.text("⚠ Warning Level " + level + " ⚠").color(titleColor);
      player.sendActionBar(actionBarMsg);
      if (level >= 4) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, level - 3));
      }
   }

   private String getStars(int level) {
      StringBuilder stars = new StringBuilder();

      for (int i = 0; i < level; i++) {
         stars.append("в\u0098…");
      }

      for (int i = level; i < 6; i++) {
         stars.append("в\u0098†");
      }

      return stars.toString();
   }

   private void alertAdmins(Player violator, int level) {
      String severityText;
      TextColor severityColor;
      switch (level) {
         case 1:
            severityText = "potential";
            severityColor = TextColor.color(255, 255, 0);
            break;
         case 2:
            severityText = "suspicious";
            severityColor = TextColor.color(255, 165, 0);
            break;
         case 3:
            severityText = "concerning";
            severityColor = TextColor.color(255, 69, 0);
            break;
         case 4:
            severityText = "severe";
            severityColor = TextColor.color(255, 0, 0);
            break;
         case 5:
            severityText = "critical";
            severityColor = TextColor.color(139, 0, 0);
            break;
         case 6:
            severityText = "MAXIMUM";
            severityColor = TextColor.color(128, 0, 128);
            break;
         default:
            severityText = "suspicious";
            severityColor = TextColor.color(255, 165, 0);
      }

      int blocksMinedRecently = this.getPlayerRecentBlocksMined(violator);
      String locationInfo = String.format(
         "%.1f, %.1f, %.1f (%s)", violator.getLocation().getX(), violator.getLocation().getY(), violator.getLocation().getZ(), violator.getWorld().getName()
      );
      Component prefix = ((TextComponent)((TextComponent)Component.text("[").color(TextColor.color(169, 169, 169)))
            .append(Component.text("OverWatch-ML").color(TextColor.color(220, 20, 60))))
         .append(Component.text("] ").color(TextColor.color(169, 169, 169)));
      Component playerName = ((TextComponent)Component.text(violator.getName()).color(severityColor))
         .hoverEvent(
            HoverEvent.showText(
               ((TextComponent)((TextComponent)((TextComponent)Component.text("Player Info:\n")
                           .append(Component.text("UUID: " + violator.getUniqueId() + "\n")))
                        .append(Component.text("IP: [Redacted]\n")))
                     .append(Component.text("Join Date: " + this.getPlayerJoinDate(violator) + "\n")))
                  .append(Component.text("Recent Blocks Mined: " + blocksMinedRecently))
            )
         );
      Component severityInfo = Component.text(" triggered a " + severityText + " Level " + level + " X-ray detection.").color(severityColor);
      Component message = prefix.append(playerName).append(severityInfo);

      for (Player admin : Bukkit.getOnlinePlayers()) {
         if (this.plugin.wantsAlerts(admin)) {
            admin.sendMessage(message);
            float pitch = Math.min(2.0F, 0.5F + level * 0.25F);
            Sound alertSound = level >= 5 ? Sound.ENTITY_ELDER_GUARDIAN_DEATH : Sound.BLOCK_NOTE_BLOCK_PLING;
            admin.playSound(admin.getLocation(), alertSound, 0.5F, pitch);
            if (level >= 4) {
               Component alertBar = ((TextComponent)((TextComponent)Component.text("⚠ ").color(TextColor.color(255, 0, 0)))
                     .append(Component.text("X-RAY ALERT: " + violator.getName() + " - Level " + level)))
                  .append(Component.text(" ⚠").color(TextColor.color(255, 0, 0)));
               admin.sendActionBar(alertBar);
            }
         }
      }

      Bukkit.getConsoleSender()
         .sendMessage(
            "[OverWatch-ML] ALERT: " + violator.getName() + " triggered a " + severityText + " Level " + level + " X-ray detection at " + locationInfo
         );
   }

   private String getPlayerJoinDate(Player player) {
      return "Unknown";
   }

   private int getPlayerRecentBlocksMined(Player player) {
      return 0;
   }

   private void spawnFakeOreVeins(Player player) {
      UUID playerId = player.getUniqueId();
      long duration = System.currentTimeMillis() + 7200000L;
      this.fakeOreVeinsActive.put(playerId, duration);
      player.sendMessage(Component.text("The ore veins around you seem... unstable. Mining valuable ores may yield only stone.", NamedTextColor.RED));
   }

   private void dropInventoryItems(Player player, int percentage) {
      if (percentage > 0) {
         UUID playerId = player.getUniqueId();
         Random random = new Random();
         PlayerInventory inventory = player.getInventory();
         List<Entry<Integer, ItemStack>> oreItems = new ArrayList<>();

         for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && this.isOreItem(item.getType())) {
               oreItems.add(new SimpleEntry<>(i, item));
            }
         }

         if (oreItems.isEmpty()) {
            if (this.configManager.isDebugEnabled()) {
               this.plugin.getLogger().info("No ore items found in inventory for player " + player.getName());
            }
         } else {
            int itemsToRemove = Math.max(1, oreItems.size() * percentage / 100);
            itemsToRemove = Math.min(itemsToRemove, oreItems.size());
            if (this.configManager.isDebugEnabled()) {
               this.plugin
                  .getLogger()
                  .info(
                     "Removing "
                        + itemsToRemove
                        + " ore items from player "
                        + player.getName()
                        + "'s inventory ("
                        + percentage
                        + "% of "
                        + oreItems.size()
                        + " ore items)"
                  );
            }

            int totalItemsRemoved = 0;
            Map<Material, Integer> removedCounts = new HashMap<>();

            for (int ix = 0; ix < itemsToRemove && !oreItems.isEmpty(); ix++) {
               int randomIndex = random.nextInt(oreItems.size());
               Entry<Integer, ItemStack> selectedEntry = oreItems.remove(randomIndex);
               int slot = selectedEntry.getKey();
               ItemStack selectedItem = selectedEntry.getValue();
               Material material = selectedItem.getType();
               int amount = selectedItem.getAmount();
               totalItemsRemoved += amount;
               removedCounts.put(material, removedCounts.getOrDefault(material, 0) + amount);
               inventory.setItem(slot, null);
            }

            player.sendMessage(
               Component.text("The OverWatch system has confiscated " + totalItemsRemoved + " ore items from your inventory!", NamedTextColor.RED)
            );
            if (!removedCounts.isEmpty()) {
               for (Entry<Material, Integer> entry : removedCounts.entrySet()) {
                  player.sendMessage(Component.text("- " + entry.getValue() + " " + this.formatItemName(entry.getKey()), NamedTextColor.YELLOW));
               }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.5F);
         }
      }
   }

   private String formatItemName(Material material) {
      String name = material.name();
      name = name.replace('_', ' ').toLowerCase();
      StringBuilder result = new StringBuilder();
      boolean capitalizeNext = true;

      for (char c : name.toCharArray()) {
         if (c == ' ') {
            capitalizeNext = true;
            result.append(c);
         } else if (capitalizeNext) {
            result.append(Character.toUpperCase(c));
            capitalizeNext = false;
         } else {
            result.append(c);
         }
      }

      return result.toString();
   }

   private boolean isOreItem(Material material) {
      String name = material.name();
      return name.contains("ORE")
         || name.contains("INGOT")
         || name.contains("DIAMOND")
         || name.contains("EMERALD")
         || name.contains("GOLD")
         || name.contains("IRON")
         || name.contains("COPPER")
         || name.contains("COAL")
         || name.contains("LAPIS")
         || name.contains("REDSTONE")
         || name.contains("QUARTZ")
         || name.contains("NETHERITE")
         || name.contains("RAW_")
         || name.equals("ANCIENT_DEBRIS");
   }

   private void damageCurrentTool(Player player, int percentage) {
   }

   private void generateSecurityReport(Player player) {
   }

   private int getMinedCount(UUID playerId, Material material) {
      return ThreadLocalRandom.current().nextInt(100, 500);
   }

   public int getPlayerPunishmentLevel(UUID playerId) {
      return this.playerPunishmentLevels.getOrDefault(playerId, 0);
   }

   public void onDisable() {
      this.savePunishmentData();
      if (this.autoSaveTask != null) {
         this.autoSaveTask.cancel();
         this.autoSaveTask = null;
      }
   }
}
