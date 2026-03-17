package net.denfry.owml.commands;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.subcommands.*;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AntiXrayCommand implements CommandExecutor, TabCompleter {

    private final OverWatchML plugin;
    private final Map<String, ISubCommand> subCommands = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public AntiXrayCommand(OverWatchML plugin, Object updateChecker) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        register(new ReloadCommand(plugin));
        register(new MLCommand(plugin));
        register(new AlertCommand(plugin));
        register(new DebugCommand(plugin));
        register(new UpdateCommand(plugin));
        register(new StaffGuiCommand(plugin));
        register(new PunishCommand(plugin));
        register(new TeleportCommand(plugin));
        register(new WorldCommand(plugin));
        register(new ModifySuspiciousCommand(plugin));
        register(new AppealCommand(plugin));
        
        // Custom subcommands
        register(new PlayerSubCommand(plugin));
        register(new StaffSubCommand(plugin));
        register(new HelpSubCommand(this));
    }

    private void register(ISubCommand subCommand) {
        subCommands.put(subCommand.getName(), subCommand);
        for (String alias : subCommand.getAliases()) {
            subCommands.put(alias, subCommand);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("owml.staff")) {
                    player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                    return true;
                }
                new net.denfry.owml.gui.StaffMenuGUI(plugin).open(player);
            } else {
                sender.sendMessage("OverWatchML v" + plugin.getDescription().getVersion() + " - Use /" + label + " help for commands.");
            }
            return true;
        }

        ISubCommand sub = subCommands.get(args[0]);
        if (sub != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return sub.execute(sender, subArgs);
        }

        sender.sendMessage(Component.text("Unknown subcommand. Use /" + label + " help", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return subCommands.values().stream()
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(ISubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        ISubCommand sub = subCommands.get(args[0]);
        if (sub != null && sender.hasPermission(sub.getPermission())) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return sub.tabComplete(sender, subArgs);
        }

        return Collections.emptyList();
    }

    private static class PlayerSubCommand extends AbstractSubCommand {
        public PlayerSubCommand(OverWatchML plugin) {
            super(plugin, "player", "owml.gui", "Open player profile", "/owml player <name>");
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return false;
            if (args.length < 1) {
                sendUsage(sender);
                return true;
            }
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
            GUINavigationStack.push(player, new net.denfry.owml.gui.modern.PlayerProfileGUI(plugin, target.getUniqueId()));
            return true;
        }
        @Override
        public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
            if (args.length == 1) {
                String partial = args[0].toLowerCase();
                return org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    private static class StaffSubCommand extends AbstractSubCommand {
        public StaffSubCommand(OverWatchML plugin) {
            super(plugin, "staff", "owml.staff", "Open staff menu", "/owml staff");
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return false;
            new net.denfry.owml.gui.StaffMenuGUI(plugin).open(player);
            return true;
        }
    }

    private static class HelpSubCommand extends AbstractSubCommand {
        private final AntiXrayCommand parent;
        public HelpSubCommand(AntiXrayCommand parent) {
            super(parent.plugin, "help", "owml.use", "Show help", "/owml help", "h", "?");
            this.parent = parent;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
            sender.sendMessage(Component.text("=== OverWatchML Help ===", NamedTextColor.GOLD, TextDecoration.BOLD));
            parent.subCommands.values().stream()
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(ISubCommand::getName)
                    .distinct()
                    .sorted()
                    .map(parent.subCommands::get)
                    .forEach(sub -> {
                        sender.sendMessage(Component.text("/owml " + sub.getName(), NamedTextColor.GREEN)
                                .append(Component.text(" - " + sub.getDescription(), NamedTextColor.WHITE)));
                    });
            return true;
        }
    }
}
