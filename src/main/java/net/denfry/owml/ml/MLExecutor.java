package net.denfry.owml.ml;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import net.denfry.owml.OverWatchML;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * РђСЃРёРЅС…СЂРѕРЅРЅС‹Р№ РёСЃРїРѕР»РЅРёС‚РµР»СЊ РґР»СЏ ML РѕРїРµСЂР°С†РёР№ СЃ graceful shutdown
 */
public class MLExecutor {

    private static final int DEFAULT_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final OverWatchML plugin;
    private final Logger logger;

    // Thread pool for heavy ML computations
    private final ExecutorService mlExecutor;

    // Scheduler for periodic tasks
    private final ScheduledExecutorService maintenanceExecutor;

    // Single thread executor for sequential operations
    private final ExecutorService sequentialExecutor;

    // Shutdown flag
    private volatile boolean shuttingDown = false;

    public MLExecutor(OverWatchML plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Create thread pool with custom thread names
        ThreadFactory mlThreadFactory = new MLThreadFactory("ML-Worker");
        this.mlExecutor = Executors.newFixedThreadPool(DEFAULT_POOL_SIZE, mlThreadFactory);

        // Scheduled executor for maintenance
        ThreadFactory maintenanceFactory = new MLThreadFactory("ML-Maintenance");
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(maintenanceFactory);

        // Sequential executor for operations requiring order
        ThreadFactory sequentialFactory = new MLThreadFactory("ML-Sequential");
        this.sequentialExecutor = Executors.newSingleThreadExecutor(sequentialFactory);

        logger.info("ML Executor initialized with " + DEFAULT_POOL_SIZE + " worker threads");
    }

    /**
     * Р’С‹РїРѕР»РЅСЏРµС‚ С‚СЏР¶РµР»СѓСЋ ML РѕРїРµСЂР°С†РёСЋ Р°СЃРёРЅС…СЂРѕРЅРЅРѕ
     */
    public <T> CompletableFuture<T> submitMLTask(Callable<T> task) {
        if (shuttingDown) {
            throw new IllegalStateException("ML Executor is shutting down");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                logger.severe("ML task failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, mlExecutor);
    }

    /**
     * Р’С‹РїРѕР»РЅСЏРµС‚ Р·Р°РґР°С‡Сѓ РЅР° main thread Bukkit
     */
    public void runOnMainThread(Runnable task) {
        if (shuttingDown) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * Р’С‹РїРѕР»РЅСЏРµС‚ Р·Р°РґР°С‡Сѓ РЅР° main thread СЃ СЂРµР·СѓР»СЊС‚Р°С‚РѕРј
     */
    public <T> CompletableFuture<T> runOnMainThread(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        runOnMainThread(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * РџР»Р°РЅРёСЂСѓРµС‚ РїРµСЂРёРѕРґРёС‡РµСЃРєСѓСЋ Р·Р°РґР°С‡Сѓ РѕР±СЃР»СѓР¶РёРІР°РЅРёСЏ
     */
    public ScheduledFuture<?> scheduleMaintenance(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (shuttingDown) {
            throw new IllegalStateException("ML Executor is shutting down");
        }

        return maintenanceExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logger.warning("Maintenance task failed: " + e.getMessage());
                }
            },
            initialDelay, period, unit
        );
    }

    /**
     * Р’С‹РїРѕР»РЅСЏРµС‚ Р·Р°РґР°С‡Сѓ РїРѕСЃР»РµРґРѕРІР°С‚РµР»СЊРЅРѕ (РґР»СЏ РѕРїРµСЂР°С†РёР№ С‚СЂРµР±СѓСЋС‰РёС… РїРѕСЂСЏРґРєР°)
     */
    public CompletableFuture<Void> submitSequentialTask(Runnable task) {
        if (shuttingDown) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("ML Executor is shutting down"));
            return future;
        }

        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.severe("Sequential task failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, sequentialExecutor);
    }

    /**
     * Graceful shutdown РІСЃРµС… executors
     */
    public void shutdown() {
        logger.info("Shutting down ML Executor...");
        shuttingDown = true;

        // Cancel all scheduled tasks
        maintenanceExecutor.shutdown();

        // Wait for current tasks to complete
        try {
            if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            maintenanceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown worker pools
        shutdownExecutor(mlExecutor, "ML Worker Pool");
        shutdownExecutor(sequentialExecutor, "Sequential Executor");

        logger.info("ML Executor shutdown complete");
    }

    /**
     * Helper method РґР»СЏ shutdown executor СЃ Р»РѕРіРёСЂРѕРІР°РЅРёРµРј
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning(name + " did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();

                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.severe(name + " failed to terminate");
                }
            } else {
                logger.info(name + " terminated gracefully");
            }
        } catch (InterruptedException e) {
            logger.warning(name + " shutdown interrupted, forcing shutdown");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, Р·Р°РІРµСЂС€Р°РµС‚СЃСЏ Р»Рё СЂР°Р±РѕС‚Р°
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * РџРѕР»СѓС‡Р°РµС‚ СЃС‚Р°С‚РёСЃС‚РёРєСѓ РёСЃРїРѕР»РЅРёС‚РµР»РµР№
     */
    public String getStats() {
        return String.format("ML Executor Stats: Workers=%d, Maintenance=1, Sequential=1",
            DEFAULT_POOL_SIZE);
    }

    /**
     * ThreadFactory СЃ РєР°СЃС‚РѕРјРЅС‹РјРё РёРјРµРЅР°РјРё РґР»СЏ РїРѕС‚РѕРєРѕРІ
     */
    private static class MLThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        MLThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            t.setDaemon(true); // Daemon threads don't prevent JVM shutdown
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
