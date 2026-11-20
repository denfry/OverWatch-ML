package net.denfry.owml.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class SchedulerUtils {
   private SchedulerUtils() {
   }

   public static boolean isFolia() {
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         return true;
      } catch (ClassNotFoundException var1) {
         return false;
      }
   }

   public static Schedulers.Task runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
      return Schedulers.global(task);
   }

   public static Schedulers.Task runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task) {
      return Schedulers.async(task);
   }

   public static Schedulers.Task runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
      return Schedulers.globalLater(task, delayTicks);
   }

   public static Schedulers.Task runTaskAtLocation(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task) {
      return Schedulers.at(location, task);
   }

   public static Schedulers.Task runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
      return Schedulers.globalTimer(task, delayTicks, periodTicks);
   }

   public static boolean isMainThread() {
      return Bukkit.isGlobalTickThread();
   }

   public static void ensureMainThread(@NotNull Plugin plugin, @NotNull Runnable task) {
      if (isMainThread()) {
         task.run();
      } else {
         runTask(plugin, task);
      }
   }
}
