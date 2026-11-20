package net.denfry.owml.ml.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class IsolationForest {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final List<IsolationForest.IsolationTree> trees;
   private final int numTrees;
   private final int maxHeight;
   private final double anomalyThreshold;
   private int trainedSamples = 0;
   private double averagePathLength = 0.0;

   public IsolationForest(int numTrees, int maxHeight, double anomalyThreshold) {
      this.numTrees = numTrees;
      this.maxHeight = maxHeight;
      this.anomalyThreshold = anomalyThreshold;
      this.trees = new ArrayList<>();
   }

   public void train(List<Map<String, Double>> trainingData) {
      if (trainingData.isEmpty()) {
         MessageManager.log("warning", "Cannot train Isolation Forest: no training data provided");
      } else {
         MessageManager.log("info", "Training Isolation Forest with {COUNT} samples", "COUNT", String.valueOf(trainingData.size()));
         this.trees.clear();
         this.trainedSamples = trainingData.size();
         this.averagePathLength = this.calculateAveragePathLength(trainingData.size());

         for (int i = 0; i < this.numTrees; i++) {
            List<Map<String, Double>> treeSample = this.sampleWithReplacement(trainingData, trainingData.size());
            IsolationForest.IsolationTree tree = new IsolationForest.IsolationTree(this.maxHeight);
            tree.build(treeSample);
            this.trees.add(tree);
         }

         MessageManager.log("info", "Isolation Forest trained with {TREES} trees", "TREES", String.valueOf(this.numTrees));
      }
   }

   public double score(Map<String, Double> features) {
      if (this.trees.isEmpty()) {
         return 0.0;
      } else {
         double averagePath = 0.0;

         for (IsolationForest.IsolationTree tree : this.trees) {
            averagePath += tree.getPathLength(features);
         }

         averagePath /= this.trees.size();
         double score = Math.pow(2.0, -averagePath / this.averagePathLength);
         return Math.min(1.0, Math.max(0.0, score));
      }
   }

   public boolean isAnomaly(Map<String, Double> features) {
      return this.score(features) >= this.anomalyThreshold;
   }

   public List<Double> scoreBatch(List<Map<String, Double>> dataPoints) {
      List<Double> scores = new ArrayList<>();

      for (Map<String, Double> point : dataPoints) {
         scores.add(this.score(point));
      }

      return scores;
   }

   public Map<String, Double> getFeatureImportance() {
      Map<String, Double> importance = new HashMap<>();

      for (IsolationForest.IsolationTree tree : this.trees) {
         Map<String, Double> treeImportance = tree.getFeatureImportance();

         for (Entry<String, Double> entry : treeImportance.entrySet()) {
            importance.put(entry.getKey(), importance.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
         }
      }

      for (String feature : importance.keySet()) {
         importance.put(feature, importance.get(feature) / this.numTrees);
      }

      return importance;
   }

   private List<Map<String, Double>> sampleWithReplacement(List<Map<String, Double>> data, int sampleSize) {
      List<Map<String, Double>> sample = new ArrayList<>();
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (int i = 0; i < sampleSize; i++) {
         int index = random.nextInt(data.size());
         sample.add(new HashMap<>(data.get(index)));
      }

      return sample;
   }

   private double calculateAveragePathLength(int n) {
      if (n <= 1) {
         return 1.0;
      } else {
         double sum = 0.0;

         for (int i = 1; i <= n; i++) {
            sum += cFactor(n - i + 1);
         }

         return sum / n;
      }
   }

   private static double cFactor(int n) {
      if (n <= 1) {
         return 0.0;
      } else {
         return n == 2 ? 1.0 : 2.0 * (Math.log(n - 1) + 0.5772156649) - 2.0 * (n - 1) / n;
      }
   }

   public Map<String, Object> getTrainingStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("numTrees", this.numTrees);
      stats.put("maxHeight", this.maxHeight);
      stats.put("trainedSamples", this.trainedSamples);
      stats.put("anomalyThreshold", this.anomalyThreshold);
      stats.put("averagePathLength", this.averagePathLength);
      stats.put("featureImportance", this.getFeatureImportance());
      return stats;
   }

   private static class IsolationTree {
      private IsolationForest.Node root;
      private final int maxHeight;
      private final Map<String, Double> featureImportance = new ConcurrentHashMap<>();

      public IsolationTree(int maxHeight) {
         this.maxHeight = maxHeight;
      }

      public void build(List<Map<String, Double>> data) {
         if (!data.isEmpty()) {
            Set<String> allFeatures = new HashSet<>();

            for (Map<String, Double> sample : data) {
               allFeatures.addAll(sample.keySet());
            }

            this.root = this.buildNode(data, 0, new ArrayList<>(allFeatures));
         }
      }

      private IsolationForest.Node buildNode(List<Map<String, Double>> data, int height, List<String> availableFeatures) {
         if (height < this.maxHeight && data.size() > 1 && !availableFeatures.isEmpty()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String splitFeature = availableFeatures.get(random.nextInt(availableFeatures.size()));
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
            List<Map<String, Double>> leftData = new ArrayList<>();
            List<Map<String, Double>> rightData = new ArrayList<>();

            for (Map<String, Double> samplex : data) {
               Double value = samplex.get(splitFeature);
               if (value != null && !(value <= splitValue)) {
                  rightData.add(samplex);
               } else {
                  leftData.add(samplex);
               }
            }

            this.featureImportance.put(splitFeature, this.featureImportance.getOrDefault(splitFeature, 0.0) + 1.0);
            List<String> remainingFeatures = new ArrayList<>(availableFeatures);
            IsolationForest.Node left = this.buildNode(leftData, height + 1, remainingFeatures);
            IsolationForest.Node right = this.buildNode(rightData, height + 1, remainingFeatures);
            return new IsolationForest.Node(splitFeature, splitValue, left, right);
         } else {
            return new IsolationForest.Node(data.size());
         }
      }

      public double getPathLength(Map<String, Double> features) {
         return this.getPathLength(this.root, features, 0);
      }

      private double getPathLength(IsolationForest.Node node, Map<String, Double> features, int depth) {
         if (node.isExternal()) {
            return IsolationForest.cFactor(node.size) + depth;
         } else {
            Double value = features.get(node.splitFeature);
            return value != null && !(value <= node.splitValue)
               ? this.getPathLength(node.right, features, depth + 1)
               : this.getPathLength(node.left, features, depth + 1);
         }
      }

      public Map<String, Double> getFeatureImportance() {
         double totalSplits = this.featureImportance.values().stream().mapToDouble(Double::doubleValue).sum();
         Map<String, Double> normalized = new HashMap<>();
         if (totalSplits > 0.0) {
            for (Entry<String, Double> entry : this.featureImportance.entrySet()) {
               normalized.put(entry.getKey(), entry.getValue() / totalSplits);
            }
         }

         return normalized;
      }
   }

   private static class Node {
      String splitFeature;
      double splitValue;
      IsolationForest.Node left;
      IsolationForest.Node right;
      int size;

      public Node(int size) {
         this.size = size;
      }

      public Node(String splitFeature, double splitValue, IsolationForest.Node left, IsolationForest.Node right) {
         this.splitFeature = splitFeature;
         this.splitValue = splitValue;
         this.left = left;
         this.right = right;
      }

      public boolean isExternal() {
         return this.left == null && this.right == null;
      }
   }
}
