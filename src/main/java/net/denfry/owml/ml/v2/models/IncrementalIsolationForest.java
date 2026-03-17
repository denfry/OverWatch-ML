package net.denfry.owml.ml.v2.models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IncrementalIsolationForest {
    private final int numTrees;
    private final int maxDepth;
    private final Node[] forest;
    private int oldestTreeIndex = 0;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    static class Node {
        int size;
        int splitFeature;
        double splitValue;
        Node left;
        Node right;
        boolean isLeaf;

        Node(int size) {
            this.size = size;
            this.isLeaf = true;
        }

        Node(int splitFeature, double splitValue, Node left, Node right) {
            this.splitFeature = splitFeature;
            this.splitValue = splitValue;
            this.left = left;
            this.right = right;
            this.isLeaf = false;
        }
    }

    public IncrementalIsolationForest(int numTrees, int sampleSize) {
        this.numTrees = numTrees;
        this.maxDepth = (int) Math.ceil(Math.log(sampleSize) / Math.log(2));
        this.forest = new Node[numTrees];
    }

    public void updateModel(double[][] newBatch) {
        if (newBatch == null || newBatch.length == 0) return;
        
        Node newTree = buildTree(newBatch, 0);
        lock.writeLock().lock();
        try {
            forest[oldestTreeIndex] = newTree;
            oldestTreeIndex = (oldestTreeIndex + 1) % numTrees;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getAnomalyScore(double[] features) {
        if (features == null) return 0.0;
        
        lock.readLock().lock();
        try {
            double avgPathLength = 0;
            int validTrees = 0;
            for (Node tree : forest) {
                if (tree != null) {
                    avgPathLength += getPathLength(features, tree, 0);
                    validTrees++;
                }
            }
            if (validTrees == 0) return 0.0; // Not trained yet
            
            avgPathLength /= validTrees;
            // 256 is the standard isolation forest sub-sample size assumption for c(n)
            double c = c(256);
            return Math.pow(2.0, -avgPathLength / c);
        } finally {
            lock.readLock().unlock();
        }
    }

    private double c(int n) {
        if (n <= 1) return 0;
        return 2.0 * (Math.log(n - 1) + 0.5772156649) - (2.0 * (n - 1) / n);
    }

    private Node buildTree(double[][] data, int currentDepth) {
        if (currentDepth >= maxDepth || data.length <= 1) {
            return new Node(data.length);
        }

        int numFeatures = data[0].length;
        int featureIdx = ThreadLocalRandom.current().nextInt(numFeatures);
        
        double min = data[0][featureIdx];
        double max = data[0][featureIdx];
        
        for (double[] row : data) {
            if (row[featureIdx] < min) min = row[featureIdx];
            if (row[featureIdx] > max) max = row[featureIdx];
        }

        if (min == max) {
            // Can't split on this feature. Return leaf to prevent infinite loop.
            return new Node(data.length);
        }

        double splitValue = min + ThreadLocalRandom.current().nextDouble() * (max - min);

        List<double[]> leftList = new ArrayList<>();
        List<double[]> rightList = new ArrayList<>();

        for (double[] row : data) {
            if (row[featureIdx] < splitValue) {
                leftList.add(row);
            } else {
                rightList.add(row);
            }
        }

        double[][] leftArr = leftList.toArray(new double[0][]);
        double[][] rightArr = rightList.toArray(new double[0][]);

        return new Node(
            featureIdx, 
            splitValue, 
            buildTree(leftArr, currentDepth + 1), 
            buildTree(rightArr, currentDepth + 1)
        );
    }

    private double getPathLength(double[] features, Node node, int currentDepth) {
        if (node.isLeaf) {
            return currentDepth + c(node.size);
        }
        if (features[node.splitFeature] < node.splitValue) {
            return getPathLength(features, node.left, currentDepth + 1);
        } else {
            return getPathLength(features, node.right, currentDepth + 1);
        }
    }
}
