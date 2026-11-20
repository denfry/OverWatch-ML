package net.denfry.owml.ml;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;

public class MLExecutor {
   private static final int DEFAULT_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
   private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;
   private final OverWatchML plugin;
   private final Logger logger;
   private final ExecutorService mlExecutor;
   private final ScheduledExecutorService maintenanceExecutor;
   private final ExecutorService sequentialExecutor;
   private volatile boolean shuttingDown = false;

   public MLExecutor(OverWatchML plugin) {
      this.plugin = plugin;
      this.logger = plugin.getLogger();
      ThreadFactory mlThreadFactory = new MLExecutor.MLThreadFactory("ML-Worker");
      this.mlExecutor = Executors.newFixedThreadPool(DEFAULT_POOL_SIZE, mlThreadFactory);
      ThreadFactory maintenanceFactory = new MLExecutor.MLThreadFactory("ML-Maintenance");
      this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(maintenanceFactory);
      ThreadFactory sequentialFactory = new MLExecutor.MLThreadFactory("ML-Sequential");
      this.sequentialExecutor = Executors.newSingleThreadExecutor(sequentialFactory);
      this.logger.info("ML Executor initialized with " + DEFAULT_POOL_SIZE + " worker threads");
   }

   public <T> CompletableFuture<T> submitMLTask(Callable<T> task) {
      if (this.shuttingDown) {
         throw new IllegalStateException("ML Executor is shutting down");
      } else {
         return CompletableFuture.supplyAsync(() -> {
            try {
               return task.call();
            } catch (Exception var3) {
               this.logger.severe("ML task failed: " + var3.getMessage());
               throw new CompletionException(var3);
            }
         }, this.mlExecutor);
      }
   }

   public void runOnMainThread(Runnable task) {
      if (!this.shuttingDown) {
         Schedulers.global(task);
      }
   }

   public void runTaskLater(Runnable task, long delayTicks) {
      if (!this.shuttingDown) {
         Schedulers.globalLater(task, delayTicks);
      }
   }

   public <T> CompletableFuture<T> runOnMainThread(Callable<T> task) {
      CompletableFuture<T> future = new CompletableFuture<>();
      this.runOnMainThread(() -> {
         try {
            T result = task.call();
            future.complete(result);
         } catch (Exception var3) {
            future.completeExceptionally(var3);
         }
      });
      return future;
   }

   public ScheduledFuture<?> scheduleMaintenance(Runnable task, long initialDelay, long period, TimeUnit unit) {
      if (this.shuttingDown) {
         throw new IllegalStateException("ML Executor is shutting down");
      } else {
         return this.maintenanceExecutor.scheduleAtFixedRate(() -> {
            try {
               task.run();
            } catch (Exception var3) {
               this.logger.warning("Maintenance task failed: " + var3.getMessage());
            }
         }, initialDelay, period, unit);
      }
   }

   public CompletableFuture<Void> submitSequentialTask(Runnable task) {
      if (this.shuttingDown) {
         CompletableFuture<Void> future = new CompletableFuture<>();
         future.completeExceptionally(new IllegalStateException("ML Executor is shutting down"));
         return future;
      } else {
         return CompletableFuture.runAsync(() -> {
            try {
               task.run();
            } catch (Exception var3) {
               this.logger.severe("Sequential task failed: " + var3.getMessage());
               throw new CompletionException(var3);
            }
         }, this.sequentialExecutor);
      }
   }

   public void shutdown() {
      this.logger.info("Shutting down ML Executor...");
      this.shuttingDown = true;
      this.maintenanceExecutor.shutdown();

      try {
         if (!this.maintenanceExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.maintenanceExecutor.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.maintenanceExecutor.shutdownNow();
         Thread.currentThread().interrupt();
      }

      this.shutdownExecutor(this.mlExecutor, "ML Worker Pool");
      this.shutdownExecutor(this.sequentialExecutor, "Sequential Executor");
      this.logger.info("ML Executor shutdown complete");
   }

   private void shutdownExecutor(ExecutorService executor, String name) {
      executor.shutdown();

      try {
         if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
            this.logger.warning(name + " did not terminate gracefully, forcing shutdown");
            executor.shutdownNow();
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
               this.logger.severe(name + " failed to terminate");
            }
         } else {
            this.logger.info(name + " terminated gracefully");
         }
      } catch (InterruptedException var4) {
         this.logger.warning(name + " shutdown interrupted, forcing shutdown");
         executor.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   public boolean isShuttingDown() {
      return this.shuttingDown;
   }

   public String getStats() {
      return String.format("ML Executor Stats: Workers=%d, Maintenance=1, Sequential=1", DEFAULT_POOL_SIZE);
   }

   private static class MLThreadFactory implements ThreadFactory {
      private final AtomicInteger threadNumber = new AtomicInteger(1);
      private final String namePrefix;

      MLThreadFactory(String namePrefix) {
         this.namePrefix = namePrefix;
      }

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, this.namePrefix + "-thread-" + this.threadNumber.getAndIncrement());
         t.setDaemon(true);
         t.setPriority(5);
         return t;
      }
   }
}
