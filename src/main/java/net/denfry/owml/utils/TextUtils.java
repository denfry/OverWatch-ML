package net.denfry.owml.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Text utilities for version-compatible text processing.
 * Refactored to use Adventure API and MiniMessage for modern Minecraft versions (1.21+).
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.3
 */
public class TextUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static boolean supportsHexColors = false;

    /**
     * Initialize text utilities
     */
    public static void initialize() {
        supportsHexColors = VersionHelper.supportsFeature("hex_colors");
    }

    /**
     * Colorize text with support for MiniMessage and Legacy color codes.
     */
    public static Component colorize(String text) {
        if (text == null) return Component.empty();

        // If it looks like MiniMessage, deserialize it
        if (text.contains("<") && text.contains(">")) {
            return MINI_MESSAGE.deserialize(text);
        }

        // Otherwise use legacy serializer
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * Convert Component to legacy string for old API compatibility
     */
    public static String color(String text) {
        if (text == null) return null;
        return LEGACY_SERIALIZER.serialize(colorize(text));
    }

    /**
     * Strip color codes from text
     */
    public static String stripColors(String text) {
        if (text == null) return null;
        return LEGACY_SERIALIZER.serialize(colorize(text).color(null));
    }

    /**
     * Send colored message to player using Adventure API
     */
    public static void sendMessage(Player player, String message) {
        if (player != null && message != null) {
            player.sendMessage(colorize(message));
        }
    }

    /**
     * Format duration in milliseconds to human readable string
     */
    public static String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";

        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";

        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";

        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";

        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    /**
     * Set display name for ItemMeta with modern color support
     */
    public static void setDisplayName(ItemMeta meta, String name) {
        if (meta != null && name != null) {
            meta.displayName(colorize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
    }

    /**
     * Set lore for ItemMeta with modern color support
     */
    public static void setLore(ItemMeta meta, List<String> lore) {
        if (meta != null && lore != null) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(colorize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            meta.lore(componentLore);
        }
    }
}