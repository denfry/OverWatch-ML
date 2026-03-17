package net.denfry.owml.ml.v2.models;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OnlineAutoencoder {
    private final int inputSize;
    private final int hiddenSize;
    private final double[][] W1, W2;
    private final double[] b1, b2;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final double learningRate;

    public OnlineAutoencoder(int inputSize, int hiddenSize, double learningRate) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.learningRate = learningRate;
        this.W1 = new double[hiddenSize][inputSize];
        this.W2 = new double[inputSize][hiddenSize];
        this.b1 = new double[hiddenSize];
        this.b2 = new double[inputSize];
        initializeWeights();
    }

    private void initializeWeights() {
        // Xavier/Glorot initialization
        double limit1 = Math.sqrt(6.0 / (inputSize + hiddenSize));
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                W1[i][j] = (Math.random() * 2 - 1) * limit1;
            }
        }
        double limit2 = Math.sqrt(6.0 / (hiddenSize + inputSize));
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                W2[i][j] = (Math.random() * 2 - 1) * limit2;
            }
        }
    }

    private double activate(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    private double derivative(double x) { return x * (1.0 - x); }

    public double getAnomalyScore(double[] features) {
        if (features == null || features.length != inputSize) return 0.0;
        
        lock.readLock().lock();
        try {
            double[] reconstructed = forward(features, new double[hiddenSize]);
            double mse = 0.0;
            for (int i = 0; i < inputSize; i++) {
                mse += Math.pow(features[i] - reconstructed[i], 2);
            }
            return mse / inputSize; // Mean Squared Error = Reconstruction Error
        } finally {
            lock.readLock().unlock();
        }
    }

    private double[] forward(double[] x, double[] h) {
        // Hidden layer
        for (int i = 0; i < hiddenSize; i++) {
            double sum = b1[i];
            for (int j = 0; j < inputSize; j++) sum += W1[i][j] * x[j];
            h[i] = activate(sum);
        }
        // Output layer
        double[] out = new double[inputSize];
        for (int i = 0; i < inputSize; i++) {
            double sum = b2[i];
            for (int j = 0; j < hiddenSize; j++) sum += W2[i][j] * h[j];
            out[i] = activate(sum);
        }
        return out;
    }

    public void trainBatch(double[][] batch) {
        if (batch == null || batch.length == 0) return;
        
        lock.writeLock().lock();
        try {
            for (double[] x : batch) {
                if (x == null || x.length != inputSize) continue;
                
                double[] h = new double[hiddenSize];
                double[] out = forward(x, h);

                // Backprop (SGD)
                double[] dOut = new double[inputSize];
                for (int i = 0; i < inputSize; i++) {
                    dOut[i] = (out[i] - x[i]) * derivative(out[i]);
                }

                double[] dH = new double[hiddenSize];
                for (int i = 0; i < hiddenSize; i++) {
                    double sum = 0;
                    for (int j = 0; j < inputSize; j++) sum += W2[j][i] * dOut[j];
                    dH[i] = sum * derivative(h[i]);
                }

                // Update Weights
                for (int i = 0; i < inputSize; i++) {
                    b2[i] -= learningRate * dOut[i];
                    for (int j = 0; j < hiddenSize; j++) {
                        W2[i][j] -= learningRate * dOut[i] * h[j];
                    }
                }
                for (int i = 0; i < hiddenSize; i++) {
                    b1[i] -= learningRate * dH[i];
                    for (int j = 0; j < inputSize; j++) {
                        W1[i][j] -= learningRate * dH[i] * x[j];
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
