package net.denfry.owml.gui;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.gui.subgui.AppealDetailsGUI;
import net.denfry.owml.gui.subgui.AppealGUI;
import net.denfry.owml.gui.subgui.AutoAnalysisGUI;
import net.denfry.owml.gui.subgui.AutoSaveSettingsGUI;
import net.denfry.owml.gui.subgui.ConfigSettingsGUI;
import net.denfry.owml.gui.subgui.DecoySettingsGUI;
import net.denfry.owml.gui.subgui.LevelPunishmentSettingsGUI;
import net.denfry.owml.gui.subgui.MLAnalysisGUI;
import net.denfry.owml.gui.subgui.MLReportsGUI;
import net.denfry.owml.gui.subgui.OreConfigGUI;
import net.denfry.owml.gui.subgui.PlayerMiningStatsGUI;
import net.denfry.owml.gui.subgui.PlayerSelectorGUI;
import net.denfry.owml.gui.subgui.PlayerStatsMainGUI;
import net.denfry.owml.gui.subgui.PunishmentSettingsGUI;
import net.denfry.owml.gui.subgui.StaffSettingsGUI;
import net.denfry.owml.gui.subgui.SuspiciousPlayersGUI;
import net.denfry.owml.gui.subgui.WebhookSettingsGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Updated GuiListener with appeal system integration
 */
public class GuiListener implements Listener {
    private final OverWatchML plugin;

    public GuiListener(OverWatchML plugin) {
        this.plugin = plugin;
        StaffMenuGUI.setPlugin(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titlePlainText = PlainTextComponentSerializer.plainText().serialize(event.getView().title());


        if (titlePlainText.contains("OverWatchML Control Panel") || titlePlainText.contains("рџ”Ќ Suspicious Activity") || titlePlainText.contains("вљ– Punishment System") || titlePlainText.contains("вљ™ Plugin Configuration") || titlePlainText.contains("вљ™ Decoy Settings") || titlePlainText.contains("вљ™ Auto-Save Settings") || titlePlainText.contains("вљ™ Staff Settings") || titlePlainText.contains("рџ“Љ Player Analytics") || titlePlainText.contains("рџ¤– ML Analysis") || titlePlainText.contains("рџ“‹ ML Analysis Reports") || titlePlainText.contains("вљ™ Auto ML Analysis Settings") || titlePlainText.contains("рџ“Љ Select Player to Analyze") || titlePlainText.contains("рџ“€ Select Normal Player for Training") || titlePlainText.contains("рџ”Ќ Select Cheater for Training") || titlePlainText.contains("Mining Stats") || titlePlainText.contains("Settings for Level") || titlePlainText.contains("в›Џ Ore Management") || titlePlainText.contains("рџ”” Discord Webhook Settings") || titlePlainText.contains("рџ“‹ OverWatchML Appeal System") || titlePlainText.contains("Appeal #") && titlePlainText.contains("Details")) {

            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

            String plainName = clicked.getItemMeta().getDisplayName();
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);


                if (titlePlainText.contains("OverWatchML Control Panel")) {
                // Use new MainFrame handler
                if (MainFrame.handleMainFrameClick(player, slot, plugin)) {
                    return;
                }
            }

            if (titlePlainText.contains("Alert Management Panel")) {
                // Use new AlertPanel handler
                if (AlertPanel.handleAlertPanelClick(player, slot, plugin)) {
                    return;
                }
            }

            if (titlePlainText.contains("OverWatchML Control Panel")) {

                if (plainName.contains("Player Analytics")) {
                    new PlayerStatsMainGUI(0).openInventory(player);
                } else if (plainName.contains("Suspicious Activity")) {
                    new SuspiciousPlayersGUI(0).openInventory(player);
                } else if (plainName.contains("Punishment System")) {
                    new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                } else if (plainName.contains("Ore Management")) {
                    new OreConfigGUI(plugin.getConfigManager()).openInventory(player);
                } else if (plainName.contains("Plugin Configuration")) {
                    if (player.hasPermission(ConfigSettingsGUI.PERMISSION)) {
                        new ConfigSettingsGUI(plugin.getConfigManager(), plugin).openInventory(player);
                    } else {
                        player.sendMessage(Component.text("You don't have permission to access the config settings.").color(NamedTextColor.RED));
                    }
                } else if (plainName.contains("Discord Webhook")) {
                    if (player.hasPermission(WebhookSettingsGUI.PERMISSION)) {
                        new WebhookSettingsGUI(plugin.getConfigManager(), plugin).openInventory(player);
                    } else {
                        player.sendMessage(Component.text("You don't have permission to access webhook settings.").color(NamedTextColor.RED));
                    }
                } else if (plainName.contains("ML Analysis")) {
                    if (player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                        new MLAnalysisGUI(plugin).openInventory(player);
                    } else {
                        player.sendMessage(Component.text("You don't have permission to access ML Analysis.").color(NamedTextColor.RED));
                    }
                } else if (plainName.contains("Player Appeals")) {
                    if (player.hasPermission("owml.gui_Appeal")) {
                        new AppealGUI(plugin, plugin.getAppealManager(), 0).openInventory(player);
                    } else {
                        player.sendMessage(Component.text("You don't have permission to access the appeals system.").color(NamedTextColor.RED));
                    }
                } else if (clicked.getType() == Material.NETHER_STAR || clicked.getType() == Material.BOOK || clicked.getType() == Material.REDSTONE || clicked.getType() == Material.COMPASS || clicked.getType() == Material.BARRIER || clicked.getType() == Material.OBSERVER || clicked.getType() == Material.LODESTONE) {

                }
            } else if (titlePlainText.contains("рџ¤– ML Analysis")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Analysis.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                MLAnalysisGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin);
            } else if (titlePlainText.contains("вљ™ Auto ML Analysis Settings")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Analysis settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                AutoAnalysisGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin);
            } else if (titlePlainText.contains("рџ“Љ Select Player to Analyze")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Analysis.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                PlayerSelectorGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin, PlayerSelectorGUI.SelectionType.ANALYZE);
            } else if (titlePlainText.contains("рџ“‹ ML Analysis Reports")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Reports.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                MLReportsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin);
            } else if (titlePlainText.contains("рџ“€ Select Normal Player for Training")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Analysis.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                PlayerSelectorGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin, PlayerSelectorGUI.SelectionType.TRAIN_NORMAL);
            } else if (titlePlainText.contains("рџ”Ќ Select Cheater for Training")) {

                if (!player.hasPermission(MLAnalysisGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access ML Analysis.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                PlayerSelectorGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin, PlayerSelectorGUI.SelectionType.TRAIN_CHEATER);
            } else if (titlePlainText.contains("рџ“‹ OverWatchML Appeal System")) {

                if (!player.hasPermission("owml.gui_Appeal")) {
                    player.sendMessage(Component.text("You don't have permission to access the appeals system.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                AppealGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin, plugin.getAppealManager());
            } else if (titlePlainText.contains("Appeal #") && titlePlainText.contains("Details")) {

                if (!player.hasPermission("owml.gui_Appeal")) {
                    player.sendMessage(Component.text("You don't have permission to access the appeals system.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                AppealDetailsGUI.handleClick(player, event.getRawSlot(), event.getClick(), event.getInventory(), plugin, plugin.getAppealManager());
            } else if (titlePlainText.contains("рџ”” Discord Webhook Settings")) {

                if (!player.hasPermission(WebhookSettingsGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access webhook settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                WebhookSettingsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin.getConfigManager(), plugin);
            } else if (titlePlainText.contains("вљ– Punishment System")) {

                if (clicked.getType() == Material.GREEN_WOOL || clicked.getType() == Material.RED_WOOL) {

                    int level = (slot / 9) + 1;


                    boolean currentlyEnabled = plugin.getConfigManager().isPunishmentEnabled(level);


                    plugin.getConfigManager().setPunishmentEnabled(level, !currentlyEnabled);


                    if (currentlyEnabled) {


                        plugin.getPunishmentManager().onPunishmentLevelDisabled(level, player.getName());


                        player.sendMessage(Component.text("Punishment level " + level + " disabled. All affected players have been notified.", NamedTextColor.GREEN));


                        if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                            plugin.getWebhookManager().sendStaffActionLog(player.getName(), "Disabled Punishment Level " + level, "All affected players");
                        }
                    } else {

                        player.sendMessage(Component.text("Punishment level " + level + " enabled.", NamedTextColor.GREEN));


                        plugin.getLogger().info("Admin " + player.getName() + " enabled punishment level " + level);


                        if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                            plugin.getWebhookManager().sendStaffActionLog(player.getName(), "Enabled Punishment Level " + level, "Server-wide setting");
                        }
                    }


                    new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                } else if (clicked.getType() == Material.BELL || (clicked.getType() == Material.GRAY_DYE && plainName.contains("Admin Alerts"))) {

                    int level = (slot / 9) + 1;


                    boolean current = plugin.getConfigManager().isPunishmentOptionEnabled(level, "admin_alert");
                    plugin.getConfigManager().setPunishmentOptionEnabled(level, "admin_alert", !current);


                    plugin.getLogger().info("Admin " + player.getName() + " " + (current ? "disabled" : "enabled") + " admin alerts for punishment level " + level);


                    if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                        plugin.getWebhookManager().sendStaffActionLog(player.getName(), (current ? "Disabled" : "Enabled") + " Admin Alerts for Punishment Level " + level, "Configuration Change");
                    }


                    new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                } else if (clicked.getType() == Material.BOOK || (clicked.getType() == Material.GRAY_DYE && plainName.contains("Warning Messages"))) {

                    int level = (slot / 9) + 1;


                    boolean current = plugin.getConfigManager().isPunishmentOptionEnabled(level, "warning_message");
                    plugin.getConfigManager().setPunishmentOptionEnabled(level, "warning_message", !current);


                    if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                        plugin.getWebhookManager().sendStaffActionLog(player.getName(), (current ? "Disabled" : "Enabled") + " Warning Messages for Punishment Level " + level, "Configuration Change");
                    }


                    new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                } else if (plainName.contains("Advanced settings")) {
                    int level = (slot / 9) + 1;
                    new LevelPunishmentSettingsGUI(plugin.getConfigManager(), level).openInventory(player);
                } else if (plainName.contains("Icon Guide")) {

                } else if (plainName.contains("Back to")) {
                    new StaffMenuGUI().openInventory(player);
                }
            } else if (titlePlainText.contains("Settings for Level")) {

                int level = Integer.parseInt(titlePlainText.substring(titlePlainText.indexOf("Level ") + 6, titlePlainText.indexOf(" Punishment")));


                if (plainName.contains("Back to")) {
                    new PunishmentSettingsGUI(plugin.getConfigManager()).openInventory(player);
                } else {
                    LevelPunishmentSettingsGUI.handleClick(player, event.getRawSlot(), level, plugin.getConfigManager());
                }
            } else if (titlePlainText.contains("рџ“Љ Player Analytics")) {

                PlayerStatsMainGUI.handleClick(player, event.getRawSlot(), event.getInventory());
            } else if (titlePlainText.contains("Mining Stats")) {

                PlayerMiningStatsGUI.handleClick(player, event.getRawSlot());
            } else if (titlePlainText.contains("в›Џ Ore Management")) {

                if (plainName.contains("Back to")) {
                    new StaffMenuGUI().openInventory(player);
                    return;
                }


                Material oreMaterial = clicked.getType();

                if (plugin.getConfigManager().getNaturalOres().contains(oreMaterial)) {

                    plugin.getConfigManager().getNaturalOres().remove(oreMaterial);
                    plugin.getConfig().set("ores.natural", plugin.getConfigManager().getNaturalOres().stream().map(Material::name).toList());
                    plugin.saveConfig();
                    player.sendMessage(Component.text(oreMaterial.name() + " removed from natural ores.").color(NamedTextColor.YELLOW));


                    if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                        plugin.getWebhookManager().sendStaffActionLog(player.getName(), "Ore Configuration", "Removed " + oreMaterial.name() + " from natural ores");
                    }
                } else {

                    plugin.getConfigManager().getNaturalOres().add(oreMaterial);
                    plugin.getConfig().set("ores.natural", plugin.getConfigManager().getNaturalOres().stream().map(Material::name).toList());
                    plugin.saveConfig();
                    player.sendMessage(Component.text(oreMaterial.name() + " added to natural ores.").color(NamedTextColor.GREEN));


                    if (plugin.getConfigManager().isWebhookAlertEnabled("staff_actions")) {
                        plugin.getWebhookManager().sendStaffActionLog(player.getName(), "Ore Configuration", "Added " + oreMaterial.name() + " to natural ores");
                    }
                }

                new OreConfigGUI(plugin.getConfigManager()).openInventory(player);
            } else if (titlePlainText.contains("рџ”Ќ Suspicious Activity")) {

                SuspiciousPlayersGUI.handleClick(player, event.getRawSlot(), event.getInventory());
            } else if (titlePlainText.equals("вљ™ Plugin Configuration")) {

                if (!player.hasPermission(ConfigSettingsGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access config settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }


                ConfigSettingsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), plugin.getConfigManager(), plugin);
            } else if (titlePlainText.equals("вљ™ Decoy Settings")) {

                if (!player.hasPermission(ConfigSettingsGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access config settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }

                DecoySettingsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), event.getClick(), plugin.getConfigManager(), plugin);
            } else if (titlePlainText.equals("вљ™ Auto-Save Settings")) {

                if (!player.hasPermission(ConfigSettingsGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access config settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }

                AutoSaveSettingsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), event.getClick(), plugin.getConfigManager(), plugin);
            } else if (titlePlainText.equals("вљ™ Staff Settings")) {

                if (!player.hasPermission(ConfigSettingsGUI.PERMISSION)) {
                    player.sendMessage(Component.text("You don't have permission to access config settings.").color(NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }

                StaffSettingsGUI.handleClick(player, event.getRawSlot(), event.getInventory(), event.getClick(), plugin.getConfigManager(), plugin);
            }

        }
    }

}
