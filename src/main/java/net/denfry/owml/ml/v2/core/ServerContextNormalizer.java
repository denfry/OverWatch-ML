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
     * All 32 feature slots are populated with plausible values to avoid
     * a sparse-vs-dense bias in Isolation Forest at startup.
     */
    public double[][] generateSyntheticXrayData(int samples) {
        double[][] data = new double[samples][32];
        java.util.Random rng = new java.util.Random(42); // deterministic seed
        for (int i = 0; i < samples; i++) {
            boolean isCheater = (i % 2 == 0);

            // Fill all slots with base noise so no feature is always zero
            for (int j = 0; j < 32; j++) {
                data[i][j] = 0.25 + rng.nextDouble() * 0.25; // [0.25, 0.50] baseline
            }

            if (isCheater) {
                data[i][1]  = 0.92 + rng.nextDouble() * 0.08; // path efficiency ~ 1.0
                data[i][3]  = 0.88 + rng.nextDouble() * 0.10; // directness high
                data[i][7]  = 0.85 + rng.nextDouble() * 0.10; // ore hit rate high
                data[i][11] = 0.90 + rng.nextDouble() * 0.08; // precision
                data[i][15] = rng.nextDouble() * 5.0;          // look deviation low
                data[i][20] = 0.0;                             // decoy bypassed
                data[i][25] = 0.88 + rng.nextDouble() * 0.10; // sequence consistency
                data[i][29] = 0.80 + rng.nextDouble() * 0.15; // spatial focus
            } else {
                data[i][1]  = 0.28 + rng.nextDouble() * 0.40;
                data[i][3]  = 0.25 + rng.nextDouble() * 0.35;
                data[i][7]  = 0.18 + rng.nextDouble() * 0.35;
                data[i][11] = 0.20 + rng.nextDouble() * 0.35;
                data[i][15] = 20.0 + rng.nextDouble() * 60.0;
                data[i][20] = 1.0;
                data[i][25] = 0.15 + rng.nextDouble() * 0.40;
                data[i][29] = 0.15 + rng.nextDouble() * 0.40;
            }
        }
        return data;
    }

    public double[][] generateSyntheticCombatData(int samples) {
        double[][] data = new double[samples][40];
        java.util.Random rng = new java.util.Random(43);
        for (int i = 0; i < samples; i++) {
            boolean isCheater = (i % 2 == 0);

            // Base noise for all slots
            for (int j = 0; j < 40; j++) {
                data[i][j] = 0.20 + rng.nextDouble() * 0.30;
            }

            if (isCheater) {
                data[i][0]  = rng.nextDouble() * 2.0;          // angle diff near 0 (aimbot)
                data[i][2]  = 0.90 + rng.nextDouble() * 0.09;  // hit rate
                data[i][5]  = rng.nextDouble() * 50.0;          // TTK low
                data[i][8]  = rng.nextDouble() * 0.5;           // reaction jitter low
                data[i][10] = rng.nextDouble() * 0.5;           // hitbox deviation low
                data[i][18] = 0.90 + rng.nextDouble() * 0.09;  // headshot rate
                data[i][30] = rng.nextDouble() * 0.02;          // movement variance near 0
            } else {
                data[i][0]  = 5.0 + rng.nextDouble() * 30.0;
                data[i][2]  = 0.25 + rng.nextDouble() * 0.35;
                data[i][5]  = 250.0 + rng.nextDouble() * 200.0;
                data[i][8]  = 30.0 + rng.nextDouble() * 80.0;
                data[i][10] = 10.0 + rng.nextDouble() * 15.0;
                data[i][18] = 0.10 + rng.nextDouble() * 0.35;
                data[i][30] = 0.08 + rng.nextDouble() * 0.20;
            }
        }
        return data;
    }
}
