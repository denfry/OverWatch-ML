package net.denfry.owml.ml.advanced;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced feature engineering system for ML models.
 * Creates derived features from raw player behavior data.
 *
 * Features:
 * - Polynomial features (squares, cubes, interactions)
 * - Temporal features (trends, seasonality, moving averages)
 * - Statistical features (distribution metrics, outliers)
 * - Behavioral patterns (sequences, frequencies)
 * - Interaction features (feature combinations)
 * - Normalization and scaling
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.7
 */
public class AdvancedFeatureEngineer {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Feature engineering configuration
    private static final int MAX_POLYNOMIAL_DEGREE = 3;
    private static final int MOVING_AVERAGE_WINDOW = 5;
    private static final double OUTLIER_THRESHOLD = 3.0; // Standard deviations

    // Cached statistical information
    private static final Map<String, FeatureStats> featureStats = new ConcurrentHashMap<>();
    private static final Map<String, List<Double>> featureHistory = new ConcurrentHashMap<>();

    // Feature engineering pipeline
    private final List<FeatureTransformer> transformers = new ArrayList<>();

    public AdvancedFeatureEngineer() {
        initializeTransformers();
    }

    /**
     * Initialize feature transformers
     */
    private void initializeTransformers() {
        transformers.add(new PolynomialTransformer());
        transformers.add(new TemporalTransformer());
        transformers.add(new StatisticalTransformer());
        transformers.add(new InteractionTransformer());
        transformers.add(new NormalizationTransformer());

        MessageManager.log("info", "Advanced feature engineering initialized with {COUNT} transformers",
            "COUNT", String.valueOf(transformers.size()));
    }

    /**
     * Engineer advanced features from raw player data
     * @param rawFeatures Raw feature map
     * @param playerHistory Historical data for temporal features
     * @return Enhanced feature map
     */
    public Map<String, Double> engineerFeatures(Map<String, Double> rawFeatures,
                                              List<Map<String, Double>> playerHistory) {
        Map<String, Double> enhancedFeatures = new HashMap<>(rawFeatures);

        // Update feature statistics
        updateFeatureStats(rawFeatures);

        // Apply each transformer
        for (FeatureTransformer transformer : transformers) {
            try {
                Map<String, Double> transformed = transformer.transform(enhancedFeatures, playerHistory);
                enhancedFeatures.putAll(transformed);
            } catch (Exception e) {
                MessageManager.log("warning", "Feature transformer {NAME} failed: {ERROR}",
                    "NAME", transformer.getClass().getSimpleName(), "ERROR", e.getMessage());
            }
        }

        return enhancedFeatures;
    }

    /**
     * Update statistical information for features
     */
    private void updateFeatureStats(Map<String, Double> features) {
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            String featureName = entry.getKey();
            double value = entry.getValue();

            // Update feature statistics
            FeatureStats stats = featureStats.computeIfAbsent(featureName, k -> new FeatureStats());
            stats.update(value);

            // Update feature history
            List<Double> history = featureHistory.computeIfAbsent(featureName, k -> new ArrayList<>());
            synchronized (history) {
                history.add(value);
                // Keep only recent history
                if (history.size() > 100) {
                    history.remove(0);
                }
            }
        }
    }

    // ===== FEATURE TRANSFORMERS =====

    /**
     * Polynomial feature transformer
     */
    private class PolynomialTransformer implements FeatureTransformer {
        @Override
        public Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history) {
            Map<String, Double> result = new HashMap<>();

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String name = entry.getKey();
                double value = entry.getValue();

                // Add polynomial features
                for (int degree = 2; degree <= MAX_POLYNOMIAL_DEGREE; degree++) {
                    double polyValue = Math.pow(value, degree);
                    result.put(name + "_pow" + degree, polyValue);
                }

                // Add square root (for positive values)
                if (value >= 0) {
                    result.put(name + "_sqrt", Math.sqrt(value));
                }

                // Add logarithmic transformation
                if (value > 0) {
                    result.put(name + "_log", Math.log(value + 1));
                }
            }

            return result;
        }
    }

    /**
     * Temporal feature transformer
     */
    private class TemporalTransformer implements FeatureTransformer {
        @Override
        public Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history) {
            Map<String, Double> result = new HashMap<>();

            if (history == null || history.size() < 2) {
                return result; // Not enough history
            }

            for (String featureName : features.keySet()) {
                List<Double> timeSeries = new ArrayList<>();

                // Extract time series for this feature
                for (Map<String, Double> historicalFeatures : history) {
                    Double value = historicalFeatures.get(featureName);
                    if (value != null) {
                        timeSeries.add(value);
                    }
                }

                if (timeSeries.size() >= MOVING_AVERAGE_WINDOW) {
                    // Moving average
                    double ma = calculateMovingAverage(timeSeries, MOVING_AVERAGE_WINDOW);
                    result.put(featureName + "_ma" + MOVING_AVERAGE_WINDOW, ma);

                    // Trend (slope)
                    double trend = calculateTrend(timeSeries);
                    result.put(featureName + "_trend", trend);

                    // Volatility (standard deviation of recent values)
                    double volatility = calculateVolatility(timeSeries, MOVING_AVERAGE_WINDOW);
                    result.put(featureName + "_volatility", volatility);

                    // Rate of change
                    if (timeSeries.size() >= 2) {
                        double current = timeSeries.get(timeSeries.size() - 1);
                        double previous = timeSeries.get(timeSeries.size() - 2);
                        double roc = previous != 0 ? (current - previous) / previous : 0;
                        result.put(featureName + "_roc", roc);
                    }
                }
            }

            return result;
        }

        private double calculateMovingAverage(List<Double> values, int window) {
            int start = Math.max(0, values.size() - window);
            double sum = 0.0;
            for (int i = start; i < values.size(); i++) {
                sum += values.get(i);
            }
            return sum / (values.size() - start);
        }

        private double calculateTrend(List<Double> values) {
            if (values.size() < 2) return 0.0;

            // Simple linear regression slope
            int n = values.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

            for (int i = 0; i < n; i++) {
                double x = i;
                double y = values.get(i);
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXX += x * x;
            }

            double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
            return Double.isNaN(slope) ? 0.0 : slope;
        }

        private double calculateVolatility(List<Double> values, int window) {
            if (values.size() < 2) return 0.0;

            int start = Math.max(0, values.size() - window);
            List<Double> windowValues = values.subList(start, values.size());

            double mean = windowValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = windowValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

            return Math.sqrt(variance);
        }
    }

    /**
     * Statistical feature transformer
     */
    private class StatisticalTransformer implements FeatureTransformer {
        @Override
        public Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history) {
            Map<String, Double> result = new HashMap<>();

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String name = entry.getKey();
                double value = entry.getValue();

                FeatureStats stats = featureStats.get(name);
                if (stats != null && stats.count > 1) {
                    // Z-score normalization
                    double zScore = (value - stats.mean) / stats.stdDev;
                    result.put(name + "_zscore", zScore);

                    // Is outlier?
                    result.put(name + "_is_outlier", Math.abs(zScore) > OUTLIER_THRESHOLD ? 1.0 : 0.0);

                    // Percentile rank
                    double percentile = calculatePercentile(value, stats);
                    result.put(name + "_percentile", percentile);

                    // Deviation from median
                    double median = stats.median;
                    result.put(name + "_median_dev", value - median);
                }
            }

            return result;
        }

        private double calculatePercentile(double value, FeatureStats stats) {
            // Simplified percentile calculation
            if (value <= stats.min) return 0.0;
            if (value >= stats.max) return 1.0;

            // Linear interpolation
            return (value - stats.min) / (stats.max - stats.min);
        }
    }

    /**
     * Interaction feature transformer
     */
    private class InteractionTransformer implements FeatureTransformer {
        @Override
        public Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history) {
            Map<String, Double> result = new HashMap<>();
            List<String> featureNames = new ArrayList<>(features.keySet());

            // Create pairwise interactions
            for (int i = 0; i < featureNames.size(); i++) {
                for (int j = i + 1; j < featureNames.size(); j++) {
                    String name1 = featureNames.get(i);
                    String name2 = featureNames.get(j);
                    double val1 = features.get(name1);
                    double val2 = features.get(name2);

                    // Product interaction
                    result.put(name1 + "_x_" + name2, val1 * val2);

                    // Ratio interaction (avoid division by zero)
                    if (val2 != 0) {
                        result.put(name1 + "_div_" + name2, val1 / val2);
                    }

                    // Sum and difference
                    result.put(name1 + "_plus_" + name2, val1 + val2);
                    result.put(name1 + "_minus_" + name2, val1 - val2);
                }
            }

            // Domain-specific interactions for mining
            addMiningInteractions(features, result);

            return result;
        }

        private void addMiningInteractions(Map<String, Double> features, Map<String, Double> result) {
            Double miningSpeed = features.get("mining_speed");
            Double idleTime = features.get("idle_time_ratio");
            Double rareOres = features.get("rare_ores_found");
            Double patternConsistency = features.get("pattern_consistency");

            if (miningSpeed != null && idleTime != null) {
                // Speed vs idle time (high speed + high idle = suspicious)
                result.put("speed_idle_product", miningSpeed * idleTime);
                result.put("speed_idle_ratio", idleTime > 0 ? miningSpeed / idleTime : 0);
            }

            if (rareOres != null && patternConsistency != null) {
                // Rare ores vs pattern consistency (high rare ores + low consistency = suspicious)
                result.put("rare_consistency_product", rareOres * (1 - patternConsistency));
            }

            if (miningSpeed != null && rareOres != null) {
                // Speed-adjusted rare ore finding
                result.put("speed_rare_ratio", miningSpeed > 0 ? rareOres / miningSpeed : 0);
            }
        }
    }

    /**
     * Normalization transformer
     */
    private class NormalizationTransformer implements FeatureTransformer {
        @Override
        public Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history) {
            Map<String, Double> result = new HashMap<>();

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String name = entry.getKey();
                double value = entry.getValue();

                FeatureStats stats = featureStats.get(name);
                if (stats != null && stats.stdDev > 0) {
                    // Min-max normalization
                    double normalized = (value - stats.min) / (stats.max - stats.min);
                    result.put(name + "_normalized", Math.max(0, Math.min(1, normalized)));

                    // Robust normalization (using median and MAD)
                    double mad = calculateMAD(featureHistory.get(name));
                    if (mad > 0) {
                        double robustNormalized = (value - stats.median) / mad;
                        result.put(name + "_robust_normalized", robustNormalized);
                    }
                }
            }

            return result;
        }

        private double calculateMAD(List<Double> values) {
            if (values == null || values.size() < 3) return 1.0;

            double median = calculateMedian(values);
            List<Double> deviations = new ArrayList<>();
            for (double value : values) {
                deviations.add(Math.abs(value - median));
            }

            return calculateMedian(deviations);
        }

        private double calculateMedian(List<Double> values) {
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int mid = sorted.size() / 2;
            return sorted.size() % 2 == 0 ?
                (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 :
                sorted.get(mid);
        }
    }

    // ===== UTILITY CLASSES =====

    /**
     * Feature transformer interface
     */
    private interface FeatureTransformer {
        Map<String, Double> transform(Map<String, Double> features, List<Map<String, Double>> history);
    }

    /**
     * Feature statistics for normalization
     */
    private static class FeatureStats {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        double sumSquares = 0.0;
        int count = 0;
        double mean = 0.0;
        double stdDev = 0.0;
        double median = 0.0;

        void update(double value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            sumSquares += value * value;
            count++;

            // Update statistics
            mean = sum / count;
            if (count > 1) {
                stdDev = Math.sqrt((sumSquares / count) - (mean * mean));
            }

            // Simple median approximation
            median = (median + value) / 2.0; // Running median approximation
        }
    }

    /**
     * Get feature engineering statistics
     */
    public Map<String, Object> getFeatureEngineeringStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFeaturesTracked", featureStats.size());
        stats.put("totalTransformers", transformers.size());
        stats.put("featureHistorySize", featureHistory.size());

        // Feature type counts
        int polynomial = 0, temporal = 0, statistical = 0, interaction = 0, normalized = 0;
        for (String featureName : featureStats.keySet()) {
            if (featureName.contains("_pow") || featureName.contains("_sqrt") || featureName.contains("_log")) {
                polynomial++;
            } else if (featureName.contains("_ma") || featureName.contains("_trend") || featureName.contains("_volatility")) {
                temporal++;
            } else if (featureName.contains("_zscore") || featureName.contains("_percentile")) {
                statistical++;
            } else if (featureName.contains("_x_") || featureName.contains("_div_")) {
                interaction++;
            } else if (featureName.contains("_normalized")) {
                normalized++;
            }
        }

        stats.put("polynomialFeatures", polynomial);
        stats.put("temporalFeatures", temporal);
        stats.put("statisticalFeatures", statistical);
        stats.put("interactionFeatures", interaction);
        stats.put("normalizedFeatures", normalized);

        return stats;
    }

    /**
     * Reset feature engineering state
     */
    public void reset() {
        featureStats.clear();
        featureHistory.clear();
        MessageManager.log("info", "Advanced feature engineering state reset");
    }
}