package net.denfry.owml.protocol;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.ml.PlayerDataCollector;
import org.jetbrains.annotations.Nullable;

public final class PacketMonitors {
   private static final String IMPL = "net.denfry.owml.protocol.ProtocolLibPacketMonitor";

   private PacketMonitors() {
   }

   @Nullable
   public static PacketMonitor create(OverWatchML plugin, PlayerDataCollector collector, ConfigManager config) {
      if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
         plugin.getLogger().info("ProtocolLib не найден — пакетные проверки (инвентарь/чат во время копания) выключены.");
         return null;
      } else {
         try {
            Class<?> cls = Class.forName("net.denfry.owml.protocol.ProtocolLibPacketMonitor", true, PacketMonitors.class.getClassLoader());
            return (PacketMonitor)cls.getConstructor(OverWatchML.class, PlayerDataCollector.class, ConfigManager.class).newInstance(plugin, collector, config);
         } catch (Throwable var4) {
            plugin.getLogger().warning("ProtocolLib есть, но пакетный мониторинг не запустился: " + var4);
            return null;
         }
      }
   }
}
