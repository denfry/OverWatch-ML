package net.denfry.owml.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.ModernMLManager;
import net.denfry.owml.utils.PerformanceMonitor;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;

public class RealtimeMetricsCollector {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
   private final RealtimeMetricsCollector.CircularBuffer<RealtimeMetricsCollector.PerformanceSnapshot> performanceHistory;
   private final RealtimeMetricsCollector.CircularBuffer<RealtimeMetricsCollector.MemorySnapshot> memoryHistory;
   private final RealtimeMetricsCollector.CircularBuffer<RealtimeMetricsCollector.PlayerSnapshot> playerHistory;
   private final RealtimeMetricsCollector.CircularBuffer<RealtimeMetricsCollector.DetectionSnapshot> detectionHistory;
   private final AtomicLong totalDetections = new AtomicLong(0L);
   private final AtomicLong totalAlerts = new AtomicLong(0L);
   private final AtomicLong totalBans = new AtomicLong(0L);
   private final AtomicLong totalKicks = new AtomicLong(0L);
   private static final int PERFORMANCE_INTERVAL = 20;
   private static final int MEMORY_INTERVAL = 100;
   private static final int PLAYER_INTERVAL = 1200;
   private static final int DETECTION_INTERVAL = 600;

   public RealtimeMetricsCollector() {
      this.performanceHistory = new RealtimeMetricsCollector.CircularBuffer<>(300);
      this.memoryHistory = new RealtimeMetricsCollector.CircularBuffer<>(60);
      this.playerHistory = new RealtimeMetricsCollector.CircularBuffer<>(60);
      this.detectionHistory = new RealtimeMetricsCollector.CircularBuffer<>(120);
   }

   public void startCollectionTasks() {
      Schedulers.globalTimer(() -> {
         try {
            this.collectPerformanceMetrics();
         } catch (Exception var5) {
         }

         try {
            this.collectMemoryMetrics();
         } catch (Exception var4) {
         }

         try {
            this.collectPlayerMetrics();
         } catch (Exception var3) {
         }

         try {
            this.collectDetectionMetrics();
         } catch (Exception var2) {
         }
      }, 0L, 200L);
   }

   private void collectPerformanceMetrics() {
      try {
         double tps = PerformanceMonitor.getTPS();
         double avgTPS = this.calculateAverageTPS();
         long usedCPU = this.getCPUUsage();
         RealtimeMetricsCollector.PerformanceSnapshot snapshot = new RealtimeMetricsCollector.PerformanceSnapshot(
            System.currentTimeMillis(), tps, avgTPS, usedCPU
         );
         this.performanceHistory.add(snapshot);
      } catch (Exception var8) {
      }
   }

   private void collectMemoryMetrics() {
      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024L / 1024L;
      long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024L / 1024L;
      long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / 1024L / 1024L;
      RealtimeMetricsCollector.MemorySnapshot snapshot = new RealtimeMetricsCollector.MemorySnapshot(System.currentTimeMillis(), heapUsed, heapMax, nonHeapUsed);
      this.memoryHistory.add(snapshot);
   }

   private void collectPlayerMetrics() {
      int onlinePlayers = Bukkit.getOnlinePlayers().size();
      int maxPlayers = Bukkit.getMaxPlayers();
      int suspiciousPlayers = this.getSuspiciousPlayersCount();
      RealtimeMetricsCollector.PlayerSnapshot snapshot = new RealtimeMetricsCollector.PlayerSnapshot(
         System.currentTimeMillis(), onlinePlayers, maxPlayers, suspiciousPlayers
      );
      this.playerHistory.add(snapshot);
   }

   private void collectDetectionMetrics() {
      long detections = this.totalDetections.get();
      long alerts = this.totalAlerts.get();
      long bans = this.totalBans.get();
      long kicks = this.totalKicks.get();
      double mlAccuracy = 0.0;
      int clusters = 0;
      if (plugin.getMLManager() != null) {
         try {
            ModernMLManager.MLStats mlStats = plugin.getMLManager().getComprehensiveStats();
            if (mlStats != null && mlStats.learningStats != null) {
               mlAccuracy = mlStats.learningStats.getAverageAccuracy();
               clusters = mlStats.clusteringStats != null ? mlStats.clusteringStats.totalClusters : 0;
            } else {
               mlAccuracy = 0.0;
               clusters = 0;
            }
         } catch (Exception var13) {
            mlAccuracy = 0.0;
            clusters = 0;
         }
      }

      RealtimeMetricsCollector.DetectionSnapshot snapshot = new RealtimeMetricsCollector.DetectionSnapshot(
         System.currentTimeMillis(), detections, alerts, bans, kicks, mlAccuracy, clusters
      );
      this.detectionHistory.add(snapshot);
   }

   public void recordDetection() {
      this.totalDetections.incrementAndGet();
   }

   public void recordAlert() {
      this.totalAlerts.incrementAndGet();
   }

   public void recordBan() {
      this.totalBans.incrementAndGet();
   }

   public void recordKick() {
      this.totalKicks.incrementAndGet();
   }

   public RealtimeMetricsCollector.PerformanceSnapshot[] getRecentPerformanceData(int minutes) {
      int entries = Math.min(this.performanceHistory.size(), minutes * 60);
      return this.performanceHistory.getLast(entries);
   }

   public RealtimeMetricsCollector.MemorySnapshot[] getRecentMemoryData(int minutes) {
      int entries = Math.min(this.memoryHistory.size(), minutes * 12);
      return this.memoryHistory.getLast(entries);
   }

   public RealtimeMetricsCollector.PlayerSnapshot[] getRecentPlayerData(int hours) {
      int entries = Math.min(this.playerHistory.size(), hours * 60);
      return this.playerHistory.getLast(entries);
   }

   public RealtimeMetricsCollector.DetectionSnapshot[] getRecentDetectionData(int hours) {
      int entries = Math.min(this.detectionHistory.size(), hours * 120);
      return this.detectionHistory.getLast(entries);
   }

   public RealtimeMetricsCollector.SystemStatus getCurrentStatus() {
      RealtimeMetricsCollector.PerformanceSnapshot latestPerf = this.performanceHistory.size() > 0 ? this.performanceHistory.getLast(1)[0] : null;
      RealtimeMetricsCollector.MemorySnapshot latestMem = this.memoryHistory.size() > 0 ? this.memoryHistory.getLast(1)[0] : null;
      RealtimeMetricsCollector.PlayerSnapshot latestPlayer = this.playerHistory.size() > 0 ? this.playerHistory.getLast(1)[0] : null;
      return new RealtimeMetricsCollector.SystemStatus(
         latestPerf != null ? latestPerf.tps : 0.0,
         latestMem != null ? latestMem.heapUsed : 0L,
         latestMem != null ? latestMem.heapMax : 0L,
         latestPlayer != null ? latestPlayer.onlinePlayers : 0,
         latestPlayer != null ? latestPlayer.maxPlayers : 0,
         latestPlayer != null ? latestPlayer.suspiciousPlayers : 0
      );
   }

   private double calculateAverageTPS() {
      try {
         Object[] temp = this.performanceHistory.getLast(Math.min(this.performanceHistory.size(), 60));
         RealtimeMetricsCollector.PerformanceSnapshot[] recent = new RealtimeMetricsCollector.PerformanceSnapshot[temp.length];
         System.arraycopy(temp, 0, recent, 0, temp.length);
         if (recent.length == 0) {
            return PerformanceMonitor.getTPS();
         } else {
            double sum = 0.0;

            for (RealtimeMetricsCollector.PerformanceSnapshot snapshot : recent) {
               sum += snapshot.tps;
            }

            return sum / recent.length;
         }
      } catch (Exception var9) {
         return PerformanceMonitor.getTPS();
      }
   }

   private long getCPUUsage() {
      try {
         double systemLoad = osBean.getSystemLoadAverage();
         return systemLoad >= 0.0 ? Math.round(systemLoad * 100.0) : 0L;
      } catch (Exception var3) {
         return 0L;
      }
   }

   private int getSuspiciousPlayersCount() {
      try {
         Class<?> suspiciousManagerClass = Class.forName("net.denfry.owml.managers.SuspiciousManager");
         Method getSuspiciousPlayersMethod = suspiciousManagerClass.getMethod("getSuspiciousPlayersStatic");
         Set<UUID> suspiciousPlayers = (Set<UUID>)getSuspiciousPlayersMethod.invoke(null);
         return suspiciousPlayers.size();
      } catch (Exception var4) {
         return 0;
      }
   }

   private static class CircularBuffer<T> {
      private final Object[] buffer;
      private int size = 0;
      private int head = 0;

      public CircularBuffer(int capacity) {
         this.buffer = new Object[capacity];
      }

      public void add(T item) {
         this.buffer[this.head] = item;
         this.head = (this.head + 1) % this.buffer.length;
         if (this.size < this.buffer.length) {
            this.size++;
         }
      }

      public T[] getLast(int count) {
         int actualCount = Math.min(count, this.size);
         T[] result = (T[])(new Object[actualCount]);

         for (int i = 0; i < actualCount; i++) {
            int index = (this.head - 1 - i + this.buffer.length) % this.buffer.length;
            result[i] = (T)this.buffer[index];
         }

         return result;
      }

      public int size() {
         return this.size;
      }
   }

   public static class DetectionSnapshot {
      public final long timestamp;
      public final long totalDetections;
      public final long totalAlerts;
      public final long totalBans;
      public final long totalKicks;
      public final double mlAccuracy;
      public final int clusters;

      public DetectionSnapshot(long timestamp, long totalDetections, long totalAlerts, long totalBans, long totalKicks, double mlAccuracy, int clusters) {
         this.timestamp = timestamp;
         this.totalDetections = totalDetections;
         this.totalAlerts = totalAlerts;
         this.totalBans = totalBans;
         this.totalKicks = totalKicks;
         this.mlAccuracy = mlAccuracy;
         this.clusters = clusters;
      }
   }

   public static class MemorySnapshot {
      public final long timestamp;
      public final long heapUsed;
      public final long heapMax;
      public final long nonHeapUsed;

      public MemorySnapshot(long timestamp, long heapUsed, long heapMax, long nonHeapUsed) {
         this.timestamp = timestamp;
         this.heapUsed = heapUsed;
         this.heapMax = heapMax;
         this.nonHeapUsed = nonHeapUsed;
      }
   }

   public static class PerformanceSnapshot {
      public final long timestamp;
      public final double tps;
      public final double averageTPS;
      public final long cpuUsage;

      public PerformanceSnapshot(long timestamp, double tps, double averageTPS, long cpuUsage) {
         this.timestamp = timestamp;
         this.tps = tps;
         this.averageTPS = averageTPS;
         this.cpuUsage = cpuUsage;
      }
   }

   public static class PlayerSnapshot {
      public final long timestamp;
      public final int onlinePlayers;
      public final int maxPlayers;
      public final int suspiciousPlayers;

      public PlayerSnapshot(long timestamp, int onlinePlayers, int maxPlayers, int suspiciousPlayers) {
         this.timestamp = timestamp;
         this.onlinePlayers = onlinePlayers;
         this.maxPlayers = maxPlayers;
         this.suspiciousPlayers = suspiciousPlayers;
      }
   }

   public static class SystemStatus {
      public final double currentTPS;
      public final long memoryUsed;
      public final long memoryMax;
      public final int onlinePlayers;
      public final int maxPlayers;
      public final int suspiciousPlayers;

      public SystemStatus(double currentTPS, long memoryUsed, long memoryMax, int onlinePlayers, int maxPlayers, int suspiciousPlayers) {
         this.currentTPS = currentTPS;
         this.memoryUsed = memoryUsed;
         this.memoryMax = memoryMax;
         this.onlinePlayers = onlinePlayers;
         this.maxPlayers = maxPlayers;
         this.suspiciousPlayers = suspiciousPlayers;
      }
   }
}
