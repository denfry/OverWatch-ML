package net.denfry.owml.ml.v2.profile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BehavioralProfile {
    public final UUID playerId;
    
    // Tier 1: Instant Filter state
    public final AtomicInteger xraySuspicionScore = new AtomicInteger(0);
    public final AtomicInteger combatSuspicionScore = new AtomicInteger(0);
    
    // ML State & Bayesian Trust
    private double bayesianTrust = 0.5; // [0.0 - 1.0] 1.0 = completely trusted
    private long firstJoinTime;
    public double crossSessionConsistency = 1.0;
    public double driftMetric = 0.0;

    // Ring buffers for Tier 2 Temporal Sequence Analysis (LSTM-lite substitute)
    public final double[][] xrayEventWindow = new double[60][];
    public int xrayWindowIndex = 0;
    
    public final double[][] combatEventWindow = new double[60][];
    public int combatWindowIndex = 0;

    public BehavioralProfile(UUID playerId) {
        this.playerId = playerId;
        this.firstJoinTime = System.currentTimeMillis();
    }

    /**
     * Updates trust using a simplified Bayesian inference combined with EMA.
     */
    public synchronized void updateTrust(boolean isAnomaly, double anomalyScore) {
        double prior = this.bayesianTrust;
        
        // Likelihood of observing this anomalyScore given they are trusted (isAnomaly=false)
        // or untrusted (isAnomaly=true).
        double likelihood = isAnomaly ? anomalyScore : (1.0 - anomalyScore);
        
        double evidence = (prior * likelihood) + ((1.0 - prior) * (1.0 - likelihood));
        if (evidence == 0) evidence = 0.0001; // prevent div zero
        
        double posterior = (prior * likelihood) / evidence;
        
        // Exponential moving average for smooth transitions
        this.bayesianTrust = (0.9 * prior) + (0.1 * posterior);
    }

    /**
     * Adapts a base threshold based on the player's historical trust level.
     */
    public double getAdaptiveThreshold(double baseThreshold) {
        // High trust -> higher threshold (harder to flag)
        // Low trust (new player or suspicious) -> lower threshold (easier to flag)
        
        // Give new players a "grace period" or strict period? 
        // Typically, new players are untrusted (trust ~0.5).
        
        double trustFactor = 1.0 + (bayesianTrust - 0.5); // ranges from 0.5 to 1.5
        double adapted = baseThreshold * trustFactor;
        
        // Cap thresholds to logical bounds
        return Math.min(0.99, Math.max(0.50, adapted));
    }

    public void addXrayEvent(double[] features) {
        synchronized (xrayEventWindow) {
            xrayEventWindow[xrayWindowIndex] = features.clone();
            xrayWindowIndex = (xrayWindowIndex + 1) % 60;
        }
    }

    public void addCombatEvent(double[] features) {
        synchronized (combatEventWindow) {
            combatEventWindow[combatWindowIndex] = features.clone();
            combatWindowIndex = (combatWindowIndex + 1) % 60;
        }
    }
}
