package net.denfry.owml.listeners;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.managers.DecoyManager;
import net.denfry.owml.managers.PunishmentManager;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PunishmentListener implements Listener {
   private final PunishmentManager punishmentManager;
   private final OverWatchML plugin;
   private final DecoyManager decoyManager;
   private final ConfigManager configManager;

   public PunishmentListener(PunishmentManager punishmentManager, DecoyManager decoyManager, ConfigManager configManager, OverWatchML plugin) {
      this.punishmentManager = punishmentManager;
      this.decoyManager = decoyManager;
      this.configManager = configManager;
      this.plugin = plugin;
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (event.getClickedBlock() != null) {
         if (this.punishmentManager.hasStoneVision(playerId)) {
            Block block = event.getClickedBlock();
            if (this.isOre(block.getType())) {
               event.setCancelled(true);
               player.sendMessage(Component.text("This block appears to be stone, despite your suspicions...", NamedTextColor.YELLOW));
            }
         }
      }
   }

   @EventHandler
   public void onPlayerConsumeMilk(PlayerItemConsumeEvent event) {
      if (event.getItem().getType() == Material.MILK_BUCKET) {
         final Player player = event.getPlayer();
         final UUID playerId = player.getUniqueId();
         final int punishmentLevel = this.punishmentManager.getPlayerPunishmentLevel(playerId);
         if (punishmentLevel > 0) {
            final String funnyMessage = this.getFunnyMilkMessage(punishmentLevel);
            (new Schedulers.Repeating() {
               @Override
               public void run() {
                  if (player.isOnline()) {
                     player.sendMessage(Component.text(funnyMessage, NamedTextColor.GOLD));
                     switch (punishmentLevel) {
                        case 1:
                           if (PunishmentListener.this.configManager.isPunishmentOptionEnabled(1, "mining_fatigue")) {
                              player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 6000, 0));
                           }

                           if (PunishmentListener.this.configManager.isPunishmentOptionEnabled(1, "heavy_pickaxe")) {
                              player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 12000, 1));
                           }
                           break;
                        case 2:
                           if (PunishmentListener.this.configManager.isPunishmentOptionEnabled(2, "xray_vision_blur")) {
                              player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 6000, 0));
                           }
                     }

                     if (PunishmentListener.this.punishmentManager.hasPermanentMiningDebuff(playerId) && player.getLocation().getY() < 0.0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 400, 1));
                        player.sendMessage(Component.text("The mining debuff remains active below Y-level 0!", NamedTextColor.RED));
                     }

                     player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.7F);
                  }
               }
            }).entityLater(player, 2L);
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (player.hasMetadata("public_notification")) {
         player.removeMetadata("public_notification", this.plugin);
         Component message = ((TextComponent)Component.text("[OverWatch-ML] ", NamedTextColor.RED)
               .append(Component.text(player.getName(), NamedTextColor.YELLOW)))
            .append(Component.text(" has returned after being punished for suspicious mining behavior.", NamedTextColor.YELLOW));
         this.plugin.getServer().sendMessage(message);
      }

      UUID playerId = player.getUniqueId();
      Component reputation = this.punishmentManager.getMiningReputation(playerId);
      if (reputation != null && !reputation.toString().contains("Trusted Miner")) {
         player.sendMessage(Component.text("You are currently flagged as: ", NamedTextColor.RED).append(reputation));
      }

      if (this.punishmentManager.hasPermanentMiningDebuff(playerId)) {
         player.sendMessage(Component.text("You have a permanent mining debuff for ores when mining below Y-level 0.", NamedTextColor.RED));
      }

      if (player.hasMetadata("requires_staff_review")) {
         player.sendMessage(Component.text("You require staff approval to mine ores below Y-level 25!", NamedTextColor.RED));
      }
   }

   private String getFunnyMilkMessage(int level) {
      String[] commonMessages = new String[]{
         "Nice try! This milk is suspiciously chunky... your punishment remains.",
         "The OverWatchML system is lactose intolerant. Try again?",
         "Milk: It does a body good. Your punishment: Still in effect!",
         "Got Milk? Got Punishment too!",
         "Milk mustache detected! Punishment effects reinstated.",
         "That's not how you use the cow tools...",
         "The milk curdles as it touches your lips. Magic? No, just OverWatchML technology!",
         "The server admins anticipated your dairy-based escape plan!"
      };

      String[] levelSpecificMessages = switch (level) {
         case 1 -> new String[]{
            "Your pickaxe somehow feels HEAVIER after drinking that milk...",
            "The milk makes your arms feel weaker. Strange!",
            "The warnings stick to you better than that milk mustache."
         };
         case 2 -> new String[]{
            "Your vision blurs even MORE after that drink. Was that really milk?",
            "The milk curdles in your stomach. Your paranoia intensifies!",
            "Did you hear that hissing sound? Oh, it's just the milk... or is it?"
         };
         case 3 -> new String[]{
            "Your mining license is still suspended. Milk can't wash away bureaucracy!",
            "The Mining Authority does not accept milk as payment for your tax debt.",
            "The milk carton has your picture on it: 'WANTED FOR X-RAY VIOLATIONS'"
         };
         case 4 -> new String[]{
            "Your cursed pickaxe laughs at your milk-drinking attempt.",
            "The milk turns black as you drink it. Your pickaxe glows ominously.",
            "The restricted areas remain restricted. Milk isn't an access key!"
         };
         case 5 -> new String[]{
            "This milk has been fortified with extra mining fatigue!",
            "Your permanent debuff laughs at your dairy-based solution.",
            "The stone you see is still stone. Stone-flavored milk, maybe?"
         };
         case 6 -> new String[]{
            "Even this milk knows you're on your last chance.",
            "The milk explodes into TNT particles. That can't be good.",
            "The server is tracking your milk consumption habits now too!"
         };
         default -> new String[0];
      };
      return levelSpecificMessages.length > 0 && ThreadLocalRandom.current().nextInt(10) < 3
         ? levelSpecificMessages[ThreadLocalRandom.current().nextInt(levelSpecificMessages.length)]
         : commonMessages[ThreadLocalRandom.current().nextInt(commonMessages.length)];
   }

   boolean isOre(Material material) {
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
}
