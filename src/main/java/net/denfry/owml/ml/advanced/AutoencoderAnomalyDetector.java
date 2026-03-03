package net.denfry.owml.ml.advanced;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Autoencoder-based anomaly detection for complex player behavior patterns.
 * Uses a simple neural network autoencoder to learn normal behavior patterns
 * and detect anomalies based on reconstruction error.
 *
 * Features:
 * - Multi-layer autoencoder with bottleneck
 * - Reconstruction error-based anomaly scoring
 * - Batch training with backpropagation
 * - Adaptive learning rate
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.7
 */
public class AutoencoderAnomalyDetector {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Network architecture
    private final int inputSize;
    private final int hiddenSize;
    private final int bottleneckSize;

    // Network weights and biases
    private double[][] encoderWeights1;
    private double[] encoderBiases1;
    private double[][] encoderWeights2;
    private double[] encoderBiases2;

    private double[][] decoderWeights1;
    private double[] decoderBiases1;
    private double[][] decoderWeights2;
    private double[] decoderBiases2;

    // Training parameters
    private volatile double learningRate = 0.001;
    private volatile double anomalyThreshold = 0.1;
    private volatile int maxEpochs = 100;
    private volatile int batchSize = 32;

    // Training statistics
    private int trainedSamples = 0;
    private double averageReconstructionError = 0.0;
    private boolean trained = false;

    /**
     * Create autoencoder anomaly detector
     * @param inputSize Number of input features
     * @param hiddenSize Size of hidden layer
     * @param bottleneckSize Size of bottleneck layer
     */
    public AutoencoderAnomalyDetector(int inputSize, int hiddenSize, int bottleneckSize) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.bottleneckSize = bottleneckSize;

        initializeWeights();
    }

    /**
     * Initialize network weights randomly
     */
    private void initializeWeights() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Encoder weights
        encoderWeights1 = new double[inputSize][hiddenSize];
        encoderBiases1 = new double[hiddenSize];
        encoderWeights2 = new double[hiddenSize][bottleneckSize];
        encoderBiases2 = new double[bottleneckSize];

        // Decoder weights
        decoderWeights1 = new double[bottleneckSize][hiddenSize];
        decoderBiases1 = new double[hiddenSize];
        decoderWeights2 = new double[hiddenSize][inputSize];
        decoderBiases2 = new double[inputSize];

        // Initialize with Xavier initialization
        double encoderLimit1 = Math.sqrt(6.0 / (inputSize + hiddenSize));
        double encoderLimit2 = Math.sqrt(6.0 / (hiddenSize + bottleneckSize));
        double decoderLimit1 = Math.sqrt(6.0 / (bottleneckSize + hiddenSize));
        double decoderLimit2 = Math.sqrt(6.0 / (hiddenSize + inputSize));

        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                encoderWeights1[i][j] = random.nextDouble(-encoderLimit1, encoderLimit1);
            }
        }

        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < bottleneckSize; j++) {
                encoderWeights2[i][j] = random.nextDouble(-encoderLimit2, encoderLimit2);
            }
        }

        for (int i = 0; i < bottleneckSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                decoderWeights1[i][j] = random.nextDouble(-decoderLimit1, decoderLimit1);
            }
        }

        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                decoderWeights2[i][j] = random.nextDouble(-decoderLimit2, decoderLimit2);
            }
        }

        // Initialize biases to small values
        Arrays.fill(encoderBiases1, 0.01);
        Arrays.fill(encoderBiases2, 0.01);
        Arrays.fill(decoderBiases1, 0.01);
        Arrays.fill(decoderBiases2, 0.01);
    }

    /**
     * Train the autoencoder on normal behavior data
     * @param trainingData List of normalized feature vectors (0.0-1.0 range)
     */
    public void train(List<double[]> trainingData) {
        if (trainingData.isEmpty()) {
            MessageManager.log("warning", "Cannot train autoencoder: no training data provided");
            return;
        }

        MessageManager.log("info", "Training autoencoder with {COUNT} samples", "COUNT", String.valueOf(trainingData.size()));

        trainedSamples = trainingData.size();

        // Training loop
        for (int epoch = 0; epoch < maxEpochs; epoch++) {
            double epochLoss = 0.0;
            int batches = 0;

            // Shuffle data for each epoch
            Collections.shuffle(trainingData);

            // Process in batches
            for (int i = 0; i < trainingData.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, trainingData.size());
                List<double[]> batch = trainingData.subList(i, endIndex);

                double batchLoss = trainBatch(batch);
                epochLoss += batchLoss;
                batches++;
            }

            double avgEpochLoss = epochLoss / batches;

            // Adaptive learning rate
            if (epoch > 10 && avgEpochLoss < 0.01) {
                learningRate *= 0.9; // Reduce learning rate as we converge
            }

            if (epoch % 10 == 0) {
                MessageManager.log("info", "Autoencoder epoch {EPOCH}: loss = {LOSS}",
                    "EPOCH", String.valueOf(epoch), "LOSS", String.format("%.6f", avgEpochLoss));
            }
        }

        // Calculate average reconstruction error on training data
        double totalError = 0.0;
        for (double[] sample : trainingData) {
            double error = calculateReconstructionError(sample);
            totalError += error;
        }
        averageReconstructionError = totalError / trainingData.size();

        trained = true;
        MessageManager.log("info", "Autoencoder training completed. Average reconstruction error: {ERROR}",
            "ERROR", String.format("%.6f", averageReconstructionError));
    }

    /**
     * Train on a single batch
     */
    private double trainBatch(List<double[]> batch) {
        double totalLoss = 0.0;

        // Accumulate gradients
        double[][] encoderGradients1 = new double[inputSize][hiddenSize];
        double[] encoderBiasGradients1 = new double[hiddenSize];
        double[][] encoderGradients2 = new double[hiddenSize][bottleneckSize];
        double[] encoderBiasGradients2 = new double[bottleneckSize];

        double[][] decoderGradients1 = new double[bottleneckSize][hiddenSize];
        double[] decoderBiasGradients1 = new double[hiddenSize];
        double[][] decoderGradients2 = new double[hiddenSize][inputSize];
        double[] decoderBiasGradients2 = new double[inputSize];

        for (double[] input : batch) {
            TrainingResult result = forwardPass(input);
            double loss = calculateLoss(input, result.output);
            totalLoss += loss;

            // Backward pass
            BackpropGradients gradients = backwardPass(input, result);

            // Accumulate gradients
            addGradients(encoderGradients1, gradients.encoderGradients1);
            addGradients(encoderBiasGradients1, gradients.encoderBiasGradients1);
            addGradients(encoderGradients2, gradients.encoderGradients2);
            addGradients(encoderBiasGradients2, gradients.encoderBiasGradients2);

            addGradients(decoderGradients1, gradients.decoderGradients1);
            addGradients(decoderBiasGradients1, gradients.decoderBiasGradients1);
            addGradients(decoderGradients2, gradients.decoderGradients2);
            addGradients(decoderBiasGradients2, gradients.decoderBiasGradients2);
        }

        // Average gradients
        double batchSize = batch.size();
        divideGradients(encoderGradients1, batchSize);
        divideGradients(encoderBiasGradients1, batchSize);
        divideGradients(encoderGradients2, batchSize);
        divideGradients(encoderBiasGradients2, batchSize);
        divideGradients(decoderGradients1, batchSize);
        divideGradients(decoderBiasGradients1, batchSize);
        divideGradients(decoderGradients2, batchSize);
        divideGradients(decoderBiasGradients2, batchSize);

        // Update weights
        updateWeights(encoderWeights1, encoderGradients1);
        updateWeights(encoderBiases1, encoderBiasGradients1);
        updateWeights(encoderWeights2, encoderGradients2);
        updateWeights(encoderBiases2, encoderBiasGradients2);

        updateWeights(decoderWeights1, decoderGradients1);
        updateWeights(decoderBiases1, decoderBiasGradients1);
        updateWeights(decoderWeights2, decoderGradients2);
        updateWeights(decoderBiases2, decoderBiasGradients2);

        return totalLoss / batch.size();
    }

    /**
     * Forward pass through the network
     */
    private TrainingResult forwardPass(double[] input) {
        // Encoder
        double[] hidden1 = matrixVectorMultiply(encoderWeights1, input);
        addVector(hidden1, encoderBiases1);
        applyActivation(hidden1); // ReLU

        double[] bottleneck = matrixVectorMultiply(encoderWeights2, hidden1);
        addVector(bottleneck, encoderBiases2);
        applyActivation(bottleneck); // ReLU

        // Decoder
        double[] hidden2 = matrixVectorMultiply(decoderWeights1, bottleneck);
        addVector(hidden2, decoderBiases1);
        applyActivation(hidden2); // ReLU

        double[] output = matrixVectorMultiply(decoderWeights2, hidden2);
        addVector(output, decoderBiases2);
        applySigmoid(output); // Sigmoid for output

        return new TrainingResult(hidden1, bottleneck, hidden2, output);
    }

    /**
     * Calculate reconstruction error (anomaly score)
     */
    public double calculateReconstructionError(double[] input) {
        if (!trained) return 0.0;

        TrainingResult result = forwardPass(input);
        return calculateLoss(input, result.output);
    }

    /**
     * Calculate anomaly score (normalized reconstruction error)
     */
    public double scoreAnomaly(double[] input) {
        if (!trained) return 0.0;

        double error = calculateReconstructionError(input);
        // Normalize by average training error
        return error / (averageReconstructionError + 1e-6);
    }

    /**
     * Check if input is anomalous
     */
    public boolean isAnomaly(double[] input) {
        return scoreAnomaly(input) >= anomalyThreshold;
    }

    // ===== HELPER METHODS =====

    private double calculateLoss(double[] target, double[] output) {
        double loss = 0.0;
        for (int i = 0; i < target.length; i++) {
            double diff = target[i] - output[i];
            loss += diff * diff;
        }
        return loss / target.length; // MSE
    }

    private BackpropGradients backwardPass(double[] input, TrainingResult result) {
        // Calculate output layer gradients
        double[] outputError = new double[input.length];
        for (int i = 0; i < outputError.length; i++) {
            outputError[i] = (result.output[i] - input[i]) * result.output[i] * (1 - result.output[i]); // Sigmoid derivative
        }

        // Decoder gradients
        double[][] decoderGradients2 = outerProduct(result.hidden2, outputError);
        double[] decoderBiasGradients2 = outputError.clone();

        double[] hidden2Error = vectorMatrixMultiplyTranspose(outputError, decoderWeights2);
        for (int i = 0; i < hidden2Error.length; i++) {
            hidden2Error[i] *= result.hidden2[i] > 0 ? 1 : 0; // ReLU derivative
        }

        double[][] decoderGradients1 = outerProduct(result.bottleneck, hidden2Error);
        double[] decoderBiasGradients1 = hidden2Error.clone();

        // Encoder gradients
        double[] bottleneckError = vectorMatrixMultiplyTranspose(hidden2Error, decoderWeights1);
        for (int i = 0; i < bottleneckError.length; i++) {
            bottleneckError[i] *= result.bottleneck[i] > 0 ? 1 : 0; // ReLU derivative
        }

        double[][] encoderGradients2 = outerProduct(result.hidden1, bottleneckError);
        double[] encoderBiasGradients2 = bottleneckError.clone();

        double[] hidden1Error = vectorMatrixMultiplyTranspose(bottleneckError, encoderWeights2);
        for (int i = 0; i < hidden1Error.length; i++) {
            hidden1Error[i] *= result.hidden1[i] > 0 ? 1 : 0; // ReLU derivative
        }

        double[][] encoderGradients1 = outerProduct(input, hidden1Error);
        double[] encoderBiasGradients1 = hidden1Error.clone();

        return new BackpropGradients(
            encoderGradients1, encoderBiasGradients1,
            encoderGradients2, encoderBiasGradients2,
            decoderGradients1, decoderBiasGradients1,
            decoderGradients2, decoderBiasGradients2
        );
    }

    // ===== MATRIX/VECTOR OPERATIONS =====

    private double[] matrixVectorMultiply(double[][] matrix, double[] vector) {
        double[] result = new double[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < vector.length; j++) {
                result[i] += matrix[i][j] * vector[j];
            }
        }
        return result;
    }

    private double[] vectorMatrixMultiplyTranspose(double[] vector, double[][] matrix) {
        double[] result = new double[matrix[0].length];
        for (int i = 0; i < matrix[0].length; i++) {
            for (int j = 0; j < vector.length; j++) {
                result[i] += vector[j] * matrix[j][i];
            }
        }
        return result;
    }

    private double[][] outerProduct(double[] a, double[] b) {
        double[][] result = new double[a.length][b.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                result[i][j] = a[i] * b[j];
            }
        }
        return result;
    }

    private void addVector(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] += b[i];
        }
    }

    private void applyActivation(double[] vector) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] = Math.max(0, vector[i]); // ReLU
        }
    }

    private void applySigmoid(double[] vector) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] = 1.0 / (1.0 + Math.exp(-vector[i]));
        }
    }

    private void addGradients(double[][] target, double[][] source) {
        for (int i = 0; i < target.length; i++) {
            for (int j = 0; j < target[i].length; j++) {
                target[i][j] += source[i][j];
            }
        }
    }

    private void addGradients(double[] target, double[] source) {
        for (int i = 0; i < target.length; i++) {
            target[i] += source[i];
        }
    }

    private void divideGradients(double[][] gradients, double divisor) {
        for (int i = 0; i < gradients.length; i++) {
            for (int j = 0; j < gradients[i].length; j++) {
                gradients[i][j] /= divisor;
            }
        }
    }

    private void divideGradients(double[] gradients, double divisor) {
        for (int i = 0; i < gradients.length; i++) {
            gradients[i] /= divisor;
        }
    }

    private void updateWeights(double[][] weights, double[][] gradients) {
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i].length; j++) {
                weights[i][j] -= learningRate * gradients[i][j];
            }
        }
    }

    private void updateWeights(double[] biases, double[] gradients) {
        for (int i = 0; i < biases.length; i++) {
            biases[i] -= learningRate * gradients[i];
        }
    }

    /**
     * Get training statistics
     */
    public Map<String, Object> getTrainingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("inputSize", inputSize);
        stats.put("hiddenSize", hiddenSize);
        stats.put("bottleneckSize", bottleneckSize);
        stats.put("trainedSamples", trainedSamples);
        stats.put("averageReconstructionError", averageReconstructionError);
        stats.put("learningRate", learningRate);
        stats.put("anomalyThreshold", anomalyThreshold);
        stats.put("trained", trained);

        return stats;
    }

    // ===== INNER CLASSES =====

    private static class TrainingResult {
        final double[] hidden1;
        final double[] bottleneck;
        final double[] hidden2;
        final double[] output;

        TrainingResult(double[] hidden1, double[] bottleneck, double[] hidden2, double[] output) {
            this.hidden1 = hidden1;
            this.bottleneck = bottleneck;
            this.hidden2 = hidden2;
            this.output = output;
        }
    }

    private static class BackpropGradients {
        final double[][] encoderGradients1;
        final double[] encoderBiasGradients1;
        final double[][] encoderGradients2;
        final double[] encoderBiasGradients2;
        final double[][] decoderGradients1;
        final double[] decoderBiasGradients1;
        final double[][] decoderGradients2;
        final double[] decoderBiasGradients2;

        BackpropGradients(double[][] eg1, double[] ebg1, double[][] eg2, double[] ebg2,
                         double[][] dg1, double[] dbg1, double[][] dg2, double[] dbg2) {
            this.encoderGradients1 = eg1;
            this.encoderBiasGradients1 = ebg1;
            this.encoderGradients2 = eg2;
            this.encoderBiasGradients2 = ebg2;
            this.decoderGradients1 = dg1;
            this.decoderBiasGradients1 = dbg1;
            this.decoderGradients2 = dg2;
            this.decoderBiasGradients2 = dbg2;
        }
    }
}