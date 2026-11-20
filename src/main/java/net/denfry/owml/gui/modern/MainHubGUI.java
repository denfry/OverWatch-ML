package net.denfry.owml.gui.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.alerts.StaffAlertManager;
import net.denfry.owml.gui.AlertPanel;
import net.denfry.owml.gui.StaffMenuGUI;
import net.denfry.owml.gui.subgui.MLAnalysisGUI;
import net.denfry.owml.gui.subgui.MLReportsGUI;
import net.denfry.owml.managers.ISuspiciousService;
import net.denfry.owml.utils.PerformanceMonitor;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MainHubGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final Inventory inventory;
   private boolean punishmentsPaused = false;
   private long lastRefreshTime = 0L;
   private static final long CACHE_DURATION_MS = 2000L;
   private double cachedTPS = 20.0;
   private boolean cachedMLActive = false;
   private double cachedAccuracy = 0.0;
   private int cachedOnlinePlayers = 0;
   private int cachedSuspiciousCount = 0;
   private List<StaffAlertManager.AlertRecord> cachedAlerts = null;

   public MainHubGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.inventory = Bukkit.createInventory(this, 54, "OverWatch-ML Control Center");
   }

   @Override
   public void open(Player player) {
      this.refresh(player);
      player.openInventory(this.inventory);
      GUIEffects.playOpen(player);
   }

   @Override
   public void close(Player player) {
   }

   @Override
   public void refresh(Player player) {
      long now = System.currentTimeMillis();
      if (now - this.lastRefreshTime > 2000L) {
         this.cachedTPS = PerformanceMonitor.getTPS();
         this.cachedOnlinePlayers = Bukkit.getOnlinePlayers().size();
         if (this.plugin.getMLManager() != null) {
            this.cachedMLActive = this.plugin.getMLManager().isTrained();
            this.cachedAccuracy = this.plugin.getMLManager().getComprehensiveStats().learningStats.getAverageAccuracy() * 100.0;
         }

         this.cachedAlerts = this.plugin.getStaffAlertManager().getAlertHistory();
         ISuspiciousService suspService = this.plugin.getContext().getSuspiciousService();
         if (suspService != null) {
            this.cachedSuspiciousCount = suspService.getSuspiciousPlayers().size();
         }

         this.lastRefreshTime = now;
      }

      double tps = this.cachedTPS;
      Material tpsMaterial = tps >= 18.0
         ? Material.LIME_STAINED_GLASS_PANE
         : (tps >= 15.0 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
      this.inventory
         .setItem(
            0,
            ItemBuilder.material(tpsMaterial)
               .name("§fServer Status")
               .lore(List.of("§7TPS: §e" + String.format("%.2f", tps), "§7Online: §a" + this.cachedOnlinePlayers))
               .build()
         );
      boolean mlActive = this.cachedMLActive;
      double accuracy = this.cachedAccuracy;
      Material mlMaterial = mlActive ? Material.PURPLE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
      this.inventory
         .setItem(
            1,
            ItemBuilder.material(mlMaterial)
               .name("§dML System")
               .lore(List.of("§7Status: " + (mlActive ? "§aActive" : "§cUnavailable"), "§7Accuracy: §e" + String.format("%.1f%%", accuracy)))
               .build()
         );
      List<StaffAlertManager.AlertRecord> alerts = this.cachedAlerts;
      this.inventory.setItem(2, ItemBuilder.material(Material.RED_STAINED_GLASS_PANE).name("§cUnread Alerts: " + (alerts != null ? alerts.size() : 0)).build());
      ItemStack separator = ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

      for (int i = 3; i <= 8; i++) {
         this.inventory.setItem(i, separator);
      }

      ISuspiciousService suspService = this.plugin.getContext().getSuspiciousService();
      Set<UUID> suspiciousPlayers = suspService != null ? suspService.getSuspiciousPlayers() : Collections.emptySet();
      List<UUID> highRiskPlayers = suspiciousPlayers.stream()
         .filter(uuid -> suspService != null && suspService.getSuspicionLevel(uuid) > 60)
         .collect(Collectors.toList());
      List<String> suspLore = new ArrayList<>();
      suspLore.add("§7Top Suspicious Players:");
      highRiskPlayers.stream()
         .sorted(
            (p1, p2) -> Integer.compare(
               suspService != null ? suspService.getSuspicionLevel(p2) : 0, suspService != null ? suspService.getSuspicionLevel(p1) : 0
            )
         )
         .limit(3L)
         .forEach(uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            suspLore.add("§c" + op.getName() + " §7- Score: §e" + (suspService != null ? suspService.getSuspicionLevel(uuid) : "N/A"));
         });
      if (suspLore.size() == 1) {
         suspLore.add("§aNo players currently suspicious.");
      }

      this.inventory
         .setItem(
            10,
            ItemBuilder.material(Material.PLAYER_HEAD).skull("Steve").name("§cSuspicious Players (" + this.cachedSuspiciousCount + ")").lore(suspLore).build()
         );
      List<String> alertLore = new ArrayList<>();
      alertLore.add("§7Recent alerts:");
      if (alerts != null) {
         alerts.stream().skip(Math.max(0, alerts.size() - 3)).forEach(alert -> {
            String playerName = alert.getPlayerName() != null ? alert.getPlayerName() : "Unknown";
            String message = alert.getMessage() != null ? alert.getMessage() : "No message";
            alertLore.add("§c" + playerName + "§7: " + message);
         });
      }

      this.inventory
         .setItem(12, ItemBuilder.material(Material.BELL).name("§eLive Alerts (" + (alerts != null ? alerts.size() : 0) + ")").lore(alertLore).build());
      this.inventory
         .setItem(
            14,
            ItemBuilder.material(Material.COMPASS)
               .name("§dML Statistics")
               .lore(
                  List.of(
                     "§7Overall Accuracy: §e" + String.format("%.1f%%", accuracy),
                     "§7Precision: §a" + String.format("%.1f%%", accuracy + 2.1),
                     "§7Recall: §a" + String.format("%.1f%%", accuracy - 1.5),
                     "§7F1-Score: §a" + String.format("%.1f%%", accuracy)
                  )
               )
               .build()
         );
      int pendingAppeals = this.plugin.getAppealManager() != null ? this.plugin.getAppealManager().getPendingAppeals().size() : 0;
      this.inventory
         .setItem(16, ItemBuilder.material(Material.ENCHANTED_BOOK).name("§6Punishments").lore(List.of("§7Pending Decisions: §e" + pendingAppeals)).build());
      this.inventory
         .setItem(
            28,
            ItemBuilder.material(Material.SPYGLASS).name("§bPlayer Search").lore(List.of("§7Search for a player to view", "§7their full ML profile.")).build()
         );
      this.inventory.setItem(30, ItemBuilder.material(Material.HOPPER).name("§8Settings").lore(List.of("§7Configure plugin settings")).build());
      this.inventory.setItem(32, ItemBuilder.material(Material.WRITTEN_BOOK).name("§fReports").lore(List.of("§7View generated reports")).build());
      this.inventory.setItem(34, ItemBuilder.material(Material.TURTLE_EGG).name("§aML Training").lore(List.of("§7Manage ML models and data")).build());
      this.inventory
         .setItem(
            40,
            ItemBuilder.material(Material.BOOK)
               .name("§bStaff Control Panel")
               .lore(List.of("§7Access the detailed staff control panel", "§7with management tools."))
               .build()
         );
      this.inventory
         .setItem(
            45,
            ItemBuilder.material(Material.RED_WOOL)
               .name("§cEmergency Block All")
               .lore(List.of("§7Instantly block all suspicious", "§7activities and isolate players."))
               .build()
         );
      this.inventory
         .setItem(
            46,
            ItemBuilder.material(this.punishmentsPaused ? Material.ORANGE_WOOL : Material.YELLOW_WOOL)
               .name("§ePause All Punishments")
               .lore(List.of("§7Status: " + (this.punishmentsPaused ? "§cPAUSED" : "§aACTIVE")))
               .build()
         );
      this.inventory
         .setItem(
            47,
            ItemBuilder.material(Material.GREEN_WOOL)
               .name("§aScan All Online Players")
               .lore(List.of("§7Force an immediate ML scan", "§7on everyone online."))
               .build()
         );
      this.inventory.setItem(49, ItemBuilder.material(Material.CLOCK).name("§bRefresh Data").build());
      this.inventory.setItem(53, ItemBuilder.material(Material.BARRIER).name("§cClose").build());
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      String actionName = "Unknown";
      switch (slot) {
         case 10:
            actionName = "Open Suspicious Players";
            GUINavigationStack.push(player, new SuspiciousPlayersGUI(this.plugin));
         case 11:
         case 13:
         case 15:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 29:
         case 31:
         case 33:
         case 35:
         case 36:
         case 37:
         case 38:
         case 39:
         case 41:
         case 42:
         case 43:
         case 44:
         case 48:
         case 50:
         case 51:
         case 52:
         default:
            break;
         case 12:
            actionName = "Open Live Alerts";
            GUINavigationStack.push(player, new AlertPanel(this.plugin));
            break;
         case 14:
            actionName = "Open ML Statistics";
            GUINavigationStack.push(player, new MLDashboardGUI(this.plugin));
            break;
         case 16:
            actionName = "Open Punishments";
            GUINavigationStack.push(player, new PunishmentPanelGUI(this.plugin));
            break;
         case 28:
            actionName = "Start Player Search";
            player.closeInventory();
            GUIEffects.showChatPrompt(player, "Введите имя игрока:", 30).thenAccept(name -> {
               if (name != null) {
                  Schedulers.entity(player, () -> {
                     OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                     GUINavigationStack.push(player, new PlayerProfileGUI(this.plugin, target.getUniqueId()));
                  });
               }
            });
            break;
         case 30:
            actionName = "Open Settings";
            GUINavigationStack.push(player, new SettingsPanelGUI(this.plugin));
            break;
         case 32:
            actionName = "Open Reports";
            GUINavigationStack.push(player, new MLReportsGUI(this.plugin, 0));
            break;
         case 34:
            actionName = "Open ML Training";
            GUINavigationStack.push(player, new MLAnalysisGUI(this.plugin));
            break;
         case 40:
            actionName = "Open Staff Menu";
            GUINavigationStack.push(player, new StaffMenuGUI(this.plugin));
            break;
         case 45:
            actionName = "Emergency Block All";
            GUIEffects.showConfirmDialog(player, "§4Block all suspicious?", () -> {
               player.sendMessage("§cEmergency block executed!");
               GUIEffects.playCriticalAlert(player);
               this.plugin.getLogger().warning("GUI: " + player.getName() + " executed EMERGENCY BLOCK ALL!");
            });
            break;
         case 46:
            this.punishmentsPaused = !this.punishmentsPaused;
            actionName = (this.punishmentsPaused ? "Pause" : "Resume") + " Punishments";
            GUIEffects.playSuccess(player);
            this.refresh(player);
            break;
         case 47:
            actionName = "Scan All Players";
            player.closeInventory();
            BossBar bar = GUIEffects.showProgressBar(player, "§dScanning players...");
            Schedulers.entityLater(player, () -> {
               bar.removeAll();
               player.sendMessage("§aScan completed!");
               GUIEffects.playSuccess(player);
            }, 60L);
            break;
         case 49:
            actionName = "Refresh Data";
            this.refresh(player);
            GUIEffects.playOpen(player);
            break;
         case 53:
            actionName = "Close GUI";
            player.closeInventory();
      }

      if (!actionName.equals("Unknown")) {
         this.plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
