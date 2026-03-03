package net.denfry.owml.ml.v2.core;

import java.util.Arrays;

public class MathUtils {

    /**
     * Dynamic Time Warping (DTW) 
     * Используется для сравнения пути игрока с оптимальным путём до руд (Seed Cracker).
     */
    public static double calculateDTW(double[][] playerPath, double[][] optimalPath) {
        if (playerPath == null || optimalPath == null || playerPath.length == 0 || optimalPath.length == 0) {
            return Double.POSITIVE_INFINITY;
        }

        int n = playerPath.length;
        int m = optimalPath.length;
        double[][] dtw = new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                dtw[i][j] = Double.POSITIVE_INFINITY;
            }
        }
        dtw[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double cost = distance3D(playerPath[i - 1], optimalPath[j - 1]);
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }
        return dtw[n][m];
    }

    public static double distance3D(double[] p1, double[] p2) {
        return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2) + Math.pow(p1[2] - p2[2], 2));
    }

    /**
     * Shannon Entropy of an array of angles (Pitch/Yaw).
     * Низкая энтропия = бот/Aimbot/Xray.
     */
    public static double calculateEntropy(double[] angles) {
        if (angles == null || angles.length == 0) return 0.0;
        
        int[] buckets = new int[36]; // 10-degree buckets
        for (double angle : angles) {
            int bucket = (int) (Math.abs(angle) % 360 / 10);
            buckets[bucket]++;
        }
        
        double entropy = 0;
        for (int count : buckets) {
            if (count > 0) {
                double p = (double) count / angles.length;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    public static double mean(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    public static double stdDev(double[] values, double mean) {
        if (values.length <= 1) return 0;
        double sumSq = 0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / values.length);
    }
    
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
