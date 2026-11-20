package net.denfry.owml.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Schedulers {
   private static volatile Plugin plugin;
   public static final Schedulers.Task NONE = new Schedulers.Task() {
      @Override
      public void cancel() {
      }

      @Override
      public boolean isCancelled() {
         return true;
      }
   };

   private Schedulers() {
   }

   public static void init(@NotNull Plugin owner) {
      plugin = owner;
   }

   private static Plugin plugin() {
      Plugin p = plugin;
      if (p == null) {
         throw new IllegalStateException("Schedulers.init() не вызван");
      } else {
         return p;
      }
   }

   private static Schedulers.Task wrap(@Nullable final ScheduledTask t) {
      return t == null ? NONE : new Schedulers.Task() {
         @Override
         public void cancel() {
            t.cancel();
         }

         @Override
         public boolean isCancelled() {
            return t.isCancelled();
         }
      };
   }

   private static long d(long ticks) {
      return Math.max(1L, ticks);
   }

   private static Consumer<ScheduledTask> c(Runnable r) {
      return t -> r.run();
   }

   public static Schedulers.Task global(@NotNull Runnable r) {
      return wrap(Bukkit.getGlobalRegionScheduler().run(plugin(), c(r)));
   }

   public static Schedulers.Task globalLater(@NotNull Runnable r, long delayTicks) {
      return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), c(r), d(delayTicks)));
   }

   public static Schedulers.Task globalTimer(@NotNull Runnable r, long delayTicks, long periodTicks) {
      return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), c(r), d(delayTicks), d(periodTicks)));
   }

   public static Schedulers.Task at(@NotNull Location loc, @NotNull Runnable r) {
      return wrap(Bukkit.getRegionScheduler().run(plugin(), loc, c(r)));
   }

   public static Schedulers.Task atLater(@NotNull Location loc, @NotNull Runnable r, long delayTicks) {
      return wrap(Bukkit.getRegionScheduler().runDelayed(plugin(), loc, c(r), d(delayTicks)));
   }

   public static Schedulers.Task atTimer(@NotNull Location loc, @NotNull Runnable r, long delayTicks, long periodTicks) {
      return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin(), loc, c(r), d(delayTicks), d(periodTicks)));
   }

   public static void runAt(@NotNull Location loc, @NotNull Runnable r) {
      if (Bukkit.isOwnedByCurrentRegion(loc)) {
         r.run();
      } else {
         at(loc, r);
      }
   }

   public static Schedulers.Task entity(@NotNull Entity e, @NotNull Runnable r) {
      return wrap(e.getScheduler().run(plugin(), c(r), null));
   }

   public static Schedulers.Task entityLater(@NotNull Entity e, @NotNull Runnable r, long delayTicks) {
      return wrap(e.getScheduler().runDelayed(plugin(), c(r), null, d(delayTicks)));
   }

   public static Schedulers.Task entityTimer(@NotNull Entity e, @NotNull Runnable r, long delayTicks, long periodTicks) {
      return wrap(e.getScheduler().runAtFixedRate(plugin(), c(r), null, d(delayTicks), d(periodTicks)));
   }

   public static void runFor(@NotNull Entity e, @NotNull Runnable r) {
      if (Bukkit.isOwnedByCurrentRegion(e)) {
         r.run();
      } else {
         entity(e, r);
      }
   }

   public static Schedulers.Task async(@NotNull Runnable r) {
      return wrap(Bukkit.getAsyncScheduler().runNow(plugin(), c(r)));
   }

   public static Schedulers.Task asyncLater(@NotNull Runnable r, long delayTicks) {
      return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin(), c(r), Math.max(0L, delayTicks) * 50L, TimeUnit.MILLISECONDS));
   }

   public static Schedulers.Task asyncTimer(@NotNull Runnable r, long delayTicks, long periodTicks) {
      return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), c(r), Math.max(0L, delayTicks) * 50L, d(periodTicks) * 50L, TimeUnit.MILLISECONDS));
   }

   public static void dispatchConsole(@NotNull String command) {
      if (Bukkit.isGlobalTickThread()) {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
      } else {
         global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
      }
   }

   public static void cancelAll() {
      Plugin p = plugin;
      if (p != null) {
         try {
            Bukkit.getGlobalRegionScheduler().cancelTasks(p);
         } catch (Throwable var3) {
         }

         try {
            Bukkit.getAsyncScheduler().cancelTasks(p);
         } catch (Throwable var2) {
         }
      }
   }

   public static boolean isOwned(@NotNull Location loc) {
      return Bukkit.isOwnedByCurrentRegion(loc);
   }

   public static boolean isOwned(@NotNull World world, int chunkX, int chunkZ) {
      return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
   }

   public static boolean isOwned(@NotNull Entity e) {
      return Bukkit.isOwnedByCurrentRegion(e);
   }

   public abstract static class Repeating implements Runnable {
      private volatile Schedulers.Task task;
      private volatile boolean cancelled;

      public void cancel() {
         this.cancelled = true;
         Schedulers.Task t = this.task;
         if (t != null) {
            t.cancel();
         }
      }

      public boolean isCancelled() {
         return this.cancelled;
      }

      private Runnable guarded() {
         return () -> {
            if (!this.cancelled) {
               this.run();
            }
         };
      }

      private Schedulers.Task bind(Schedulers.Task t) {
         this.task = t;
         if (this.cancelled) {
            t.cancel();
         }

         return t;
      }

      public Schedulers.Task global() {
         return this.bind(Schedulers.global(this.guarded()));
      }

      public Schedulers.Task globalLater(long delay) {
         return this.bind(Schedulers.globalLater(this.guarded(), delay));
      }

      public Schedulers.Task globalTimer(long delay, long period) {
         return this.bind(Schedulers.globalTimer(this.guarded(), delay, period));
      }

      public Schedulers.Task entity(Entity e) {
         return this.bind(Schedulers.entity(e, this.guarded()));
      }

      public Schedulers.Task entityLater(Entity e, long delay) {
         return this.bind(Schedulers.entityLater(e, this.guarded(), delay));
      }

      public Schedulers.Task entityTimer(Entity e, long delay, long period) {
         return this.bind(Schedulers.entityTimer(e, this.guarded(), delay, period));
      }

      public Schedulers.Task at(Location l) {
         return this.bind(Schedulers.at(l, this.guarded()));
      }

      public Schedulers.Task atLater(Location l, long delay) {
         return this.bind(Schedulers.atLater(l, this.guarded(), delay));
      }

      public Schedulers.Task atTimer(Location l, long delay, long period) {
         return this.bind(Schedulers.atTimer(l, this.guarded(), delay, period));
      }

      public Schedulers.Task async() {
         return this.bind(Schedulers.async(this.guarded()));
      }

      public Schedulers.Task asyncLater(long delay) {
         return this.bind(Schedulers.asyncLater(this.guarded(), delay));
      }

      public Schedulers.Task asyncTimer(long delay, long period) {
         return this.bind(Schedulers.asyncTimer(this.guarded(), delay, period));
      }
   }

   public interface Task {
      void cancel();

      boolean isCancelled();
   }
}
