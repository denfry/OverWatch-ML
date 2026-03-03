package net.denfry.owml.commands.subcommands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class UpdateCommand {

    private final OverWatchML plugin;
    private final UpdateChecker updateChecker;

    public UpdateCommand(OverWatchML plugin, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("owml.autoupdate")) {
            player.sendMessage(Component.text("You don't have permission to auto-update the plugin.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /" + label + " update [check|auto|apply]").color(NamedTextColor.GREEN));
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "check":
                player.sendMessage(Component.text("Checking for updates...").color(NamedTextColor.YELLOW));
                // Use fresh check to bypass cache
                updateChecker.getVersionFresh(version -> {
                    String currentVersion = plugin.getDescription().getVersion();
                    plugin.getLogger().info("[UpdateChecker] Current version: " + currentVersion + ", Remote version: " + version);

                    // Check if remote version is newer
                    boolean isRemoteNewer = updateChecker.isNewerVersion(version, currentVersion);
                    plugin.getLogger().info("[UpdateChecker] Is remote version newer? " + isRemoteNewer);

                    if (isRemoteNewer) {
                        player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text(" OverWatch-ML: New update available!").color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text(" Current version: ").color(NamedTextColor.GREEN).append(Component.text(currentVersion).color(NamedTextColor.RED)));
                        player.sendMessage(Component.text(" New version: ").color(NamedTextColor.GREEN).append(Component.text(version).color(NamedTextColor.GREEN)));
                        player.sendMessage(Component.text(" Type '/OverWatch update auto' to download the update").color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));
                    } else {
                        if (!version.equalsIgnoreCase(currentVersion)) {
                            player.sendMessage(Component.text("Your version (").color(NamedTextColor.YELLOW)
                                .append(Component.text(currentVersion).color(NamedTextColor.GREEN))
                                .append(Component.text(") is newer than remote version (").color(NamedTextColor.YELLOW))
                                .append(Component.text(version).color(NamedTextColor.RED))
                                .append(Component.text(")").color(NamedTextColor.YELLOW)));
                        } else {
                            player.sendMessage(Component.text("OverWatchML is up to date!").color(NamedTextColor.GREEN));
                        }
                    }
                });
                break;

            case "auto":
                if (!updateChecker.isUpdateAvailable()) {
                    player.sendMessage(Component.text("Checking for updates first...").color(NamedTextColor.YELLOW));
                    updateChecker.getVersionFresh(version -> {
                        String currentVersion = plugin.getDescription().getVersion();
                        plugin.getLogger().info("[UpdateChecker] Current version: " + currentVersion + ", Remote version: " + version);

                        boolean isRemoteNewer = updateChecker.isNewerVersion(version, currentVersion);
                        plugin.getLogger().info("[UpdateChecker] Is remote version newer? " + isRemoteNewer);

                        if (isRemoteNewer) {
                            downloadUpdate(player);
                        } else {
                            if (!version.equalsIgnoreCase(currentVersion)) {
                                player.sendMessage(Component.text("Your version (").color(NamedTextColor.YELLOW)
                                    .append(Component.text(currentVersion).color(NamedTextColor.GREEN))
                                    .append(Component.text(") is newer than remote version (").color(NamedTextColor.YELLOW))
                                    .append(Component.text(version).color(NamedTextColor.RED))
                                    .append(Component.text(")").color(NamedTextColor.YELLOW)));
                            } else {
                                player.sendMessage(Component.text("OverWatchML is already up to date!").color(NamedTextColor.GREEN));
                            }
                        }
                    });
                } else {
                    downloadUpdate(player);
                }
                break;

            case "apply":
                applyUpdate(player);
                break;

            default:
                player.sendMessage(Component.text("Unknown action. Use 'check' to check for updates, 'auto' to download the latest version, or 'apply' to finalize an update.").color(NamedTextColor.RED));
                break;
        }

        return true;
    }

    /**
     * Manually applies a downloaded update by deleting the old plugin JAR
     */
    private void applyUpdate(Player player) {
        File pluginsDir = plugin.getServer().getUpdateFolderFile().getParentFile();
        String pluginName = "OverWatch-ML";
        String currentVersion = plugin.getPluginMeta().getVersion();
        String newVersion = updateChecker.getLatestVersion();
        File oldPluginFile = new File(pluginsDir, pluginName + "-" + currentVersion + ".jar");
        File newPluginFile = new File(pluginsDir, pluginName + "-" + newVersion + ".jar");

        if (!oldPluginFile.exists() || !newPluginFile.exists()) {
            player.sendMessage(Component.text("Using update marker to identify files...").color(NamedTextColor.YELLOW));

            File updateMarker = new File(plugin.getDataFolder(), "pending_update.txt");

            if (!updateMarker.exists()) {
                File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().startsWith(pluginName.toLowerCase()) && name.toLowerCase().endsWith(".jar"));

                if (jarFiles != null && jarFiles.length > 0) {
                    player.sendMessage(Component.text("Found " + jarFiles.length + " plugin JARs in directory.").color(NamedTextColor.YELLOW));

                    Arrays.sort(jarFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                    if (jarFiles.length >= 2) {
                        newPluginFile = jarFiles[0];
                        oldPluginFile = jarFiles[1];
                    } else {
                        player.sendMessage(Component.text("Could not find both versions of the plugin. Download the update first.").color(NamedTextColor.RED));
                        return;
                    }
                } else {
                    player.sendMessage(Component.text("No pending update found. Use '/OverWatch update auto' to download an update first.").color(NamedTextColor.RED));
                    return;
                }
            } else {
                try {
                    Properties props = new Properties();
                    try (FileInputStream fis = new FileInputStream(updateMarker)) {
                        props.load(fis);
                    }

                    String currentPluginPath = props.getProperty("current_plugin");
                    String newPluginPath = props.getProperty("new_plugin");
                    boolean alreadyDeleted = Boolean.parseBoolean(props.getProperty("already_deleted", "false"));

                    if (alreadyDeleted) {
                        player.sendMessage(Component.text("The old plugin file was already deleted. Server restart is required to use version " + newVersion).color(NamedTextColor.YELLOW));
                        return;
                    }

                    if (currentPluginPath == null || newPluginPath == null) {
                        player.sendMessage(Component.text("Invalid update information. Please try downloading the update again.").color(NamedTextColor.RED));
                        return;
                    }

                    oldPluginFile = new File(currentPluginPath);
                    newPluginFile = new File(newPluginPath);
                } catch (Exception e) {
                    player.sendMessage(Component.text("Error reading update marker: " + e.getMessage()).color(NamedTextColor.RED));
                    plugin.getLogger().warning("Error reading update marker: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
        }

        if (!newPluginFile.exists()) {
            player.sendMessage(Component.text("New plugin file not found: " + newPluginFile.getAbsolutePath()).color(NamedTextColor.RED));
            return;
        }

        plugin.getLogger().info("Old plugin file: " + (oldPluginFile.exists() ? oldPluginFile.getAbsolutePath() : "Not found"));
        plugin.getLogger().info("New plugin file: " + newPluginFile.getAbsolutePath());

        boolean deleteSuccess = false;
        if (oldPluginFile.exists()) {
            deleteSuccess = oldPluginFile.delete();

            if (deleteSuccess) {
                player.sendMessage(Component.text("Successfully deleted old plugin JAR: " + oldPluginFile.getName()).color(NamedTextColor.GREEN));

                File updateMarker = new File(plugin.getDataFolder(), "pending_update.txt");
                if (updateMarker.exists()) {
                    try {
                        Properties props = new Properties();
                        try (FileInputStream fis = new FileInputStream(updateMarker)) {
                            props.load(fis);
                        }
                        props.setProperty("already_deleted", "true");
                        try (FileOutputStream fos = new FileOutputStream(updateMarker)) {
                            props.store(fos, "Update status");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to update marker file: " + e.getMessage());
                    }
                }
            } else {
                player.sendMessage(Component.text("Failed to delete old plugin JAR. The file may be in use.").color(NamedTextColor.RED));
                player.sendMessage(Component.text("A server restart is required to apply the update.").color(NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text("Old plugin JAR not found at " + oldPluginFile.getAbsolutePath()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("It may have been already deleted or moved.").color(NamedTextColor.YELLOW));
            deleteSuccess = true;
        }

        if (deleteSuccess) {
            player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text(" OverWatchML update to version " + newVersion + " prepared.").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text(" Please restart your server to use the new version.").color(NamedTextColor.GREEN));

            if (player.isOp()) {
                Component restartMessage = Component.text(" Click here to restart the server now ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.suggestCommand("/restart")).hoverEvent(HoverEvent.showText(Component.text("Click to get the restart command")));

                player.sendMessage(restartMessage);
            }

            player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));

            plugin.getLogger().info("OverWatchML update to version " + newVersion + " prepared.");
            plugin.getLogger().info("The old plugin JAR has been deleted.");
            plugin.getLogger().info("Please restart your server to use the new version.");
        }
    }

    private void downloadUpdate(Player player) {
        player.sendMessage(Component.text("рџљЂ Starting download of the latest version from " +
            updateChecker.getCurrentSource().name + "...").color(NamedTextColor.YELLOW));

        // Show changelog if available
        String changelog = updateChecker.getUpdateChangelog();
        if (changelog != null && !changelog.isEmpty()) {
            player.sendMessage(Component.text("рџ“ќ Changelog:").color(NamedTextColor.GREEN));
            for (String line : changelog.split("\n")) {
                if (line.trim().length() > 0) {
                    player.sendMessage(Component.text("  вЂў " + line.trim()).color(NamedTextColor.WHITE));
                }
            }
        }

        updateChecker.downloadUpdate(success -> {
            if (success) {
                player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("вњ… OverWatchML update downloaded successfully!").color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("рџ“¦ Downloaded from: " + updateChecker.getCurrentSource().name).color(NamedTextColor.GREEN));

                Component applyMessage = Component.text("рџ”„ Click here to apply the update now ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/OverWatch update apply")).hoverEvent(HoverEvent.showText(Component.text("Click to prepare the update for next restart")));

                player.sendMessage(applyMessage);

                if (updateChecker.isHotReloadAvailable()) {
                    Component hotReloadMessage = Component.text("рџ”Ґ Hot reload available! ").color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text("Plugin will reload automatically.").color(NamedTextColor.GREEN));
                    player.sendMessage(hotReloadMessage);
                } else {
                    player.sendMessage(Component.text("рџ”„ Manual restart required to apply the update.").color(NamedTextColor.YELLOW));
                }

                player.sendMessage(Component.text("=================================================").color(NamedTextColor.GREEN));

                plugin.getLogger().info("OverWatchML update downloaded successfully from " + updateChecker.getCurrentSource().name + "!");
            } else {
                player.sendMessage(Component.text("вќЊ Failed to download the update. Check the console for more details.").color(NamedTextColor.RED));

                // Suggest alternative sources
                player.sendMessage(Component.text("рџ’Ў Try again later or check alternative sources.").color(NamedTextColor.GRAY));
            }
        }, progress -> {
            // Show progress every 25%
            if (progress > 0 && progress % 25 == 0) {
                Component progressMessage = Component.text("рџ“Ґ Download progress: " + progress + "%").color(NamedTextColor.BLUE);
                player.sendMessage(progressMessage);
            }
        });
    }
}
