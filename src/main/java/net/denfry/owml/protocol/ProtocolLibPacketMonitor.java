package net.denfry.owml.protocol;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.PacketType.Play.Client;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.ml.PlayerDataCollector;
import org.bukkit.entity.Player;

public class ProtocolLibPacketMonitor implements PacketMonitor {
   private final OverWatchML plugin;
   private final ProtocolManager protocolManager;
   private final PlayerDataCollector dataCollector;
   private final Map<UUID, Boolean> inventoryOpenMap = new ConcurrentHashMap<>();
   private final Map<UUID, ProtocolLibPacketMonitor.PlayerActionTracker> playerActionTrackers = new ConcurrentHashMap<>();

   public ProtocolLibPacketMonitor(OverWatchML plugin, PlayerDataCollector dataCollector, ConfigManager configManager) {
      this.plugin = plugin;
      this.dataCollector = dataCollector;
      this.protocolManager = ProtocolLibrary.getProtocolManager();
      this.registerInventoryListeners();
      this.registerConcurrentActionListeners();
      plugin.getLogger().info("Advanced packet-based detection enabled with ProtocolLib.");
   }

   private void registerInventoryListeners() {
      this.protocolManager.addPacketListener(new PacketAdapter(this.plugin, Client.WINDOW_CLICK, Server.OPEN_WINDOW, Client.CLOSE_WINDOW) {
         public void onPacketReceiving(PacketEvent event) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            if (ProtocolLibPacketMonitor.this.dataCollector.isCollectingData(playerId)) {
               if (event.getPacketType() == Client.WINDOW_CLICK) {
                  ProtocolLibPacketMonitor.this.inventoryOpenMap.put(playerId, true);
                  ProtocolLibPacketMonitor.this.checkConcurrentActions(playerId, "INVENTORY_CLICK");
               } else if (event.getPacketType() == Client.CLOSE_WINDOW) {
                  ProtocolLibPacketMonitor.this.inventoryOpenMap.put(playerId, false);
               }
            }
         }

         public void onPacketSending(PacketEvent event) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            if (ProtocolLibPacketMonitor.this.dataCollector.isCollectingData(playerId)) {
               if (event.getPacketType() == Server.OPEN_WINDOW) {
                  ProtocolLibPacketMonitor.this.inventoryOpenMap.put(playerId, true);
               }
            }
         }
      });
   }

   private void registerConcurrentActionListeners() {
      this.protocolManager.addPacketListener(new PacketAdapter(this.plugin, Client.BLOCK_DIG) {
         public void onPacketReceiving(PacketEvent event) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            if (ProtocolLibPacketMonitor.this.dataCollector.isCollectingData(playerId)) {
               String actionType = null;
               if (event.getPacketType() == Client.BLOCK_DIG) {
                  int action = ((PlayerDigType)event.getPacket().getEnumModifier(PlayerDigType.class, 2).read(0)).ordinal();
                  if (action == 0 || action == 2) {
                     actionType = "DIGGING";
                     boolean inventoryOpen = ProtocolLibPacketMonitor.this.inventoryOpenMap.getOrDefault(playerId, false);
                     if (inventoryOpen) {
                        PlayerProtocolData protocolData = ProtocolLibPacketMonitor.this.dataCollector.getPlayerProtocolData(playerId);
                        if (protocolData != null) {
                           protocolData.recordConcurrentAction("INVENTORY_OPEN_WHILE_DIGGING");
                        }
                     }
                  }
               }

               if (actionType != null) {
                  ProtocolLibPacketMonitor.this.checkConcurrentActions(playerId, actionType);
               }
            }
         }
      });
      this.protocolManager.addPacketListener(new PacketAdapter(this.plugin, Client.CHAT) {
         public void onPacketReceiving(PacketEvent event) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            if (ProtocolLibPacketMonitor.this.dataCollector.isCollectingData(playerId)) {
               String message = (String)event.getPacket().getStrings().read(0);
               if (!message.startsWith("/")) {
                  ProtocolLibPacketMonitor.this.checkConcurrentActions(playerId, "CHATTING");
               }
            }
         }
      });
   }

   private void checkConcurrentActions(UUID playerId, String newAction) {
      ProtocolLibPacketMonitor.PlayerActionTracker tracker = this.playerActionTrackers
         .computeIfAbsent(playerId, k -> new ProtocolLibPacketMonitor.PlayerActionTracker());
      PlayerProtocolData protocolData = this.dataCollector.getPlayerProtocolData(playerId);
      if (protocolData != null) {
         Player player = this.plugin.getServer().getPlayer(playerId);
         String playerName = player != null ? player.getName() : playerId.toString();
         boolean wasDigging = tracker.isActionActive("DIGGING");
         boolean wasChatting = tracker.isActionActive("CHATTING");
         long timestamp = System.currentTimeMillis();
         boolean isDuplicate = tracker.addAction(newAction, timestamp);
         if (isDuplicate) {
            this.plugin.getLogger().fine("Skipping duplicate " + newAction + " packet from " + playerName);
         } else {
            boolean inventoryOpen = this.inventoryOpenMap.getOrDefault(playerId, false);
            if (inventoryOpen && newAction.equals("DIGGING")) {
               protocolData.recordConcurrentAction("INVENTORY_OPEN_WHILE_DIGGING");
            }

            if (newAction.equals("DIGGING") && wasChatting || newAction.equals("CHATTING") && wasDigging) {
               protocolData.recordConcurrentAction("CHATTING_WHILE_DIGGING");
            }
         }
      }
   }

   @Override
   public boolean isInventoryOpen(UUID playerId) {
      return this.inventoryOpenMap.getOrDefault(playerId, false);
   }

   @Override
   public void cleanupPlayer(UUID playerId) {
      this.inventoryOpenMap.remove(playerId);
      this.playerActionTrackers.remove(playerId);
   }

   @Override
   public void shutdown() {
      this.inventoryOpenMap.clear();
      this.playerActionTrackers.clear();
   }

   @Override
   public boolean isPlayerDigging(UUID playerId) {
      ProtocolLibPacketMonitor.PlayerActionTracker tracker = this.playerActionTrackers.get(playerId);
      return tracker != null && tracker.isActionActive("DIGGING");
   }

   private static class PlayerActionTracker {
      private static final long ACTION_EXPIRY_TIME = 350L;
      private static final long DUPLICATE_THRESHOLD = 100L;
      private final Map<String, Long> lastActionTimes = new ConcurrentHashMap<>();
      private final Map<String, Long> lastProcessedTimes = new ConcurrentHashMap<>();

      boolean addAction(String action, long timestamp) {
         Long lastProcessed = this.lastProcessedTimes.get(action);
         if (lastProcessed != null && timestamp - lastProcessed < 100L) {
            return true;
         } else {
            this.lastActionTimes.put(action, timestamp);
            this.lastProcessedTimes.put(action, timestamp);
            return false;
         }
      }

      boolean isActionActive(String action) {
         Long lastTime = this.lastActionTimes.get(action);
         return lastTime == null ? false : System.currentTimeMillis() - lastTime < 350L;
      }
   }
}
