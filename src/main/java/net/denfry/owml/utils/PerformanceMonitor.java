package net.denfry.owml.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class PerformanceMonitor {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private static final long[] tickTimes = new long[600];
   private static int tickIndex = 0;
   private static long lastTickTime = System.nanoTime();
   private static final AtomicLong blockBreakChecks = new AtomicLong(0L);
   private static final AtomicLong mlAnalysisRuns = new AtomicLong(0L);
   private static final AtomicLong alertTriggers = new AtomicLong(0L);
   private static final AtomicLong dataSaveOperations = new AtomicLong(0L);
   private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
   private static final double TPS_WARNING_THRESHOLD = 18.0;
   private static final double TPS_CRITICAL_THRESHOLD = 15.0;
   private static final long MSPT_WARNING_THRESHOLD = 50000000L;
   private static final double MEMORY_WARNING_PERCENT = 85.0;

   private static void updateMetrics() {
      long currentTime = System.nanoTime();
      long tickDuration = currentTime - lastTickTime;
      lastTickTime = currentTime;
      tickTimes[tickIndex] = tickDuration;
      tickIndex = (tickIndex + 1) % tickTimes.length;
   }

   private static void logPerformanceWarnings() {
      double tps = getTPS();
      long mspt = getMSPT();
      double memoryPercent = getMemoryUsagePercent();
      if (tps <= 15.0) {
         MessageManager.log("error", "Critical TPS drop detected: {TPS}", "TPS", String.format("%.1f", tps));
      } else if (tps <= 18.0) {
         MessageManager.log("warning", "Low TPS detected: {TPS}", "TPS", String.format("%.1f", tps));
      }

      if (mspt >= 50000000L) {
         MessageManager.log("warning", "High MSPT detected: {MSPT}ms", "MSPT", String.format("%.1f", mspt / 1000000.0));
      }

      if (memoryPercent >= 85.0) {
         MessageManager.log("warning", "High memory usage: {USAGE}%", "USAGE", String.format("%.1f", memoryPercent));
      }
   }

   public static double getTPS() {
      try {
         double[] tps = Bukkit.getTPS();
         return tps[0];
      } catch (Throwable var9) {
         long total = 0L;
         int n = 0;

         for (long t : tickTimes) {
            if (t > 0L) {
               total += t;
               n++;
            }
         }

         if (n == 0) {
            return 20.0;
         } else {
            double secondsPer20Ticks = (double)total / n / 1.0E9;
            return Math.min(20.0, 20.0 / Math.max(secondsPer20Ticks, 1.0E-6));
         }
      }
   }

   public static long getMSPT() {
      long totalTime = 0L;
      int validTicks = 0;

      for (long tickTime : tickTimes) {
         if (tickTime > 0L) {
            totalTime += tickTime;
            validTicks++;
         }
      }

      return validTicks == 0 ? 0L : totalTime / validTicks / 1000000L;
   }

   public static double getMemoryUsagePercent() {
      MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
      long used = heapUsage.getUsed();
      long max = heapUsage.getMax();
      return max == 0L ? 0.0 : (double)used / max * 100.0;
   }

   public static long getMemoryUsageMB() {
      MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
      return heapUsage.getUsed() / 1048576L;
   }

   public static long getMaxMemoryMB() {
      MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
      return heapUsage.getMax() / 1048576L;
   }

   public static void recordBlockBreakCheck() {
      blockBreakChecks.incrementAndGet();
   }

   public static void recordMLAnalysis() {
      mlAnalysisRuns.incrementAndGet();
   }

   public static void recordAlertTrigger() {
      alertTriggers.incrementAndGet();
   }

   public static void recordDataSave() {
      dataSaveOperations.incrementAndGet();
   }

   @NotNull
   public static PerformanceMonitor.PerformanceStats getStats() {
      return new PerformanceMonitor.PerformanceStats(
         getTPS(), getMSPT(), getMemoryUsageMB(), getMaxMemoryMB(), blockBreakChecks.get(), mlAnalysisRuns.get(), alertTriggers.get(), dataSaveOperations.get()
      );
   }

   public static void resetCounters() {
      blockBreakChecks.set(0L);
      mlAnalysisRuns.set(0L);
      alertTriggers.set(0L);
      dataSaveOperations.set(0L);
   }

   public static double getEstimatedPluginTPSImpact() {
      long totalOperations = blockBreakChecks.get() + mlAnalysisRuns.get() + alertTriggers.get() + dataSaveOperations.get();
      if (totalOperations == 0L) {
         return 0.0;
      } else {
         double estimatedTimeMs = totalOperations * 0.01;
         double estimatedTPSImpact = estimatedTimeMs / 50.0 * 20.0;
         return Math.min(estimatedTPSImpact, getTPS() * 0.1);
      }
   }

   public static boolean isPerformanceDegraded() {
      return getTPS() < 18.0 || getMSPT() > 50000000L || getMemoryUsagePercent() > 85.0;
   }

   public static double getCurrentTPS() {
      return getTPS();
   }

   static {
      SchedulerUtils.runTaskTimer(plugin, PerformanceMonitor::updateMetrics, 20L, 20L);
      SchedulerUtils.runTaskTimer(plugin, PerformanceMonitor::logPerformanceWarnings, 1200L, 1200L);
   }

   public static class PerformanceStats {
      public final double tps;
      public final long mspt;
      public final long memoryUsedMB;
      public final long memoryMaxMB;
      public final long blockBreakChecks;
      public final long mlAnalysisRuns;
      public final long alertTriggers;
      public final long dataSaveOperations;

      public PerformanceStats(
         double tps, long mspt, long memoryUsedMB, long memoryMaxMB, long blockBreakChecks, long mlAnalysisRuns, long alertTriggers, long dataSaveOperations
      ) {
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
         return String.format(
            "TPS: %.1f, MSPT: %dms, Memory: %d/%dMB, Operations: %d checks, %d ML, %d alerts, %d saves",
            this.tps,
            this.mspt,
            this.memoryUsedMB,
            this.memoryMaxMB,
            this.blockBreakChecks,
            this.mlAnalysisRuns,
            this.alertTriggers,
            this.dataSaveOperations
         );
      }
   }
}
