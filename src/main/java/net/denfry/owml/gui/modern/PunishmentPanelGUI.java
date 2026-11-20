package net.denfry.owml.gui.modern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.subgui.AppealGUI;
import net.denfry.owml.ml.ReasoningMLModel;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class PunishmentPanelGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final Inventory inventory;
   private UUID selectedPlayerId = null;
   private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
   private final Path historyFile;
   private List<PunishmentPanelGUI.PunishmentRecord> history;

   public PunishmentPanelGUI(OverWatchML plugin) {
      this.plugin = plugin;
      this.inventory = Bukkit.createInventory(this, 54, "Punishment Control Panel");
      this.historyFile = plugin.getDataFolder().toPath().resolve("punishment_history.json");
      this.loadHistory();
   }

   public PunishmentPanelGUI(OverWatchML plugin, UUID targetId) {
      this.plugin = plugin;
      this.selectedPlayerId = targetId;
      this.inventory = Bukkit.createInventory(this, 54, "Punishment Control Panel");
      this.historyFile = plugin.getDataFolder().toPath().resolve("punishment_history.json");
      this.loadHistory();
   }

   private void loadHistory() {
      this.history = new ArrayList<>();
      if (Files.exists(this.historyFile)) {
         try {
            String content = new String(Files.readAllBytes(this.historyFile));
            if (!content.isEmpty()) {
               String[] records = content.split("\\},\\{");

               for (String record : records) {
                  try {
                     String cleaned = record.replace("[", "").replace("]", "").replace("{", "").replace("}", "").trim();
                     String[] parts = cleaned.split(",");
                     String playerName = null;
                     String type = null;
                     long timestamp = 0L;

                     for (String part : parts) {
                        String[] kv = part.split(":");
                        if (kv.length == 2) {
                           String key = kv[0].trim().replace("\"", "");
                           String value = kv[1].trim().replace("\"", "");
                           if (key.equals("playerName")) {
                              playerName = value;
                           } else if (key.equals("type")) {
                              type = value;
                           } else if (key.equals("timestamp")) {
                              timestamp = Long.parseLong(value);
                           }
                        }
                     }

                     if (playerName != null && type != null) {
                        PunishmentPanelGUI.PunishmentRecord rec = new PunishmentPanelGUI.PunishmentRecord(playerName, type);
                        rec.timestamp = timestamp;
                        this.history.add(rec);
                     }
                  } catch (Exception var20) {
                  }
               }
            }
         } catch (IOException var21) {
            this.plugin.getLogger().warning("Failed to load punishment history: " + var21.getMessage());
         }
      }
   }

   private void saveHistory() {
      try {
         StringBuilder sb = new StringBuilder("[");

         for (int i = 0; i < this.history.size(); i++) {
            PunishmentPanelGUI.PunishmentRecord rec = this.history.get(i);
            sb.append("{\"playerName\":\"")
               .append(rec.playerName)
               .append("\",\"type\":\"")
               .append(rec.type)
               .append("\",\"timestamp\":")
               .append(rec.timestamp)
               .append("}");
            if (i < this.history.size() - 1) {
               sb.append(",");
            }
         }

         sb.append("]");
         Files.writeString(this.historyFile, sb.toString());
      } catch (IOException var4) {
         this.plugin.getLogger().warning("Failed to save punishment history: " + var4.getMessage());
      }
   }

   private void addHistory(String name, String type) {
      this.history.add(new PunishmentPanelGUI.PunishmentRecord(name, type));
      if (this.history.size() > 50) {
         this.history.remove(0);
      }

      this.saveHistory();
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
      List<UUID> awaiting = this.plugin
         .getContext()
         .getSuspiciousService()
         .getSuspiciousPlayers()
         .stream()
         .filter(uuid -> this.plugin.getSuspicionLevel(Bukkit.getOfflinePlayer(uuid).getName()) > 60)
         .limit(5L)
         .collect(Collectors.toList());
      int[] awaitingSlots = new int[]{0, 9, 18, 27, 36};

      for (int i = 0; i < awaiting.size(); i++) {
         UUID id = awaiting.get(i);
         OfflinePlayer op = Bukkit.getOfflinePlayer(id);
         int score = this.plugin.getSuspicionLevel(op.getName());
         boolean isSelected = id.equals(this.selectedPlayerId);
         ReasoningMLModel.DetectionResult mlResult = this.plugin.getMLManager().getDetectionResults().get(id);
         String reason = mlResult != null ? mlResult.getConclusion() : "Manual flags detected";
         this.inventory
            .setItem(
               awaitingSlots[i],
               ItemBuilder.material(Material.PLAYER_HEAD)
                  .skull(op.getName())
                  .name((isSelected ? "§b» §l" : "§7") + op.getName() + " §e(" + score + ")")
                  .lore(List.of("§7ML Recommendation: §cHigh Risk", "§7Reasoning: §f" + reason, "", "§eLeft-Click: §7Select Player"))
                  .build()
            );
      }

      if (this.selectedPlayerId != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(this.selectedPlayerId);
         ReasoningMLModel.DetectionResult ml = this.plugin.getMLManager().getDetectionResults().get(this.selectedPlayerId);
         this.inventory.setItem(4, ItemBuilder.material(Material.PLAYER_HEAD).skull(target.getName()).name("§b" + target.getName()).build());
         this.inventory
            .setItem(
               13,
               ItemBuilder.material(Material.COMPASS)
                  .name("§fML Confidence: §d" + (ml != null ? String.format("%.1f%%", ml.getProbability() * 100.0) : "N/A"))
                  .build()
            );
         this.inventory.setItem(22, ItemBuilder.material(Material.BOOK).name("§fSuggested: §eTemp Ban 24h").build());
         this.inventory.setItem(31, ItemBuilder.material(Material.PAPER).name("§fRecent flags: §c3 Critical").build());
      } else {
         this.inventory.setItem(22, ItemBuilder.material(Material.BARRIER).name("§7Select a player to see details").build());
      }

      this.inventory.setItem(8, ItemBuilder.material(Material.OAK_DOOR).name("§eKick Player").build());
      this.inventory.setItem(17, ItemBuilder.material(Material.CLOCK).name("§6Temp Ban").build());
      this.inventory.setItem(26, ItemBuilder.material(Material.BARRIER).name("§cPermanent Ban").build());
      this.inventory.setItem(35, ItemBuilder.material(Material.ARROW).name("§dMute Player").build());
      this.inventory.setItem(44, ItemBuilder.material(Material.COMMAND_BLOCK_MINECART).name("§fCustom Command").build());
      this.inventory
         .setItem(49, ItemBuilder.material(Material.WRITABLE_BOOK).name("§aReview Appeals").lore(List.of("§7Manage player punishment appeals.")).build());
      int[] historySlots = new int[]{45, 46, 47, 48, 50, 51, 52, 53};

      for (int i = 0; i < Math.min(historySlots.length, this.history.size()); i++) {
         PunishmentPanelGUI.PunishmentRecord rec = this.history.get(this.history.size() - 1 - i);
         this.inventory
            .setItem(
               historySlots[i],
               ItemBuilder.material(Material.PAPER)
                  .name("§7" + rec.playerName + " §f- " + rec.type)
                  .lore(List.of("§7Time: §f" + this.timeFormat.format(rec.timestamp), "§eClick to cancel (if <10m)"))
                  .build()
            );
      }
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      String actionName = "Unknown";
      if (slot == 0 || slot == 9 || slot == 18 || slot == 27 || slot == 36) {
         List<UUID> awaiting = this.plugin
            .getContext()
            .getSuspiciousService()
            .getSuspiciousPlayers()
            .stream()
            .filter(uuid -> this.plugin.getSuspicionLevel(Bukkit.getOfflinePlayer(uuid).getName()) > 60)
            .limit(5L)
            .collect(Collectors.toList());
         int index = slot / 9;
         if (index < awaiting.size()) {
            this.selectedPlayerId = awaiting.get(index);
            actionName = "Select Player: " + Bukkit.getOfflinePlayer(this.selectedPlayerId).getName();
            this.refresh(player);
            GUIEffects.playOpen(player);
         }
      } else if (slot == 49) {
         actionName = "Open Appeals GUI";
         GUINavigationStack.push(player, new AppealGUI(this.plugin));
         this.plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
      } else if (this.selectedPlayerId != null || slot != 8 && slot != 17 && slot != 26 && slot != 35 && slot != 44) {
         OfflinePlayer target = this.selectedPlayerId != null ? Bukkit.getOfflinePlayer(this.selectedPlayerId) : null;
         switch (slot) {
            case 8:
               actionName = "Execute KICK on " + target.getName();
               this.executePunishment(player, target, "KICK");
               break;
            case 17:
               actionName = "Open Ban Duration Selection for " + target.getName();
               this.openDurationSelection(player, target, "BAN");
               break;
            case 26:
               actionName = "Confirm Perm Ban for " + target.getName();
               GUIEffects.showConfirmDialog(player, "Ban " + target.getName() + "?", () -> {
                  this.executePunishment(player, target, "PERMANENT BAN");
                  this.plugin.getLogger().warning("GUI: " + player.getName() + " executed PERMANENT BAN on " + target.getName());
               });
               break;
            case 35:
               actionName = "Open Mute Duration Selection for " + target.getName();
               this.openDurationSelection(player, target, "MUTE");
               break;
            case 44:
               actionName = "Enter Custom Command for " + target.getName();
               player.closeInventory();
               GUIEffects.showChatPrompt(player, "Enter command (use %p for name):", 30).thenAccept(cmd -> {
                  if (cmd != null) {
                     Schedulers.global(() -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%p", target.getName()));
                        this.addHistory(target.getName(), "CUSTOM");
                        this.plugin.getLogger().info("GUI: " + player.getName() + " executed custom command on " + target.getName() + ": " + cmd);
                     });
                  }
               });
         }

         int[] historySlots = new int[]{45, 46, 47, 48, 50, 51, 52, 53};

         for (int i = 0; i < historySlots.length; i++) {
            if (slot == historySlots[i]) {
               int hIndex = this.history.size() - 1 - i;
               if (hIndex >= 0 && hIndex < this.history.size()) {
                  PunishmentPanelGUI.PunishmentRecord rec = this.history.get(hIndex);
                  if (System.currentTimeMillis() - rec.timestamp < 600000L) {
                     actionName = "Cancel Punishment for " + rec.playerName;
                     player.sendMessage("§aPunishment for " + rec.playerName + " cancelled.");
                     this.history.remove(hIndex);
                     this.refresh(player);
                     GUIEffects.playSuccess(player);
                  }
               }
            }
         }

         if (!actionName.equals("Unknown")) {
            this.plugin.getLogger().info("GUI: " + player.getName() + " -> " + actionName);
         }
      } else {
         player.sendMessage("§cPlease select a player first!");
         GUIEffects.playError(player);
      }
   }

   private void executePunishment(Player staff, OfflinePlayer target, String type) {
      if (target != null && target.getName() != null) {
         staff.sendMessage("§aExecuted: " + type + " on " + target.getName());
         this.addHistory(target.getName(), type);
         this.selectedPlayerId = null;
         this.refresh(staff);
         GUIEffects.playSuccess(staff);
      } else {
         staff.sendMessage("§cInvalid target for punishment!");
      }
   }

   private void openDurationSelection(final Player staff, final OfflinePlayer target, final String baseType) {
      OverWatchGUI durationGui = new OverWatchGUI() {
         private final Inventory inv = Bukkit.createInventory(this, 9, "Select Duration");

         @Override
         public void open(Player p) {
            this.inv.setItem(0, ItemBuilder.material(Material.CLOCK).name("1 Hour").build());
            this.inv.setItem(2, ItemBuilder.material(Material.CLOCK).name("6 Hours").build());
            this.inv.setItem(4, ItemBuilder.material(Material.CLOCK).name("1 Day").build());
            this.inv.setItem(6, ItemBuilder.material(Material.CLOCK).name("3 Days").build());
            this.inv.setItem(8, ItemBuilder.material(Material.CLOCK).name("7 Days").build());
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
            if (e.getCurrentItem() != null) {
               String duration = e.getCurrentItem().getItemMeta().getDisplayName();
               PunishmentPanelGUI.this.executePunishment(staff, target, baseType + " (" + duration + ")");
            }
         }

         public Inventory getInventory() {
            return this.inv;
         }
      };
      GUINavigationStack.push(staff, durationGui);
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public static class PunishmentRecord {
      String playerName;
      String type;
      long timestamp;

      public PunishmentRecord(String playerName, String type) {
         this.playerName = playerName;
         this.type = type;
         this.timestamp = System.currentTimeMillis();
      }
   }
}
