package net.denfry.owml.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ISubCommand {
    @NotNull String getName();
    @NotNull String getPermission();
    @NotNull String getDescription();
    @NotNull String getUsage();
    
    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);
    @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args);
}
