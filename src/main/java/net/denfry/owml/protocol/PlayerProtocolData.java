package net.denfry.owml.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.denfry.owml.ml.PlayerMiningData;

public class PlayerProtocolData extends PlayerMiningData {
   private static final long ACTION_DEDUP_THRESHOLD = 500L;
   private final Map<String, Integer> concurrentActionCounts = new ConcurrentHashMap<>();
   private final Map<String, Long> lastActionRecordedTime = new ConcurrentHashMap<>();
   private Logger logger;

   public PlayerProtocolData(String playerName, boolean labeledAsCheater, Logger logger) {
      super(playerName, labeledAsCheater);
      this.logger = logger;
   }

   public PlayerProtocolData(UUID playerId, Logger logger) {
      super("Unknown", false);
      this.logger = logger;
   }

   public PlayerProtocolData(String playerName, boolean labeledAsCheater) {
      super(playerName, labeledAsCheater);
   }

   public PlayerProtocolData(UUID playerId) {
      super("Unknown", false);
   }

   public void recordConcurrentAction(String actionType) {
      long currentTime = System.currentTimeMillis();
      Long lastRecorded = this.lastActionRecordedTime.get(actionType);
      if (lastRecorded == null || currentTime - lastRecorded >= 500L) {
         this.lastActionRecordedTime.put(actionType, currentTime);
         this.concurrentActionCounts.put(actionType, this.concurrentActionCounts.getOrDefault(actionType, 0) + 1);
      }
   }

   public int getConcurrentActionCount(String actionType) {
      return this.concurrentActionCounts.getOrDefault(actionType, 0);
   }

   public Map<String, Integer> getAllConcurrentActionCounts() {
      return new HashMap<>(this.concurrentActionCounts);
   }

   @Override
   public void calculateDerivedFeatures() {
      super.calculateDerivedFeatures();
      Map<String, Double> features = this.getFeatures();

      for (Entry<String, Integer> entry : this.concurrentActionCounts.entrySet()) {
         features.put("concurrent_" + entry.getKey().toLowerCase(), (double)entry.getValue().intValue());
      }
   }
}
