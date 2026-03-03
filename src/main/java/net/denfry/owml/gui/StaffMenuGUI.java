package net.denfry.owml.gui;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.modern.GUIEffects;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.ItemBuilder;
import net.denfry.owml.gui.modern.OverWatchGUI;
import net.denfry.owml.utils.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern implementation of StaffMenuGUI using OverWatchGUI interface.
 */
public class StaffMenuGUI implements OverWatchGUI {
    private static OverWatchML plugin;
    private final Inventory inventory;

    public StaffMenuGUI() {
        String title = TextUtils.color("&bвњ§ &9&lOverWatch&8-&c&lXRay &6Control Panel &bвњ§");
        this.inventory = Bukkit.createInventory(this, 54, title);
        initializeItems();
    }

    public static void setPlugin(OverWatchML overwatchML) {
        plugin = overwatchML;
    }

    private void initializeItems() {
        createBorder();
        addSectionTitles();
        addMainButtons();
        addAppealsButton();
        addDecorativeElements();
        addPluginInfo();
    }

    private void createBorder() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 1; i <= 4; i++) {
            inventory.setItem(i * 9, ItemBuilder.material(Material.CYAN_STAINED_GLASS_PANE).name(" ").build());
            inventory.setItem(i * 9 + 8, ItemBuilder.material(Material.CYAN_STAINED_GLASS_PANE).name(" ").build());
        }

        inventory.setItem(0, ItemBuilder.material(Material.PURPLE_STAINED_GLASS_PANE).name("&dOverWatch-ML").enchanted(true).build());
        inventory.setItem(8, ItemBuilder.material(Material.PURPLE_STAINED_GLASS_PANE).name("&dOverWatch-ML").enchanted(true).build());
        inventory.setItem(45, ItemBuilder.material(Material.PURPLE_STAINED_GLASS_PANE).name("&dOverWatch-ML").enchanted(true).build());
        inventory.setItem(53, ItemBuilder.material(Material.PURPLE_STAINED_GLASS_PANE).name("&dOverWatch-ML").enchanted(true).build());
    }

    private void addSectionTitles() {
        inventory.setItem(2, ItemBuilder.material(Material.OBSERVER).name("&b&lвњ¦ Analysis & Monitoring вњ¦").enchanted(true).build());
        inventory.setItem(6, ItemBuilder.material(Material.LODESTONE).name("&6&lвњ¦ Management & Configuration вњ¦").enchanted(true).build());
    }

    private void addMainButtons() {
        inventory.setItem(10, ItemBuilder.material(Material.PLAYER_HEAD)
                .skull("MHF_Question")
                .name("&a&lрџ“Љ Player Analytics")
                .lore(List.of("&7Detailed mining statistics and player data", "&fвЂў View ore mining trends", "&fвЂў Track player mining history", "", "&aВ» Click to access analytics dashboard"))
                .enchanted(true)
                .build());

        inventory.setItem(19, ItemBuilder.material(Material.COMPASS)
                .name("&c&lрџ”Ќ Suspicious Activity")
                .lore(List.of("&7Monitor potentially suspicious players", "&fвЂў Recent flagged behaviors", "&fвЂў Abnormal mining patterns", "&fвЂў X-Ray probability assessment", "", "&cВ» Click to investigate"))
                .enchanted(true)
                .build());

        inventory.setItem(28, ItemBuilder.material(Material.DRAGON_HEAD)
                .name("&5&lрџ¤– ML Analysis")
                .lore(List.of("&7Advanced reasoning-based detection", "&fвЂў Mining style recognition", "&fвЂў Gets smarter over time [With More training data]", "&fвЂў Ore discovery pattern detection", "&fвЂў Step-by-step reasoning engine", "", "&5В» Click to access ML tools"))
                .enchanted(true)
                .build());

        inventory.setItem(14, ItemBuilder.material(Material.WRITABLE_BOOK)
                .name("&6&lвљ– Punishment System")
                .lore(List.of("&7Configure automated enforcement", "&fвЂў Manage punishment tiers", "&fвЂў Configure response actions", "&fвЂў Set escalation thresholds", "", "&6В» Click to manage"))
                .enchanted(true)
                .build());

        inventory.setItem(23, ItemBuilder.material(Material.END_CRYSTAL)
                .name("&d&lрџ”” Discord Webhook")
                .lore(List.of("&7Real-time Discord notifications", "&fвЂў Customize alert channels", "&fвЂў Configure notification types", "", "&dВ» Click to configure"))
                .enchanted(true)
                .build());

        inventory.setItem(32, ItemBuilder.material(Material.DIAMOND_PICKAXE)
                .name("&b&lв›Џ Ore Management")
                .lore(List.of("&7Configure ore tracking system", "&fвЂў Set tracked ore types", "&fвЂў Configure detection thresholds", "&fвЂў Manage decoy ore placement", "", "&bВ» Click to configure"))
                .enchanted(true)
                .build());

        inventory.setItem(25, ItemBuilder.material(Material.COMMAND_BLOCK)
                .name("&d&lвљ™ Plugin Configuration")
                .lore(List.of("&7Advanced plugin settings", "&fвЂў Core functionality options", "&fвЂў Staff permission controls", "", "&dВ» Click to configure"))
                .enchanted(true)
                .build());
    }

    private void addAppealsButton() {
        int pendingCount = 0;
        if (plugin != null && plugin.getAppealManager() != null) {
            pendingCount = plugin.getAppealManager().getPendingAppeals().size();
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Manage player punishment appeals");
        lore.add("");
        if (pendingCount > 0) {
            lore.add("&fвЂў &e" + pendingCount + " pending " + (pendingCount == 1 ? "appeal" : "appeals"));
        } else {
            lore.add("&fвЂў No pending appeals");
        }
        lore.add("&fвЂў Review player submissions");
        lore.add("&fвЂў Approve or deny appeals");
        lore.add("");
        lore.add("&6В» Click to manage appeals");

        inventory.setItem(34, ItemBuilder.material(pendingCount > 0 ? Material.FILLED_MAP : Material.MAP)
                .name("&6&lрџ“‹ Player Appeals")
                .lore(lore)
                .enchanted(pendingCount > 0)
                .build());
    }

    private void addDecorativeElements() {
        inventory.setItem(4, ItemBuilder.material(Material.NETHER_STAR)
                .name("&b&lOverWatch-ML")
                .lore(List.of("&eAdvanced X-Ray Protection", "&ePowerful anti-cheat system"))
                .enchanted(true)
                .build());

        inventory.setItem(37, ItemBuilder.material(Material.REDSTONE).name("&cActive Protection").build());
        inventory.setItem(43, ItemBuilder.material(Material.COMPASS).name("&9Player Monitoring").build());
        inventory.setItem(40, ItemBuilder.material(Material.BARRIER).name("&4Cheat Prevention").build());
    }

    private void addPluginInfo() {
        String version = "v" + (plugin != null ? plugin.getDescription().getVersion() : "1.0.0");
        inventory.setItem(49, ItemBuilder.material(Material.BOOK)
                .name("&6&lPlugin Information")
                .lore(List.of("&eOverWatchML " + version, "&bDeveloped by OverWatch Team", "", "&aActive protection systems:", "&fвњ“ Mining pattern analysis", "&fвњ“ ML-based detection", "&fвњ“ Decoy ore system", "&fвњ“ Player appeals system"))
                .build());
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {
        // Nothing to clean up
    }

    @Override
    public void refresh(Player player) {
        initializeItems();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        switch (slot) {
            case 10 -> player.sendMessage("Analytics dashboard...");
            case 19 -> player.sendMessage("Suspicious activity list...");
            case 28 -> player.sendMessage("ML tools...");
            case 14 -> player.sendMessage("Punishment manager...");
            case 23 -> player.sendMessage("Webhook configuration...");
            case 32 -> player.sendMessage("Ore management...");
            case 25 -> player.sendMessage("Plugin settings...");
            case 34 -> player.sendMessage("Player appeals...");
            case 4 -> refresh(player);
        }
    }

    public void openInventory(Player player) {
        open(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
