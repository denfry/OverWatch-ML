package net.denfry.owml.gui.subgui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.ModernMLManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * GUI for selecting players for ML analysis or training with pagination
 */
public class PlayerSelectorGUI {
    private static final int ROWS = 5;
    private static final int SLOTS_PER_PAGE = ROWS * 9;
    private static final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_BUTTON_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private final OverWatchML plugin;
    private final SelectionType type;
    public PlayerSelectorGUI(OverWatchML plugin, SelectionType type) {
        this.plugin = plugin;
        this.type = type;
    }

    /**
     * Handle clicks in the player selection GUI
     */
    public static void handleClick(Player player, int slot, Inventory inventory, OverWatchML plugin, SelectionType type) {

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);


        if (slot == PREV_PAGE_SLOT && inventory.getItem(PREV_PAGE_SLOT) != null) {

            new PlayerSelectorGUI(plugin, type).openInventory(player, currentPage - 1);
            return;
        } else if (slot == NEXT_PAGE_SLOT && inventory.getItem(NEXT_PAGE_SLOT) != null) {

            new PlayerSelectorGUI(plugin, type).openInventory(player, currentPage + 1);
            return;
        } else if (slot == BACK_BUTTON_SLOT) {

            new MLAnalysisGUI(plugin).openInventory(player);
            return;
        } else if (slot == BACK_BUTTON_SLOT - 1 && (type == SelectionType.TRAIN_NORMAL || type == SelectionType.TRAIN_CHEATER)) {

            return;
        }


        if (slot < SLOTS_PER_PAGE) {
            ItemStack clicked = inventory.getItem(slot);
            if (clicked != null && clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                    if (target != null) {
                        ModernMLManager mlManager = plugin.getMLManager();

                        // Check if display name starts with gray color code (В§7)
                        String displayName = meta.getDisplayName();
                        if (displayName != null && displayName.startsWith("В§7")) {
                            player.sendMessage(Component.text("This player cannot be selected. They are already in another category.").color(NamedTextColor.RED));
                            return;
                        }


                        boolean inAnalysis = mlManager.getPlayersUnderAnalysis().contains(target.getUniqueId());
                        boolean inTraining = mlManager.getPlayersInTraining().containsKey(target.getUniqueId());

                        if (inAnalysis || inTraining) {
                            player.sendMessage(Component.text("This player is already in analysis or training. Cannot select.").color(NamedTextColor.RED));
                            player.closeInventory();
                            new PlayerSelectorGUI(plugin, type).openInventory(player, currentPage);
                            return;
                        }

                        switch (type) {
                            case ANALYZE -> {

                                if (mlManager.getPlayersUnderAnalysis().size() >= MLAnalysisGUI.MAX_PLAYERS) {
                                    player.sendMessage(Component.text("Maximum number of players already being analyzed!").color(NamedTextColor.RED));
                                    return;
                                }


                                mlManager.startAnalysis(target);
                                player.sendMessage(Component.text("Started analyzing " + target.getName()).color(NamedTextColor.GREEN));
                            }
                            case TRAIN_NORMAL, TRAIN_CHEATER -> {

                                boolean isCheater = type == SelectionType.TRAIN_CHEATER;


                                long currentCount = mlManager.getPlayersInTraining().entrySet().stream().filter(e -> e.getValue() == isCheater).count();

                                if (currentCount >= MLAnalysisGUI.MAX_PLAYERS) {
                                    player.sendMessage(Component.text("Maximum number of players already in training!").color(NamedTextColor.RED));
                                    return;
                                }


                                openTrainingConfirmationGUI(player, target, isCheater, plugin);
                                return;
                            }
                        }


                        new MLAnalysisGUI(plugin).openInventory(player);
                    }
                }
            }
        }
    }

    /**
     * Opens a confirmation dialog for training data selection
     */
    private static void openTrainingConfirmationGUI(Player staff, Player target, boolean isCheater, OverWatchML plugin) {

        Inventory confirmInventory = Bukkit.createInventory(null, 3 * 9, Component.text(isCheater ? "вљ  CONFIRM CHEATER TRAINING вљ " : "вљ  CONFIRM NORMAL TRAINING вљ ").color(isCheater ? NamedTextColor.RED : NamedTextColor.GREEN));


        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 3 * 9; i++) {
            confirmInventory.setItem(i, filler);
        }


        List<String> warningLore = new ArrayList<>();
        warningLore.add("В§cBy proceeding, you confirm that:");

        if (isCheater) {
            warningLore.add("В§fвЂў You have DEFINITIVE PROOF that");
            warningLore.add("В§f  " + target.getName() + " is using X-ray");
            warningLore.add("В§fвЂў You understand incorrect labeling will");
            warningLore.add("В§f  significantly damage the ML model");
            warningLore.add("");
            warningLore.add("В§eRemember: It's better to use yourself or");
            warningLore.add("В§etrusted staff to generate cheater data");
        } else {
            warningLore.add("В§fвЂў You are CERTAIN that");
            warningLore.add("В§f  " + target.getName() + " is a legitimate player");
            warningLore.add("В§fвЂў You understand incorrect labeling will");
            warningLore.add("В§f  cause false positives/negatives");
            warningLore.add("");
            warningLore.add("В§eRemember: It's better to use yourself or");
            warningLore.add("В§etrusted staff for normal player data");
        }

        ItemStack warningSign = createItem(Material.OAK_SIGN, isCheater ? "В§cвљ  TRAINING AS CHEATER вљ " : "В§aвљ  TRAINING AS NORMAL PLAYER вљ ", warningLore);
        confirmInventory.setItem(4, warningSign);


        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text("Selected Player: " + target.getName()).color(isCheater ? NamedTextColor.RED : NamedTextColor.GREEN));
        playerHead.setItemMeta(meta);
        confirmInventory.setItem(13, playerHead);


        ItemStack confirmButton = createItem(Material.LIME_CONCRETE, "В§aвњ“ I UNDERSTAND - PROCEED", List.of("В§eClick to start training with", "В§e" + target.getName() + " as " + (isCheater ? "CHEATER" : "NORMAL PLAYER"), "", "В§cYou will be held responsible for", "В§cthe accuracy of this training data"));
        confirmInventory.setItem(11, confirmButton);


        ItemStack cancelButton = createItem(Material.RED_CONCRETE, "В§cвњ— CANCEL", List.of("В§eReturn to player selection"));
        confirmInventory.setItem(15, cancelButton);


        staff.openInventory(confirmInventory);


        plugin.getServer().getPluginManager().registerEvents(new TrainingConfirmationListener(staff, target, isCheater, plugin), plugin);
    }

    /**
     * Helper method to create an item
     */
    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name));

        if (!lore.isEmpty()) {
            List<Component> loreComponents = lore.stream().map(line -> {
                if (line.startsWith("В§")) {
                    return Component.text(line);
                } else {
                    return Component.text(line).color(NamedTextColor.GRAY);
                }
            }).collect(Collectors.toList());

            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Open the player selection GUI
     */
    public void openInventory(Player player) {
        openInventory(player, 0);
    }

    /**
     * Open the player selection GUI on a specific page
     */
    public void openInventory(Player player, int page) {

        playerPages.put(player.getUniqueId(), page);


        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());


        int invSize = 6 * 9;

        String title = switch (type) {
            case ANALYZE -> "рџ“Љ Select Player to Analyze";
            case TRAIN_NORMAL -> "рџ“€ Select Normal Player for Training";
            case TRAIN_CHEATER -> "рџ”Ќ Select Cheater for Training";
        };

        if (onlinePlayers.size() > SLOTS_PER_PAGE) {
            title += " (Page " + (page + 1) + ")";
        }

        Inventory inventory = Bukkit.createInventory(null, invSize, Component.text(title).color(NamedTextColor.DARK_AQUA));


        if (onlinePlayers.isEmpty()) {
            ItemStack noPlayers = createItem(Material.BARRIER, "No Players Available", List.of("There are no players available for selection"));
            inventory.setItem(22, noPlayers);
        }


        ModernMLManager mlManager = plugin.getMLManager();


        int totalPages = (int) Math.ceil(onlinePlayers.size() / (double) SLOTS_PER_PAGE);
        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, onlinePlayers.size());


        for (int i = startIndex; i < endIndex; i++) {
            Player target = onlinePlayers.get(i);
            int slot = i - startIndex;


            ItemStack head = createPlayerHead(target, player, mlManager);
            inventory.setItem(slot, head);
        }


        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createItem(Material.ARROW, "Previous Page", List.of("Go to page " + page)));
        }


        inventory.setItem(BACK_BUTTON_SLOT, createItem(Material.BARRIER, "Back to ML Analysis", List.of("Return to the ML Analysis menu")));


        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createItem(Material.ARROW, "Next Page", List.of("Go to page " + (page + 2))));
        }


        if (type == SelectionType.TRAIN_NORMAL) {
            inventory.setItem(BACK_BUTTON_SLOT - 1, createNormalTrainingInstructionsItem());
        } else if (type == SelectionType.TRAIN_CHEATER) {
            inventory.setItem(BACK_BUTTON_SLOT - 1, createCheaterTrainingInstructionsItem());
        }


        player.openInventory(inventory);
    }

    /**
     * Create instruction item for normal player training
     */
    private ItemStack createNormalTrainingInstructionsItem() {
        return createItem(Material.BOOK, "В§aВ§lрџ“‹ How To Train Normal Players", List.of("В§eВ§lFor good NORMAL player training data:", "В§fвЂў Mine normally (straight, branch mine)", "В§fвЂў Explore caves naturally", "В§fвЂў Mine various ore types, not just diamonds", "В§fвЂў Play legitimately without any cheats", "В§cвЂў DO NOT stay AFK during training", "В§cвЂў DO NOT use unusual mining patterns", "", "В§eВ§lWHO TO SELECT:", "В§fвЂў Yourself (best option)", "В§fвЂў Trusted staff members", "В§fвЂў Players you're 100% confident are legitimate", "В§cвЂў NEVER guess or assume a player is legitimate"));
    }

    /**
     * Create instruction item for cheater training
     */
    private ItemStack createCheaterTrainingInstructionsItem() {
        return createItem(Material.ENCHANTED_BOOK, "В§cВ§lрџ“‹ How To Train Cheater Data", List.of("В§eВ§lFor accurate CHEATER training data:", "В§fвЂў Enable X-ray yourself", "В§fвЂў Mine directly to valuable ores", "В§fвЂў Skip iron/coal, focus on diamonds", "В§fвЂў Use unusual mining patterns", "В§fвЂў Dig straight to ores through stone", "В§cвЂў DO NOT stay AFK during training", "В§cвЂў DO NOT mine randomly or normally", "", "В§eВ§lWHO TO SELECT:", "В§fвЂў Yourself using X-ray (best option)", "В§fвЂў Trusted staff mimicking X-ray behavior", "В§fвЂў ONLY players with confirmed X-ray use", "В§cвЂў NEVER label someone as cheater without proof"));
    }

    /**
     * Create a player head item with appropriate metadata
     */
    private ItemStack createPlayerHead(Player target, Player viewer, ModernMLManager mlManager) {

        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);


        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);


        boolean inAnalysis = mlManager.getPlayersUnderAnalysis().contains(target.getUniqueId());
        boolean inTrainingNormal = mlManager.getPlayersInTraining().containsKey(target.getUniqueId()) && !mlManager.getPlayersInTraining().get(target.getUniqueId());
        boolean inTrainingCheater = mlManager.getPlayersInTraining().containsKey(target.getUniqueId()) && mlManager.getPlayersInTraining().get(target.getUniqueId());


        boolean canBeSelected = switch (type) {
            case ANALYZE -> !inAnalysis && !inTrainingNormal && !inTrainingCheater;
            case TRAIN_NORMAL -> !inAnalysis && !inTrainingNormal && !inTrainingCheater;
            case TRAIN_CHEATER -> !inAnalysis && !inTrainingNormal && !inTrainingCheater;
        };


        List<Component> loreComponents = new ArrayList<>();


        NamedTextColor nameColor = NamedTextColor.WHITE;
        if (canBeSelected) {
            switch (type) {
                case ANALYZE -> {
                    nameColor = NamedTextColor.AQUA;
                    loreComponents.add(Component.text("Click to analyze this player").color(NamedTextColor.YELLOW));
                    loreComponents.add(Component.text("for potential X-ray usage").color(NamedTextColor.YELLOW));
                }
                case TRAIN_NORMAL -> {
                    nameColor = NamedTextColor.GREEN;
                    loreComponents.add(Component.text("Click to use this player's data").color(NamedTextColor.YELLOW));
                    loreComponents.add(Component.text("for training as a NORMAL player").color(NamedTextColor.GREEN));


                    loreComponents.add(Component.empty());
                    loreComponents.add(Component.text("вљ  WARNING:").color(NamedTextColor.RED));
                    loreComponents.add(Component.text("Only select players you are CERTAIN").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("are legitimate. Bad training data").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("will reduce detection accuracy.").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.empty());
                    loreComponents.add(Component.text("Recommended: Use yourself or other").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("trusted staff members for training.").color(NamedTextColor.WHITE));
                }
                case TRAIN_CHEATER -> {
                    nameColor = NamedTextColor.RED;
                    loreComponents.add(Component.text("Click to use this player's data").color(NamedTextColor.YELLOW));
                    loreComponents.add(Component.text("for training as a CHEATER").color(NamedTextColor.RED));


                    loreComponents.add(Component.empty());
                    loreComponents.add(Component.text("вљ  WARNING:").color(NamedTextColor.RED));
                    loreComponents.add(Component.text("Only select players you have CONFIRMED").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("are using X-ray. Never guess or assume.").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("False data will harm the ML system.").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.empty());
                    loreComponents.add(Component.text("Recommended: Ask trusted staff members").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("or use X-ray yourself and mine diamonds").color(NamedTextColor.WHITE));
                    loreComponents.add(Component.text("to generate accurate training data.").color(NamedTextColor.WHITE));
                }
            }
        } else {
            nameColor = NamedTextColor.GRAY;
            loreComponents.add(Component.text("Cannot select this player").color(NamedTextColor.RED));


            if (inAnalysis) {
                loreComponents.add(Component.text("Player is already being analyzed").color(NamedTextColor.RED));
            }
            if (inTrainingNormal) {
                loreComponents.add(Component.text("Player is in training as NORMAL").color(NamedTextColor.RED));
            }
            if (inTrainingCheater) {
                loreComponents.add(Component.text("Player is in training as CHEATER").color(NamedTextColor.RED));
            }
        }

        meta.displayName(Component.text(target.getName()).color(nameColor));
        meta.lore(loreComponents);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Type of selection to perform
     */
    public enum SelectionType {
        ANALYZE, TRAIN_NORMAL, TRAIN_CHEATER
    }

    /**
     * Listener class to handle the confirmation dialog
     */
    public static class TrainingConfirmationListener implements org.bukkit.event.Listener {

        private final Player staff;
        private final Player target;
        private final boolean isCheater;
        private final OverWatchML plugin;

        public TrainingConfirmationListener(Player staff, Player target, boolean isCheater, OverWatchML plugin) {
            this.staff = staff;
            this.target = target;
            this.isCheater = isCheater;
            this.plugin = plugin;
        }

        @org.bukkit.event.EventHandler
        public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
            if (event.getWhoClicked() != staff) return;

            // Use reflection for version compatibility
            String title = event.getView().title().toString();
            if (title.contains("CONFIRM")) {
                event.setCancelled(true);

                if (event.getRawSlot() == 11) {

                    ModernMLManager mlManager = plugin.getMLManager();
                    mlManager.startTraining(target, isCheater);

                    staff.sendMessage(Component.text("Started training " + target.getName() + " as " + (isCheater ? "cheater" : "normal player")).color(NamedTextColor.GREEN));


                    plugin.getLogger().info(staff.getName() + " confirmed training " + target.getName() + " as " + (isCheater ? "CHEATER" : "NORMAL PLAYER"));


                    staff.closeInventory();
                    org.bukkit.event.HandlerList.unregisterAll(this);


                    new MLAnalysisGUI(plugin).openInventory(staff);

                } else if (event.getRawSlot() == 15) {

                    staff.closeInventory();
                    org.bukkit.event.HandlerList.unregisterAll(this);
                    new PlayerSelectorGUI(plugin, isCheater ? SelectionType.TRAIN_CHEATER : SelectionType.TRAIN_NORMAL).openInventory(staff);
                }
            }
        }

        @org.bukkit.event.EventHandler
        public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
            if (event.getPlayer() == staff) {

                org.bukkit.event.HandlerList.unregisterAll(this);
            }
        }
    }

}
