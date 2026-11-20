package net.denfry.owml.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.utils.MaterialHelper;
import net.denfry.owml.utils.WebhookSecurity;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
   private final JavaPlugin plugin;
   private int oreThreshold;
   private long timeWindowTicks;
   private double decoyDistance;
   private double decoyFieldOffset;
   private double maxDecayDistance;
   private long decoyRevertDelay;
   private boolean warnOnDecoy;
   private boolean decoyEnabled;
   private boolean decoyRequireBuried;
   private int decoySearchRadius;
   private int buriedThreshold;
   private boolean debugEnabled;
   private boolean staffOreAlerts;
   private long staffOreResetTime;
   private boolean staffAlertEnabled;
   private boolean statsAutoSaveEnabled;
   private int statsAutoSaveInterval;
   private boolean statsAutoSaveLogging;
   private boolean suspiciousAutoSaveEnabled;
   private int suspiciousAutoSaveInterval;
   private boolean suspiciousAutoSaveLogging;
   private boolean punishmentAutoSaveEnabled;
   private int punishmentAutoSaveInterval;
   private boolean punishmentAutoSaveLogging;
   private boolean commandHidingEnabled;
   private String commandHidingErrorLine1;
   private String commandHidingErrorLine2;
   private boolean worldDisablingEnabled;
   private final Set<String> disabledWorlds = ConcurrentHashMap.newKeySet();
   private final Set<Material> naturalOres = ConcurrentHashMap.newKeySet();
   private final AtomicBoolean cachedDebugEnabled = new AtomicBoolean(false);
   private final AtomicInteger cachedOreThreshold = new AtomicInteger(10);
   private final AtomicLong cachedTimeWindowTicks = new AtomicLong(3600L);
   private final AtomicLong cachedDecoyRevertDelay = new AtomicLong(1200L);
   private final AtomicBoolean cachedDecoyEnabled = new AtomicBoolean(true);
   private final AtomicBoolean cachedRequireBuried = new AtomicBoolean(true);
   private final AtomicBoolean cachedStaffOreAlerts = new AtomicBoolean(true);
   private final AtomicBoolean cachedStaffAlertEnabled = new AtomicBoolean(true);
   private final AtomicBoolean cachedCommandHidingEnabled = new AtomicBoolean(false);
   private final ConcurrentHashMap<String, Boolean> worldCheckCache = new ConcurrentHashMap<>();

   public ConfigManager(JavaPlugin plugin) {
      this.plugin = plugin;
      this.loadConfiguration();
      this.validateConfiguration();
   }

   private void loadConfiguration() {
      this.oreThreshold = this.plugin.getConfig().getInt("decoy.oreThreshold", 8);
      this.cachedOreThreshold.set(this.oreThreshold);
      this.timeWindowTicks = this.plugin.getConfig().getLong("decoy.timeWindowTicks", 3800L);
      this.cachedTimeWindowTicks.set(this.timeWindowTicks);
      this.decoyDistance = this.plugin.getConfig().getDouble("decoy.distance", 2.0);
      this.decoyFieldOffset = this.plugin.getConfig().getDouble("decoy.fieldOffset", 0.5);
      this.maxDecayDistance = this.plugin.getConfig().getDouble("decoy.maxDistance", 10.0);
      this.decoyRevertDelay = this.plugin.getConfig().getLong("decoy.revertDelay", 1200L);
      this.cachedDecoyRevertDelay.set(this.decoyRevertDelay);
      this.warnOnDecoy = this.plugin.getConfig().getBoolean("decoy.warnOnDecoy", true);
      this.decoyEnabled = this.plugin.getConfig().getBoolean("decoy.enabled", true);
      this.cachedDecoyEnabled.set(this.decoyEnabled);
      this.decoyRequireBuried = this.plugin.getConfig().getBoolean("decoy.requireBuried", true);
      this.cachedRequireBuried.set(this.decoyRequireBuried);
      this.decoySearchRadius = this.plugin.getConfig().getInt("decoy.searchRadius", 3);
      this.buriedThreshold = this.plugin.getConfig().getInt("decoy.buriedThreshold", 4);
      this.debugEnabled = this.plugin.getConfig().getBoolean("debug.enabled", false);
      this.cachedDebugEnabled.set(this.debugEnabled);
      this.staffOreAlerts = this.plugin.getConfig().getBoolean("staff.oreAlerts", true);
      this.cachedStaffOreAlerts.set(this.staffOreAlerts);
      this.staffOreResetTime = this.plugin.getConfig().getLong("staff.oreResetTime", 36000L);
      this.staffAlertEnabled = this.plugin.getConfig().getBoolean("staff.alertEnabled", true);
      this.cachedStaffAlertEnabled.set(this.staffAlertEnabled);
      this.statsAutoSaveEnabled = this.plugin.getConfig().getBoolean("stats.autoSave.enabled", true);
      this.statsAutoSaveInterval = this.plugin.getConfig().getInt("stats.autoSave.intervalMinutes", 10);
      this.statsAutoSaveLogging = this.plugin.getConfig().getBoolean("stats.autoSave.logging", false);
      this.suspiciousAutoSaveEnabled = this.plugin.getConfig().getBoolean("suspicious.autoSave.enabled", true);
      this.suspiciousAutoSaveInterval = this.plugin.getConfig().getInt("suspicious.autoSave.intervalMinutes", 10);
      this.suspiciousAutoSaveLogging = this.plugin.getConfig().getBoolean("suspicious.autoSave.logging", true);
      this.punishmentAutoSaveEnabled = this.plugin.getConfig().getBoolean("punishment.autoSave.enabled", true);
      this.punishmentAutoSaveInterval = this.plugin.getConfig().getInt("punishment.autoSave.intervalMinutes", 10);
      this.punishmentAutoSaveLogging = this.plugin.getConfig().getBoolean("punishment.autoSave.logging", true);
      this.commandHidingEnabled = this.plugin.getConfig().getBoolean("command-hiding.enabled", false);
      this.cachedCommandHidingEnabled.set(this.commandHidingEnabled);
      this.commandHidingErrorLine1 = this.plugin
         .getConfig()
         .getString("command-hiding.messages.error-line1", "§cUnknown or incomplete command, see below for error");
      this.commandHidingErrorLine2 = this.plugin.getConfig().getString("command-hiding.messages.error-line2", "§c§n{command}§r§c§o<--[HERE]");
      this.worldDisablingEnabled = this.plugin.getConfig().getBoolean("disabled-worlds.enabled", false);
      this.worldCheckCache.clear();
      List<String> oreList = this.plugin.getConfig().getStringList("ores.natural");
      this.naturalOres.clear();

      for (String oreName : oreList) {
         try {
            Material mat = MaterialHelper.getMaterial(oreName.toUpperCase());
            if (mat == null) {
               this.plugin.getLogger().warning("Invalid ore in config: " + oreName);
            } else {
               this.naturalOres.add(mat);
            }
         } catch (IllegalArgumentException var5) {
            this.plugin.getLogger().warning("Invalid ore in config: " + oreName);
         }
      }

      List<String> worldList = this.plugin.getConfig().getStringList("disabled-worlds.worlds");
      this.disabledWorlds.clear();

      for (String worldName : worldList) {
         this.disabledWorlds.add(worldName);
      }
   }

   private void validateConfiguration() {
      ConfigValidator validator = new ConfigValidator(this.plugin.getConfig());
      ConfigValidator.ValidationResult result = validator.validate();
      validator.logResults();
      if (result.hasErrors()) {
         this.plugin.getLogger().warning("Configuration has errors that may affect plugin functionality. Please check the configuration file.");
      }
   }

   public void setupDefaultAutoSaveConfigs() {
      this.setupDefaultStatsAutoSaveConfig();
      if (!this.plugin.getConfig().isSet("suspicious.autoSave.enabled")) {
         this.plugin.getConfig().set("suspicious.autoSave.enabled", true);
      }

      if (!this.plugin.getConfig().isSet("suspicious.autoSave.intervalMinutes")) {
         this.plugin.getConfig().set("suspicious.autoSave.intervalMinutes", 10);
      }

      if (!this.plugin.getConfig().isSet("suspicious.autoSave.logging")) {
         this.plugin.getConfig().set("suspicious.autoSave.logging", true);
      }

      if (!this.plugin.getConfig().isSet("punishment.autoSave.enabled")) {
         this.plugin.getConfig().set("punishment.autoSave.enabled", true);
      }

      if (!this.plugin.getConfig().isSet("punishment.autoSave.intervalMinutes")) {
         this.plugin.getConfig().set("punishment.autoSave.intervalMinutes", 10);
      }

      if (!this.plugin.getConfig().isSet("punishment.autoSave.logging")) {
         this.plugin.getConfig().set("punishment.autoSave.logging", true);
      }

      this.plugin.saveConfig();
   }

   public void setupDefaultStatsAutoSaveConfig() {
      if (!this.plugin.getConfig().isSet("stats.autoSave.enabled")) {
         this.plugin.getConfig().set("stats.autoSave.enabled", true);
      }

      if (!this.plugin.getConfig().isSet("stats.autoSave.intervalMinutes")) {
         this.plugin.getConfig().set("stats.autoSave.intervalMinutes", 10);
      }

      if (!this.plugin.getConfig().isSet("stats.autoSave.logging")) {
         this.plugin.getConfig().set("stats.autoSave.logging", false);
      }

      this.plugin.saveConfig();
   }

   public boolean isPunishmentEnabled(int level) {
      return this.plugin.getConfig().getBoolean("punishment.enabled." + level, false);
   }

   public void setPunishmentEnabled(int level, boolean enabled) {
      this.plugin.getConfig().set("punishment.enabled." + level, enabled);
      this.plugin.saveConfig();
   }

   public boolean isPunishmentOptionEnabled(int level, String option) {
      String path = "punishment.levels." + level + ".options." + option;
      return this.plugin.getConfig().getBoolean(path, false);
   }

   public void setPunishmentOptionEnabled(int level, String option, boolean enabled) {
      String path = "punishment.levels." + level + ".options." + option;
      this.plugin.getConfig().set(path, enabled);
      this.plugin.saveConfig();
   }

   public void setupDefaultPunishmentOptions() {
      for (int i = 1; i <= 6; i++) {
         if (!this.plugin.getConfig().isSet("punishment.enabled." + i)) {
            this.plugin.getConfig().set("punishment.enabled." + i, false);
         }

         this.setDefaultOption(i, "admin_alert", true);
         this.setDefaultOption(i, "warning_message", i <= 3);
      }

      this.setDefaultOption(1, "mining_fatigue", true);
      this.setDefaultOption(1, "fake_diamonds", false);
      this.setDefaultOption(1, "admin_alert", true);
      this.setDefaultOption(1, "heavy_pickaxe", false);
      this.setDefaultOption(2, "fake_ore_veins", true);
      this.setDefaultOption(2, "inventory_drop", false);
      this.setDefaultOption(2, "xray_vision_blur", true);
      this.setDefaultOption(2, "tool_damage", false);
      this.setDefaultOption(2, "paranoia_mode", false);
      this.setDefaultOption(3, "temporary_kick", true);
      this.setDefaultOption(3, "mining_license_suspension", false);
      this.setDefaultOption(3, "resource_tax", false);
      this.setDefaultOption(3, "decoy_attraction", true);
      this.setDefaultOption(3, "fools_gold", false);
      this.setDefaultOption(4, "extended_ban", true);
      this.setDefaultOption(4, "mining_reputation", false);
      this.setDefaultOption(4, "restricted_areas", false);
      this.setDefaultOption(4, "cursed_pickaxe", false);
      this.setDefaultOption(5, "long_term_ban", true);
      this.setDefaultOption(5, "public_notification", false);
      this.setDefaultOption(5, "permanent_mining_debuff", false);
      this.setDefaultOption(5, "staff_review", false);
      this.setDefaultOption(5, "stone_vision", false);
      this.setDefaultOption(6, "permanent_ban", true);
      this.setDefaultOption(6, "ip_tracking", false);
      this.setDefaultOption(6, "security_report", true);
      this.setDefaultOption(6, "tnt_execution", false);
      this.plugin.saveConfig();
   }

   private void setDefaultOption(int level, String option, boolean defaultValue) {
      String path = "punishment.levels." + level + ".options." + option;
      if (!this.plugin.getConfig().isSet(path)) {
         this.plugin.getConfig().set(path, defaultValue);
      }
   }

   public void reloadConfig() {
      this.plugin.reloadConfig();
      this.loadConfiguration();
   }

   public String getWebhookUrl() {
      String storedUrl = this.plugin.getConfig().getString("webhook.url", "");
      if (storedUrl == null || storedUrl.isEmpty()) {
         return "";
      } else if (storedUrl.startsWith("https://")) {
         return storedUrl;
      } else {
         try {
            return WebhookSecurity.deobfuscateWebhookUrl(storedUrl);
         } catch (Exception var3) {
            return storedUrl;
         }
      }
   }

   public void setWebhookUrl(String url) {
      this.plugin.getConfig().set("webhook.url", url);
      this.plugin.saveConfig();
   }

   public boolean isWebhookAlertEnabled(String alertType) {
      return this.plugin.getConfig().getBoolean("webhook.alerts." + alertType, false);
   }

   public void setWebhookAlertEnabled(String alertType, boolean enabled) {
      this.plugin.getConfig().set("webhook.alerts." + alertType, enabled);
      this.plugin.saveConfig();
   }

   public void initializeWebhookSettings() {
      if (!this.plugin.getConfig().contains("webhook.url")) {
         this.plugin.getConfig().set("webhook.url", "");
      }

      if (!this.plugin.getConfig().contains("webhook.alerts.xray_detection")) {
         this.plugin.getConfig().set("webhook.alerts.xray_detection", true);
      }

      if (!this.plugin.getConfig().contains("webhook.alerts.suspicious_mining")) {
         this.plugin.getConfig().set("webhook.alerts.suspicious_mining", true);
      }

      if (!this.plugin.getConfig().contains("webhook.alerts.punishment_applied")) {
         this.plugin.getConfig().set("webhook.alerts.punishment_applied", true);
      }

      if (!this.plugin.getConfig().contains("webhook.alerts.staff_actions")) {
         this.plugin.getConfig().set("webhook.alerts.staff_actions", false);
      }

      if (!this.plugin.getConfig().contains("webhook.alerts.appeal_updates")) {
         this.plugin.getConfig().set("webhook.alerts.appeal_updates", true);
      }

      this.plugin.saveConfig();
   }

   public int getOreThreshold() {
      return this.oreThreshold;
   }

   public long getTimeWindowTicks() {
      return this.timeWindowTicks;
   }

   public double getDecoyDistance() {
      return this.decoyDistance;
   }

   public double getDecoyFieldOffset() {
      return this.decoyFieldOffset;
   }

   public double getMaxDecayDistance() {
      return this.maxDecayDistance;
   }

   public long getDecoyRevertDelay() {
      return this.decoyRevertDelay;
   }

   public boolean isWarnOnDecoy() {
      return this.warnOnDecoy;
   }

   public boolean isDecoyEnabled() {
      return this.decoyEnabled;
   }

   public boolean isDecoyRequireBuried() {
      return this.decoyRequireBuried;
   }

   public int getDecoySearchRadius() {
      return this.decoySearchRadius;
   }

   public int getBuriedThreshold() {
      return this.buriedThreshold;
   }

   public boolean isDebugEnabled() {
      return this.debugEnabled;
   }

   public boolean isStaffOreAlerts() {
      return this.staffOreAlerts;
   }

   public long getStaffOreResetTime() {
      return this.staffOreResetTime;
   }

   public boolean isStaffAlertEnabled() {
      return this.staffAlertEnabled;
   }

   public void setDecoyEnabled(boolean enabled) {
      this.decoyEnabled = enabled;
      this.cachedDecoyEnabled.set(enabled);
      this.plugin.getConfig().set("decoy.enabled", enabled);
      this.plugin.saveConfig();
   }

   public void setStaffOreAlerts(boolean enabled) {
      this.staffOreAlerts = enabled;
      this.cachedStaffOreAlerts.set(enabled);
      this.plugin.getConfig().set("staff.oreAlerts", enabled);
      this.plugin.saveConfig();
   }

   public void setStaffAlertEnabled(boolean enabled) {
      this.staffAlertEnabled = enabled;
      this.cachedStaffAlertEnabled.set(enabled);
      this.plugin.getConfig().set("staff.alertEnabled", enabled);
      this.plugin.saveConfig();
   }

   public Set<Material> getNaturalOres() {
      return this.naturalOres;
   }

   public boolean isCommandHidingEnabled() {
      return this.commandHidingEnabled;
   }

   public void setCommandHidingEnabled(boolean enabled) {
      this.commandHidingEnabled = enabled;
      this.plugin.getConfig().set("command-hiding.enabled", enabled);
      this.plugin.saveConfig();
   }

   public String getCommandHidingErrorLine1() {
      return this.commandHidingErrorLine1;
   }

   public String getCommandHidingErrorLine2() {
      return this.commandHidingErrorLine2;
   }

   public void setupDefaultCommandHidingConfig() {
      if (!this.plugin.getConfig().isSet("command-hiding.enabled")) {
         this.plugin.getConfig().set("command-hiding.enabled", false);
      }

      if (!this.plugin.getConfig().isSet("command-hiding.messages.error-line1")) {
         this.plugin.getConfig().set("command-hiding.messages.error-line1", "§cUnknown or incomplete command, see below for error");
      }

      if (!this.plugin.getConfig().isSet("command-hiding.messages.error-line2")) {
         this.plugin.getConfig().set("command-hiding.messages.error-line2", "§c§n{command}§r§c§o<--[HERE]");
      }

      this.plugin.saveConfig();
   }

   public void setupDefaultWorldDisablingConfig() {
      if (!this.plugin.getConfig().isSet("disabled-worlds.enabled")) {
         this.plugin.getConfig().set("disabled-worlds.enabled", false);
      }

      if (!this.plugin.getConfig().isSet("disabled-worlds.worlds")) {
         this.plugin.getConfig().set("disabled-worlds.worlds", new ArrayList());
      }

      this.plugin.saveConfig();
   }

   public boolean isStatsAutoSaveEnabled() {
      return this.statsAutoSaveEnabled;
   }

   public void setStatsAutoSaveEnabled(boolean enabled) {
      this.statsAutoSaveEnabled = enabled;
      this.plugin.getConfig().set("stats.autoSave.enabled", enabled);
      this.plugin.saveConfig();
   }

   public int getStatsAutoSaveInterval() {
      return this.statsAutoSaveInterval;
   }

   public void setStatsAutoSaveInterval(int minutes) {
      if (minutes < 1) {
         minutes = 1;
      }

      this.statsAutoSaveInterval = minutes;
      this.plugin.getConfig().set("stats.autoSave.intervalMinutes", minutes);
      this.plugin.saveConfig();
   }

   public boolean isStatsAutoSaveLoggingEnabled() {
      return this.statsAutoSaveLogging;
   }

   public void setStatsAutoSaveLoggingEnabled(boolean enabled) {
      this.statsAutoSaveLogging = enabled;
      this.plugin.getConfig().set("stats.autoSave.logging", enabled);
      this.plugin.saveConfig();
   }

   public boolean isSuspiciousAutoSaveEnabled() {
      return this.suspiciousAutoSaveEnabled;
   }

   public void setSuspiciousAutoSaveEnabled(boolean enabled) {
      this.suspiciousAutoSaveEnabled = enabled;
      this.plugin.getConfig().set("suspicious.autoSave.enabled", enabled);
      this.plugin.saveConfig();
   }

   public int getSuspiciousAutoSaveInterval() {
      return this.suspiciousAutoSaveInterval;
   }

   public void setSuspiciousAutoSaveInterval(int minutes) {
      if (minutes < 1) {
         minutes = 1;
      }

      this.suspiciousAutoSaveInterval = minutes;
      this.plugin.getConfig().set("suspicious.autoSave.intervalMinutes", minutes);
      this.plugin.saveConfig();
   }

   public boolean isSuspiciousAutoSaveLoggingEnabled() {
      return this.suspiciousAutoSaveLogging;
   }

   public void setSuspiciousAutoSaveLoggingEnabled(boolean enabled) {
      this.suspiciousAutoSaveLogging = enabled;
      this.plugin.getConfig().set("suspicious.autoSave.logging", enabled);
      this.plugin.saveConfig();
   }

   public boolean isPunishmentAutoSaveEnabled() {
      return this.punishmentAutoSaveEnabled;
   }

   public void setPunishmentAutoSaveEnabled(boolean enabled) {
      this.punishmentAutoSaveEnabled = enabled;
      this.plugin.getConfig().set("punishment.autoSave.enabled", enabled);
      this.plugin.saveConfig();
   }

   public int getPunishmentAutoSaveInterval() {
      return this.punishmentAutoSaveInterval;
   }

   public void setPunishmentAutoSaveInterval(int minutes) {
      if (minutes < 1) {
         minutes = 1;
      }

      this.punishmentAutoSaveInterval = minutes;
      this.plugin.getConfig().set("punishment.autoSave.intervalMinutes", minutes);
      this.plugin.saveConfig();
   }

   public boolean isPunishmentAutoSaveLoggingEnabled() {
      return this.punishmentAutoSaveLogging;
   }

   public void setPunishmentAutoSaveLoggingEnabled(boolean enabled) {
      this.punishmentAutoSaveLogging = enabled;
      this.plugin.getConfig().set("punishment.autoSave.logging", enabled);
      this.plugin.saveConfig();
   }

   public boolean isWorldDisablingEnabled() {
      return this.worldDisablingEnabled;
   }

   public void setWorldDisablingEnabled(boolean enabled) {
      this.worldDisablingEnabled = enabled;
      this.plugin.getConfig().set("disabled-worlds.enabled", enabled);
      this.plugin.saveConfig();
   }

   public boolean isWorldDisabled(String worldName) {
      return !this.worldDisablingEnabled ? false : this.worldCheckCache.computeIfAbsent(worldName, key -> this.disabledWorlds.contains(key));
   }

   public Set<String> getDisabledWorlds() {
      return new HashSet<>(this.disabledWorlds);
   }

   public void addDisabledWorld(String worldName) {
      this.disabledWorlds.add(worldName);
      List<String> worldList = new ArrayList<>(this.disabledWorlds);
      this.plugin.getConfig().set("disabled-worlds.worlds", worldList);
      this.plugin.saveConfig();
      this.worldCheckCache.clear();
   }

   public void removeDisabledWorld(String worldName) {
      this.disabledWorlds.remove(worldName);
      List<String> worldList = new ArrayList<>(this.disabledWorlds);
      this.plugin.getConfig().set("disabled-worlds.worlds", worldList);
      this.plugin.saveConfig();
      this.worldCheckCache.clear();
   }

   public void setDisabledWorlds(Set<String> worlds) {
      this.disabledWorlds.clear();
      this.disabledWorlds.addAll(worlds);
      List<String> worldList = new ArrayList<>(worlds);
      this.plugin.getConfig().set("disabled-worlds.worlds", worldList);
      this.plugin.saveConfig();
      this.worldCheckCache.clear();
   }

   public int getCachedOreThreshold() {
      return this.cachedOreThreshold.get();
   }

   public long getCachedTimeWindowTicks() {
      return this.cachedTimeWindowTicks.get();
   }

   public long getCachedDecoyRevertDelay() {
      return this.cachedDecoyRevertDelay.get();
   }

   public boolean isCachedDecoyEnabled() {
      return this.cachedDecoyEnabled.get();
   }

   public boolean isCachedRequireBuried() {
      return this.cachedRequireBuried.get();
   }

   public boolean isCachedDebugEnabled() {
      return this.cachedDebugEnabled.get();
   }

   public boolean isCachedStaffOreAlertsEnabled() {
      return this.cachedStaffOreAlerts.get();
   }

   public boolean isCachedStaffAlertEnabled() {
      return this.cachedStaffAlertEnabled.get();
   }

   public boolean isCachedCommandHidingEnabled() {
      return this.cachedCommandHidingEnabled.get();
   }
}
