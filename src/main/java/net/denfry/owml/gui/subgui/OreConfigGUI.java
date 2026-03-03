package net.denfry.owml.gui.subgui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.denfry.owml.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class OreConfigGUI {
    public static final String PERMISSION = "owml.gui_oreconfig";
    private final Inventory inv;
    private final ConfigManager configManager;
    private final Material[] availableOres;

    public OreConfigGUI(ConfigManager configManager) {
        this.configManager = configManager;
        this.availableOres = initializeAvailableOres();

        inv = Bukkit.createInventory(null, 27, Component.text("⛏ Ore Management").color(NamedTextColor.AQUA));
        initializeItems();
    }

    /**
     * Initialize the array of available ores based on materials that exist in the current version
     */
    private Material[] initializeAvailableOres() {
        List<Material> ores = new ArrayList<>();

        // Base ores that exist in most versions
        String[] baseOres = {
            "COAL_ORE", "IRON_ORE", "GOLD_ORE", "REDSTONE_ORE", "LAPIS_ORE",
            "DIAMOND_ORE", "EMERALD_ORE", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS"
        };

        // Version-specific ores (only add if they exist)
        String[] versionSpecificOres = {
            "DEEPSLATE_COAL_ORE", "DEEPSLATE_IRON_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
            "DEEPSLATE_GOLD_ORE", "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_LAPIS_ORE",
            "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_EMERALD_ORE"
        };

        // Add base ores
        for (String oreName : baseOres) {
            try {
                Material material = Material.valueOf(oreName);
                if (material != null && material.isBlock()) {
                    ores.add(material);
                }
            } catch (IllegalArgumentException e) {
                // Material doesn't exist in this version, skip it
            }
        }

        // Add version-specific ores (these might not exist in older versions)
        for (String oreName : versionSpecificOres) {
            try {
                Material material = Material.valueOf(oreName);
                if (material != null && material.isBlock()) {
                    ores.add(material);
                }
            } catch (IllegalArgumentException e) {
                // Material doesn't exist in this version, skip it
            }
        }

        return ores.toArray(new Material[0]);
    }

    private void initializeItems() {

        inv.clear();

        Set<Material> naturalOres = configManager.getNaturalOres();

        int slot = 0;
        for (Material ore : availableOres) {

            ItemStack item = new ItemStack(ore);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(ore.name()).color(NamedTextColor.GOLD));


            List<Component> lore = new ArrayList<>();
            if (naturalOres.contains(ore)) {
                lore.add(Component.text("Selected").color(NamedTextColor.GREEN));

                meta.addEnchant(Enchantment.MENDING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(Component.text("Not Selected").color(NamedTextColor.RED));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        inv.setItem(26, createGuiItem(Material.BARRIER, "Back to ⛏ Staff Control Panel"));
    }

    private ItemStack createGuiItem(Material material, String name, Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.GOLD));

        if (lore.length > 0) {
            meta.lore(Arrays.asList(lore));
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Opens this GUI for the given player.
     */
    public void openInventory(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("You do not have permission to configure ores.").color(NamedTextColor.RED));
            return;
        }
        player.openInventory(inv);
    }

    /**
     * Call this method after toggling an ore to refresh the GUI.
     */
    public void refresh() {
        initializeItems();
    }
}
