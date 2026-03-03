package net.denfry.owml.gui.modern;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Базовый интерфейс для всех GUI системы OverWatch.
 */
public interface OverWatchGUI extends InventoryHolder {
    /**
     * Открывает GUI для игрока.
     */
    void open(Player player);

    /**
     * Вызывается при закрытии GUI.
     */
    void close(Player player);

    /**
     * Обновляет содержимое GUI.
     */
    void refresh(Player player);

    /**
     * Обрабатывает клик в инвентаре.
     */
    void handleClick(InventoryClickEvent event);
}
