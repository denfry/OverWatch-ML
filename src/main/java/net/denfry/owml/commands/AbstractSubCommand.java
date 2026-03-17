package net.denfry.owml.commands;

import net.denfry.owml.OverWatchML;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSubCommand implements ISubCommand {
    protected final OverWatchML plugin;
    private final String name;
    private final String permission;
    private final String description;
    private final String usage;
    private final List<String> aliases;

    public AbstractSubCommand(OverWatchML plugin, String name, String permission, String description, String usage, String... aliases) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.description = description;
        this.usage = usage;
        this.aliases = new ArrayList<>();
        Collections.addAll(this.aliases, aliases);
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    @NotNull
    public String getPermission() {
        return permission;
    }

    @Override
    @NotNull
    public String getDescription() {
        return description;
    }

    @Override
    @NotNull
    public String getUsage() {
        return usage;
    }

    protected void sendNoPermission(CommandSender sender) {
        sender.sendMessage(Component.text("You do not have permission to use this command!").color(NamedTextColor.RED));
    }

    protected void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: " + getUsage()).color(NamedTextColor.RED));
    }

    @Override
    @Nullable
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
