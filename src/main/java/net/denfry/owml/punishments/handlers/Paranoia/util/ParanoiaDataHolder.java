package net.denfry.owml.punishments.handlers.Paranoia.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;

public class ParanoiaDataHolder {
   private final Map<UUID, List<Entity>> activeGhostEntities = new ConcurrentHashMap<>();

   public void addGhostEntity(UUID playerId, Entity entity) {
      if (!this.activeGhostEntities.containsKey(playerId)) {
         this.activeGhostEntities.put(playerId, new ArrayList<>());
      }

      this.activeGhostEntities.get(playerId).add(entity);
   }

   public void removeGhostEntity(UUID playerId, Entity entity) {
      if (this.activeGhostEntities.containsKey(playerId)) {
         this.activeGhostEntities.get(playerId).remove(entity);
      }
   }

   public List<Entity> getPlayerGhostEntities(UUID playerId) {
      return this.activeGhostEntities.getOrDefault(playerId, new ArrayList<>());
   }

   public Map<UUID, List<Entity>> getAllGhostEntities() {
      return this.activeGhostEntities;
   }

   public void cleanupPlayerGhostEntities(UUID playerId) {
      if (this.activeGhostEntities.containsKey(playerId)) {
         for (Entity entity : this.activeGhostEntities.get(playerId)) {
            if (entity != null && !entity.isDead()) {
               entity.remove();
            }
         }

         this.activeGhostEntities.remove(playerId);
      }
   }

   public void cleanupAllGhostEntities() {
      for (List<Entity> entities : this.activeGhostEntities.values()) {
         for (Entity entity : entities) {
            if (entity != null && !entity.isDead()) {
               entity.remove();
            }
         }
      }

      this.activeGhostEntities.clear();
   }

   public void removeDeadEntities() {
      for (UUID playerId : new ArrayList<>(this.activeGhostEntities.keySet())) {
         List<Entity> entities = this.activeGhostEntities.get(playerId);
         entities.removeIf(entity -> entity == null || entity.isDead());
         if (entities.isEmpty()) {
            this.activeGhostEntities.remove(playerId);
         }
      }
   }
}
