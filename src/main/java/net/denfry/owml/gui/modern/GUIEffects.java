package net.denfry.owml.gui.modern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Утилитный класс для визуальных и звуковых эффектов GUI.
 */
public class GUIEffects {

    public static void playOpen(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);
    }

    public static void playSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
    }

    public static void playError(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    public static void playCriticalAlert(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
    }

    /**
     * Создает и показывает BossBar с прогрессом.
     */
    public static BossBar showProgressBar(Player player, String title) {
        BossBar bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        bar.setVisible(true);
        return bar;
    }

    /**
     * Отправляет запрос в чат и ожидает ввод текста с таймером в BossBar.
     */
    public static CompletableFuture<String> showChatPrompt(Player player, String message, int timeoutSeconds) {
        CompletableFuture<String> future = new CompletableFuture<>();
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(GUIEffects.class);

        player.sendMessage("§b[OverWatch] §f" + message);
        player.sendMessage("§7Введите 'cancel' для отмены.");

        BossBar bar = Bukkit.createBossBar("§eОжидание ввода... (" + timeoutSeconds + "с)", BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);

        int[] timeLeft = {timeoutSeconds};
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            timeLeft[0]--;
            if (timeLeft[0] <= 0) {
                if (!future.isDone()) {
                    future.complete(null);
                    player.sendMessage("§cВремя ожидания истекло. Ввод отменен.");
                }
            } else {
                bar.setTitle("§eОжидание ввода... (" + timeLeft[0] + "с)");
                bar.setProgress((double) timeLeft[0] / timeoutSeconds);
            }
        }, 20L, 20L);

        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent e) {
                if (e.getPlayer().equals(player)) {
                    e.setCancelled(true);
                    String text = e.getMessage();
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!future.isDone()) {
                            if (text.equalsIgnoreCase("cancel")) {
                                future.complete(null);
                                player.sendMessage("§cВвод отменен пользователем.");
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
            Bukkit.getScheduler().cancelTask(taskId);
            bar.removeAll();
            HandlerList.unregisterAll(listener);
        });

        return future;
    }

    /**
     * Открывает диалог подтверждения.
     */
    public static void showConfirmDialog(Player player, String question, Runnable onConfirm) {
        OverWatchGUI confirmGui = new OverWatchGUI() {
            private final Inventory inv = Bukkit.createInventory(this, 27, "§0" + question);

            @Override
            public void open(Player p) {
                inv.setItem(11, ItemBuilder.material(Material.LIME_CONCRETE).name("§a§lConfirm").build());
                inv.setItem(15, ItemBuilder.material(Material.RED_CONCRETE).name("§c§lCancel").build());
                p.openInventory(inv);
            }

            @Override
            public void close(Player p) {}

            @Override
            public void refresh(Player p) {}

            @Override
            public void handleClick(InventoryClickEvent event) {
                if (event.getSlot() == 11) {
                    player.closeInventory();
                    onConfirm.run();
                } else if (event.getSlot() == 15) {
                    GUINavigationStack.pop(player);
                }
            }

            @Override
            public Inventory getInventory() {
                return inv;
            }
        };
        GUINavigationStack.push(player, confirmGui);
    }
}
