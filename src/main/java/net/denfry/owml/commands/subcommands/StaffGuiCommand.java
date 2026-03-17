package net.denfry.owml.commands.subcommands;

import net.denfry.owml.commands.AbstractSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.denfry.owml.gui.StaffMenuGUI;
import net.denfry.owml.OverWatchML;
import org.jetbrains.annotations.NotNull;

public class StaffGuiCommand extends AbstractSubCommand {

    public StaffGuiCommand(OverWatchML plugin) {
        super(plugin, "staffgui", "owml.gui", "Open staff GUI", "/owml staffgui", "gui", "menu");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sendNoPermission(player);
            return true;
        }

        new StaffMenuGUI(plugin).open(player);
        return true;
    }
}
