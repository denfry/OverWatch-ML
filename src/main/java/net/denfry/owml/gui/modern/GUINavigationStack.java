package net.denfry.owml.gui.modern;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores GUI navigation stack for each player.
 */
public class GUINavigationStack {
    private static final Map<UUID, Deque<OverWatchGUI>> playerStacks = new ConcurrentHashMap<>();

    public static void push(Player player, OverWatchGUI gui) {
        playerStacks.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(gui);
        gui.open(player);
    }

    public static OverWatchGUI pop(Player player) {
        Deque<OverWatchGUI> stack = playerStacks.get(player.getUniqueId());
        if (stack == null || stack.size() <= 1) {
            player.closeInventory();
            if (stack != null) stack.clear();
            return null;
        }

        stack.pop(); // Remove current
        OverWatchGUI previous = stack.peek();
        if (previous != null) {
            previous.open(player);
        } else {
            player.closeInventory();
        }
        return previous;
    }

    public static void clear(Player player) {
        playerStacks.remove(player.getUniqueId());
    }

    public static OverWatchGUI peek(Player player) {
        Deque<OverWatchGUI> stack = playerStacks.get(player.getUniqueId());
        return (stack != null) ? stack.peek() : null;
    }

    public static boolean hasStack(Player player) {
        Deque<OverWatchGUI> stack = playerStacks.get(player.getUniqueId());
        return stack != null && !stack.isEmpty();
    }

    public static int getStackSize(Player player) {
        Deque<OverWatchGUI> stack = playerStacks.get(player.getUniqueId());
        return stack != null ? stack.size() : 0;
    }
}
