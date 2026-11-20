package net.denfry.owml.protocol;

import java.util.UUID;

public interface PacketMonitor {
   boolean isInventoryOpen(UUID var1);

   boolean isPlayerDigging(UUID var1);

   void cleanupPlayer(UUID var1);

   void shutdown();
}
