package net.denfry.owml.gui.modern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.PlayerBehaviorProfile;
import net.denfry.owml.managers.ISuspiciousService;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class SuspiciousPlayersGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final Inventory inventory;
   private int currentPage = 0;
   private SuspiciousPlayersGUI.TypeFilter typeFilter = SuspiciousPlayersGUI.TypeFilter.ALL;
   private SuspiciousPlayersGUI.StatusFilter statusFilter = SuspiciousPlayersGUI.StatusFilter.ALL;
   private SuspiciousPlayersGUI.SortType sortType = SuspiciousPlayersGUI.SortType.SCORE_DESC;
   private String searchQuery = "";

   public SuspiciousPlayersGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.inventory = Bukkit.createInventory(this, 54, "Suspicious Players List");
   }

   public Inventory getInventory() {
      return this.inventory;
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
      this.inventory.clear();
      List<UUID> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(OfflinePlayer::getUniqueId).toList());
      Map<UUID, Double> playerScores = new HashMap<>();
      Map<UUID, String> mainReasons = new HashMap<>();

      for (UUID uuid : players) {
         PlayerBehaviorProfile profile = this.plugin.getContext().getProfileManager().getProfile(uuid);
         double maxScore = 0.0;
         String mainCat = "None";

         for (Entry<String, Double> entry : profile.getDetectionScores().entrySet()) {
            if (entry.getValue() > maxScore) {
               maxScore = entry.getValue();
               mainCat = entry.getKey().toUpperCase();
            }
         }

         playerScores.put(uuid, maxScore);
         mainReasons.put(uuid, mainCat);
      }

      players = players.stream().filter(uuidx -> {
         OfflinePlayer opx = Bukkit.getOfflinePlayer(uuidx);
         if (this.searchQuery.isEmpty() || opx.getName() != null && opx.getName().toLowerCase().contains(this.searchQuery.toLowerCase())) {
            double score = playerScores.getOrDefault(uuidx, 0.0);
            return !(score < 0.1) || this.statusFilter == SuspiciousPlayersGUI.StatusFilter.ALL;
         } else {
            return false;
         }
      }).collect(Collectors.toList());
      players.sort((p1, p2) -> {
         switch (this.sortType) {
            case SCORE_DESC:
            case RECENT_ALERT:
            default:
               return Double.compare(playerScores.get(p2), playerScores.get(p1));
            case SCORE_ASC:
               return Double.compare(playerScores.get(p1), playerScores.get(p2));
            case NAME:
               return Bukkit.getOfflinePlayer(p1).getName().compareToIgnoreCase(Bukkit.getOfflinePlayer(p2).getName());
         }
      });
      int totalPages = (int)Math.ceil(players.size() / 45.0);
      if (this.currentPage >= totalPages && totalPages > 0) {
         this.currentPage = totalPages - 1;
      }

      int start = this.currentPage * 45;
      int end = Math.min(start + 45, players.size());

      for (int i = 0; i < end - start; i++) {
         UUID uuid = players.get(start + i);
         OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
         double score = playerScores.getOrDefault(uuid, 0.0);
         String scoreColor = score > 0.8 ? "§c" : (score > 0.5 ? "§6" : "§a");
         this.inventory
            .setItem(
               i,
               ItemBuilder.material(Material.PLAYER_HEAD)
                  .skull(op.getName())
                  .name(scoreColor + op.getName() + " §7(" + String.format("%.0f%%", score * 100.0) + ")")
                  .lore(
                     List.of(
                        "§7Status: " + (op.isOnline() ? "§aOnline" : "§7Offline"),
                        "§7Primary Issue: " + scoreColor + mainReasons.get(uuid),
                        "§7Last Analysis: §fJust now",
                        "",
                        "§eLeft-Click: §7Full Profile",
                        "§eRight-Click: §7Quick Actions",
                        "§eShift-Click: §7Teleport"
                     )
                  )
                  .build()
            );
      }

      this.inventory
         .setItem(
            45,
            ItemBuilder.material(Material.HOPPER)
               .name("§bFilter Type: §f" + this.typeFilter)
               .lore(List.of("§7Click to cycle", "§7Shift-Click to go BACK"))
               .build()
         );
      this.inventory.setItem(46, ItemBuilder.material(Material.COMPARATOR).name("§bStatus Filter: §f" + this.statusFilter).build());
      this.inventory.setItem(47, ItemBuilder.material(Material.LECTERN).name("§bSort: §f" + this.sortType).build());
      this.inventory
         .setItem(49, ItemBuilder.material(Material.SPYGLASS).name("§fSearch: " + (this.searchQuery.isEmpty() ? "§7None" : "§e" + this.searchQuery)).build());
      if (this.currentPage > 0) {
         this.inventory.setItem(51, ItemBuilder.material(Material.ARROW).name("§7Previous Page").build());
      } else {
         this.inventory.setItem(51, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
      }

      this.inventory.setItem(52, ItemBuilder.material(Material.PAPER).name("§fPage " + (this.currentPage + 1) + " / " + Math.max(1, totalPages)).build());
      if (this.currentPage < totalPages - 1) {
         this.inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Next Page").build());
      } else {
         this.inventory.setItem(53, ItemBuilder.material(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
      }
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      String actionName = "Unknown";
      if (slot < 45) {
         int index = this.currentPage * 45 + slot;
         ISuspiciousService service = this.plugin.getContext().getSuspiciousService();
         List<UUID> players = new ArrayList<>(service.getSuspiciousPlayers());
         if (index < players.size()) {
            UUID targetId = players.get(index);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            if (event.isShiftClick()) {
               actionName = "Teleport to " + target.getName();
               if (target.isOnline()) {
                  player.teleportAsync(target.getPlayer().getLocation());
                  player.sendMessage("§aTeleported to " + target.getName());
               }
            } else if (event.isLeftClick()) {
               actionName = "Open Profile for " + target.getName();
               GUINavigationStack.push(player, new PlayerProfileGUI(this.plugin, targetId));
            } else if (event.isRightClick()) {
               actionName = "Open Quick Actions for " + target.getName();
               this.openQuickActionMenu(player, targetId);
            }

            if (!actionName.equals("Unknown")) {
               this.plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
            }
         }
      } else {
         switch (slot) {
            case 45:
               if (event.isShiftClick()) {
                  actionName = "Go Back";
                  GUINavigationStack.pop(player);
               } else {
                  this.typeFilter = SuspiciousPlayersGUI.TypeFilter.values()[(this.typeFilter.ordinal() + 1) % SuspiciousPlayersGUI.TypeFilter.values().length];
                  actionName = "Cycle Type Filter: " + this.typeFilter;
                  this.refresh(player);
               }
               break;
            case 46:
               this.statusFilter = SuspiciousPlayersGUI.StatusFilter.values()[(this.statusFilter.ordinal() + 1)
                  % SuspiciousPlayersGUI.StatusFilter.values().length];
               actionName = "Cycle Status Filter: " + this.statusFilter;
               this.refresh(player);
               break;
            case 47:
               this.sortType = SuspiciousPlayersGUI.SortType.values()[(this.sortType.ordinal() + 1) % SuspiciousPlayersGUI.SortType.values().length];
               actionName = "Cycle Sort Type: " + this.sortType;
               this.refresh(player);
            case 48:
            case 50:
            case 52:
            default:
               break;
            case 49:
               actionName = "Open Search Prompt";
               player.closeInventory();
               GUIEffects.showChatPrompt(player, "Enter player name to search (or 'cancel' to reset):", 30)
                  .thenAccept(name -> Schedulers.entity(player, () -> {
                     this.searchQuery = name != null && !name.isEmpty() ? name : "";
                     this.currentPage = 0;
                     this.open(player);
                  }));
               break;
            case 51:
               if (this.currentPage > 0) {
                  this.currentPage--;
                  actionName = "Previous Page (" + (this.currentPage + 1) + ")";
                  this.refresh(player);
               }
               break;
            case 53:
               this.currentPage++;
               actionName = "Next Page (" + (this.currentPage + 1) + ")";
               this.refresh(player);
         }

         if (!actionName.equals("Unknown")) {
            this.plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
         }
      }
   }

   private void openQuickActionMenu(final Player player, final UUID targetId) {
      final OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
      OverWatchGUI quickMenu = new OverWatchGUI() {
         private final Inventory inv = Bukkit.createInventory(this, 27, "Quick Actions: " + target.getName());

         @Override
         public void open(Player p) {
            this.inv.setItem(10, ItemBuilder.material(Material.ENDER_EYE).name("§3Watch").build());
            this.inv.setItem(11, ItemBuilder.material(Material.YELLOW_DYE).name("§eWarn").build());
            this.inv.setItem(12, ItemBuilder.material(Material.RED_DYE).name("§cPunish").build());
            this.inv.setItem(13, ItemBuilder.material(Material.LIME_DYE).name("§aMark Legit").build());
            this.inv.setItem(14, ItemBuilder.material(Material.PLAYER_HEAD).skull(target.getName()).name("§bFull Profile").build());
            p.openInventory(this.inv);
         }

         @Override
         public void close(Player p) {
         }

         @Override
         public void refresh(Player p) {
         }

         @Override
         public void handleClick(InventoryClickEvent e) {
            switch (e.getSlot()) {
               case 10:
                  player.closeInventory();
                  player.teleportAsync(target.getPlayer().getLocation());
                  break;
               case 11:
                  player.sendMessage("§eWarned!");
                  player.closeInventory();
                  break;
               case 12:
                  player.sendMessage("§cPunishing...");
                  player.closeInventory();
                  break;
               case 13:
                  player.sendMessage("§aLegit!");
                  player.closeInventory();
                  break;
               case 14:
                  GUINavigationStack.push(player, new PlayerProfileGUI(SuspiciousPlayersGUI.this.plugin, targetId));
            }
         }

         public Inventory getInventory() {
            return this.inv;
         }
      };
      GUINavigationStack.push(player, quickMenu);
   }

   static enum SortType {
      SCORE_DESC,
      SCORE_ASC,
      RECENT_ALERT,
      NAME;
   }

   static enum StatusFilter {
      ALL,
      ONLINE_ONLY,
      NEW_ALERTS_ONLY;
   }

   static enum TypeFilter {
      ALL,
      XRAY_ONLY,
      COMBAT_ONLY,
      MOVEMENT_ONLY;
   }
}
