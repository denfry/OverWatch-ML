package net.denfry.owml.managers;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AppealManager implements Listener {
   private final OverWatchML plugin;
   private final File appealFile;
   private final Map<Integer, AppealManager.Appeal> appealById = new ConcurrentHashMap<>();
   private final Map<UUID, List<AppealManager.Appeal>> appealsByPlayer = new ConcurrentHashMap<>();
   private int nextAppealId = 1;

   public AppealManager(OverWatchML plugin) {
      this.plugin = plugin;
      this.appealFile = new File(plugin.getDataFolder(), "appeals.yml");
      plugin.getServer().getPluginManager().registerEvents(this, plugin);
      this.loadAppeals();
   }

   public AppealManager.Appeal createAppeal(Player player, int punishmentLevel, String reason) {
      UUID playerId = player.getUniqueId();

      for (AppealManager.Appeal existingAppeal : this.getAppealsForPlayer(playerId)) {
         if ((existingAppeal.getStatus() == AppealManager.AppealStatus.PENDING || existingAppeal.getStatus() == AppealManager.AppealStatus.UNDER_REVIEW)
            && existingAppeal.getPunishmentLevel() == punishmentLevel) {
            return existingAppeal;
         }
      }

      int appealId = this.getNextAppealId();
      AppealManager.Appeal appeal = new AppealManager.Appeal(appealId, playerId, player.getName(), punishmentLevel, reason);
      this.appealById.put(appealId, appeal);
      this.appealsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(appeal);
      this.saveAppeals();
      if (this.plugin.getConfigManager().isWebhookAlertEnabled("appeal_updates")) {
         this.plugin.getWebhookManager().sendNewAppealAlert(appealId, player.getName(), punishmentLevel, reason);
      }

      this.notifyStaffAboutNewAppeal(appeal);
      return appeal;
   }

   private synchronized int getNextAppealId() {
      return this.nextAppealId++;
   }

   public AppealManager.Appeal getAppeal(int appealId) {
      return this.appealById.get(appealId);
   }

   public List<AppealManager.Appeal> getAppealsForPlayer(UUID playerId) {
      return this.appealsByPlayer.getOrDefault(playerId, new ArrayList<>());
   }

   public AppealManager.Appeal getActiveAppealForPlayer(UUID playerId) {
      List<AppealManager.Appeal> appeals = this.getAppealsForPlayer(playerId);
      int currentPunishmentLevel = this.plugin.getPunishmentManager().getPlayerPunishmentLevel(playerId);

      for (AppealManager.Appeal appeal : appeals) {
         if ((appeal.getStatus() == AppealManager.AppealStatus.PENDING || appeal.getStatus() == AppealManager.AppealStatus.UNDER_REVIEW)
            && appeal.getPunishmentLevel() == currentPunishmentLevel) {
            return appeal;
         }
      }

      return null;
   }

   public List<AppealManager.Appeal> getPendingAppeals() {
      return this.appealById
         .values()
         .stream()
         .filter(appeal -> appeal.getStatus() == AppealManager.AppealStatus.PENDING || appeal.getStatus() == AppealManager.AppealStatus.UNDER_REVIEW)
         .collect(Collectors.toList());
   }

   public void updateAppealStatus(int appealId, AppealManager.AppealStatus newStatus, String staffName, String response) {
      AppealManager.Appeal appeal = this.appealById.get(appealId);
      if (appeal != null) {
         appeal.setStatus(newStatus);
         appeal.setStaffName(staffName);
         appeal.setStaffResponse(response);
         this.saveAppeals();
         if (this.plugin.getConfigManager().isWebhookAlertEnabled("appeal_updates")) {
            this.plugin
               .getWebhookManager()
               .sendAppealStatusAlert(appealId, appeal.getPlayerName(), appeal.getPunishmentLevel(), newStatus, staffName, response);
         }

         if (newStatus == AppealManager.AppealStatus.APPROVED) {
            Player player = Bukkit.getPlayer(appeal.getPlayerId());
            if (player != null && player.isOnline()) {
               this.plugin.getPunishmentManager().removePunishment(player);
               player.sendMessage(Component.text("Your appeal has been approved! Your punishment has been removed.").color(NamedTextColor.GREEN));
               player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
            }
         } else if (newStatus == AppealManager.AppealStatus.DENIED) {
            Player player = Bukkit.getPlayer(appeal.getPlayerId());
            if (player != null && player.isOnline()) {
               player.sendMessage(Component.text("Your appeal has been denied.").color(NamedTextColor.RED));
               if (!response.isEmpty()) {
                  player.sendMessage(Component.text("Reason: " + response).color(NamedTextColor.YELLOW));
               }

               player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.8F);
            }
         } else if (newStatus == AppealManager.AppealStatus.UNDER_REVIEW) {
            Player player = Bukkit.getPlayer(appeal.getPlayerId());
            if (player != null && player.isOnline()) {
               player.sendMessage(Component.text("Your appeal is now under review by staff.").color(NamedTextColor.AQUA));
               player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.2F);
            }
         }
      }
   }

   private void notifyStaffAboutNewAppeal(AppealManager.Appeal appeal) {
      Component message = ((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("[").color(NamedTextColor.DARK_GRAY))
                     .append(Component.text("OverWatch-ML").color(NamedTextColor.RED)))
                  .append(Component.text("] ").color(NamedTextColor.DARK_GRAY)))
               .append(Component.text("New appeal #" + appeal.getId() + " from ").color(NamedTextColor.YELLOW)))
            .append(Component.text(appeal.getPlayerName()).color(NamedTextColor.AQUA)))
         .append(Component.text(" (Level " + appeal.getPunishmentLevel() + ")").color(NamedTextColor.YELLOW));

      for (Player staff : Bukkit.getOnlinePlayers()) {
         if (staff.hasPermission("owml.staff")) {
            staff.sendMessage(message);
            staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7F, 1.2F);
         }
      }
   }

   @EventHandler
   public void onStaffJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("owml.staff")) {
         List<AppealManager.Appeal> pendingAppeals = this.getPendingAppeals();
         if (!pendingAppeals.isEmpty()) {
            Schedulers.entityLater(
               player,
               () -> {
                  player.sendMessage(Component.text("╔═════════════════════════════╗").color(NamedTextColor.AQUA));
                  player.sendMessage(
                     ((TextComponent)((TextComponent)Component.text("║ ").color(NamedTextColor.AQUA))
                           .append(((TextComponent)Component.text("OverWatchML Appeal System").color(NamedTextColor.YELLOW)).decorate(TextDecoration.BOLD)))
                        .append(Component.text(" ║").color(NamedTextColor.AQUA))
                  );
                  player.sendMessage(
                     ((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text("║ ")
                                          .color(NamedTextColor.AQUA))
                                       .append(Component.text("There ").color(NamedTextColor.WHITE)))
                                    .append(Component.text(pendingAppeals.size() == 1 ? "is " : "are ").color(NamedTextColor.WHITE)))
                                 .append(Component.text(pendingAppeals.size()).color(NamedTextColor.GOLD)))
                              .append(Component.text(" pending ").color(NamedTextColor.WHITE)))
                           .append(Component.text(pendingAppeals.size() == 1 ? "appeal" : "appeals").color(NamedTextColor.WHITE)))
                        .append(Component.text(" ║").color(NamedTextColor.AQUA))
                  );
                  player.sendMessage(
                     ((TextComponent)((TextComponent)Component.text("║ ").color(NamedTextColor.AQUA))
                           .append(Component.text("Check the Staff GUI to review them").color(NamedTextColor.YELLOW)))
                        .append(Component.text(" ║").color(NamedTextColor.AQUA))
                  );
                  player.sendMessage(Component.text("╚═════════════════════════════╝").color(NamedTextColor.AQUA));
                  player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7F, 1.2F);
               },
               60L
            );
         }
      }
   }

   private void loadAppeals() {
      if (this.appealFile.exists()) {
         FileConfiguration config = YamlConfiguration.loadConfiguration(this.appealFile);
         ConfigurationSection appealsSection = config.getConfigurationSection("appeals");
         if (appealsSection != null) {
            this.appealById.clear();
            this.appealsByPlayer.clear();
            int highestId = 0;

            for (String key : appealsSection.getKeys(false)) {
               ConfigurationSection appealSection = appealsSection.getConfigurationSection(key);
               if (appealSection != null) {
                  int id = Integer.parseInt(key);
                  highestId = Math.max(highestId, id);
                  UUID playerId = UUID.fromString(appealSection.getString("playerId"));
                  String playerName = appealSection.getString("playerName");
                  int punishmentLevel = appealSection.getInt("punishmentLevel");
                  String reason = appealSection.getString("reason");
                  long timestamp = appealSection.getLong("timestamp");
                  AppealManager.AppealStatus status = AppealManager.AppealStatus.fromString(appealSection.getString("status"));
                  String staffResponse = appealSection.getString("staffResponse", "");
                  String staffName = appealSection.getString("staffName", "");
                  AppealManager.Appeal appeal = new AppealManager.Appeal(
                     id, playerId, playerName, punishmentLevel, reason, timestamp, status, staffResponse, staffName
                  );
                  this.appealById.put(id, appeal);
                  this.appealsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(appeal);
               }
            }

            this.nextAppealId = highestId + 1;
         }
      }
   }

   private void saveAppeals() {
      FileConfiguration config = new YamlConfiguration();
      ConfigurationSection appealsSection = config.createSection("appeals");

      for (AppealManager.Appeal appeal : this.appealById.values()) {
         ConfigurationSection appealSection = appealsSection.createSection(String.valueOf(appeal.getId()));
         appealSection.set("playerId", appeal.getPlayerId().toString());
         appealSection.set("playerName", appeal.getPlayerName());
         appealSection.set("punishmentLevel", appeal.getPunishmentLevel());
         appealSection.set("reason", appeal.getReason());
         appealSection.set("timestamp", appeal.getTimestamp());
         appealSection.set("status", appeal.getStatus().name());
         appealSection.set("staffResponse", appeal.getStaffResponse());
         appealSection.set("staffName", appeal.getStaffName());
      }

      try {
         config.save(this.appealFile);
      } catch (IOException var6) {
         this.plugin.getLogger().severe("Failed to save appeals data: " + var6.getMessage());
      }
   }

   public static class Appeal {
      private final int id;
      private final UUID playerId;
      private final String playerName;
      private final int punishmentLevel;
      private final String reason;
      private final long timestamp;
      private AppealManager.AppealStatus status;
      private String staffResponse;
      private String staffName;

      public Appeal(int id, UUID playerId, String playerName, int punishmentLevel, String reason) {
         this.id = id;
         this.playerId = playerId;
         this.playerName = playerName;
         this.punishmentLevel = punishmentLevel;
         this.reason = reason;
         this.timestamp = System.currentTimeMillis();
         this.status = AppealManager.AppealStatus.PENDING;
         this.staffResponse = "";
         this.staffName = "";
      }

      public Appeal(
         int id,
         UUID playerId,
         String playerName,
         int punishmentLevel,
         String reason,
         long timestamp,
         AppealManager.AppealStatus status,
         String staffResponse,
         String staffName
      ) {
         this.id = id;
         this.playerId = playerId;
         this.playerName = playerName;
         this.punishmentLevel = punishmentLevel;
         this.reason = reason;
         this.timestamp = timestamp;
         this.status = status;
         this.staffResponse = staffResponse;
         this.staffName = staffName;
      }

      public int getId() {
         return this.id;
      }

      public UUID getPlayerId() {
         return this.playerId;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public int getPunishmentLevel() {
         return this.punishmentLevel;
      }

      public String getReason() {
         return this.reason;
      }

      public long getTimestamp() {
         return this.timestamp;
      }

      public AppealManager.AppealStatus getStatus() {
         return this.status;
      }

      public void setStatus(AppealManager.AppealStatus status) {
         this.status = status;
      }

      public String getStaffResponse() {
         return this.staffResponse;
      }

      public void setStaffResponse(String staffResponse) {
         this.staffResponse = staffResponse;
      }

      public String getStaffName() {
         return this.staffName;
      }

      public void setStaffName(String staffName) {
         this.staffName = staffName;
      }

      public String getFormattedTimestamp() {
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
         return sdf.format(new Date(this.timestamp));
      }
   }

   public static enum AppealStatus {
      PENDING("Pending", TextColor.color(255, 215, 0)),
      APPROVED("Approved", TextColor.color(50, 205, 50)),
      DENIED("Denied", TextColor.color(255, 0, 0)),
      UNDER_REVIEW("Under Review", TextColor.color(30, 144, 255));

      private final String displayName;
      private final TextColor color;

      private AppealStatus(String displayName, TextColor color) {
         this.displayName = displayName;
         this.color = color;
      }

      public static AppealManager.AppealStatus fromString(String statusStr) {
         try {
            return valueOf(statusStr.toUpperCase());
         } catch (IllegalArgumentException var2) {
            return PENDING;
         }
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public TextColor getColor() {
         return this.color;
      }
   }
}
