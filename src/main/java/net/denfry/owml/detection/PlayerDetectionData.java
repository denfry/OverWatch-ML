package net.denfry.owml.detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Comprehensive data tracking for player detection analysis.
 * Maintains historical data, patterns, and behavioral metrics.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public class PlayerDetectionData {

    private final UUID playerId;
    private final AtomicLong firstSeen = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger totalBlocksMined = new AtomicInteger(0);
    private final AtomicInteger suspiciousActions = new AtomicInteger(0);

    // Movement tracking
    private volatile Location lastLocation;
    private volatile Location lastSuspiciousLocation; // Last location where suspicious activity was detected
    private final Queue<Location> recentLocations = new ConcurrentLinkedQueue<>();
    private final Queue<Long> miningTimestamps = new ConcurrentLinkedQueue<>();
    private final Map<Material, Integer> minedBlocksByType = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_LOCATIONS = 100;
    
    // Suspicious locations history
    private final Queue<SuspiciousLocationRecord> suspiciousLocations = new ConcurrentLinkedQueue<>();
    private static final int MAX_SUSPICIOUS_LOCATIONS = 50; // Keep last 50 suspicious locations

    // Detection history
    private final Queue<DetectionResult> recentDetections = new ConcurrentLinkedQueue<>();
    private static final int MAX_RECENT_DETECTIONS = 50;

    // Behavioral metrics
    private final Map<String, Double> behavioralScores = new ConcurrentHashMap<>();
    private final AtomicLong totalMiningTime = new AtomicLong(0);
    private volatile double averageMiningSpeed = 0.0;

    // Session data
    private volatile long sessionStartTime = System.currentTimeMillis();
    private final AtomicInteger sessionBlocksMined = new AtomicInteger(0);

    public PlayerDetectionData(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * Update player activity
     */
    public void updateActivity(Location location) {
        lastActivity.set(System.currentTimeMillis());

        if (location != null) {
            lastLocation = location.clone();

            // Keep recent locations for pattern analysis
            recentLocations.offer(location.clone());
            if (recentLocations.size() > MAX_RECENT_LOCATIONS) {
                recentLocations.poll();
            }
        }
    }

    /**
     * Record mining activity
     */
    public void recordMining(Material material, Location location) {
        totalBlocksMined.incrementAndGet();
        sessionBlocksMined.incrementAndGet();

        // Track mined blocks by type
        minedBlocksByType.merge(material, 1, Integer::sum);

        miningTimestamps.offer(System.currentTimeMillis());
        if (miningTimestamps.size() > 100) {
            miningTimestamps.poll();
        }

        updateMiningSpeed();
        updateActivity(location);
    }

    /**
     * Record detection result
     */
    public void addDetectionResult(DetectionResult result) {
        recentDetections.offer(result);
        if (recentDetections.size() > MAX_RECENT_DETECTIONS) {
            recentDetections.poll();
        }

        if (result.isSuspicious()) {
            suspiciousActions.incrementAndGet();
        }
    }

    /**
     * Update mining speed calculation
     */
    private void updateMiningSpeed() {
        if (miningTimestamps.size() < 2) return;

        long[] timestamps = miningTimestamps.stream().mapToLong(Long::longValue).toArray();
        long totalTime = timestamps[timestamps.length - 1] - timestamps[0];
        int blocksInPeriod = timestamps.length;

        if (totalTime > 0) {
            averageMiningSpeed = (double) blocksInPeriod / (totalTime / 1000.0); // blocks per second
        }
    }

    /**
     * Mark player as inactive
     */
    public void markInactive() {
        lastActivity.set(System.currentTimeMillis());
    }

    /**
     * Check if player is currently active
     */
    public boolean isActive() {
        return (System.currentTimeMillis() - lastActivity.get()) < (5 * 60 * 1000); // 5 minutes
    }

    /**
     * Reset session data
     */
    public void resetSession() {
        sessionStartTime = System.currentTimeMillis();
        sessionBlocksMined.set(0);
    }

    /**
     * Get behavioral score for specific metric
     */
    public double getBehavioralScore(String metric) {
        return behavioralScores.getOrDefault(metric, 0.0);
    }

    /**
     * Set behavioral score
     */
    public void setBehavioralScore(String metric, double score) {
        behavioralScores.put(metric, score);
    }

    /**
     * Get mining efficiency (blocks per minute in current session)
     */
    public double getMiningEfficiency() {
        long sessionTime = System.currentTimeMillis() - sessionStartTime;
        if (sessionTime == 0) return 0.0;

        return (double) sessionBlocksMined.get() / (sessionTime / 60000.0); // blocks per minute
    }

    /**
     * Get suspicion ratio (suspicious actions / total actions)
     */
    public double getSuspicionRatio() {
        int total = totalBlocksMined.get() + recentLocations.size();
        if (total == 0) return 0.0;

        return (double) suspiciousActions.get() / total;
    }

    /**
     * Get recent detection trend (last 10 detections)
     */
    public double getRecentDetectionTrend() {
        List<DetectionResult> recent = new ArrayList<>();
        recentDetections.forEach(recent::add);

        if (recent.size() < 5) return 0.0;

        // Get last 10 or all if less
        int count = Math.min(10, recent.size());
        List<DetectionResult> lastDetections = recent.subList(recent.size() - count, recent.size());

        return lastDetections.stream()
                .mapToDouble(result -> result.getConfidence())
                .average()
                .orElse(0.0);
    }

    /**
     * Check if player has consistent suspicious behavior
     */
    public boolean hasConsistentSuspiciousBehavior() {
        if (recentDetections.size() < 5) return false;

        long suspiciousCount = recentDetections.stream()
                .filter(DetectionResult::isSuspicious)
                .count();

        return (double) suspiciousCount / recentDetections.size() > 0.6; // 60% suspicious
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public long getFirstSeen() { return firstSeen.get(); }
    public long getLastActivity() { return lastActivity.get(); }
    public int getTotalBlocksMined() { return totalBlocksMined.get(); }
    public int getSuspiciousActions() { return suspiciousActions.get(); }
    public Location getLastLocation() { return lastLocation; }
    public Location getLastSuspiciousLocation() { return lastSuspiciousLocation; }
    public void setLastSuspiciousLocation(Location location) { 
        if (location != null) {
            this.lastSuspiciousLocation = location.clone(); 
        }
    }
    public double getAverageMiningSpeed() { return averageMiningSpeed; }
    public Queue<Location> getRecentLocations() { return new ConcurrentLinkedQueue<>(recentLocations); }
    public Queue<DetectionResult> getRecentDetections() { return new ConcurrentLinkedQueue<>(recentDetections); }
    public Map<String, Double> getBehavioralScores() { return new HashMap<>(behavioralScores); }

    public long getSessionStartTime() { return sessionStartTime; }

    public List<DetectionResult> getDetectionResults() {
        return new ArrayList<>(recentDetections);
    }

    public Map<Material, Integer> getMinedBlocksByType() {
        return new HashMap<>(minedBlocksByType);
    }

    /**
     * Add a suspicious location to the history
     */
    public void addSuspiciousLocation(Location location, String reason) {
        if (location != null) {
            SuspiciousLocationRecord record = new SuspiciousLocationRecord(
                location.clone(), 
                System.currentTimeMillis(), 
                reason
            );
            suspiciousLocations.offer(record);
            
            // Keep only the last MAX_SUSPICIOUS_LOCATIONS
            while (suspiciousLocations.size() > MAX_SUSPICIOUS_LOCATIONS) {
                suspiciousLocations.poll();
            }
            
            // Also update the last suspicious location for quick access
            this.lastSuspiciousLocation = location.clone();
        }
    }
    
    /**
     * Get all suspicious locations
     */
    public List<SuspiciousLocationRecord> getSuspiciousLocations() {
        return new ArrayList<>(suspiciousLocations);
    }
    
    /**
     * Get suspicious locations from the last N minutes
     */
    public List<SuspiciousLocationRecord> getRecentSuspiciousLocations(int minutes) {
        long cutoffTime = System.currentTimeMillis() - (minutes * 60 * 1000L);
        return suspiciousLocations.stream()
            .filter(record -> record.timestamp >= cutoffTime)
            .toList();
    }

    @Override
    public String toString() {
        return String.format("PlayerDetectionData{player=%s, mined=%d, suspicious=%d, efficiency=%.2f, suspicionRatio=%.3f}",
                           playerId, totalBlocksMined.get(), suspiciousActions.get(),
                           getMiningEfficiency(), getSuspicionRatio());
    }
    
    /**
     * Record of a suspicious location with timestamp and reason
     */
    public static class SuspiciousLocationRecord {
        private final Location location;
        private final long timestamp;
        private final String reason;
        
        public SuspiciousLocationRecord(Location location, long timestamp, String reason) {
            this.location = location;
            this.timestamp = timestamp;
            this.reason = reason != null ? reason : "Unknown";
        }
        
        public Location getLocation() { return location; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
        
        public String getFormattedTime() {
            long minutesAgo = (System.currentTimeMillis() - timestamp) / 1000 / 60;
            if (minutesAgo < 1) return "just now";
            if (minutesAgo == 1) return "1 minute ago";
            if (minutesAgo < 60) return minutesAgo + " minutes ago";
            long hoursAgo = minutesAgo / 60;
            if (hoursAgo == 1) return "1 hour ago";
            if (hoursAgo < 24) return hoursAgo + " hours ago";
            long daysAgo = hoursAgo / 24;
            return daysAgo + " day" + (daysAgo > 1 ? "s" : "") + " ago";
        }
        
        @Override
        public String toString() {
            return String.format("SuspiciousLocation{loc=%s, reason=%s, time=%s}", 
                location, reason, getFormattedTime());
        }
    }
}
