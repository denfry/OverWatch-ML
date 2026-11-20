package net.denfry.owml.punishments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.SchedulerUtils;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public class CompositePunishmentEngine {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<UUID, CompositePunishmentEngine.CompositePunishment> activeComposites = new ConcurrentHashMap<>();
   private final Map<String, CompositePunishmentEngine.PunishmentTemplate> templates = new ConcurrentHashMap<>();
   private final AdaptivePunishmentEngine adaptiveEngine = new AdaptivePunishmentEngine();

   public CompositePunishmentEngine() {
      this.initializeTemplates();
      MessageManager.log("info", "Composite Punishment Engine initialized with {TEMPLATES} templates", "TEMPLATES", String.valueOf(this.templates.size()));
   }

   private void initializeTemplates() {
      this.templates
         .put(
            "light_combo",
            new CompositePunishmentEngine.PunishmentTemplate(
               "Light Combination",
               Arrays.asList(
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MESSAGE, 1, 30000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MINING_DEBUFF, 1, 60000L)
               ),
               CompositePunishmentEngine.PunishmentChain.SEQUENTIAL
            )
         );
      this.templates
         .put(
            "medium_combo",
            new CompositePunishmentEngine.PunishmentTemplate(
               "Medium Combination",
               Arrays.asList(
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MINING_DEBUFF, 2, 180000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.INVENTORY_RESTRICTION, 1, 120000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MESSAGE, 2, 30000L)
               ),
               CompositePunishmentEngine.PunishmentChain.PARALLEL
            )
         );
      this.templates
         .put(
            "heavy_combo",
            new CompositePunishmentEngine.PunishmentTemplate(
               "Heavy Combination",
               Arrays.asList(
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MINING_DEBUFF, 3, 300000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.INVENTORY_RESTRICTION, 2, 240000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MOVEMENT_DEBUFF, 2, 180000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.PARANOIA_EFFECT, 1, 120000L)
               ),
               CompositePunishmentEngine.PunishmentChain.CASCADE
            )
         );
      this.templates
         .put(
            "escalation",
            new CompositePunishmentEngine.PunishmentTemplate(
               "Progressive Escalation",
               Arrays.asList(
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MESSAGE, 1, 20000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MINING_DEBUFF, 1, 60000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.INVENTORY_RESTRICTION, 1, 80000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.MOVEMENT_DEBUFF, 1, 100000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.PARANOIA_EFFECT, 1, 120000L)
               ),
               CompositePunishmentEngine.PunishmentChain.CASCADE
            )
         );
      this.templates
         .put(
            "random_mix",
            new CompositePunishmentEngine.PunishmentTemplate(
               "Random Mix",
               Arrays.asList(
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.random(), 1, 90000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.random(), 2, 120000L),
                  new CompositePunishmentEngine.PunishmentComponent(CompositePunishmentEngine.PunishmentType.random(), 1, 60000L)
               ),
               CompositePunishmentEngine.PunishmentChain.SEQUENTIAL
            )
         );
   }

   public void applyAdaptivePunishment(
      @NotNull UUID playerId, @NotNull String offenseType, double offenseSeverity, @NotNull Map<String, Object> context, @NotNull String reason
   ) {
      Player player = plugin.getServer().getPlayer(playerId);
      if (player != null && player.isOnline()) {
         AdaptivePunishmentEngine.PunishmentRecommendation recommendation = this.adaptiveEngine
            .recommendPunishment(playerId, offenseType, offenseSeverity, context);
         String templateName = this.mapRecommendationToTemplate(recommendation);
         this.applyCompositePunishment(playerId, templateName, reason + " (Adaptive: " + recommendation.getReasoning() + ")");
         Schedulers.globalLater(() -> this.trackPunishmentOutcome(playerId, recommendation), 6000L);
      }
   }

   private String mapRecommendationToTemplate(AdaptivePunishmentEngine.PunishmentRecommendation recommendation) {
      String punishmentType = recommendation.punishmentType;
      double intensity = recommendation.intensity;
      if (intensity < 0.5) {
         switch (punishmentType) {
            case "warning":
               return "light";
            case "kick":
               return "light";
            case "mining_debuff":
               return "light";
            default:
               return "light";
         }
      } else if (intensity < 0.8) {
         switch (punishmentType) {
            case "temp_ban_1h":
               return "medium";
            case "inventory_lock":
               return "medium";
            case "speed_reduction":
               return "medium";
            default:
               return "medium";
         }
      } else if (intensity < 1.2) {
         switch (punishmentType) {
            case "temp_ban_24h":
               return "heavy";
            case "fake_ore_veins":
               return "heavy";
            case "paranoia_effects":
               return "heavy";
            default:
               return "heavy";
         }
      } else {
         switch (punishmentType) {
            case "temp_ban_7d":
               return "severe";
            case "perm_ban":
               return "severe";
            case "cursed_pickaxe":
               return "severe";
            default:
               return "severe";
         }
      }
   }

   private void trackPunishmentOutcome(UUID playerId, AdaptivePunishmentEngine.PunishmentRecommendation recommendation) {
      Player player = plugin.getServer().getPlayer(playerId);
      if (player != null) {
         boolean wasEffective = player.isOnline() && !this.hasRecentDetections(playerId);
         Map<String, Object> outcomeContext = new HashMap<>();
         outcomeContext.put("player_online", player.isOnline());
         outcomeContext.put("time_since_punishment", 300);
         outcomeContext.put("has_recent_detections", this.hasRecentDetections(playerId));
         this.adaptiveEngine.recordPunishmentOutcome(playerId, recommendation.punishmentType, wasEffective, outcomeContext);
      }
   }

   private boolean hasRecentDetections(UUID playerId) {
      return false;
   }

   public void applyCompositePunishment(@NotNull UUID playerId, @NotNull String templateName, @NotNull String reason) {
      Player player = plugin.getServer().getPlayer(playerId);
      if (player != null && player.isOnline()) {
         CompositePunishmentEngine.PunishmentTemplate template = this.templates.get(templateName);
         if (template == null) {
            MessageManager.log("error", "Unknown punishment template: {TEMPLATE}", "TEMPLATE", templateName);
         } else {
            CompositePunishmentEngine.CompositePunishment existing = this.activeComposites.get(playerId);
            if (existing != null && !existing.isExpired()) {
               this.mergePunishments(player, existing, template, reason);
            } else {
               CompositePunishmentEngine.CompositePunishment composite = new CompositePunishmentEngine.CompositePunishment(
                  playerId, template, reason, System.currentTimeMillis()
               );
               this.activeComposites.put(playerId, composite);
               this.applyPunishmentChain(player, composite);
               this.notifyCompositePunishment(player, template, reason);
               MessageManager.log(
                  "info",
                  "Applied composite punishment '{TEMPLATE}' to {PLAYER}: {REASON}",
                  "TEMPLATE",
                  templateName,
                  "PLAYER",
                  player.getName(),
                  "REASON",
                  reason
               );
            }
         }
      }
   }

   private void applyPunishmentChain(Player player, CompositePunishmentEngine.CompositePunishment composite) {
      CompositePunishmentEngine.PunishmentTemplate template = composite.template;
      switch (template.chainType) {
         case SEQUENTIAL:
            this.applySequentialChain(player, composite);
            break;
         case PARALLEL:
            this.applyParallelChain(player, composite);
            break;
         case CASCADE:
            this.applyCascadeChain(player, composite);
            break;
         case RANDOM:
            this.applyRandomChain(player, composite);
      }
   }

   private void applySequentialChain(Player player, CompositePunishmentEngine.CompositePunishment composite) {
      CompositePunishmentEngine.PunishmentTemplate template = composite.template;
      long currentDelay = 0L;

      for (CompositePunishmentEngine.PunishmentComponent component : template.components) {
         Schedulers.entityLater(player, () -> this.applyPunishmentComponent(player, component), currentDelay / 50L);
         currentDelay += component.duration;
      }

      composite.totalDuration = currentDelay;
   }

   private void applyParallelChain(Player player, CompositePunishmentEngine.CompositePunishment composite) {
      CompositePunishmentEngine.PunishmentTemplate template = composite.template;
      long maxDuration = 0L;

      for (CompositePunishmentEngine.PunishmentComponent component : template.components) {
         this.applyPunishmentComponent(player, component);
         maxDuration = Math.max(maxDuration, component.duration);
      }

      composite.totalDuration = maxDuration;
   }

   private void applyCascadeChain(Player player, CompositePunishmentEngine.CompositePunishment composite) {
      CompositePunishmentEngine.PunishmentTemplate template = composite.template;
      long cumulativeDelay = 0L;
      long totalDuration = 0L;

      for (CompositePunishmentEngine.PunishmentComponent component : template.components) {
         Schedulers.entityLater(player, () -> this.applyPunishmentComponent(player, component), cumulativeDelay / 50L);
         cumulativeDelay += component.delay;
         totalDuration += component.duration;
      }

      composite.totalDuration = totalDuration;
   }

   private void applyRandomChain(Player player, CompositePunishmentEngine.CompositePunishment composite) {
      CompositePunishmentEngine.PunishmentTemplate template = composite.template;
      List<CompositePunishmentEngine.PunishmentComponent> shuffled = new ArrayList<>(template.components);
      Collections.shuffle(shuffled);
      long maxDuration = 0L;
      Random random = new Random();

      for (CompositePunishmentEngine.PunishmentComponent component : shuffled) {
         long delay = random.nextInt(10000);
         Schedulers.entityLater(player, () -> this.applyPunishmentComponent(player, component), delay / 50L);
         maxDuration = Math.max(maxDuration, delay + component.duration);
      }

      composite.totalDuration = maxDuration;
   }

   private void applyPunishmentComponent(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      if (!Schedulers.isOwned(player)) {
         Schedulers.entity(player, () -> this.applyPunishmentComponent(player, component));
      } else {
         switch (component.type) {
            case MESSAGE:
               this.applyMessageEffect(player, component);
               break;
            case MINING_DEBUFF:
               this.applyMiningDebuff(player, component);
               break;
            case INVENTORY_RESTRICTION:
               this.applyInventoryRestriction(player, component);
               break;
            case MOVEMENT_DEBUFF:
               this.applyMovementDebuff(player, component);
               break;
            case PARANOIA_EFFECT:
               this.applyParanoiaEffect(player, component);
               break;
            case VISUAL_EFFECT:
               this.applyVisualEffect(player, component);
               break;
            case SOUND_EFFECT:
               this.applySoundEffect(player, component);
         }
      }
   }

   private void mergePunishments(
      Player player, CompositePunishmentEngine.CompositePunishment existing, CompositePunishmentEngine.PunishmentTemplate newTemplate, String reason
   ) {
      List<CompositePunishmentEngine.PunishmentComponent> mergedComponents = new ArrayList<>(existing.template.components);

      for (CompositePunishmentEngine.PunishmentComponent newComponent : newTemplate.components) {
         boolean exists = mergedComponents.stream().anyMatch(component -> component.type == newComponent.type);
         if (!exists) {
            mergedComponents.add(newComponent);
         }
      }

      CompositePunishmentEngine.PunishmentTemplate mergedTemplate = new CompositePunishmentEngine.PunishmentTemplate(
         "Merged: " + existing.template.name + " + " + newTemplate.name, mergedComponents, CompositePunishmentEngine.PunishmentChain.PARALLEL
      );
      CompositePunishmentEngine.CompositePunishment merged = new CompositePunishmentEngine.CompositePunishment(
         player.getUniqueId(), mergedTemplate, reason + " (merged)", System.currentTimeMillis()
      );
      this.activeComposites.put(player.getUniqueId(), merged);
      this.applyPunishmentChain(player, merged);
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", "Merged punishment effects", "PLAYER", player.getName(), "LEVEL", "Enhanced");
   }

   private void notifyCompositePunishment(Player player, CompositePunishmentEngine.PunishmentTemplate template, String reason) {
      MessageManager.send(player, "punishment.applied", "PUNISHMENT", template.name, "PLAYER", player.getName(), "LEVEL", "Composite");
      MessageManager.broadcastToPermission(
         "owml.staff",
         "punishment.applied",
         "PUNISHMENT",
         template.name + " (" + template.components.size() + " effects)",
         "PLAYER",
         player.getName(),
         "LEVEL",
         "Composite"
      );
   }

   public void startCompositeCleanupTask() {
      SchedulerUtils.runTaskTimer(plugin, () -> {
         long now = System.currentTimeMillis();
         this.activeComposites.entrySet().removeIf(entry -> {
            CompositePunishmentEngine.CompositePunishment punishment = entry.getValue();
            if (punishment.isExpired(now)) {
               Player player = plugin.getServer().getPlayer(entry.getKey());
               if (player != null) {
                  Schedulers.entity(player, () -> this.removeCompositeEffects(player, punishment));
               }

               return true;
            } else {
               return false;
            }
         });
      }, 1200L, 1200L);
   }

   private void removeCompositeEffects(Player player, CompositePunishmentEngine.CompositePunishment punishment) {
      for (CompositePunishmentEngine.PunishmentComponent component : punishment.template.components) {
         this.removePunishmentComponent(player, component);
      }

      MessageManager.send(player, "punishment.removed", "PLAYER", player.getName());
   }

   private void applyMessageEffect(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      String message = "§e⚠ Punishment Effect: " + component.type.name() + " (Level " + component.intensity + ")";
      player.sendMessage(message);
   }

   private void applyMiningDebuff(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      MessageManager.send(
         player, "punishment.applied", "PUNISHMENT", "Mining Debuff (Level " + component.intensity + ")", "PLAYER", player.getName(), "LEVEL", "Composite"
      );
   }

   private void applyInventoryRestriction(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      MessageManager.send(
         player,
         "punishment.applied",
         "PUNISHMENT",
         "Inventory Restriction (Level " + component.intensity + ")",
         "PLAYER",
         player.getName(),
         "LEVEL",
         "Composite"
      );
   }

   private void applyMovementDebuff(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(component.duration / 50L), component.intensity - 1));
   }

   private void applyParanoiaEffect(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      MessageManager.send(
         player, "punishment.applied", "PUNISHMENT", "Paranoia Effect (Level " + component.intensity + ")", "PLAYER", player.getName(), "LEVEL", "Composite"
      );
   }

   private void applyVisualEffect(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      player.sendMessage("§c\ud83d\udc41 Visual effects applied!");
   }

   private void applySoundEffect(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      player.sendMessage("§4\ud83d\udd0a Audio effects applied!");
   }

   private void removePunishmentComponent(Player player, CompositePunishmentEngine.PunishmentComponent component) {
      switch (component.type) {
         case MOVEMENT_DEBUFF:
            player.removePotionEffect(PotionEffectType.SLOWNESS);
         case MESSAGE:
         case MINING_DEBUFF:
         case INVENTORY_RESTRICTION:
         case PARANOIA_EFFECT:
         case VISUAL_EFFECT:
         case SOUND_EFFECT:
      }
   }

   public boolean hasActiveCompositePunishment(UUID playerId) {
      CompositePunishmentEngine.CompositePunishment punishment = this.activeComposites.get(playerId);
      return punishment != null && !punishment.isExpired();
   }

   public CompositePunishmentEngine.CompositePunishment getActiveCompositePunishment(UUID playerId) {
      return this.activeComposites.get(playerId);
   }

   public void removeCompositePunishment(UUID playerId) {
      CompositePunishmentEngine.CompositePunishment punishment = this.activeComposites.remove(playerId);
      if (punishment != null) {
         Player player = plugin.getServer().getPlayer(playerId);
         if (player != null) {
            this.removeCompositeEffects(player, punishment);
         }
      }
   }

   public Set<String> getAvailableTemplates() {
      return new HashSet<>(this.templates.keySet());
   }

   public CompositePunishmentEngine.CompositeStats getStats() {
      int activeCount = (int)this.activeComposites.values().stream().filter(p -> !p.isExpired()).count();
      return new CompositePunishmentEngine.CompositeStats(this.activeComposites.size(), activeCount, this.templates.size(), System.currentTimeMillis());
   }

   public Map<String, Object> getAdaptiveStats() {
      return this.adaptiveEngine.getAdaptiveStats();
   }

   public static class CompositePunishment {
      public final UUID playerId;
      public final CompositePunishmentEngine.PunishmentTemplate template;
      public final String reason;
      public final long startTime;
      public long totalDuration;

      public CompositePunishment(UUID playerId, CompositePunishmentEngine.PunishmentTemplate template, String reason, long startTime) {
         this.playerId = playerId;
         this.template = template;
         this.reason = reason;
         this.startTime = startTime;
      }

      public boolean isExpired() {
         return this.isExpired(System.currentTimeMillis());
      }

      public boolean isExpired(long currentTime) {
         return currentTime - this.startTime >= this.totalDuration;
      }

      public long getRemainingTime() {
         long elapsed = System.currentTimeMillis() - this.startTime;
         return Math.max(0L, this.totalDuration - elapsed);
      }
   }

   public static class CompositeStats {
      public final int totalPunishments;
      public final int activePunishments;
      public final int availableTemplates;
      public final long lastUpdate;

      public CompositeStats(int totalPunishments, int activePunishments, int availableTemplates, long lastUpdate) {
         this.totalPunishments = totalPunishments;
         this.activePunishments = activePunishments;
         this.availableTemplates = availableTemplates;
         this.lastUpdate = lastUpdate;
      }
   }

   public static enum PunishmentChain {
      SEQUENTIAL,
      PARALLEL,
      CASCADE,
      RANDOM;
   }

   public static class PunishmentComponent {
      public final CompositePunishmentEngine.PunishmentType type;
      public final int intensity;
      public final long duration;
      public final long delay;

      public PunishmentComponent(CompositePunishmentEngine.PunishmentType type, int intensity, long duration) {
         this(type, intensity, duration, 0L);
      }

      public PunishmentComponent(CompositePunishmentEngine.PunishmentType type, int intensity, long duration, long delay) {
         this.type = type;
         this.intensity = intensity;
         this.duration = duration;
         this.delay = delay;
      }
   }

   public static class PunishmentTemplate {
      public final String name;
      public final List<CompositePunishmentEngine.PunishmentComponent> components;
      public final CompositePunishmentEngine.PunishmentChain chainType;

      public PunishmentTemplate(
         String name, List<CompositePunishmentEngine.PunishmentComponent> components, CompositePunishmentEngine.PunishmentChain chainType
      ) {
         this.name = name;
         this.components = components;
         this.chainType = chainType;
      }
   }

   public static enum PunishmentType {
      MESSAGE,
      MINING_DEBUFF,
      INVENTORY_RESTRICTION,
      MOVEMENT_DEBUFF,
      PARANOIA_EFFECT,
      VISUAL_EFFECT,
      SOUND_EFFECT;

      public static CompositePunishmentEngine.PunishmentType random() {
         return values()[new Random().nextInt(values().length)];
      }
   }
}
