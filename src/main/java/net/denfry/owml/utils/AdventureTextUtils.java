package net.denfry.owml.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Adventure API text utilities using MiniMessage for modern Minecraft versions (1.21+).
 */
public class AdventureTextUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Deserialize MiniMessage string to Component
     */
    public static Component mini(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * Convert color codes to Component (legacy support)
     */
    public static String color(String text) {
        Component component = LEGACY_SERIALIZER.deserialize(text);
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Set item display name using MiniMessage (modern) or Legacy
     */
    public static void setDisplayName(ItemMeta meta, String name) {
        if (name.contains("<") && name.contains(">")) {
            meta.displayName(MINI_MESSAGE.deserialize(name).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName(LEGACY_SERIALIZER.deserialize(name).decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * Set item lore using MiniMessage
     */
    public static void setLore(ItemMeta meta, List<String> lore) {
        List<Component> componentLore = lore.stream()
                .map(line -> line.contains("<") && line.contains(">") 
                    ? MINI_MESSAGE.deserialize(line).decoration(TextDecoration.ITALIC, false)
                    : LEGACY_SERIALIZER.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
        meta.lore(componentLore);
    }

    /**
     * Create a visual progress bar component
     * Example: [■■■■■□□□□□]
     */
    public static Component createProgressBar(double progress, int size, String filledColor, String emptyColor) {
        int filledCount = (int) (progress * size);
        int emptyCount = size - filledCount;
        
        String bar = "<" + filledColor + ">" + "■".repeat(Math.max(0, filledCount)) +
                     "<" + emptyColor + ">" + "□".repeat(Math.max(0, emptyCount));
        
        return MINI_MESSAGE.deserialize(bar);
    }

    /**
     * Get confidence component with gradient
     */
    public static Component getConfidenceGradient(double confidence) {
        String color;
        if (confidence > 0.8) color = "red";
        else if (confidence > 0.5) color = "yellow";
        else color = "green";
        
        return MINI_MESSAGE.deserialize("<gradient:white:" + color + ">" + (int)(confidence * 100) + "%</gradient>");
    }

    public static String toLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    public static Component fromLegacy(String legacy) {
        return LEGACY_SERIALIZER.deserialize(legacy);
    }
}