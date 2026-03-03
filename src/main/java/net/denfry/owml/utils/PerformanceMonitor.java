package net.denfry.owml.utils;

import net.denfry.owml.OverWatchML;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring system for OverWatch-ML.
 * Tracks TPS, MSPT, memory usage, and plugin performance impact.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class PerformanceMonitor {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // TPS and timing metrics
    private static final long[] tickTimes = new long[600]; // Last 30 seconds * 20 TPS
    private static int tickIndex = 0;
    private static long lastTickTime = System.nanoTime();

    // Performance counters
    private static final AtomicLong blockBreakChecks = new AtomicLong(0);
    private static final AtomicLong mlAnalysisRuns = new AtomicLong(0);
    private static final AtomicLong alertTriggers = new AtomicLong(0);
    private static final AtomicLong dataSaveOperations = new AtomicLong(0);

    // Memory monitoring
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // Performance thresholds
    private static final double TPS_WARNING_THRESHOLD = 18.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;
    private static final long MSPT_WARNING_THRESHOLD = 50000000L; // 50ms in nanoseconds
    private static final double MEMORY_WARNING_PERCENT = 85.0;

    static {
        // Start monitoring task - MUST run every tick for accurate TPS calculation
        SchedulerUtils.runTaskTimer(plugin, PerformanceMonitor::updateMetrics, 1L, 1L); // Every tick (50ms)

        // Start performance logging task
        SchedulerUtils.runTaskTimer(plugin, PerformanceMonitor::logPerformanceWarnings, 1200L, 1200L); // Every minute
    }

    /**
     * Update performance metrics.
     */
    private static void updateMetrics() {
        long currentTime = System.nanoTime();
        long tickDuration = currentTime - lastTickTime;
        lastTickTime = currentTime;

        tickTimes[tickIndex] = tickDuration;
        tickIndex = (tickIndex + 1) % tickTimes.length;
    }

    /**
     * Log performance warnings if thresholds are exceeded.
     */
    private static void logPerformanceWarnings() {
        double tps = getTPS();
        long mspt = getMSPT();
        double memoryPercent = getMemoryUsagePercent();

        // TPS warnings
        if (tps <= TPS_CRITICAL_THRESHOLD) {
            MessageManager.log("error", "Critical TPS drop detected: {TPS}", "TPS", String.format("%.1f", tps));
        } else if (tps <= TPS_WARNING_THRESHOLD) {
            MessageManager.log("warning", "Low TPS detected: {TPS}", "TPS", String.format("%.1f", tps));
        }

        // MSPT warnings
        if (mspt >= MSPT_WARNING_THRESHOLD) {
            MessageManager.log("warning", "High MSPT detected: {MSPT}ms", "MSPT", String.format("%.1f", mspt / 1_000_000.0));
        }

        // Memory warnings
        if (memoryPercent >= MEMORY_WARNING_PERCENT) {
            MessageManager.log("warning", "High memory usage: {USAGE}%", "USAGE", String.format("%.1f", memoryPercent));
        }
    }

    /**
     * Get current TPS (Ticks Per Second).
     */
    public static double getTPS() {
        long totalTime = 0;
        int validTicks = 0;

        for (long tickTime : tickTimes) {
            if (tickTime > 0) {
                totalTime += tickTime;
                validTicks++;
            }
        }

        if (validTicks == 0) return 20.0;

        double averageTickTime = (double) totalTime / validTicks;
        return Math.min(20.0, 1_000_000_000.0 / averageTickTime); // Convert nanoseconds to TPS
    }

    /**
     * Get current MSPT (Milliseconds Per Tick).
     */
    public static long getMSPT() {
        long totalTime = 0;
        int validTicks = 0;

        for (long tickTime : tickTimes) {
            if (tickTime > 0) {
                totalTime += tickTime;
                validTicks++;
            }
        }

        if (validTicks == 0) return 0;

        return (totalTime / validTicks) / 1_000_000; // Convert to milliseconds
    }

    /**
     * Get memory usage percentage.
     */
    public static double getMemoryUsagePercent() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();

        if (max == 0) return 0.0;

        return ((double) used / max) * 100.0;
    }

    /**
     * Get memory usage in MB.
     */
    public static long getMemoryUsageMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed() / (1024 * 1024);
    }

    /**
     * Get maximum memory in MB.
     */
    public static long getMaxMemoryMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getMax() / (1024 * 1024);
    }

    /**
     * Record a block break check operation.
     */
    public static void recordBlockBreakCheck() {
        blockBreakChecks.incrementAndGet();
    }

    /**
     * Record an ML analysis operation.
     */
    public static void recordMLAnalysis() {
        mlAnalysisRuns.incrementAndGet();
    }

    /**
     * Record an alert trigger.
     */
    public static void recordAlertTrigger() {
        alertTriggers.incrementAndGet();
    }

    /**
     * Record a data save operation.
     */
    public static void recordDataSave() {
        dataSaveOperations.incrementAndGet();
    }

    /**
     * Get performance statistics.
     */
    @NotNull
    public static PerformanceStats getStats() {
        return new PerformanceStats(
            getTPS(),
            getMSPT(),
            getMemoryUsageMB(),
            getMaxMemoryMB(),
            blockBreakChecks.get(),
            mlAnalysisRuns.get(),
            alertTriggers.get(),
            dataSaveOperations.get()
        );
    }

    /**
     * Reset performance counters.
     */
    public static void resetCounters() {
        blockBreakChecks.set(0);
        mlAnalysisRuns.set(0);
        alertTriggers.set(0);
        dataSaveOperations.set(0);
    }

    /**
     * Get TPS impact percentage of this plugin.
     * This is an estimation based on plugin activity.
     */
    public static double getEstimatedPluginTPSImpact() {
        long totalOperations = blockBreakChecks.get() + mlAnalysisRuns.get() +
                             alertTriggers.get() + dataSaveOperations.get();

        if (totalOperations == 0) return 0.0;

        // Rough estimation: each operation takes ~0.01ms
        double estimatedTimeMs = totalOperations * 0.01;
        double estimatedTPSImpact = (estimatedTimeMs / 50.0) * 20.0; // Convert to TPS loss

        return Math.min(estimatedTPSImpact, getTPS() * 0.1); // Cap at 10% of current TPS
    }

    /**
     * Check if server performance is degraded.
     */
    public static boolean isPerformanceDegraded() {
        return getTPS() < TPS_WARNING_THRESHOLD ||
               getMSPT() > MSPT_WARNING_THRESHOLD ||
               getMemoryUsagePercent() > MEMORY_WARNING_PERCENT;
    }

    /**
     * Performance statistics data class.
     */
    public static class PerformanceStats {
        public final double tps;
        public final long mspt;
        public final long memoryUsedMB;
        public final long memoryMaxMB;
        public final long blockBreakChecks;
        public final long mlAnalysisRuns;
        public final long alertTriggers;
        public final long dataSaveOperations;

        public PerformanceStats(double tps, long mspt, long memoryUsedMB, long memoryMaxMB,
                              long blockBreakChecks, long mlAnalysisRuns, long alertTriggers, long dataSaveOperations) {
            this.tps = tps;
            this.mspt = mspt;
            this.memoryUsedMB = memoryUsedMB;
            this.memoryMaxMB = memoryMaxMB;
            this.blockBreakChecks = blockBreakChecks;
            this.mlAnalysisRuns = mlAnalysisRuns;
            this.alertTriggers = alertTriggers;
            this.dataSaveOperations = dataSaveOperations;
        }

        @Override
        public String toString() {
            return String.format("TPS: %.1f, MSPT: %dms, Memory: %d/%dMB, Operations: %d checks, %d ML, %d alerts, %d saves",
                tps, mspt, memoryUsedMB, memoryMaxMB, blockBreakChecks, mlAnalysisRuns, alertTriggers, dataSaveOperations);
        }
    }

    /**
     * Get the current TPS (Ticks Per Second).
     *
     * @return current TPS value
     */
    public static double getCurrentTPS() {
        return getTPS();
    }
}
