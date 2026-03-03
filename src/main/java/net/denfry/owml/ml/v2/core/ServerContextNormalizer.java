package net.denfry.owml.ml.v2.core;

import java.util.concurrent.atomic.AtomicReference;

public class ServerContextNormalizer {
    
    // Exponential Moving Averages for server-wide metrics
    private final AtomicReference<Double> emaOreRatio = new AtomicReference<>(0.02);
    private final AtomicReference<Double> emaCombatHitRate = new AtomicReference<>(0.40);
    private final double ALPHA = 0.05;

    public void updateServerOreRatio(double sessionRatio) {
        emaOreRatio.updateAndGet(current -> (ALPHA * sessionRatio) + ((1.0 - ALPHA) * current));
    }

    public void updateServerCombatHitRate(double hitRate) {
        emaCombatHitRate.updateAndGet(current -> (ALPHA * hitRate) + ((1.0 - ALPHA) * current));
    }
    
    public double getServerOreRatio() {
        return emaOreRatio.get();
    }

    public double getZScore(double playerValue, double mean, double stdDev) {
        if (stdDev == 0) return 0;
        return (playerValue - mean) / stdDev;
    }

    /**
     * Synthetically generates valid data for cold starts without prior ML models.
     */
    public double[][] generateSyntheticXrayData(int samples) {
        double[][] data = new double[samples][32];
        for (int i = 0; i < samples; i++) {
            boolean isSyntheticCheater = (i % 2 == 0); // 50/50 split
            
            if (isSyntheticCheater) {
                data[i][1] = 0.95 + (Math.random() * 0.05); // Path efficiency ~ 1.0
                data[i][15] = Math.random() * 5.0; // Look deviation before finding (low)
                data[i][20] = 0.0; // Decoy interaction (0 = bypassed)
            } else {
                data[i][1] = 0.30 + (Math.random() * 0.40); // Path efficiency messy
                data[i][15] = 20.0 + Math.random() * 60.0; // Look deviation high
                data[i][20] = 1.0; // Interacts with decoys naturally
            }
        }
        return data;
    }

    public double[][] generateSyntheticCombatData(int samples) {
        double[][] data = new double[samples][40];
        for (int i = 0; i < samples; i++) {
            boolean isSyntheticCheater = (i % 2 == 0);
            
            if (isSyntheticCheater) {
                data[i][0] = 0.0 + (Math.random() * 2.0); // Angle diff near 0 (aimbot)
                data[i][5] = Math.random() * 50.0; // TTK / reaction very low
                data[i][10] = 0.0 + Math.random() * 0.5; // Deviation from hitbox center
            } else {
                data[i][0] = 5.0 + (Math.random() * 30.0); // Messy aim
                data[i][5] = 250.0 + Math.random() * 200.0; // Human reaction time
                data[i][10] = 10.0 + Math.random() * 15.0; // Natural deviation
            }
        }
        return data;
    }
}
