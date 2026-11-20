package net.denfry.owml.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.commands.subcommands.AlertCommand;
import net.denfry.owml.commands.subcommands.AppealCommand;
import net.denfry.owml.commands.subcommands.DebugCommand;
import net.denfry.owml.commands.subcommands.MLCommand;
import net.denfry.owml.commands.subcommands.ModifySuspiciousCommand;
import net.denfry.owml.commands.subcommands.PunishCommand;
import net.denfry.owml.commands.subcommands.ReloadCommand;
import net.denfry.owml.commands.subcommands.StaffGuiCommand;
import net.denfry.owml.commands.subcommands.TeleportCommand;
import net.denfry.owml.commands.subcommands.UpdateCommand;
import net.denfry.owml.commands.subcommands.WorldCommand;
import net.denfry.owml.gui.StaffMenuGUI;
import net.denfry.owml.gui.modern.GUINavigationStack;
import net.denfry.owml.gui.modern.PlayerProfileGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntiXrayCommand implements CommandExecutor, TabCompleter {
   private final OverWatchML plugin;
   private final Map<String, ISubCommand> subCommands = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   public AntiXrayCommand(OverWatchML plugin, Object updateChecker) {
      this.plugin = plugin;
      this.registerSubCommands();
   }

   private void registerSubCommands() {
      this.register(new ReloadCommand(this.plugin));
      this.register(new MLCommand(this.plugin));
      this.register(new AlertCommand(this.plugin));
      this.register(new DebugCommand(this.plugin));
      this.register(new UpdateCommand(this.plugin));
      this.register(new StaffGuiCommand(this.plugin));
      this.register(new PunishCommand(this.plugin));
      this.register(new TeleportCommand(this.plugin));
      this.register(new WorldCommand(this.plugin));
      this.register(new ModifySuspiciousCommand(this.plugin));
      this.register(new AppealCommand(this.plugin));
      this.register(new AntiXrayCommand.PlayerSubCommand(this.plugin));
      this.register(new AntiXrayCommand.StaffSubCommand(this.plugin));
      this.register(new AntiXrayCommand.HelpSubCommand(this));
   }

   private void register(ISubCommand subCommand) {
      this.subCommands.put(subCommand.getName(), subCommand);

      for (String alias : subCommand.getAliases()) {
         this.subCommands.put(alias, subCommand);
      }
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player player) {
            if (!player.hasPermission("owml.staff")) {
               player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
               return true;
            }

            new StaffMenuGUI(this.plugin).open(player);
         } else {
            sender.sendMessage("OverWatchML v" + this.plugin.getDescription().getVersion() + " - Use /" + label + " help for commands.");
         }

         return true;
      } else {
         ISubCommand sub = this.subCommands.get(args[0].toLowerCase(Locale.ROOT));
         if (sub != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return sub.execute(sender, subArgs);
         } else {
            sender.sendMessage(Component.text("Unknown subcommand. Use /" + label + " help", NamedTextColor.RED));
            return true;
         }
      }
   }

   @Nullable
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (args.length == 1) {
         return this.subCommands
            .values()
            .stream()
            .filter(subx -> sender.hasPermission(subx.getPermission()))
            .map(ISubCommand::getName)
            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
      } else {
         ISubCommand sub = this.subCommands.get(args[0].toLowerCase(Locale.ROOT));
         if (sub != null && sender.hasPermission(sub.getPermission())) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return sub.tabComplete(sender, subArgs);
         } else {
            return Collections.emptyList();
         }
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
         sender.sendMessage(Component.text("=== OverWatchML Help ===", NamedTextColor.GOLD, new TextDecoration[]{TextDecoration.BOLD}));
         this.parent
            .subCommands
            .values()
            .stream()
            .filter(sub -> sender.hasPermission(sub.getPermission()))
            .map(ISubCommand::getName)
            .distinct()
            .sorted()
            .map(this.parent.subCommands::get)
            .forEach(
               sub -> sender.sendMessage(
                  Component.text("/owml " + sub.getName(), NamedTextColor.GREEN).append(Component.text(" - " + sub.getDescription(), NamedTextColor.WHITE))
               )
            );
         return true;
      }
   }

   private static class PlayerSubCommand extends AbstractSubCommand {
      public PlayerSubCommand(OverWatchML plugin) {
         super(plugin, "player", "owml.gui", "Open player profile", "/owml player <name>");
      }

      @Override
      public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
         if (sender instanceof Player player) {
            if (args.length < 1) {
               this.sendUsage(sender);
               return true;
            } else {
               OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
               GUINavigationStack.push(player, new PlayerProfileGUI(this.plugin, target.getUniqueId()));
               return true;
            }
         } else {
            return false;
         }
      }

      @Override
      public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
         if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers()
               .stream()
               .<String>map(Player::getName)
               .filter(name -> name.toLowerCase().startsWith(partial))
               .collect(Collectors.toList());
         } else {
            return Collections.emptyList();
         }
      }
   }

   private static class StaffSubCommand extends AbstractSubCommand {
      public StaffSubCommand(OverWatchML plugin) {
         super(plugin, "staff", "owml.staff", "Open staff menu", "/owml staff");
      }

      @Override
      public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
         if (sender instanceof Player player) {
            new StaffMenuGUI(this.plugin).open(player);
            return true;
         } else {
            return false;
         }
      }
   }
}
