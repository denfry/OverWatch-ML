package net.denfry.owml.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.denfry.owml.OverWatchML;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputHandler implements Listener {
   private final OverWatchML plugin;
   private final Map<UUID, Function<String, Boolean>> chatInputHandlers = new ConcurrentHashMap<>();

   public ChatInputHandler(OverWatchML plugin) {
      this.plugin = plugin;
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
   }

   public void registerChatInputHandler(UUID playerId, Function<String, Boolean> handler) {
      this.chatInputHandlers.put(playerId, handler);
   }

   public void unregisterChatInputHandler(UUID playerId) {
      this.chatInputHandlers.remove(playerId);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();
      if (this.chatInputHandlers.containsKey(playerId)) {
         Function<String, Boolean> handler = this.chatInputHandlers.get(playerId);
         String message = event.getMessage();
         if (message.contains("discord.com/api/webhooks/") || message.contains("discordapp.com/api/webhooks/")) {
            event.setCancelled(true);
            boolean consumeEvent = handler.apply(message);
            if (consumeEvent) {
               this.unregisterChatInputHandler(playerId);
            }

            return;
         }

         boolean consumeEvent = handler.apply(message);
         if (consumeEvent) {
            event.setCancelled(true);
            this.unregisterChatInputHandler(playerId);
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.unregisterChatInputHandler(event.getPlayer().getUniqueId());
   }
}
