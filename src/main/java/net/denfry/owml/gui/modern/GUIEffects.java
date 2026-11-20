package net.denfry.owml.gui.modern;

import java.util.concurrent.CompletableFuture;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public class GUIEffects {
   public static void playOpen(Player player) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3F, 1.2F);
   }

   public static void playSuccess(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.0F);
   }

   public static void playError(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 1.0F);
   }

   public static void playCriticalAlert(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3F, 1.0F);
   }

   public static BossBar showProgressBar(Player player, String title) {
      BossBar bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_10, new BarFlag[0]);
      bar.addPlayer(player);
      bar.setVisible(true);
      return bar;
   }

   public static CompletableFuture<String> showChatPrompt(final Player player, String message, int timeoutSeconds) {
      final CompletableFuture<String> future = new CompletableFuture<>();
      JavaPlugin plugin = JavaPlugin.getProvidingPlugin(GUIEffects.class);
      player.sendMessage("§b[OverWatch] §f" + message);
      player.sendMessage("§7Type 'cancel' to cancel.");
      BossBar bar = Bukkit.createBossBar("§eWaiting for input... (" + timeoutSeconds + "s)", BarColor.YELLOW, BarStyle.SOLID, new BarFlag[0]);
      bar.addPlayer(player);
      int[] timeLeft = new int[]{timeoutSeconds};
      Schedulers.Task timerTask = Schedulers.entityTimer(player, () -> {
         timeLeft[0]--;
         if (timeLeft[0] <= 0) {
            if (!future.isDone()) {
               future.complete(null);
               player.sendMessage("§cInput timeout. Request cancelled.");
            }
         } else {
            bar.setTitle("§eWaiting for input... (" + timeLeft[0] + "s)");
            bar.setProgress((double)timeLeft[0] / timeoutSeconds);
         }
      }, 20L, 20L);
      Listener listener = new Listener() {
         @EventHandler(priority = EventPriority.LOWEST)
         public void onChat(AsyncPlayerChatEvent e) {
            if (e.getPlayer().equals(player)) {
               e.setCancelled(true);
               String text = e.getMessage();
               Schedulers.entity(player, () -> {
                  if (!future.isDone()) {
                     if (text.equalsIgnoreCase("cancel")) {
                        future.complete(null);
                        player.sendMessage("§cInput cancelled by user.");
                     } else {
                        future.complete(text);
                     }
                  }
               });
               HandlerList.unregisterAll(this);
            }
         }

         @EventHandler(priority = EventPriority.LOWEST)
         public void onChatModern(PlayerChatEvent e) {
            if (e.getPlayer().equals(player)) {
               e.setCancelled(true);
               String text = e.getMessage();
               Schedulers.entity(player, () -> {
                  if (!future.isDone()) {
                     if (text.equalsIgnoreCase("cancel")) {
                        future.complete(null);
                        player.sendMessage("§cInput cancelled by user.");
                     } else {
                        future.complete(text);
                     }
                  }
               });
               HandlerList.unregisterAll(this);
            }
         }
      };
      Bukkit.getPluginManager().registerEvents(listener, plugin);
      future.whenComplete((res, err) -> {
         timerTask.cancel();
         bar.removeAll();
         HandlerList.unregisterAll(listener);
      });
      return future;
   }

   public static void showConfirmDialog(final Player player, final String question, final Runnable onConfirm) {
      OverWatchGUI confirmGui = new OverWatchGUI() {
         private final Inventory inv = Bukkit.createInventory(this, 27, "§0" + question);

         @Override
         public void open(Player p) {
            this.inv.setItem(11, ItemBuilder.material(Material.LIME_CONCRETE).name("§a§lConfirm").build());
            this.inv.setItem(15, ItemBuilder.material(Material.RED_CONCRETE).name("§c§lCancel").build());
            p.openInventory(this.inv);
         }

         @Override
         public void close(Player p) {
         }

         @Override
         public void refresh(Player p) {
         }

         @Override
         public void handleClick(InventoryClickEvent event) {
            if (event.getSlot() == 11) {
               player.closeInventory();
               onConfirm.run();
            } else if (event.getSlot() == 15) {
               GUINavigationStack.pop(player);
            }
         }

         public Inventory getInventory() {
            return this.inv;
         }
      };
      GUINavigationStack.push(player, confirmGui);
   }
}
