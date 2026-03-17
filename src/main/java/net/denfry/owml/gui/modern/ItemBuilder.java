package net.denfry.owml.gui.modern;

import net.denfry.owml.utils.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилитный класс для удобного создания ItemStack через цепочку вызовов.
 */
public class ItemBuilder {
    private ItemStack item;
    private ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder material(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.displayName(TextUtils.colorize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null) {
            List<net.kyori.adventure.text.Component> componentLore = lore.stream()
                    .map(TextUtils::colorize)
                    .map(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .collect(Collectors.toList());
            meta.lore(componentLore);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder enchanted() {
        return enchanted(true);
    }

    public ItemBuilder enchanted(boolean enchanted) {
        if (meta != null && enchanted) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder skull(String playerName) {
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        }
        return this;
    }

    public ItemBuilder leatherColor(Color color) {
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
