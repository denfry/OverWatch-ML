package net.denfry.owml.commands.subcommands;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.AbstractSubCommand;
import net.denfry.owml.managers.AppealManager;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

public class AppealCommand extends AbstractSubCommand implements Listener {
   private final AppealManager appealManager;
   private final Map<UUID, Integer> awaitingReason = new ConcurrentHashMap<>();

   public AppealCommand(OverWatchML plugin) {
      super(plugin, "appeal", "owml.use", "Appeal your punishment", "/owml appeal");
      this.appealManager = plugin.getAppealManager();
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   @Override
   public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
      if (sender instanceof Player player) {
         UUID playerId = player.getUniqueId();
         int punishmentLevel = this.plugin.getPunishmentManager().getPlayerPunishmentLevel(playerId);
         if (punishmentLevel <= 0) {
            player.sendMessage(Component.text("You don't have any active punishments to appeal.").color(NamedTextColor.RED));
            return true;
         } else {
            AppealManager.Appeal existingAppeal = this.appealManager.getActiveAppealForPlayer(playerId);
            if (existingAppeal == null
               || existingAppeal.getStatus() != AppealManager.AppealStatus.PENDING && existingAppeal.getStatus() != AppealManager.AppealStatus.UNDER_REVIEW) {
               this.awaitingReason.put(playerId, punishmentLevel);
               player.sendMessage(Component.text("Please type your appeal reason in chat.").color(NamedTextColor.GOLD));
               return true;
            } else {
               player.sendMessage(Component.text("You already have a pending appeal.").color(NamedTextColor.YELLOW));
               return true;
            }
         }
      } else {
         sender.sendMessage("This command can only be used by players.");
         return true;
      }
   }

   @EventHandler
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.awaitingReason.containsKey(playerId)) {
         event.setCancelled(true);
         int punishmentLevel = this.awaitingReason.remove(playerId);
         String reason = event.getMessage();
         Schedulers.entity(player, () -> {
            this.appealManager.createAppeal(player, punishmentLevel, reason);
            player.sendMessage(Component.text("Appeal submitted!").color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.2F);
         });
      }
   }
}
