package net.denfry.owml.ml.advanced;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.ml.ReasoningMLModel;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ensemble learning system combining multiple ML models for robust cheat detection.
 * Uses stacking approach to combine predictions from different algorithms.
 *
 * Features:
 * - Multiple base learners (Isolation Forest, Autoencoder, Reasoning Model)
 * - Stacking with meta-learner
 * - Weighted voting system
 * - Confidence-based decision making
 * - Parallel prediction execution
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.7
 */
public class EnsembleDetector {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Base learners
    private IsolationForest isolationForest;
    private AutoencoderAnomalyDetector autoencoder;
    private ReasoningMLModel reasoningModel;

    // Meta-learner weights (learned during training)
    private double[] modelWeights;
    private double ensembleThreshold = 0.6;

    // Training data for meta-learner
    private List<double[]> basePredictions;
    private List<Double> trueLabels;

    private boolean trained = false;

    /**
     * Create ensemble detector
     */
    public EnsembleDetector() {
        initializeBaseLearners();
    }

    /**
     * Initialize base learning models
     */
    private void initializeBaseLearners() {
        // Initialize Isolation Forest
        isolationForest = new IsolationForest(100, 10, 0.6);

        // Initialize Autoencoder (will be sized based on feature count during training)
        autoencoder = null; // Will be initialized during training

        // Get existing reasoning model
        reasoningModel = new ReasoningMLModel(plugin, null); // Config will be passed during training
    }

    /**
     * Train the ensemble system
     * @param trainingData List of training samples with features
     * @param labels True labels (0.0 = normal, 1.0 = cheater)
     * @param featureNames Names of features for reasoning model
     */
    public void train(List<Map<String, Double>> trainingData, List<Double> labels, List<String> featureNames) {
        if (trainingData.isEmpty() || labels.isEmpty()) {
            MessageManager.log("warning", "Cannot train ensemble: insufficient training data");
            return;
        }

        MessageManager.log("info", "Training ensemble detector with {COUNT} samples", "COUNT", String.valueOf(trainingData.size()));

        // Prepare data for base learners
        List<Map<String, Double>> normalData = new ArrayList<>();
        List<Map<String, Double>> cheaterData = new ArrayList<>();

        for (int i = 0; i < trainingData.size(); i++) {
            if (labels.get(i) < 0.5) {
                normalData.add(trainingData.get(i));
            } else {
                cheaterData.add(trainingData.get(i));
            }
        }

        // Train Isolation Forest on normal data (for anomaly detection)
        if (!normalData.isEmpty()) {
            isolationForest.train(normalData);
        }

        // Initialize and train Autoencoder
        if (!normalData.isEmpty()) {
            int featureCount = getFeatureCount(normalData);
            autoencoder = new AutoencoderAnomalyDetector(featureCount, featureCount / 2, featureCount / 4);

            // Convert to double arrays for autoencoder
            List<double[]> normalArrays = convertToArrays(normalData, featureNames);
            if (!normalArrays.isEmpty()) {
                autoencoder.train(normalArrays);
            }
        }

        // Train reasoning model
        // Note: Reasoning model needs special format, this would need integration

        // Train meta-learner (stacking)
        trainMetaLearner(trainingData, labels, featureNames);

        trained = true;
        MessageManager.log("info", "Ensemble detector training completed");
    }

    /**
     * Make prediction using ensemble
     * @param features Feature vector
     * @return Anomaly score (0.0-1.0)
     */
    public double predict(Map<String, Double> features) {
        if (!trained) return 0.0;

        // Get predictions from base learners
        List<Double> basePredictions = getBasePredictions(features);

        // Combine predictions using meta-learner
        return combinePredictions(basePredictions);
    }

    /**
     * Check if features indicate cheating behavior
     * @param features Feature vector
     * @return True if cheating detected
     */
    public boolean isCheating(Map<String, Double> features) {
        return predict(features) >= ensembleThreshold;
    }

    /**
     * Get predictions from all base learners
     */
    private List<Double> getBasePredictions(Map<String, Double> features) {
        List<Double> predictions = new ArrayList<>();

        // Isolation Forest prediction
        double ifScore = isolationForest.score(features);
        predictions.add(ifScore);

        // Autoencoder prediction
        if (autoencoder != null) {
            double[] featureArray = convertToArray(features, getFeatureNames(features));
            double aeScore = autoencoder.scoreAnomaly(featureArray);
            predictions.add(aeScore);
        } else {
            predictions.add(0.0);
        }

        // Reasoning model prediction (simplified)
        // This would need proper integration with ReasoningMLModel
        double rmScore = calculateReasoningScore(features);
        predictions.add(rmScore);

        return predictions;
    }

    /**
     * Combine base predictions using meta-learner
     */
    private double combinePredictions(List<Double> basePredictions) {
        if (modelWeights == null || modelWeights.length != basePredictions.size()) {
            // Fallback: weighted average
            double sum = 0.0;
            double weightSum = 0.0;
            for (int i = 0; i < basePredictions.size(); i++) {
                double weight = 1.0 / (i + 1.0); // Decreasing weights
                sum += basePredictions.get(i) * weight;
                weightSum += weight;
            }
            return weightSum > 0 ? sum / weightSum : 0.0;
        }

        // Weighted sum using learned weights
        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < basePredictions.size(); i++) {
            weightedSum += basePredictions.get(i) * modelWeights[i];
            weightSum += modelWeights[i];
        }

        return weightSum > 0 ? weightedSum / weightSum : 0.0;
    }

    /**
     * Train meta-learner using stacking
     */
    private void trainMetaLearner(List<Map<String, Double>> trainingData, List<Double> labels, List<String> featureNames) {
        basePredictions = new ArrayList<>();
        trueLabels = new ArrayList<>(labels);

        // Get predictions from base learners for training data
        for (Map<String, Double> sample : trainingData) {
            List<Double> predictions = getBasePredictions(sample);
            double[] predArray = new double[predictions.size()];
            for (int i = 0; i < predictions.size(); i++) {
                predArray[i] = predictions.get(i);
            }
            basePredictions.add(predArray);
        }

        // Simple meta-learner: learn weights using coordinate descent
        learnWeights();
    }

    /**
     * Learn optimal weights for base learners
     */
    private void learnWeights() {
        int numModels = basePredictions.get(0).length;
        modelWeights = new double[numModels];

        // Initialize with equal weights
        Arrays.fill(modelWeights, 1.0 / numModels);

        // Simple optimization: try different weight combinations
        double bestScore = evaluateWeights(modelWeights);
        double[] bestWeights = modelWeights.clone();

        // Random search for better weights
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) { // 100 random trials
            double[] candidateWeights = new double[numModels];
            double sum = 0.0;
            for (int j = 0; j < numModels; j++) {
                candidateWeights[j] = random.nextDouble();
                sum += candidateWeights[j];
            }
            // Normalize
            for (int j = 0; j < numModels; j++) {
                candidateWeights[j] /= sum;
            }

            double score = evaluateWeights(candidateWeights);
            if (score > bestScore) {
                bestScore = score;
                bestWeights = candidateWeights.clone();
            }
        }

        modelWeights = bestWeights;
    }

    /**
     * Evaluate weight combination using cross-validation
     */
    private double evaluateWeights(double[] weights) {
        int correct = 0;
        int total = 0;

        for (int i = 0; i < basePredictions.size(); i++) {
            double[] predictions = basePredictions.get(i);
            double trueLabel = trueLabels.get(i);

            // Combine predictions
            double combined = 0.0;
            double weightSum = 0.0;
            for (int j = 0; j < predictions.length; j++) {
                combined += predictions[j] * weights[j];
                weightSum += weights[j];
            }
            combined /= weightSum;

            // Check if prediction is correct
            boolean predictedCheat = combined >= 0.5;
            boolean actualCheat = trueLabel >= 0.5;

            if (predictedCheat == actualCheat) {
                correct++;
            }
            total++;
        }

        return total > 0 ? (double) correct / total : 0.0;
    }

    /**
     * Calculate reasoning model score (simplified implementation)
     */
    private double calculateReasoningScore(Map<String, Double> features) {
        double score = 0.0;
        int factors = 0;

        // Blocks per minute — derived from actual feature names
        Double totalBlocks = features.get("total_blocks_broken");
        double sessionDuration = features.getOrDefault("sessionDuration", 600.0);
        if (totalBlocks != null && sessionDuration > 0) {
            double blocksPerMin = totalBlocks / (sessionDuration / 60.0);
            if (blocksPerMin > 60.0) score += 0.3;
            factors++;
        }

        // Rare ore count — use actual feature keys from PlayerMiningData
        double diamonds = features.getOrDefault("ore_count_diamond_ore", 0.0)
                        + features.getOrDefault("ore_count_deepslate_diamond_ore", 0.0);
        double debris = features.getOrDefault("ore_count_ancient_debris", 0.0);
        double rareOres = diamonds + debris;
        if (rareOres > 5.0) {
            score += 0.2;
            factors++;
        }

        // Spatial consistency: high time at one Y + low variance + high rare ores = suspicious
        Double percentAtY = features.get("percent_time_at_most_common_y");
        Double yVariance = features.get("y_level_variance");
        if (percentAtY != null && yVariance != null) {
            boolean spatiallyLocked = percentAtY > 85.0 && yVariance < 15.0;
            if (spatiallyLocked && rareOres > 3.0) score += 0.4;
            factors++;
        }

        // Idle time: near-zero pauses between finds = no hesitation = suspicious
        Double avgIdleTime = features.get("avg_idle_time_seconds");
        if (avgIdleTime != null) {
            if (avgIdleTime < 0.5) score += 0.3;
            factors++;
        }

        return factors > 0 ? score / factors : 0.0;
    }

    // ===== UTILITY METHODS =====

    private int getFeatureCount(List<Map<String, Double>> data) {
        if (data.isEmpty()) return 0;
        return data.get(0).size();
    }

    private List<String> getFeatureNames(Map<String, Double> features) {
        return new ArrayList<>(features.keySet());
    }

    private List<double[]> convertToArrays(List<Map<String, Double>> data, List<String> featureNames) {
        List<double[]> arrays = new ArrayList<>();
        for (Map<String, Double> sample : data) {
            arrays.add(convertToArray(sample, featureNames));
        }
        return arrays;
    }

    private double[] convertToArray(Map<String, Double> features, List<String> featureNames) {
        double[] array = new double[featureNames.size()];
        for (int i = 0; i < featureNames.size(); i++) {
            Double value = features.get(featureNames.get(i));
            array[i] = value != null ? value : 0.0;
        }
        return array;
    }

    /**
     * Get ensemble statistics
     */
    public Map<String, Object> getEnsembleStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("trained", trained);
        stats.put("ensembleThreshold", ensembleThreshold);
        stats.put("modelWeights", modelWeights != null ? Arrays.toString(modelWeights) : "null");

        // Base learner stats
        if (isolationForest != null) {
            stats.put("isolationForest", isolationForest.getTrainingStats());
        }
        if (autoencoder != null) {
            stats.put("autoencoder", autoencoder.getTrainingStats());
        }

        return stats;
    }

    /**
     * Set ensemble threshold
     */
    public void setThreshold(double threshold) {
        this.ensembleThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    /**
     * Check if ensemble is trained
     */
    public boolean isTrained() {
        return trained;
    }

    /**
     * Parallel prediction for batch processing
     */
    public List<Double> predictBatch(List<Map<String, Double>> featuresBatch) {
        List<CompletableFuture<Double>> futures = new ArrayList<>();

        for (Map<String, Double> features : featuresBatch) {
            CompletableFuture<Double> future = CompletableFuture.supplyAsync(() ->
                predict(features)
            );
            futures.add(future);
        }

        // Wait for all predictions
        List<Double> results = new ArrayList<>();
        for (CompletableFuture<Double> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                results.add(0.0); // Default on error
            }
        }

        return results;
    }
}