package net.denfry.owml.utils;

import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Optimized asynchronous executor for OverWatchML operations.
 * Leverages Java 21 Virtual Threads for high-concurrency I/O and light tasks.
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.1
 */
public class AsyncExecutor {

    // Virtual thread executor for I/O and light tasks - highly scalable
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    // Fixed thread pool for CPU-heavy tasks to prevent context switching overhead
    private static final ExecutorService COMPUTATION_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
        Thread.ofPlatform().name("OverWatch-Compute-", 1).factory()
    );

    /**
     * Execute IO-bound operations (file operations, database, network) using Virtual Threads.
     */
    public static void executeIO(@NotNull Runnable task) {
        VIRTUAL_EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                MessageManager.log("error", "IO task failed: {ERROR}", "ERROR", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Execute computation-heavy operations (ML analysis, complex calculations) on a fixed pool.
     */
    public static void executeComputation(@NotNull Runnable task) {
        COMPUTATION_EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                MessageManager.log("error", "Computation task failed: {ERROR}", "ERROR", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Execute light operations using Virtual Threads.
     */
    public static void executeLight(@NotNull Runnable task) {
        VIRTUAL_EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                MessageManager.log("error", "Light task failed: {ERROR}", "ERROR", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Submit IO-bound operation and return a CompletableFuture.
     */
    @NotNull
    public static <T> CompletableFuture<T> submitIO(@NotNull java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                MessageManager.log("error", "IO task failed: {ERROR}", "ERROR", e.getMessage());
                throw new RuntimeException(e);
            }
        }, VIRTUAL_EXECUTOR);
    }

    /**
     * Submit computation operation and return a CompletableFuture.
     */
    @NotNull
    public static <T> CompletableFuture<T> submitComputation(@NotNull java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                MessageManager.log("error", "Computation task failed: {ERROR}", "ERROR", e.getMessage());
                throw new RuntimeException(e);
            }
        }, COMPUTATION_EXECUTOR);
    }

    /**
     * Schedule a light task with delay using virtual threads.
     */
    public static void scheduleLight(@NotNull Runnable task, long delayMillis) {
        VIRTUAL_EXECUTOR.execute(() -> {
            try {
                Thread.sleep(delayMillis);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                MessageManager.log("error", "Scheduled task failed: {ERROR}", "ERROR", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Shutdown all executors gracefully.
     */
    public static void shutdown() {
        try {
            VIRTUAL_EXECUTOR.shutdown();
            COMPUTATION_EXECUTOR.shutdown();
            
            if (!VIRTUAL_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                VIRTUAL_EXECUTOR.shutdownNow();
            }
            if (!COMPUTATION_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                COMPUTATION_EXECUTOR.shutdownNow();
            }
            
            MessageManager.log("info", "All async executors (Virtual & Platform) shut down successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            VIRTUAL_EXECUTOR.shutdownNow();
            COMPUTATION_EXECUTOR.shutdownNow();
        }
    }
}