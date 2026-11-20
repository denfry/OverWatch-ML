package net.denfry.owml.ml;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.protocol.PlayerProtocolData;
import net.denfry.owml.utils.MaterialHelper;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

public class PlayerDataCollector {
   private static final long MOVEMENT_RECORD_INTERVAL = 500L;
   private final Map<UUID, PlayerMiningData> playerDataMap = new ConcurrentHashMap<>();
   private final MLConfig mlConfig;
   private final Set<Material> rareMaterials = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> lastMovementRecordTime = new ConcurrentHashMap<>();

   public PlayerDataCollector(MLConfig mlConfig) {
      this.mlConfig = mlConfig;
      this.initRareMaterials();
   }

   private void initRareMaterials() {
      this.addMaterialIfExists("DIAMOND_ORE");
      this.addMaterialIfExists("DEEPSLATE_DIAMOND_ORE");
      this.addMaterialIfExists("ANCIENT_DEBRIS");
      this.addMaterialIfExists("EMERALD_ORE");
      this.addMaterialIfExists("DEEPSLATE_EMERALD_ORE");
   }

   private void addMaterialIfExists(String materialName) {
      Material material = MaterialHelper.getMaterial(materialName);
      if (material != null) {
         this.rareMaterials.add(material);
      }
   }

   public void startCollecting(Player player, boolean isLabeledCheater) {
      UUID playerId = player.getUniqueId();
      this.playerDataMap.put(playerId, new PlayerProtocolData(player.getName(), isLabeledCheater));
   }

   public PlayerMiningData stopCollecting(Player player) {
      return this.playerDataMap.remove(player.getUniqueId());
   }

   public void processBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      if (this.playerDataMap.containsKey(playerId)) {
         PlayerMiningData data = this.playerDataMap.get(playerId);
         Block block = event.getBlock();
         Material blockType = block.getType();
         Vector lookDirection = player.getLocation().getDirection();
         data.recordBlockBreak(block, lookDirection);
      }
   }

   public PlayerMiningData getPlayerData(UUID playerId) {
      return this.playerDataMap.get(playerId);
   }

   public PlayerProtocolData getPlayerProtocolData(UUID playerId) {
      PlayerMiningData data = this.playerDataMap.get(playerId);
      return data instanceof PlayerProtocolData ? (PlayerProtocolData)data : null;
   }

   public boolean isCollectingData(UUID playerId) {
      return this.playerDataMap.containsKey(playerId);
   }

   public void saveAllData() {
      for (PlayerMiningData data : this.playerDataMap.values()) {
         data.calculateDerivedFeatures();
         MLDataManager.savePlayerData(data);
      }
   }

   public void processPlayerMove(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.playerDataMap.containsKey(playerId)) {
         long currentTime = System.currentTimeMillis();
         long lastRecordTime = this.lastMovementRecordTime.getOrDefault(playerId, 0L);
         if (currentTime - lastRecordTime >= 500L) {
            this.lastMovementRecordTime.put(playerId, currentTime);
            PlayerMiningData data = this.playerDataMap.get(playerId);
            Vector position = player.getLocation().toVector();
            data.recordPlayerPosition(position);
         }
      }
   }
}
