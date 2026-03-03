package net.denfry.owml.ml;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/**
 * Memory optimization system for ML data storage.
 * Features:
 * - Soft/weak references for cached data
 * - LRU cache with size limits
 * - Memory pressure monitoring
 * - Automatic cache cleanup
 * - Efficient data structures
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.6
 */
public class MemoryOptimizer {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Memory limits
    private static final long MAX_MEMORY_USAGE_MB = 256; // 256MB for ML data
    private static final int MAX_CACHE_ENTRIES = 10000;
    private static final long CACHE_CLEANUP_INTERVAL = 5 * 60 * 1000; // 5 minutes

    // Cache structures
    private static final Map<String, CacheEntry> lruCache = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private static final Map<String, SoftReference<Map<String, Object>>> softCache = new ConcurrentHashMap<>();
    private static final Map<String, WeakReference<Map<String, Object>>> weakCache = new ConcurrentHashMap<>();

    // Memory monitoring
    private static final ScheduledExecutorService memoryMonitor = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "ML-Memory-Monitor"));

    private static volatile long lastMemoryCheck = System.currentTimeMillis();
    private static volatile long cacheHits = 0;
    private static volatile long cacheMisses = 0;

    static {
        // Start memory monitoring
        startMemoryMonitoring();
    }

    /**
     * Cache a training sample with automatic memory management
     */
    public static void cacheTrainingSample(String key, Map<String, Object> sample) {
        if (isMemoryPressureHigh()) {
            // Use weak references under memory pressure
            weakCache.put(key, new WeakReference<>(sample));
        } else {
            // Use soft references for normal conditions
            softCache.put(key, new SoftReference<>(sample));
        }

        // Also add to LRU cache for fast access
        CacheEntry entry = new CacheEntry(sample, System.currentTimeMillis());
        synchronized (lruCache) {
            lruCache.put(key, entry);
        }
    }

    /**
     * Get cached training sample
     */
    public static Map<String, Object> getCachedSample(String key) {
        // Try LRU cache first (fastest)
        synchronized (lruCache) {
            CacheEntry entry = lruCache.get(key);
            if (entry != null) {
                entry.lastAccess = System.currentTimeMillis();
                cacheHits++;
                return entry.data;
            }
        }

        // Try soft cache
        SoftReference<Map<String, Object>> softRef = softCache.get(key);
        if (softRef != null) {
            Map<String, Object> data = softRef.get();
            if (data != null) {
                cacheHits++;
                return data;
            } else {
                // Reference was cleared, remove it
                softCache.remove(key);
            }
        }

        // Try weak cache
        WeakReference<Map<String, Object>> weakRef = weakCache.get(key);
        if (weakRef != null) {
            Map<String, Object> data = weakRef.get();
            if (data != null) {
                cacheHits++;
                return data;
            } else {
                // Reference was cleared, remove it
                weakCache.remove(key);
            }
        }

        cacheMisses++;
        return null;
    }

    /**
     * Remove sample from all caches
     */
    public static void removeFromCache(String key) {
        synchronized (lruCache) {
            lruCache.remove(key);
        }
        softCache.remove(key);
        weakCache.remove(key);
    }

    /**
     * Check if memory pressure is high
     */
    private static boolean isMemoryPressureHigh() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // Consider memory pressure high if using more than 80% of allocated heap
        return (double) usedMemory / totalMemory > 0.8;
    }

    /**
     * Get current memory usage for ML data
     */
    public static long getMLMemoryUsage() {
        long usage = 0;

        // Estimate LRU cache size
        synchronized (lruCache) {
            for (CacheEntry entry : lruCache.values()) {
                usage += estimateObjectSize(entry.data);
            }
        }

        // Soft and weak references don't count toward our direct usage
        // as they can be cleared by GC

        return usage;
    }

    /**
     * Estimate size of a map object (rough approximation)
     */
    private static long estimateObjectSize(Map<String, Object> map) {
        if (map == null) return 0;

        long size = 0;
        // Base object overhead
        size += 16;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            // Key string
            size += 40 + (entry.getKey().length() * 2); // String overhead + chars

            // Value (rough estimate)
            Object value = entry.getValue();
            if (value instanceof String) {
                size += 40 + (((String) value).length() * 2);
            } else if (value instanceof Number) {
                size += 16; // Primitive wrapper
            } else if (value instanceof Map) {
                size += estimateObjectSize((Map<String, Object>) value);
            } else {
                size += 16; // Generic object
            }
        }

        return size;
    }

    /**
     * Perform cache cleanup
     */
    public static void performCacheCleanup() {
        long startTime = System.currentTimeMillis();

        int softCleaned = 0;
        int weakCleaned = 0;

        // Clean soft references
        Iterator<Map.Entry<String, SoftReference<Map<String, Object>>>> softIter = softCache.entrySet().iterator();
        while (softIter.hasNext()) {
            Map.Entry<String, SoftReference<Map<String, Object>>> entry = softIter.next();
            if (entry.getValue().get() == null) {
                softIter.remove();
                softCleaned++;
            }
        }

        // Clean weak references
        Iterator<Map.Entry<String, WeakReference<Map<String, Object>>>> weakIter = weakCache.entrySet().iterator();
        while (weakIter.hasNext()) {
            Map.Entry<String, WeakReference<Map<String, Object>>> entry = weakIter.next();
            if (entry.getValue().get() == null) {
                weakIter.remove();
                weakCleaned++;
            }
        }

        // Clean old LRU entries
        synchronized (lruCache) {
            long cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000); // 30 minutes
            Iterator<Map.Entry<String, CacheEntry>> lruIter = lruCache.entrySet().iterator();
            while (lruIter.hasNext()) {
                Map.Entry<String, CacheEntry> entry = lruIter.next();
                if (entry.getValue().lastAccess < cutoffTime) {
                    lruIter.remove();
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        MessageManager.log("info", "Cache cleanup completed in {TIME}ms - removed {SOFT} soft, {WEAK} weak references",
            "TIME", String.valueOf(duration), "SOFT", String.valueOf(softCleaned), "WEAK", String.valueOf(weakCleaned));
    }

    /**
     * Start memory monitoring thread
     */
    private static void startMemoryMonitoring() {
        memoryMonitor.scheduleAtFixedRate(() -> {
            try {
                // Check memory usage
                long mlMemoryUsage = getMLMemoryUsage();
                long maxMemory = MAX_MEMORY_USAGE_MB * 1024 * 1024;

                if (mlMemoryUsage > maxMemory) {
                    MessageManager.log("warning", "ML memory usage high: {USED}MB / {MAX}MB",
                        "USED", String.valueOf(mlMemoryUsage / (1024 * 1024)),
                        "MAX", String.valueOf(MAX_MEMORY_USAGE_MB));

                    // Force cleanup
                    performCacheCleanup();
                }

                // Periodic cleanup
                if (System.currentTimeMillis() - lastMemoryCheck > CACHE_CLEANUP_INTERVAL) {
                    performCacheCleanup();
                    lastMemoryCheck = System.currentTimeMillis();
                }

            } catch (Exception e) {
                MessageManager.log("error", "Error in memory monitoring: {ERROR}", "ERROR", e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES); // Check every minute
    }

    /**
     * Get memory optimization statistics
     */
    public static Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("mlMemoryUsageMB", getMLMemoryUsage() / (1024.0 * 1024.0));
        stats.put("maxMemoryLimitMB", MAX_MEMORY_USAGE_MB);
        stats.put("lruCacheSize", lruCache.size());
        stats.put("softCacheSize", softCache.size());
        stats.put("weakCacheSize", weakCache.size());
        stats.put("cacheHits", cacheHits);
        stats.put("cacheMisses", cacheMisses);
        stats.put("cacheHitRate", cacheHits + cacheMisses > 0 ?
            (double) cacheHits / (cacheHits + cacheMisses) : 0.0);
        stats.put("memoryPressureHigh", isMemoryPressureHigh());

        return stats;
    }

    /**
     * Clear all caches (useful for testing or memory emergencies)
     */
    public static void clearAllCaches() {
        synchronized (lruCache) {
            lruCache.clear();
        }
        softCache.clear();
        weakCache.clear();
        cacheHits = 0;
        cacheMisses = 0;

        MessageManager.log("info", "All ML caches cleared");
    }

    /**
     * Shutdown memory monitoring
     */
    public static void shutdown() {
        memoryMonitor.shutdown();
        try {
            if (!memoryMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                memoryMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            memoryMonitor.shutdownNow();
        }
    }

    /**
     * Cache entry with access tracking
     */
    private static class CacheEntry {
        public final Map<String, Object> data;
        public volatile long lastAccess;

        public CacheEntry(Map<String, Object> data, long lastAccess) {
            this.data = data;
            this.lastAccess = lastAccess;
        }
    }
}