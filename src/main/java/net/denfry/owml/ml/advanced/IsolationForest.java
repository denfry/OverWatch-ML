package net.denfry.owml.ml.advanced;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Isolation Forest implementation for anomaly detection in player behavior.
 * Based on the paper "Isolation Forest" by Fei Tony Liu et al.
 *
 * This algorithm isolates anomalies by randomly partitioning data points,
 * where anomalies are easier to isolate and have shorter path lengths.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.7
 */
public class IsolationForest {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    private final List<IsolationTree> trees;
    private final int numTrees;
    private final int maxHeight;
    private final double anomalyThreshold;

    // Training data statistics
    private int trainedSamples = 0;
    private double averagePathLength = 0.0;

    /**
     * Create Isolation Forest
     * @param numTrees Number of trees in the forest
     * @param maxHeight Maximum height of each tree
     * @param anomalyThreshold Threshold for anomaly classification (0.0-1.0)
     */
    public IsolationForest(int numTrees, int maxHeight, double anomalyThreshold) {
        this.numTrees = numTrees;
        this.maxHeight = maxHeight;
        this.anomalyThreshold = anomalyThreshold;
        this.trees = new ArrayList<>();
    }

    /**
     * Train the Isolation Forest on player behavior data
     * @param trainingData List of feature vectors (player behavior data)
     */
    public void train(List<Map<String, Double>> trainingData) {
        if (trainingData.isEmpty()) {
            MessageManager.log("warning", "Cannot train Isolation Forest: no training data provided");
            return;
        }

        MessageManager.log("info", "Training Isolation Forest with {COUNT} samples", "COUNT", String.valueOf(trainingData.size()));

        trees.clear();
        trainedSamples = trainingData.size();

        // Calculate average path length for normalization
        averagePathLength = calculateAveragePathLength(trainingData.size());

        // Build forest
        for (int i = 0; i < numTrees; i++) {
            // Sample subset for this tree (with replacement)
            List<Map<String, Double>> treeSample = sampleWithReplacement(trainingData, trainingData.size());

            IsolationTree tree = new IsolationTree(maxHeight);
            tree.build(treeSample);

            trees.add(tree);
        }

        MessageManager.log("info", "Isolation Forest trained with {TREES} trees", "TREES", String.valueOf(numTrees));
    }

    /**
     * Calculate anomaly score for a data point
     * @param features Feature vector to score
     * @return Anomaly score (0.0 = normal, 1.0 = anomaly)
     */
    public double score(Map<String, Double> features) {
        if (trees.isEmpty()) {
            return 0.0; // Not trained yet
        }

        double averagePath = 0.0;
        for (IsolationTree tree : trees) {
            averagePath += tree.getPathLength(features);
        }
        averagePath /= trees.size();

        // Normalize to anomaly score
        double score = Math.pow(2, -averagePath / averagePathLength);

        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * Check if a data point is anomalous
     * @param features Feature vector to check
     * @return True if anomalous
     */
    public boolean isAnomaly(Map<String, Double> features) {
        return score(features) >= anomalyThreshold;
    }

    /**
     * Calculate anomaly scores for multiple data points
     * @param dataPoints List of feature vectors
     * @return List of anomaly scores
     */
    public List<Double> scoreBatch(List<Map<String, Double>> dataPoints) {
        List<Double> scores = new ArrayList<>();
        for (Map<String, Double> point : dataPoints) {
            scores.add(score(point));
        }
        return scores;
    }

    /**
     * Get feature importance based on isolation patterns
     * @return Map of feature names to importance scores
     */
    public Map<String, Double> getFeatureImportance() {
        Map<String, Double> importance = new HashMap<>();

        for (IsolationTree tree : trees) {
            Map<String, Double> treeImportance = tree.getFeatureImportance();
            for (Map.Entry<String, Double> entry : treeImportance.entrySet()) {
                importance.put(entry.getKey(),
                    importance.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }

        // Normalize by number of trees
        for (String feature : importance.keySet()) {
            importance.put(feature, importance.get(feature) / numTrees);
        }

        return importance;
    }

    /**
     * Sample data with replacement (bootstrap sampling)
     */
    private List<Map<String, Double>> sampleWithReplacement(List<Map<String, Double>> data, int sampleSize) {
        List<Map<String, Double>> sample = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < sampleSize; i++) {
            int index = random.nextInt(data.size());
            sample.add(new HashMap<>(data.get(index))); // Deep copy
        }

        return sample;
    }

    /**
     * Calculate average path length for normalization
     */
    private double calculateAveragePathLength(int n) {
        if (n <= 1) return 1.0;

        double sum = 0.0;
        for (int i = 1; i <= n; i++) {
            sum += cFactor(n - i + 1);
        }
        return sum / n;
    }

    /**
     * c(n) function for path length normalization
     */
    private static double cFactor(int n) {
        if (n <= 1) return 0.0;
        if (n == 2) return 1.0;

        return 2.0 * (Math.log(n - 1) + 0.5772156649) - 2.0 * (n - 1) / n;
    }

    /**
     * Get training statistics
     */
    public Map<String, Object> getTrainingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("numTrees", numTrees);
        stats.put("maxHeight", maxHeight);
        stats.put("trainedSamples", trainedSamples);
        stats.put("anomalyThreshold", anomalyThreshold);
        stats.put("averagePathLength", averagePathLength);
        stats.put("featureImportance", getFeatureImportance());

        return stats;
    }

    // ===== INNER CLASSES =====

    /**
     * Isolation Tree implementation
     */
    private static class IsolationTree {
        private Node root;
        private final int maxHeight;
        private final Map<String, Double> featureImportance = new HashMap<>();

        public IsolationTree(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        /**
         * Build the isolation tree
         */
        public void build(List<Map<String, Double>> data) {
            if (data.isEmpty()) return;

            // Get all feature names
            Set<String> allFeatures = new HashSet<>();
            for (Map<String, Double> sample : data) {
                allFeatures.addAll(sample.keySet());
            }

            root = buildNode(data, 0, new ArrayList<>(allFeatures));
        }

        /**
         * Recursively build tree nodes
         */
        private Node buildNode(List<Map<String, Double>> data, int height, List<String> availableFeatures) {
            if (height >= maxHeight || data.size() <= 1 || availableFeatures.isEmpty()) {
                return new Node(data.size()); // External node
            }

            // Randomly select feature and split value
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String splitFeature = availableFeatures.get(random.nextInt(availableFeatures.size()));

            // Calculate min/max for the feature
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;

            for (Map<String, Double> sample : data) {
                Double value = sample.get(splitFeature);
                if (value != null) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }

            double splitValue = min + (max - min) * random.nextDouble();

            // Split data
            List<Map<String, Double>> leftData = new ArrayList<>();
            List<Map<String, Double>> rightData = new ArrayList<>();

            for (Map<String, Double> sample : data) {
                Double value = sample.get(splitFeature);
                if (value == null || value <= splitValue) {
                    leftData.add(sample);
                } else {
                    rightData.add(sample);
                }
            }

            // Update feature importance
            featureImportance.put(splitFeature,
                featureImportance.getOrDefault(splitFeature, 0.0) + 1.0);

            // Recursively build children
            List<String> remainingFeatures = new ArrayList<>(availableFeatures);
            Node left = buildNode(leftData, height + 1, remainingFeatures);
            Node right = buildNode(rightData, height + 1, remainingFeatures);

            return new Node(splitFeature, splitValue, left, right);
        }

        /**
         * Get path length for a data point
         */
        public double getPathLength(Map<String, Double> features) {
            return getPathLength(root, features, 0);
        }

        private double getPathLength(Node node, Map<String, Double> features, int depth) {
            if (node.isExternal()) {
                // Path length for external node (Equation 2 in the paper)
                return cFactor(node.size) + depth;
            }

            Double value = features.get(node.splitFeature);
            if (value == null || value <= node.splitValue) {
                return getPathLength(node.left, features, depth + 1);
            } else {
                return getPathLength(node.right, features, depth + 1);
            }
        }

        /**
         * Get feature importance for this tree
         */
        public Map<String, Double> getFeatureImportance() {
            // Normalize by total splits
            double totalSplits = featureImportance.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<String, Double> normalized = new HashMap<>();

            if (totalSplits > 0) {
                for (Map.Entry<String, Double> entry : featureImportance.entrySet()) {
                    normalized.put(entry.getKey(), entry.getValue() / totalSplits);
                }
            }

            return normalized;
        }
    }

    /**
     * Tree node
     */
    private static class Node {
        String splitFeature;
        double splitValue;
        Node left, right;
        int size; // For external nodes

        // External node
        public Node(int size) {
            this.size = size;
        }

        // Internal node
        public Node(String splitFeature, double splitValue, Node left, Node right) {
            this.splitFeature = splitFeature;
            this.splitValue = splitValue;
            this.left = left;
            this.right = right;
        }

        public boolean isExternal() {
            return left == null && right == null;
        }
    }
}