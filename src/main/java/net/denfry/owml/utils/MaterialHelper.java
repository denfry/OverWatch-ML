package net.denfry.owml.utils;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Material utility helper for Minecraft 1.21+.
 */
public class MaterialHelper {

    private static final Map<String, Material> materialCache = new HashMap<>();

    /**
     * Initialize material helper
     */
    public static void initialize() {
        // Cache basic ores
        cacheMaterial("DIAMOND_ORE");
        cacheMaterial("GOLD_ORE");
        cacheMaterial("EMERALD_ORE");
        cacheMaterial("IRON_ORE");
        cacheMaterial("COAL_ORE");
        cacheMaterial("DEEPSLATE_DIAMOND_ORE");
        cacheMaterial("DEEPSLATE_GOLD_ORE");
        cacheMaterial("DEEPSLATE_EMERALD_ORE");
        cacheMaterial("ANCIENT_DEBRIS");
        
        // Minecraft 1.21+ specific materials
        cacheMaterial("COPPER_ORE");
        cacheMaterial("DEEPSLATE_COPPER_ORE");
        cacheMaterial("RAW_COPPER_BLOCK");
        cacheMaterial("TUFF");
        cacheMaterial("CHISELED_TUFF");
        cacheMaterial("POLISHED_TUFF");
        cacheMaterial("TUFF_BRICKS");
    }

    /**
     * Get material by name.
     */
    public static Material getMaterial(String name) {
        Material cached = materialCache.get(name);
        if (cached != null) {
            return cached;
        }

        try {
            Material material = Material.valueOf(name);
            materialCache.put(name, material);
            return material;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if material is a valuable ore
     */
    public static boolean isValuableOre(Material material) {
        if (material == null) return false;

        String name = material.name();
        return name.contains("DIAMOND") ||
               name.contains("EMERALD") ||
               name.contains("GOLD") ||
               name.contains("ANCIENT_DEBRIS") ||
               name.contains("NETHERITE") ||
               name.contains("COPPER");
    }

    /**
     * Check if material is stone or similar
     */
    public static boolean isStoneLike(Material material) {
        if (material == null) return false;

        return material.name().contains("STONE") ||
               material.name().contains("COBBLESTONE") ||
               material.name().contains("ANDESITE") ||
               material.name().contains("DIORITE") ||
               material.name().contains("GRANITE") ||
               material.name().contains("TUFF") ||
               material.name().contains("DEEPSLATE");
    }

    private static void cacheMaterial(String name) {
        Material material = Material.valueOf(name);
        if (material != null) {
            materialCache.put(name, material);
        }
    }
}
