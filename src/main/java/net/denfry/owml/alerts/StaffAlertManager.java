package net.denfry.owml.alerts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StaffAlertManager {
   private final JavaPlugin plugin;
   private final ConfigManager configManager;
   private final Set<UUID> disabledAlerts = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private final Map<UUID, Map<Material, StaffAlertManager.OreCounter>> playerOreCounters = new ConcurrentHashMap<>();
   private final List<StaffAlertManager.AlertRecord> alertHistory = Collections.synchronizedList(new ArrayList<>());
   private static final int MAX_HISTORY_SIZE = 100;

   public StaffAlertManager(JavaPlugin plugin, ConfigManager configManager) {
      this.plugin = plugin;
      this.configManager = configManager;
   }

   public boolean toggleOreAlert(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.disabledAlerts.contains(playerId)) {
         this.disabledAlerts.remove(playerId);
         return false;
      } else {
         this.disabledAlerts.add(playerId);
         return true;
      }
   }

   public boolean areAlertsDisabled(Player player) {
      return this.disabledAlerts.contains(player.getUniqueId());
   }

   public void alertStaffWithTeleport(Player player, Location location, String message) {
      this.alertHistory.add(new StaffAlertManager.AlertRecord(message, location.clone(), player.getName()));
      if (this.alertHistory.size() > 100) {
         this.alertHistory.remove(0);
      }

      Component alertMessage = ((Builder)((Builder)((Builder)Component.text()
                  .append(Component.text("[OverWatch-ML] ", NamedTextColor.GOLD, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text(message, NamedTextColor.YELLOW)))
            .append(
               ((TextComponent)Component.text(" [TELEPORT]", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})
                     .clickEvent(
                        ClickEvent.runCommand("/tp " + player.getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ())
                     ))
                  .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to location", NamedTextColor.GRAY)))
            ))
         .build();

      for (Player staff : Bukkit.getOnlinePlayers()) {
         if (staff.hasPermission("owml.staff") && !this.areAlertsDisabled(staff)) {
            staff.sendMessage(alertMessage);
         }
      }

      if (this.plugin instanceof OverWatchML ow && ow.getMiningTelemetry() != null) {
         ow.getMiningTelemetry().recordAlert("alert", player.getName(), location, message);
      }

      this.plugin
         .getLogger()
         .info(
            "[ALERT] "
               + message
               + " at "
               + location.getWorld().getName()
               + " "
               + location.getBlockX()
               + ","
               + location.getBlockY()
               + ","
               + location.getBlockZ()
         );
   }

   public void logDecoyEvent(String message) {
      this.plugin.getLogger().info("[DECOY] " + message);
   }

   public void updateStaffOreCounter(Player player, Material ore, Location location) {
      UUID playerId = player.getUniqueId();
      Map<Material, StaffAlertManager.OreCounter> oreCounters = this.playerOreCounters.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
      StaffAlertManager.OreCounter counter = oreCounters.computeIfAbsent(ore, k -> new StaffAlertManager.OreCounter());
      counter.increment();
      Component counterMessage = ((Builder)((Builder)((Builder)((Builder)((Builder)Component.text().append(Component.text("[COUNTER] ", NamedTextColor.AQUA)))
                     .append(Component.text(player.getName(), NamedTextColor.YELLOW)))
                  .append(Component.text(" mined ", NamedTextColor.GRAY)))
               .append(Component.text(counter.getCount() + "x ", NamedTextColor.GREEN)))
            .append(Component.text(this.formatMaterialName(ore), NamedTextColor.WHITE)))
         .build();

      for (Player staff : Bukkit.getOnlinePlayers()) {
         if (staff.hasPermission("owml.staff") && !this.areAlertsDisabled(staff)) {
            staff.sendMessage(counterMessage);
         }
      }
   }

   public int getOreCount(UUID playerId, Material ore) {
      Map<Material, StaffAlertManager.OreCounter> oreCounters = this.playerOreCounters.get(playerId);
      if (oreCounters == null) {
         return 0;
      } else {
         StaffAlertManager.OreCounter counter = oreCounters.get(ore);
         return counter != null ? counter.getCount() : 0;
      }
   }

   public Map<Material, StaffAlertManager.OreCounter> getAllOreCounters(UUID playerId) {
      return this.playerOreCounters.getOrDefault(playerId, Collections.emptyMap());
   }

   public void clearOreCounters(UUID playerId) {
      this.playerOreCounters.remove(playerId);
   }

   public List<StaffAlertManager.AlertRecord> getAlertHistory() {
      synchronized (this.alertHistory) {
         return new ArrayList<>(this.alertHistory);
      }
   }

   public void clearAlertHistory() {
      this.alertHistory.clear();
   }

   public void clearAllOreCounters() {
      this.playerOreCounters.clear();
   }

   public int getDisabledAlertsCount() {
      return this.disabledAlerts.size();
   }

   private String formatMaterialName(Material material) {
      return material.name().toLowerCase().replace("_", " ");
   }

   public static class AlertRecord {
      private final String message;
      private final Location location;
      private final String playerName;
      private final long timestamp;

      public AlertRecord(String message, Location location, String playerName) {
         this.message = message;
         this.location = location;
         this.playerName = playerName;
         this.timestamp = System.currentTimeMillis();
      }

      public String getMessage() {
         return this.message;
      }

      public Location getLocation() {
         return this.location;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public long getTimestamp() {
         return this.timestamp;
      }
   }

   public static class OreCounter {
      private int count = 0;

      public void increment() {
         this.count++;
      }

      public int getCount() {
         return this.count;
      }

      public void reset() {
         this.count = 0;
      }
   }
}
