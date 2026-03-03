package net.denfry.owml.utils;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Material compatibility helper for different Minecraft versions.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class MaterialHelper {

    private static final Map<String, Material> materialCache = new HashMap<>();

    /**
     * Initialize material helper
     */
    public static void initialize() {
        // Cache commonly used materials that are likely to exist
        // Only cache materials that we know exist in most versions
        cacheMaterial("DIAMOND_ORE");
        cacheMaterial("GOLD_ORE");
        cacheMaterial("EMERALD_ORE");
        cacheMaterial("IRON_ORE");
        cacheMaterial("COAL_ORE");
        cacheMaterial("STONE");
        cacheMaterial("COBBLESTONE");
        cacheMaterial("DIRT");
        cacheMaterial("GRASS_BLOCK");

        // Try to cache newer materials, but don't fail if they don't exist
        tryCacheMaterial("DEEPSLATE_DIAMOND_ORE");
        tryCacheMaterial("DEEPSLATE_GOLD_ORE");
        tryCacheMaterial("DEEPSLATE_EMERALD_ORE");
        tryCacheMaterial("ANCIENT_DEBRIS");
        tryCacheMaterial("NETHERITE_BLOCK");

        // Minecraft 1.21+ materials
        tryCacheMaterial("COPPER_ORE");
        tryCacheMaterial("DEEPSLATE_COPPER_ORE");
        tryCacheMaterial("RAW_COPPER_BLOCK");
        tryCacheMaterial("COPPER_BLOCK");
        tryCacheMaterial("EXPOSED_COPPER");
        tryCacheMaterial("WEATHERED_COPPER");
        tryCacheMaterial("OXIDIZED_COPPER");
        tryCacheMaterial("CUT_COPPER");
        tryCacheMaterial("EXPOSED_CUT_COPPER");
        tryCacheMaterial("WEATHERED_CUT_COPPER");
        tryCacheMaterial("OXIDIZED_CUT_COPPER");
        tryCacheMaterial("COPPER_GRATE");
        tryCacheMaterial("COPPER_BULB");
        tryCacheMaterial("TUFF");
        tryCacheMaterial("CHISELED_TUFF");
        tryCacheMaterial("POLISHED_TUFF");
        tryCacheMaterial("TUFF_BRICKS");
        tryCacheMaterial("CHISELED_TUFF_BRICKS");
        tryCacheMaterial("MUD_BRICKS");
        tryCacheMaterial("PACKED_MUD");
        tryCacheMaterial("MUDDY_MANGROVE_ROOTS");
    }

    /**
     * Get material by name with version compatibility
     */
    public static Material getMaterial(String name) {
        // Check cache first
        Material cached = materialCache.get(name);
        if (cached != null) {
            return cached;
        }

        // Try to get material directly
        try {
            Material material = Material.valueOf(name);
            materialCache.put(name, material);
            return material;
        } catch (IllegalArgumentException e) {
            // Material doesn't exist in this version
            return null;
        }
    }

    /**
     * Get material with fallback
     */
    public static Material getMaterial(String name, Material fallback) {
        Material material = getMaterial(name);
        return material != null ? material : fallback;
    }

    /**
     * Check if material exists
     */
    public static boolean hasMaterial(String name) {
        return getMaterial(name) != null;
    }

    /**
     * Cache a material
     */
    private static void cacheMaterial(String name) {
        Material material = getMaterial(name);
        if (material != null) {
            materialCache.put(name, material);
        }
    }

    /**
     * Try to cache a material without throwing exceptions if it doesn't exist
     */
    private static void tryCacheMaterial(String name) {
        try {
            Material material = Material.valueOf(name);
            if (material != null) {
                materialCache.put(name, material);
            }
        } catch (IllegalArgumentException e) {
            // Material doesn't exist in this version, skip caching it
        }
    }

    /**
     * Get all cached materials
     */
    public static Map<String, Material> getCachedMaterials() {
        return new HashMap<>(materialCache);
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
               material.name().contains("GRANITE");
    }
}
