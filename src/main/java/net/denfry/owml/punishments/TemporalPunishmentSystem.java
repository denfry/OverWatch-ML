package net.denfry.owml.punishments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.SchedulerUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public class TemporalPunishmentSystem {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, List<TemporalPunishmentSystem.ActivePunishment>> activePunishments = new ConcurrentHashMap<>();
   private final Map<UUID, TemporalPunishmentSystem.PunishmentHistory> punishmentHistory = new ConcurrentHashMap<>();
   private final Map<Integer, TemporalPunishmentSystem.PunishmentLevel> punishmentLevels = new ConcurrentHashMap<>();

   public TemporalPunishmentSystem() {
      this.initializePunishmentLevels();
      MessageManager.log(
         "info", "Temporal Punishment System initialized with {LEVELS} punishment levels", "LEVELS", String.valueOf(this.punishmentLevels.size())
      );
   }

   private void initializePunishmentLevels() {
      this.punishmentLevels
         .put(
            1,
            new TemporalPunishmentSystem.PunishmentLevel(
               1,
               "Warning",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.MESSAGE, 0, "§e⚠ You have been warned for suspicious mining activity!"
                  )
               ),
               300000L
            )
         );
      this.punishmentLevels
         .put(
            2,
            new TemporalPunishmentSystem.PunishmentLevel(
               2,
               "Mining Debuff",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.MINING_DEBUFF, 30, "§c⛏ Your mining speed has been reduced!"
                  )
               ),
               900000L
            )
         );
      this.punishmentLevels
         .put(
            3,
            new TemporalPunishmentSystem.PunishmentLevel(
               3,
               "Inventory Restriction",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.INVENTORY_LOCK, 60, "§c\ud83d\udce6 Your inventory has been temporarily locked!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.MINING_DEBUFF, 50, "§c⛏ Mining speed further reduced!"
                  )
               ),
               1800000L
            )
         );
      this.punishmentLevels
         .put(
            4,
            new TemporalPunishmentSystem.PunishmentLevel(
               4,
               "Movement Restriction",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.SLOWNESS, 120, "§c\ud83d\udc0c You have been slowed down!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.INVENTORY_LOCK, 90, "§c\ud83d\udce6 Inventory remains locked!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.MINING_DEBUFF, 75, "§c⛏ Mining heavily debuffed!"
                  )
               ),
               3600000L
            )
         );
      this.punishmentLevels
         .put(
            5,
            new TemporalPunishmentSystem.PunishmentLevel(
               5,
               "Paranoia",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.PARANOIA, 180, "§4\ud83d\udc7b Paranoia effects activated!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.SLOWNESS, 150, "§c\ud83d\udc0c Movement severely restricted!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.INVENTORY_LOCK, 120, "§c\ud83d\udce6 Inventory locked!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(TemporalPunishmentSystem.PunishmentEffect.Type.MINING_DEBUFF, 90, "§c⛏ Mining disabled!")
               ),
               7200000L
            )
         );
      this.punishmentLevels
         .put(
            6,
            new TemporalPunishmentSystem.PunishmentLevel(
               6,
               "Severe Paranoia",
               Arrays.asList(
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.SEVERE_PARANOIA, 300, "§4\ud83d\udc80 Severe paranoia activated!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.SLOWNESS, 200, "§c\ud83d\udc0c Movement critically restricted!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.INVENTORY_LOCK, 180, "§c\ud83d\udce6 Inventory permanently locked!"
                  ),
                  new TemporalPunishmentSystem.PunishmentEffect(
                     TemporalPunishmentSystem.PunishmentEffect.Type.MINING_DEBUFF, 100, "§c⛏ Mining completely disabled!"
                  )
               ),
               14400000L
            )
         );
   }

   public void applyPunishment(@NotNull UUID playerId, int level, @NotNull String reason) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null && player.isOnline()) {
         TemporalPunishmentSystem.PunishmentHistory history = this.punishmentHistory
            .computeIfAbsent(playerId, k -> new TemporalPunishmentSystem.PunishmentHistory(playerId));
         int effectiveLevel = this.calculateEffectiveLevel(playerId, level, history);
         TemporalPunishmentSystem.PunishmentLevel punishmentLevel = this.punishmentLevels.get(effectiveLevel);
         if (punishmentLevel == null) {
            MessageManager.log("error", "Invalid punishment level: {LEVEL}", "LEVEL", String.valueOf(effectiveLevel));
         } else {
            TemporalPunishmentSystem.ActivePunishment activePunishment = new TemporalPunishmentSystem.ActivePunishment(
               playerId, effectiveLevel, reason, System.currentTimeMillis(), punishmentLevel.duration
            );
            Schedulers.runFor(player, () -> this.applyPunishmentEffects(player, punishmentLevel.effects));
            this.activePunishments.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>()).add(activePunishment);
            history.addPunishment(effectiveLevel, reason, System.currentTimeMillis());
            this.schedulePunishmentRemoval(playerId, activePunishment);
            this.notifyPunishment(player, punishmentLevel, reason, effectiveLevel);
            MessageManager.log(
               "info",
               "Applied punishment level {LEVEL} to {PLAYER}: {REASON}",
               "LEVEL",
               String.valueOf(effectiveLevel),
               "PLAYER",
               player.getName(),
               "REASON",
               reason
            );
         }
      }
   }

   private int calculateEffectiveLevel(UUID playerId, int baseLevel, TemporalPunishmentSystem.PunishmentHistory history) {
      long now = System.currentTimeMillis();
      long recentWindow = 3600000L;
      int recentPunishments = history.getPunishmentsInTimeframe(now - recentWindow, now);
      if (recentPunishments >= 5) {
         return Math.min(6, baseLevel + 2);
      } else if (recentPunishments >= 3) {
         return Math.min(6, baseLevel + 1);
      } else {
         return recentPunishments >= 1 && baseLevel == 1 ? Math.min(6, baseLevel + 1) : Math.min(6, Math.max(1, baseLevel));
      }
   }

   private void applyPunishmentEffects(Player player, List<TemporalPunishmentSystem.PunishmentEffect> effects) {
      for (TemporalPunishmentSystem.PunishmentEffect effect : effects) {
         this.applySingleEffect(player, effect);
      }
   }

   private void applySingleEffect(Player player, TemporalPunishmentSystem.PunishmentEffect effect) {
      UUID playerId = player.getUniqueId();
      switch (effect.type) {
         case MESSAGE:
            MessageManager.send(player, "punishment.applied", "PUNISHMENT", effect.description, "PLAYER", player.getName(), "LEVEL", "Dynamic");
            break;
         case MINING_DEBUFF:
            this.applyMiningDebuff(player, effect.intensity);
            break;
         case INVENTORY_LOCK:
            this.applyInventoryLock(player, effect.intensity);
            break;
         case SLOWNESS:
            this.applySlownessEffect(player, effect.intensity);
            break;
         case PARANOIA:
            this.applyParanoiaEffects(player, effect.intensity);
            break;
         case SEVERE_PARANOIA:
            this.applySevereParanoiaEffects(player, effect.intensity);
      }
   }

   private void schedulePunishmentRemoval(UUID playerId, TemporalPunishmentSystem.ActivePunishment punishment) {
      long remainingTime = punishment.getRemainingTime();
      if (remainingTime > 0L) {
         SchedulerUtils.runTaskLater(plugin, () -> this.removePunishment(playerId, punishment), remainingTime / 50L);
      }
   }

   public void removePunishment(UUID playerId, TemporalPunishmentSystem.ActivePunishment punishment) {
      List<TemporalPunishmentSystem.ActivePunishment> playerPunishments = this.activePunishments.get(playerId);
      if (playerPunishments != null) {
         playerPunishments.remove(punishment);
         if (playerPunishments.isEmpty()) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
               Schedulers.runFor(p, () -> this.removeAllEffects(p));
            }
         }
      }
   }

   public void removeAllPunishments(UUID playerId) {
      List<TemporalPunishmentSystem.ActivePunishment> punishments = this.activePunishments.remove(playerId);
      if (punishments != null && !punishments.isEmpty()) {
         Player player = Bukkit.getPlayer(playerId);
         if (player != null) {
            Schedulers.runFor(player, () -> {
               this.removeAllEffects(player);
               MessageManager.send(player, "punishment.removed", "PLAYER", player.getName());
            });
         }
      }
   }

   private void notifyPunishment(Player player, TemporalPunishmentSystem.PunishmentLevel level, String reason, int effectiveLevel) {
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", level.name, "PLAYER", player.getName(), "LEVEL", String.valueOf(effectiveLevel));
      MessageManager.broadcastToPermission(
         "owml.staff", "punishment.applied", "PUNISHMENT", level.name, "PLAYER", player.getName(), "LEVEL", String.valueOf(effectiveLevel)
      );
      long durationMinutes = level.duration / 60000L;
      MessageManager.send(player, "punishment.cooldown", "TIME", durationMinutes + " minutes");
   }

   public void startPunishmentCleanupTask() {
      SchedulerUtils.runTaskTimer(plugin, () -> {
         long now = System.currentTimeMillis();

         for (Entry<UUID, List<TemporalPunishmentSystem.ActivePunishment>> entry : this.activePunishments.entrySet()) {
            UUID playerId = entry.getKey();
            List<TemporalPunishmentSystem.ActivePunishment> punishments = entry.getValue();
            punishments.removeIf(punishment -> {
               if (punishment.isExpired(now)) {
                  Player player = Bukkit.getPlayer(playerId);
                  if (player != null) {
                     Schedulers.entity(player, () -> this.removeSingleEffect(player, punishment.level));
                  }

                  return true;
               } else {
                  return false;
               }
            });
            if (punishments.isEmpty()) {
               this.activePunishments.remove(playerId);
            }
         }
      }, 1200L, 1200L);
   }

   private void applyMiningDebuff(Player player, int intensity) {
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", "Mining Debuff (" + intensity + "%)", "PLAYER", player.getName(), "LEVEL", "Dynamic");
   }

   private void applyInventoryLock(Player player, int intensity) {
      MessageManager.send(
         player, "punishment.applied", "PUNISHMENT", "Inventory Lock (" + intensity + " seconds)", "PLAYER", player.getName(), "LEVEL", "Dynamic"
      );
   }

   private void applySlownessEffect(Player player, int intensity) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, intensity * 20, 2));
   }

   private void applyParanoiaEffects(Player player, int intensity) {
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", "Paranoia Effects", "PLAYER", player.getName(), "LEVEL", "Dynamic");
   }

   private void applySevereParanoiaEffects(Player player, int intensity) {
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", "Severe Paranoia Effects", "PLAYER", player.getName(), "LEVEL", "Dynamic");
   }

   private void removeAllEffects(Player player) {
      if (player != null) {
         player.removePotionEffect(PotionEffectType.SLOWNESS);
      }
   }

   private void removeSingleEffect(Player player, int level) {
      TemporalPunishmentSystem.PunishmentLevel punishmentLevel = this.punishmentLevels.get(level);
      if (punishmentLevel != null && player != null) {
         player.removePotionEffect(PotionEffectType.SLOWNESS);
      }
   }

   public boolean hasActivePunishments(UUID playerId) {
      List<TemporalPunishmentSystem.ActivePunishment> punishments = this.activePunishments.get(playerId);
      return punishments != null && !punishments.isEmpty();
   }

   public List<TemporalPunishmentSystem.ActivePunishment> getActivePunishments(UUID playerId) {
      return new ArrayList<>(this.activePunishments.getOrDefault(playerId, Collections.emptyList()));
   }

   public TemporalPunishmentSystem.PunishmentHistory getPunishmentHistory(UUID playerId) {
      return this.punishmentHistory.get(playerId);
   }

   public TemporalPunishmentSystem.PunishmentStats getStats() {
      int totalActive = this.activePunishments.values().stream().mapToInt(List::size).sum();
      int totalHistorical = this.punishmentHistory.values().stream().mapToInt(history -> history.getTotalPunishments()).sum();
      return new TemporalPunishmentSystem.PunishmentStats(totalActive, totalHistorical, this.activePunishments.size());
   }

   public static class ActivePunishment {
      public final UUID playerId;
      public final int level;
      public final String reason;
      public final long startTime;
      public final long duration;

      public ActivePunishment(UUID playerId, int level, String reason, long startTime, long duration) {
         this.playerId = playerId;
         this.level = level;
         this.reason = reason;
         this.startTime = startTime;
         this.duration = duration;
      }

      public boolean isExpired(long currentTime) {
         return currentTime - this.startTime >= this.duration;
      }

      public long getRemainingTime() {
         long elapsed = System.currentTimeMillis() - this.startTime;
         return Math.max(0L, this.duration - elapsed);
      }
   }

   public static class PunishmentEffect {
      public final TemporalPunishmentSystem.PunishmentEffect.Type type;
      public final int intensity;
      public final String description;

      public PunishmentEffect(TemporalPunishmentSystem.PunishmentEffect.Type type, int intensity, String description) {
         this.type = type;
         this.intensity = intensity;
         this.description = description;
      }

      public static enum Type {
         MESSAGE,
         MINING_DEBUFF,
         INVENTORY_LOCK,
         SLOWNESS,
         PARANOIA,
         SEVERE_PARANOIA;
      }
   }

   public static class PunishmentHistory {
      private final UUID playerId;
      private final List<TemporalPunishmentSystem.PunishmentRecord> records = new ArrayList<>();

      public PunishmentHistory(UUID playerId) {
         this.playerId = playerId;
      }

      public void addPunishment(int level, String reason, long timestamp) {
         this.records.add(new TemporalPunishmentSystem.PunishmentRecord(level, reason, timestamp));
      }

      public int getTotalPunishments() {
         return this.records.size();
      }

      public int getPunishmentsInTimeframe(long startTime, long endTime) {
         return (int)this.records.stream().filter(record -> record.timestamp >= startTime && record.timestamp <= endTime).count();
      }

      public List<TemporalPunishmentSystem.PunishmentRecord> getRecentPunishments(int count) {
         int start = Math.max(0, this.records.size() - count);
         return new ArrayList<>(this.records.subList(start, this.records.size()));
      }
   }

   public static class PunishmentLevel {
      public final int level;
      public final String name;
      public final List<TemporalPunishmentSystem.PunishmentEffect> effects;
      public final long duration;

      public PunishmentLevel(int level, String name, List<TemporalPunishmentSystem.PunishmentEffect> effects, long duration) {
         this.level = level;
         this.name = name;
         this.effects = effects;
         this.duration = duration;
      }
   }

   public static class PunishmentRecord {
      public final int level;
      public final String reason;
      public final long timestamp;

      public PunishmentRecord(int level, String reason, long timestamp) {
         this.level = level;
         this.reason = reason;
         this.timestamp = timestamp;
      }
   }

   public static class PunishmentStats {
      public final int totalActive;
      public final int totalHistorical;
      public final int affectedPlayers;

      public PunishmentStats(int totalActive, int totalHistorical, int affectedPlayers) {
         this.totalActive = totalActive;
         this.totalHistorical = totalHistorical;
         this.affectedPlayers = affectedPlayers;
      }
   }
}
