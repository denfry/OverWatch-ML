package net.denfry.owml.monitoring;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.PerformanceMonitor;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time metrics collector for performance monitoring and graphing.
 * Collects TPS, memory usage, player counts, and detection statistics.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class RealtimeMetricsCollector {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    // Data storage (circular buffers for last N measurements)
    private final CircularBuffer<PerformanceSnapshot> performanceHistory;
    private final CircularBuffer<MemorySnapshot> memoryHistory;
    private final CircularBuffer<PlayerSnapshot> playerHistory;
    private final CircularBuffer<DetectionSnapshot> detectionHistory;

    // Statistics counters
    private final AtomicLong totalDetections = new AtomicLong(0);
    private final AtomicLong totalAlerts = new AtomicLong(0);
    private final AtomicLong totalBans = new AtomicLong(0);
    private final AtomicLong totalKicks = new AtomicLong(0);

    // Collection intervals (in ticks)
    private static final int PERFORMANCE_INTERVAL = 20; // Every second
    private static final int MEMORY_INTERVAL = 100; // Every 5 seconds
    private static final int PLAYER_INTERVAL = 1200; // Every minute
    private static final int DETECTION_INTERVAL = 600; // Every 30 seconds

    public RealtimeMetricsCollector() {
        // Initialize circular buffers (keep last 300 entries = 5 minutes at 1/sec)
        performanceHistory = new CircularBuffer<>(300);
        memoryHistory = new CircularBuffer<>(60); // 5 minutes at 5/sec intervals
        playerHistory = new CircularBuffer<>(60); // 1 hour at 1/min intervals
        detectionHistory = new CircularBuffer<>(120); // 1 hour at 30/sec intervals

        // Collection tasks will be started later to avoid scheduler issues during initialization
    }

    /**
     * Start periodic data collection tasks
     */
    public void startCollectionTasks() {
        // Performance metrics (TPS, etc.)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            collectPerformanceMetrics();
        }, 0L, PERFORMANCE_INTERVAL);

        // Memory metrics
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            collectMemoryMetrics();
        }, 0L, MEMORY_INTERVAL);

        // Player metrics
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            collectPlayerMetrics();
        }, 0L, PLAYER_INTERVAL);

        // Detection metrics
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            collectDetectionMetrics();
        }, 0L, DETECTION_INTERVAL);
    }

    /**
     * Collect performance metrics
     */
    private void collectPerformanceMetrics() {
        try {
            // Get actual TPS from PerformanceMonitor
            double tps = PerformanceMonitor.getTPS();
            double avgTPS = calculateAverageTPS();
            long usedCPU = getCPUUsage();

            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                System.currentTimeMillis(),
                tps,
                avgTPS,
                usedCPU
            );

            performanceHistory.add(snapshot);
        } catch (Exception e) {
            // Silently ignore metrics collection errors to prevent performance impact
            // TPS monitoring should not slow down the server
        }
    }

    /**
     * Collect memory metrics
     */
    private void collectMemoryMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024; // MB
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024; // MB
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024; // MB

        MemorySnapshot snapshot = new MemorySnapshot(
            System.currentTimeMillis(),
            heapUsed,
            heapMax,
            nonHeapUsed
        );

        memoryHistory.add(snapshot);
    }

    /**
     * Collect player metrics
     */
    private void collectPlayerMetrics() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        // Get actual suspicious players count
        int suspiciousPlayers = getSuspiciousPlayersCount();

        PlayerSnapshot snapshot = new PlayerSnapshot(
            System.currentTimeMillis(),
            onlinePlayers,
            maxPlayers,
            suspiciousPlayers
        );

        playerHistory.add(snapshot);
    }

    /**
     * Collect detection metrics
     */
    private void collectDetectionMetrics() {
        long detections = totalDetections.get();
        long alerts = totalAlerts.get();
        long bans = totalBans.get();
        long kicks = totalKicks.get();

        // ML metrics if available
        double mlAccuracy = 0.0;
        int clusters = 0;

        if (plugin.getMLManager() != null) {
            try {
                var mlStats = plugin.getMLManager().getComprehensiveStats();
                // Get actual ML metrics if available
                if (mlStats != null && mlStats.learningStats != null) {
                    mlAccuracy = mlStats.learningStats.getAverageAccuracy();
                    clusters = mlStats.clusteringStats != null ? mlStats.clusteringStats.totalClusters : 0;
                } else {
                    mlAccuracy = 0.0;
                    clusters = 0;
                }
            } catch (Exception e) {
                // Fallback if ML stats are not available
                mlAccuracy = 0.0;
                clusters = 0;
            }
        }

        DetectionSnapshot snapshot = new DetectionSnapshot(
            System.currentTimeMillis(),
            detections,
            alerts,
            bans,
            kicks,
            mlAccuracy,
            clusters
        );

        detectionHistory.add(snapshot);
    }

    /**
     * Record detection event
     */
    public void recordDetection() {
        totalDetections.incrementAndGet();
    }

    /**
     * Record alert event
     */
    public void recordAlert() {
        totalAlerts.incrementAndGet();
    }

    /**
     * Record ban event
     */
    public void recordBan() {
        totalBans.incrementAndGet();
    }

    /**
     * Record kick event
     */
    public void recordKick() {
        totalKicks.incrementAndGet();
    }

    /**
     * Get recent performance data for charting
     */
    public PerformanceSnapshot[] getRecentPerformanceData(int minutes) {
        int entries = Math.min(performanceHistory.size(), minutes * 60); // 60 entries per minute
        return performanceHistory.getLast(entries);
    }

    /**
     * Get recent memory data for charting
     */
    public MemorySnapshot[] getRecentMemoryData(int minutes) {
        int entries = Math.min(memoryHistory.size(), minutes * 12); // 12 entries per minute (every 5 sec)
        return memoryHistory.getLast(entries);
    }

    /**
     * Get recent player data for charting
     */
    public PlayerSnapshot[] getRecentPlayerData(int hours) {
        int entries = Math.min(playerHistory.size(), hours * 60); // 60 entries per hour
        return playerHistory.getLast(entries);
    }

    /**
     * Get recent detection data for charting
     */
    public DetectionSnapshot[] getRecentDetectionData(int hours) {
        int entries = Math.min(detectionHistory.size(), hours * 120); // 120 entries per hour
        return detectionHistory.getLast(entries);
    }

    /**
     * Get current system status
     */
    public SystemStatus getCurrentStatus() {
        PerformanceSnapshot latestPerf = performanceHistory.size() > 0 ? performanceHistory.getLast(1)[0] : null;
        MemorySnapshot latestMem = memoryHistory.size() > 0 ? memoryHistory.getLast(1)[0] : null;
        PlayerSnapshot latestPlayer = playerHistory.size() > 0 ? playerHistory.getLast(1)[0] : null;

        return new SystemStatus(
            latestPerf != null ? latestPerf.tps : 0.0,
            latestMem != null ? latestMem.heapUsed : 0,
            latestMem != null ? latestMem.heapMax : 0,
            latestPlayer != null ? latestPlayer.onlinePlayers : 0,
            latestPlayer != null ? latestPlayer.maxPlayers : 0,
            latestPlayer != null ? latestPlayer.suspiciousPlayers : 0
        );
    }

    /**
     * Calculate average TPS from recent performance snapshots
     */
    private double calculateAverageTPS() {
        try {
            Object[] temp = performanceHistory.getLast(Math.min(performanceHistory.size(), 60)); // Last minute
            PerformanceSnapshot[] recent = new PerformanceSnapshot[temp.length];
            System.arraycopy(temp, 0, recent, 0, temp.length);
            if (recent.length == 0) {
                return PerformanceMonitor.getTPS(); // Fallback to current TPS
            }

            double sum = 0.0;
            for (PerformanceSnapshot snapshot : recent) {
                sum += snapshot.tps;
            }

            return sum / recent.length;
        } catch (Exception e) {
            // Return current TPS on error to avoid performance impact
            return PerformanceMonitor.getTPS();
        }
    }

    /**
     * Get CPU usage percentage (0-100)
     */
    private long getCPUUsage() {
        try {
            // Try to get system CPU load using OperatingSystemMXBean
            double systemLoad = osBean.getSystemLoadAverage();
            if (systemLoad >= 0) {
                // Convert load average to percentage (multiply by 100)
                return Math.round(systemLoad * 100.0);
            }

            // Fallback: try to get process CPU time if available
            // Note: This is a simplified implementation
            return 0L;
        } catch (Exception e) {
            // If CPU monitoring is not available, return 0
            return 0L;
        }
    }

    /**
     * Get the count of suspicious players
     */
    private int getSuspiciousPlayersCount() {
        try {
            // Import SuspiciousManager dynamically to avoid circular dependencies
            Class<?> suspiciousManagerClass = Class.forName("net.denfry.owml.managers.SuspiciousManager");
            java.lang.reflect.Method getSuspiciousPlayersMethod = suspiciousManagerClass.getMethod("getSuspiciousPlayersStatic");
            @SuppressWarnings("unchecked")
            java.util.Set<java.util.UUID> suspiciousPlayers = (java.util.Set<java.util.UUID>) getSuspiciousPlayersMethod.invoke(null);
            return suspiciousPlayers.size();
        } catch (Exception e) {
            // If SuspiciousManager is not available, return 0
            return 0;
        }
    }

    // ===== DATA CLASSES =====

    /**
     * Performance snapshot
     */
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

    /**
     * Memory snapshot
     */
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

    /**
     * Player snapshot
     */
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

    /**
     * Detection snapshot
     */
    public static class DetectionSnapshot {
        public final long timestamp;
        public final long totalDetections;
        public final long totalAlerts;
        public final long totalBans;
        public final long totalKicks;
        public final double mlAccuracy;
        public final int clusters;

        public DetectionSnapshot(long timestamp, long totalDetections, long totalAlerts,
                               long totalBans, long totalKicks, double mlAccuracy, int clusters) {
            this.timestamp = timestamp;
            this.totalDetections = totalDetections;
            this.totalAlerts = totalAlerts;
            this.totalBans = totalBans;
            this.totalKicks = totalKicks;
            this.mlAccuracy = mlAccuracy;
            this.clusters = clusters;
        }
    }

    /**
     * Current system status
     */
    public static class SystemStatus {
        public final double currentTPS;
        public final long memoryUsed;
        public final long memoryMax;
        public final int onlinePlayers;
        public final int maxPlayers;
        public final int suspiciousPlayers;

        public SystemStatus(double currentTPS, long memoryUsed, long memoryMax,
                          int onlinePlayers, int maxPlayers, int suspiciousPlayers) {
            this.currentTPS = currentTPS;
            this.memoryUsed = memoryUsed;
            this.memoryMax = memoryMax;
            this.onlinePlayers = onlinePlayers;
            this.maxPlayers = maxPlayers;
            this.suspiciousPlayers = suspiciousPlayers;
        }
    }

    /**
     * Circular buffer for storing historical data
     */
    private static class CircularBuffer<T> {
        private final Object[] buffer;
        private int size = 0;
        private int head = 0;

        public CircularBuffer(int capacity) {
            this.buffer = new Object[capacity];
        }

        public void add(T item) {
            buffer[head] = item;
            head = (head + 1) % buffer.length;
            if (size < buffer.length) {
                size++;
            }
        }

        @SuppressWarnings("unchecked")
        public T[] getLast(int count) {
            int actualCount = Math.min(count, size);
            T[] result = (T[]) new Object[actualCount];

            for (int i = 0; i < actualCount; i++) {
                int index = (head - 1 - i + buffer.length) % buffer.length;
                result[i] = (T) buffer[index];
            }

            return result;
        }

        public int size() {
            return size;
        }
    }
}
