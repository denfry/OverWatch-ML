package net.denfry.owml.gui.modern;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.detection.PlayerBehaviorProfile;
import net.denfry.owml.detection.PlayerEventBuffer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PlayerProfileGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final UUID targetId;
   private final Inventory inventory;
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

   public PlayerProfileGUI(OverWatchML plugin, UUID targetId) {
      this.plugin = plugin;
      this.targetId = targetId;
      OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
      this.inventory = Bukkit.createInventory(this, 54, "Profile: " + op.getName());
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
      OfflinePlayer op = Bukkit.getOfflinePlayer(this.targetId);
      PlayerBehaviorProfile profile = this.plugin.getContext().getProfileManager().getProfile(this.targetId);
      PlayerEventBuffer buffer = this.plugin.getContext().getProfileManager().getEventBuffer(this.targetId);
      if (profile != null && buffer != null) {
         String status = op.isOnline() ? "§aOnline" : "§7Offline";
         String lastAnalysis = this.dateFormat.format(new Date(profile.getLastAnalysisTime()));
         this.inventory
            .setItem(
               4,
               ItemBuilder.material(Material.PLAYER_HEAD)
                  .skull(op.getName())
                  .name("§b" + (op.getName() != null ? op.getName() : "Unknown"))
                  .lore(
                     LoreFormatter.format(
                        List.of(
                           "§7Status: " + status,
                           "§7Last Analysis: §f" + lastAnalysis,
                           "§7Overall Suspicion: " + this.getOverallSuspicion(profile),
                           "§7First Seen: §f" + this.dateFormat.format(new Date(op.getFirstPlayed()))
                        )
                     )
                  )
                  .build()
            );
         this.inventory.setItem(10, this.getCategoryItem(Material.DIAMOND_ORE, "Xray", profile.getDetectionScore("xray")));
         this.inventory.setItem(12, this.getCategoryItem(Material.IRON_SWORD, "Combat", profile.getDetectionScore("combat")));
         this.inventory.setItem(14, this.getCategoryItem(Material.FEATHER, "Movement", profile.getDetectionScore("movement")));
         this.inventory.setItem(16, this.getCategoryItem(Material.SCAFFOLDING, "World/Scaffold", profile.getDetectionScore("world")));
         List<PlayerEventBuffer.BehavioralEvent> events = buffer.getEvents();
         int eventSlot = 28;
         int maxEvents = 16;
         this.inventory.setItem(19, ItemBuilder.material(Material.BOOK).name("§eRecent Behavioral Events").build());
         if (events != null) {
            for (int i = 0; i < Math.min(events.size(), maxEvents); i++) {
               PlayerEventBuffer.BehavioralEvent event = events.get(events.size() - 1 - i);
               this.inventory
                  .setItem(
                     eventSlot + i + i / 8 * 1,
                     ItemBuilder.material(this.getEventMaterial(event.eventType()))
                        .name("§f" + event.eventType().toUpperCase())
                        .lore(
                           List.of(
                              "§7Time: §f" + new SimpleDateFormat("HH:mm:ss").format(new Date(event.timestamp())),
                              "§7Value: §e" + event.value(),
                              "§7Context: §7" + event.context().toString()
                           )
                        )
                        .build()
                  );
            }
         }

         this.inventory.setItem(45, ItemBuilder.material(Material.PAPER).name("§fFull Statistics").build());
         this.inventory.setItem(47, ItemBuilder.material(Material.ENDER_EYE).name("§3Teleport to Player").build());
         this.inventory.setItem(49, ItemBuilder.material(Material.YELLOW_DYE).name("§eWarn Player").build());
         this.inventory.setItem(51, ItemBuilder.material(Material.RED_DYE).name("§cPunish Player").build());
         this.inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§7Back").build());
      } else {
         player.sendMessage("§cNo data available for player " + (op.getName() != null ? op.getName() : "Unknown"));
      }
   }

   private String getOverallSuspicion(PlayerBehaviorProfile profile) {
      double maxScore = profile.getDetectionScores().values().stream().max(Double::compare).orElse(0.0);
      String color = maxScore > 0.8 ? "§c" : (maxScore > 0.5 ? "§6" : "§a");
      return color + String.format("%.0f%%", maxScore * 100.0);
   }

   private ItemStack getCategoryItem(Material mat, String name, double score) {
      String color = score > 0.8 ? "§c" : (score > 0.5 ? "§6" : "§a");
      return ItemBuilder.material(mat)
         .name(color + name + " Detection")
         .lore(
            List.of(
               "§7Current Score: " + color + String.format("%.1f%%", score * 100.0),
               "§7Status: " + (score > 0.8 ? "§4CRITICAL" : (score > 0.5 ? "§6SUSPICIOUS" : "§aSAFE"))
            )
         )
         .build();
   }

   private Material getEventMaterial(String type) {
      String var2 = type.toLowerCase();

      return switch (var2) {
         case "move" -> Material.FEATHER;
         case "combat_hit" -> Material.REDSTONE;
         case "place" -> Material.BRICKS;
         case "interact" -> Material.LEVER;
         default -> Material.PAPER;
      };
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      OfflinePlayer op = Bukkit.getOfflinePlayer(this.targetId);
      switch (slot) {
         case 47:
            if (op.isOnline()) {
               player.closeInventory();
               player.teleportAsync(op.getPlayer().getLocation());
               player.sendMessage("§aTeleported to " + op.getName());
               GUIEffects.playSuccess(player);
            } else {
               GUIEffects.playError(player);
               player.sendMessage("§cPlayer is offline.");
            }
         case 48:
         case 50:
         case 52:
         default:
            break;
         case 49:
            GUIEffects.showConfirmDialog(player, "Warn " + op.getName() + "?", () -> {
               player.sendMessage("§ePlayer warned for suspicious behavior.");
               GUIEffects.playSuccess(player);
            });
            break;
         case 51:
            player.sendMessage("§cOpening Punishment Panel...");
            GUINavigationStack.push(player, new PunishmentPanelGUI(this.plugin, this.targetId));
            break;
         case 53:
            GUINavigationStack.pop(player);
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
