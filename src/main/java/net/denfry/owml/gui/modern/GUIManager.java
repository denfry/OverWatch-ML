package net.denfry.owml.gui.modern;

import net.denfry.owml.OverWatchML;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Менеджер GUI, отвечающий за обработку событий и автообновление.
 */
public class GUIManager implements Listener {
    private final OverWatchML plugin;

    public GUIManager(OverWatchML plugin) {
        this.plugin = plugin;
        
        // Задача на автообновление раз в 5 секунд
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Inventory topInv = player.getOpenInventory().getTopInventory();
                if (topInv.getHolder() instanceof OverWatchGUI gui) {
                    gui.refresh(player);
                }
            }
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof OverWatchGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof OverWatchGUI) {
            Player player = (Player) event.getPlayer();
            // Очищаем стек только если закрыли вручную (не через переход в другой GUI нашей системы)
            // В данной реализации мы полагаемся на то, что переход в другой GUI вызывает open(), 
            // который закроет текущий инвентарь, но мы можем проверять, открыт ли новый GUI OverWatch.
            
            // Для упрощения согласно ТЗ: "очищает стек если игрок закрыл GUI нажатием Escape"
            // Мы проверим это через небольшую задержку: если через 1 тик у игрока не открыт наш GUI, очищаем.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof OverWatchGUI)) {
                    GUINavigationStack.clear(player);
                }
            }, 1L);
        }
    }
}
