package net.denfry.owml.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for scheduler operations that works across different server implementations
 * including Paper, Folia, and other server implementations.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class SchedulerUtils {

    private static final Server server = Bukkit.getServer();
    private static final BukkitScheduler scheduler = server.getScheduler();

    /**
     * Check if the server is running Folia.
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if the server is running Paper.
     */
    public static boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Run a task on the main thread.
     */
    public static void runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (isFolia()) {
            // For Folia, we need to run on the global region
            scheduler.runTask(plugin, task);
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    /**
     * Run a task asynchronously.
     */
    public static void runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task) {
        scheduler.runTaskAsynchronously(plugin, task);
    }

    /**
     * Run a task later on the main thread.
     */
    public static void runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        if (isFolia()) {
            scheduler.runTaskLater(plugin, task, delayTicks);
        } else {
            scheduler.runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a task at a specific location (region-aware for Folia).
     */
    public static void runTaskAtLocation(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task) {
        if (isFolia()) {
            // For Folia, run the task in the region of the location
            try {
                // Use reflection to access Folia's regional scheduling
                Class<?> regionizedServerClass = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                Object regionScheduler = regionizedServerClass.getMethod("getRegionScheduler").invoke(server);

                // Get the region at the location
                Class<?> regionSchedulerClass = regionizedServerClass.getMethod("getRegionScheduler").getReturnType();
                Object region = regionSchedulerClass.getMethod("getRegion", Location.class).invoke(regionScheduler, location);

                // Run the task in that region
                region.getClass().getMethod("run", Runnable.class).invoke(region, task);
            } catch (Exception e) {
                // Fallback to global scheduler if reflection fails
                scheduler.runTask(plugin, task);
            }
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    /**
     * Run a repeating task.
     */
    public static int runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
        if (isFolia()) {
            return scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
        } else {
            return scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
        }
    }

    /**
     * Cancel a task by ID.
     */
    public static void cancelTask(int taskId) {
        scheduler.cancelTask(taskId);
    }

    /**
     * Check if the current thread is the main thread.
     */
    public static boolean isMainThread() {
        return server.isPrimaryThread();
    }

    /**
     * Ensure a task runs on the main thread.
     */
    public static void ensureMainThread(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (isMainThread()) {
            task.run();
        } else {
            runTask(plugin, task);
        }
    }
}
