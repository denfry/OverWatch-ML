package net.denfry.owml.punishments.handlers.Paranoia.cooldown;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
   private final Map<UUID, Map<CooldownManager.CooldownType, Long>> cooldowns = new ConcurrentHashMap<>();

   public boolean isOnCooldown(UUID playerId, CooldownManager.CooldownType type) {
      if (!this.cooldowns.containsKey(playerId)) {
         return false;
      } else {
         Map<CooldownManager.CooldownType, Long> playerCooldowns = this.cooldowns.get(playerId);
         return !playerCooldowns.containsKey(type) ? false : System.currentTimeMillis() - playerCooldowns.get(type) < type.getDuration();
      }
   }

   public void setCooldown(UUID playerId, CooldownManager.CooldownType type) {
      this.cooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(type, System.currentTimeMillis());
   }

   public long getRemainingCooldown(UUID playerId, CooldownManager.CooldownType type) {
      if (!this.isOnCooldown(playerId, type)) {
         return 0L;
      } else {
         long elapsedTime = System.currentTimeMillis() - this.cooldowns.get(playerId).get(type);
         return Math.max(0L, type.getDuration() - elapsedTime);
      }
   }

   public void removeCooldown(UUID playerId, CooldownManager.CooldownType type) {
      if (this.cooldowns.containsKey(playerId)) {
         this.cooldowns.get(playerId).remove(type);
      }
   }

   public void clearPlayerCooldowns(UUID playerId) {
      this.cooldowns.remove(playerId);
   }

   public static enum CooldownType {
      GHOST_MOB(15000L),
      VISUAL(8000L),
      FAKE_DAMAGE(30000L),
      MESSAGE(20000L),
      SOUND(3000L),
      NIGHTMARE(1800000L);

      private final long duration;

      private CooldownType(long duration) {
         this.duration = duration;
      }

      public long getDuration() {
         return this.duration;
      }
   }
}
