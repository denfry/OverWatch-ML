package net.denfry.owml.gui.subgui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelPunishmentSettingsGUI implements OverWatchGUI {
    private final OverWatchML plugin;
    private final ConfigManager configManager;
    private final int level;
    private final Inventory inventory;
    private final Map<Integer, String> slotToKey = new HashMap<>();

    public LevelPunishmentSettingsGUI(OverWatchML plugin, int level) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.level = level;
        this.inventory = Bukkit.createInventory(this, 54, "Settings for Level " + level);
    }

    @Override
    public void open(Player player) {
        refresh(player);
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        inventory.clear();
        slotToKey.clear();

        switch (level) {
            case 1:
                addOption(10, "mining_fatigue", Material.IRON_PICKAXE, "Mining Fatigue");
                addOption(14, "fake_diamonds", Material.COAL, "Fake Diamonds");
                addOption(28, "heavy_pickaxe", Material.NETHERITE_PICKAXE, "Heavy Pickaxe");
                break;
            case 2:
                addOption(10, "fake_ore_veins", Material.DIAMOND_ORE, "Fake Ore Veins");
                addOption(12, "inventory_drop", Material.DROPPER, "Inventory Drop");
                addOption(14, "xray_vision_blur", Material.GLASS, "X-Ray Vision Blur");
                addOption(28, "paranoia_mode", Material.CREEPER_HEAD, "Paranoia Mode");
                break;
            case 3:
                addOption(10, "temporary_kick", Material.IRON_DOOR, "Temporary Kick");
                addOption(12, "mining_license_suspension", Material.FILLED_MAP, "License Suspension");
                addOption(28, "fools_gold", Material.GOLD_ORE, "Fool's Gold");
                break;
            case 4:
                addOption(10, "extended_ban", Material.REDSTONE_BLOCK, "Extended Ban");
                addOption(28, "cursed_pickaxe", Material.ENCHANTED_BOOK, "Cursed Pickaxe");
                break;
            case 5:
                addOption(10, "long_term_ban", Material.BEDROCK, "Long-Term Ban");
                addOption(28, "stone_vision", Material.STONE, "Stone Vision");
                break;
            case 6:
                addOption(10, "permanent_ban", Material.TNT, "Permanent Ban");
                break;
        }

        inventory.setItem(49, ItemBuilder.material(Material.BARRIER).name("§cBack").build());
    }

    private void addOption(int slot, String key, Material material, String name) {
        boolean enabled = configManager.isPunishmentOptionEnabled(level, key);
        inventory.setItem(slot, ItemBuilder.material(enabled ? material : Material.GRAY_DYE)
                .name((enabled ? "§a" : "§7") + name)
                .lore(List.of("§7Status: " + (enabled ? "§aON" : "§cOFF"), "§7Click to toggle"))
                .build());
        slotToKey.put(slot, key);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 49) {
            GUINavigationStack.pop(player);
            return;
        }

        String key = slotToKey.get(slot);
        if (key != null) {
            boolean current = configManager.isPunishmentOptionEnabled(level, key);
            configManager.setPunishmentOptionEnabled(level, key, !current);
            refresh(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
