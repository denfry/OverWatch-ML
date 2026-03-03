package net.denfry.owml.gui.modern;

import org.bukkit.ChatColor;

/**
 * Цветовые константы системы GUI.
 */
public enum GUIColor {
    RED(ChatColor.RED),
    YELLOW(ChatColor.YELLOW),
    GREEN(ChatColor.GREEN),
    BLUE(ChatColor.BLUE),
    PURPLE(ChatColor.LIGHT_PURPLE),
    GRAY(ChatColor.GRAY),
    WHITE(ChatColor.WHITE);

    private final ChatColor color;

    GUIColor(ChatColor color) {
        this.color = color;
    }

    public ChatColor getColor() {
        return color;
    }

    @Override
    public String toString() {
        return color.toString();
    }
}
