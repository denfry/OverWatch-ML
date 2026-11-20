package net.denfry.owml.gui.subgui;

import java.util.List;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.managers.AppealManager;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class AppealDetailsGUI implements OverWatchGUI {
   private final OverWatchML plugin;
   private final AppealManager appealManager;
   private final AppealManager.Appeal appeal;
   private final Inventory inventory;

   public AppealDetailsGUI(OverWatchML plugin, AppealManager appealManager, AppealManager.Appeal appeal) {
      this.plugin = plugin;
      this.appealManager = appealManager;
      this.appeal = appeal;
      this.inventory = Bukkit.createInventory(this, 54, "Appeal Details: " + appeal.getPlayerName());
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
      this.inventory
         .setItem(
            4,
            ItemBuilder.material(Material.PLAYER_HEAD)
               .skull(this.appeal.getPlayerName())
               .name("§b" + this.appeal.getPlayerName())
               .lore(
                  List.of(
                     "§7Level: §c" + this.appeal.getPunishmentLevel(),
                     "§7Date: §f" + this.appeal.getFormattedTimestamp(),
                     "§7Status: §e" + this.appeal.getStatus().getDisplayName()
                  )
               )
               .build()
         );
      this.inventory.setItem(22, ItemBuilder.material(Material.WRITABLE_BOOK).name("§6Reason").lore(List.of("§f" + this.appeal.getReason())).build());
      this.inventory.setItem(38, ItemBuilder.material(Material.LIME_WOOL).name("§aApprove").build());
      this.inventory.setItem(40, ItemBuilder.material(Material.ORANGE_WOOL).name("§6Mark Review").build());
      this.inventory.setItem(42, ItemBuilder.material(Material.RED_WOOL).name("§cDeny").build());
      this.inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
   }

   @Override
   public void handleClick(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      int slot = event.getSlot();
      switch (slot) {
         case 38:
            this.handleAction(player, AppealManager.AppealStatus.APPROVED);
            break;
         case 40:
            this.appealManager.updateAppealStatus(this.appeal.getId(), AppealManager.AppealStatus.UNDER_REVIEW, player.getName(), "Under review");
            player.sendMessage("§6Marked as under review.");
            this.refresh(player);
            break;
         case 42:
            this.handleAction(player, AppealManager.AppealStatus.DENIED);
            break;
         case 49:
            GUINavigationStack.pop(player);
      }
   }

   private void handleAction(Player player, AppealManager.AppealStatus status) {
      player.closeInventory();
      GUIEffects.showChatPrompt(player, "Enter response for " + status.name() + " (or 'cancel'):", 60).thenAccept(response -> {
         if (response != null && !response.equalsIgnoreCase("cancel")) {
            Schedulers.entity(player, () -> {
               this.appealManager.updateAppealStatus(this.appeal.getId(), status, player.getName(), response);
               player.sendMessage("§aAppeal " + status.name().toLowerCase() + "ed.");
               GUINavigationStack.pop(player);
            });
         } else {
            Schedulers.entity(player, () -> this.open(player));
         }
      });
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
