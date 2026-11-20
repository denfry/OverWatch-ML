package net.denfry.owml.ml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.Bukkit;

public class MLDataManager {
   private static final String TRAINING_DIR = "training";
   private static final String ANALYSIS_DIR = "analysis";
   private static final String REPORTS_DIR = "reports";
   private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
   private static OverWatchML plugin;
   private static File dataDir;

   public static void initialize(OverWatchML plugin) {
      MLDataManager.plugin = plugin;
      dataDir = new File(plugin.getDataFolder(), "ml-data");
      File trainingDir = new File(dataDir, "training");
      File analysisDir = new File(dataDir, "analysis");
      File reportsDir = new File(dataDir, "reports");
      if (!dataDir.exists() && !dataDir.mkdirs()) {
         plugin.getLogger().severe("Failed to create ML data directory!");
      }

      if (!trainingDir.exists() && !trainingDir.mkdirs()) {
         plugin.getLogger().severe("Failed to create ML training data directory!");
      }

      if (!analysisDir.exists() && !analysisDir.mkdirs()) {
         plugin.getLogger().severe("Failed to create ML analysis data directory!");
      }

      if (!reportsDir.exists() && !reportsDir.mkdirs()) {
         plugin.getLogger().severe("Failed to create ML reports directory!");
      }

      try {
         DataArchiver.loadArchiveIndex();
         DataCompressor.compressExistingData();
         startBackgroundMaintenance();
      } catch (Exception var5) {
         plugin.getLogger().warning("Failed to initialize data optimization components: " + var5.getMessage());
      }

      extractBundledTrainingData();
   }

   private static void startBackgroundMaintenance() {
      Schedulers.asyncTimer(() -> {
         try {
            DataArchiver.checkAndArchiveData();
            DataCleanupManager.performAutomaticCleanup();
            DataDeduplicator.deduplicateExistingData();
         } catch (Exception var1) {
            plugin.getLogger().warning("Error in background maintenance: " + var1.getMessage());
         }
      }, 24000L, 24000L);
      plugin.getLogger().info("Started ML data optimization background tasks");
   }

   public static void savePlayerData(PlayerMiningData data) {
      if (plugin == null) {
         Bukkit.getLogger().severe("MLDataManager not initialized!");
      } else {
         if (data.getFeatures().isEmpty()) {
            data.calculateDerivedFeatures();
         }

         String dirName = "analysis";
         File saveDir = new File(dataDir, dirName);
         String timestamp = DATE_FORMAT.format(new Date());
         String label = "analysis";
         String filename = String.format("%s_%s_%s.json", data.getPlayerName(), label, timestamp);
         File outputFile = new File(saveDir, filename);
         savePlayerDataToFile(data, outputFile, label, timestamp);
      }
   }

   public static void saveTrainingData(PlayerMiningData data) {
      if (plugin == null) {
         Bukkit.getLogger().severe("MLDataManager not initialized!");
      } else {
         if (data.getFeatures().isEmpty()) {
            data.calculateDerivedFeatures();
         }

         Map<String, Object> sample = new HashMap<>();
         sample.put("timestamp", System.currentTimeMillis());
         sample.put("label", data.isLabeledAsCheater() ? "cheater" : "normal");
         sample.put("source", "player_session_" + data.getPlayerName());
         sample.put("features", new HashMap<>(data.getFeatures()));
         if (DataDeduplicator.isDuplicate(sample)) {
            plugin.getLogger().info("Skipped duplicate training sample for " + data.getPlayerName());
         } else {
            DataCompressor.compressAndStoreTrainingSample(sample);
            String cacheKey = data.getPlayerName() + "_" + sample.get("timestamp");
            MemoryOptimizer.cacheTrainingSample(cacheKey, sample);
            plugin.getLogger().info("Saved optimized ML training data for " + data.getPlayerName() + " labeled as " + sample.get("label"));
         }
      }
   }

   public static String saveDetectionReport(String playerName, ReasoningMLModel.DetectionResult result, PlayerMiningData playerData) {
      if (plugin == null) {
         Bukkit.getLogger().severe("MLDataManager not initialized!");
         return null;
      } else {
         File saveDir = new File(dataDir, "reports");
         String timestamp = DATE_FORMAT.format(new Date());
         String suspicionLevel = getSuspicionLevelTag(result.getProbability());
         String filename = String.format("%s_%s_%s.json", playerName, suspicionLevel, timestamp);
         File outputFile = new File(saveDir, filename);

         try {
            String var22;
            try (FileWriter writer = new FileWriter(outputFile)) {
               StringBuilder json = new StringBuilder("{\n");
               json.append("  \"playerName\": \"").append(playerName).append("\",\n");
               json.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
               json.append("  \"suspicionScore\": ").append(result.getProbability()).append(",\n");
               json.append("  \"suspicionLevel\": \"").append(suspicionLevel).append("\",\n");
               json.append("  \"conclusion\": \"").append(escapeJsonString(result.getConclusion())).append("\",\n");
               json.append("  \"reasoningSteps\": [\n");
               List<String> steps = result.getReasoningSteps();

               for (int i = 0; i < steps.size(); i++) {
                  json.append("    \"").append(escapeJsonString(steps.get(i))).append("\"");
                  if (i < steps.size() - 1) {
                     json.append(",");
                  }

                  json.append("\n");
               }

               json.append("  ]");
               if (playerData != null) {
                  json.append(",\n  \"playerData\": {\n");
                  json.append("    \"sessionDuration\": ").append(playerData.getTotalMiningTimeMs() / 1000.0).append(",\n");
                  json.append("    \"features\": {\n");
                  Map<String, List<Entry<String, Double>>> featureCategories = new LinkedHashMap<>();
                  featureCategories.put("basic_metrics", new ArrayList<>());
                  featureCategories.put("ore_counts", new ArrayList<>());
                  featureCategories.put("ore_rates", new ArrayList<>());
                  featureCategories.put("head_movement", new ArrayList<>());
                  featureCategories.put("concurrent_actions", new ArrayList<>());
                  featureCategories.put("other", new ArrayList<>());

                  for (Entry<String, Double> feature : playerData.getFeatures().entrySet()) {
                     String key = feature.getKey();
                     if (key.startsWith("total_") || key.equals("sessionDuration")) {
                        featureCategories.get("basic_metrics").add(feature);
                     } else if (key.startsWith("ore_count_")) {
                        featureCategories.get("ore_counts").add(feature);
                     } else if (key.startsWith("ore_rate_")) {
                        featureCategories.get("ore_rates").add(feature);
                     } else if (key.startsWith("head_")) {
                        featureCategories.get("head_movement").add(feature);
                     } else if (key.startsWith("concurrent_")) {
                        featureCategories.get("concurrent_actions").add(feature);
                     } else {
                        featureCategories.get("other").add(feature);
                     }
                  }

                  boolean firstCategory = true;

                  for (Entry<String, List<Entry<String, Double>>> category : featureCategories.entrySet()) {
                     if (!category.getValue().isEmpty()) {
                        if (!firstCategory) {
                           json.append(",\n");
                        }

                        firstCategory = false;
                        json.append("      // ").append(category.getKey().replace("_", " ")).append("\n");
                        category.getValue().sort(Comparator.comparing(Entry::getKey));
                        boolean firstFeature = true;

                        for (Entry<String, Double> featurex : category.getValue()) {
                           if (!firstFeature) {
                              json.append(",\n");
                           }

                           firstFeature = false;
                           json.append("      \"").append(featurex.getKey()).append("\": ").append(featurex.getValue());
                        }
                     }
                  }

                  json.append("\n    }\n");
                  json.append("  }");
               }

               json.append("\n}");
               writer.write(json.toString());
               plugin.getLogger().info("Saved detection report for " + playerName + " to " + outputFile.getName());
               var22 = outputFile.getPath();
            }

            return var22;
         } catch (IOException var20) {
            plugin.getLogger().severe("Error saving detection report: " + var20.getMessage());
            var20.printStackTrace();
            return null;
         }
      }
   }

   private static String getSuspicionLevelTag(double suspicionScore) {
      if (suspicionScore >= 0.8) {
         return "high_risk";
      } else if (suspicionScore >= 0.6) {
         return "suspicious";
      } else if (suspicionScore >= 0.4) {
         return "medium_risk";
      } else {
         return suspicionScore >= 0.2 ? "low_risk" : "normal";
      }
   }

   private static String escapeJsonString(String input) {
      return input.replace("\\", "\\\\")
         .replace("\"", "\\\"")
         .replace("\b", "\\b")
         .replace("\f", "\\f")
         .replace("\n", "\\n")
         .replace("\r", "\\r")
         .replace("\t", "\\t");
   }

   private static void savePlayerDataToFile(PlayerMiningData data, File outputFile, String label, String timestamp) {
      try (FileWriter writer = new FileWriter(outputFile)) {
         StringBuilder json = new StringBuilder("{\n");
         json.append("  \"playerName\": \"").append(data.getPlayerName()).append("\",\n");
         json.append("  \"label\": \"").append(label).append("\",\n");
         json.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
         json.append("  \"sessionDuration\": ").append(data.getTotalMiningTimeMs() / 1000.0).append(",\n");
         json.append("  \"features\": {\n");
         Map<String, List<Entry<String, Double>>> featureCategories = new LinkedHashMap<>();
         featureCategories.put("basic_metrics", new ArrayList<>());
         featureCategories.put("ore_counts", new ArrayList<>());
         featureCategories.put("ore_rates", new ArrayList<>());
         featureCategories.put("head_movement", new ArrayList<>());
         featureCategories.put("concurrent_actions", new ArrayList<>());
         featureCategories.put("other", new ArrayList<>());

         for (Entry<String, Double> feature : data.getFeatures().entrySet()) {
            String key = feature.getKey();
            if (key.startsWith("total_") || key.equals("sessionDuration")) {
               featureCategories.get("basic_metrics").add(feature);
            } else if (key.startsWith("ore_count_")) {
               featureCategories.get("ore_counts").add(feature);
            } else if (key.startsWith("ore_rate_")) {
               featureCategories.get("ore_rates").add(feature);
            } else if (key.startsWith("head_")) {
               featureCategories.get("head_movement").add(feature);
            } else if (key.startsWith("concurrent_")) {
               featureCategories.get("concurrent_actions").add(feature);
            } else {
               featureCategories.get("other").add(feature);
            }
         }

         boolean firstCategory = true;

         for (Entry<String, List<Entry<String, Double>>> category : featureCategories.entrySet()) {
            if (!category.getValue().isEmpty()) {
               if (!firstCategory) {
                  json.append(",\n");
               }

               firstCategory = false;
               json.append("    // ").append(category.getKey().replace("_", " ")).append("\n");
               category.getValue().sort(Comparator.comparing(Entry::getKey));
               boolean firstFeature = true;

               for (Entry<String, Double> featurex : category.getValue()) {
                  if (!firstFeature) {
                     json.append(",\n");
                  }

                  firstFeature = false;
                  json.append("    \"").append(featurex.getKey()).append("\": ").append(featurex.getValue());
               }
            }
         }

         json.append("\n  }\n");
         json.append("}");
         writer.write(json.toString());
         plugin.getLogger().info("Saved ML data for " + data.getPlayerName() + " to " + outputFile.getName());
      } catch (IOException var15) {
         plugin.getLogger().severe("Error saving ML data: " + var15.getMessage());
         var15.printStackTrace();
      }
   }

   public static MLDataManager.MLTrainingData loadTrainingData() {
      List<Map<String, Double>> normalFeatures = new ArrayList<>();
      List<Map<String, Double>> cheaterFeatures = new ArrayList<>();
      File trainingDir = new File(dataDir, "training");
      if (!trainingDir.exists()) {
         plugin.getLogger().warning("Training data directory does not exist!");
         return new MLDataManager.MLTrainingData(normalFeatures, cheaterFeatures);
      } else {
         File[] files = trainingDir.listFiles((dir, name) -> name.endsWith(".json"));
         if (files == null) {
            plugin.getLogger().warning("No training data files found!");
            return new MLDataManager.MLTrainingData(normalFeatures, cheaterFeatures);
         } else {
            for (File file : files) {
               try {
                  String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                  Map<String, Double> features = new HashMap<>();
                  String label = content.contains("\"label\": \"cheater\"") ? "cheater" : "normal";
                  int featuresStart = content.indexOf("\"features\": {") + 13;
                  int featuresEnd = content.lastIndexOf("}");
                  String featuresStr = content.substring(featuresStart, featuresEnd);
                  String[] lines = featuresStr.split("\n");

                  for (String line : lines) {
                     line = line.trim();
                     if (!line.isEmpty() && !line.startsWith("//")) {
                        if (line.endsWith(",")) {
                           line = line.substring(0, line.length() - 1);
                        }

                        int keyEnd = line.indexOf(":");
                        if (keyEnd > 0) {
                           String key = line.substring(0, keyEnd).trim().replace("\"", "");
                           String valueStr = line.substring(keyEnd + 1).trim();

                           try {
                              double value = Double.parseDouble(valueStr);
                              features.put(key, value);
                           } catch (NumberFormatException var24) {
                           }
                        }
                     }
                  }

                  if ("cheater".equals(label)) {
                     cheaterFeatures.add(features);
                  } else {
                     normalFeatures.add(features);
                  }
               } catch (IOException var25) {
                  plugin.getLogger().warning("Error reading training file " + file.getName() + ": " + var25.getMessage());
               }
            }

            plugin.getLogger()
               .info("Loaded " + normalFeatures.size() + " normal player records and " + cheaterFeatures.size() + " cheater records for training");
            return new MLDataManager.MLTrainingData(normalFeatures, cheaterFeatures);
         }
      }
   }

   public static List<String> getPlayerReports(String playerName) {
      List<String> reports = new ArrayList<>();
      File reportsDir = new File(dataDir, "reports");
      if (!reportsDir.exists()) {
         return reports;
      } else {
         File[] files = reportsDir.listFiles((dir, name) -> name.endsWith(".json"));
         if (files == null) {
            return reports;
         } else {
            for (File file : files) {
               if (playerName == null || file.getName().startsWith(playerName + "_")) {
                  reports.add(file.getPath());
               }
            }

            reports.sort((path1, path2) -> {
               File file1 = new File(path1);
               File file2 = new File(path2);
               return Long.compare(file2.lastModified(), file1.lastModified());
            });
            return reports;
         }
      }
   }

   public static void extractBundledTrainingData() {
      File trainingDir = new File(dataDir, "training");
      if (!trainingDir.exists()) {
         trainingDir.mkdirs();
      }

      File[] existingFiles = trainingDir.listFiles((dir, namex) -> namex.endsWith(".json"));
      if (existingFiles != null && existingFiles.length > 0) {
         plugin.getLogger().info("Training data already exists, skipping extraction");
      } else {
         plugin.getLogger().info("No existing training data found, extracting bundled data...");

         try {
            String resourcePath = "ml-data/training";
            URL url = plugin.getClass().getClassLoader().getResource(resourcePath);
            if (url == null) {
               plugin.getLogger().warning("No bundled training data found in JAR");
               return;
            }

            if (url.getProtocol().equals("jar")) {
               String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
               int extractedCount = 0;

               try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
                  Enumeration<JarEntry> entries = jar.entries();
                  String pathToMatch = resourcePath + "/";

                  while (entries.hasMoreElements()) {
                     JarEntry entry = entries.nextElement();
                     String name = entry.getName();
                     if (name.startsWith(pathToMatch) && !entry.isDirectory() && name.endsWith(".json")) {
                        String filename = name.substring(pathToMatch.length());
                        InputStream is = plugin.getClass().getClassLoader().getResourceAsStream(name);
                        if (is != null) {
                           File outFile = new File(trainingDir, filename);
                           Files.copy(is, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                           extractedCount++;
                           is.close();
                        }
                     }
                  }
               }

               plugin.getLogger().info("Extracted " + extractedCount + " training files from plugin JAR");
            }
         } catch (Exception var16) {
            plugin.getLogger().severe("Error extracting bundled training data: " + var16.getMessage());
            var16.printStackTrace();
         }
      }
   }

   public static void saveSyntheticTrainingSample(Map<String, Object> sample) {
      try {
         if (DataDeduplicator.isDuplicate(sample)) {
            return;
         }

         DataCompressor.compressAndStoreTrainingSample(sample);
         String cacheKey = "synthetic_" + sample.get("timestamp") + "_" + sample.hashCode();
         MemoryOptimizer.cacheTrainingSample(cacheKey, sample);
      } catch (Exception var2) {
         plugin.getLogger().warning("Failed to save synthetic training sample: " + var2.getMessage());
      }
   }

   public static Map<String, Object> getOptimizationStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("compression", DataCompressor.getCompressionStats());
      stats.put("deduplication", DataDeduplicator.getDeduplicationStats());
      stats.put("archival", DataArchiver.getArchivalStats());
      stats.put("memory", MemoryOptimizer.getMemoryStats());
      stats.put("cleanup", DataCleanupManager.getCleanupStats());
      return stats;
   }

   public static void forceOptimizationMaintenance() {
      plugin.getLogger().info("Forced ML data optimization maintenance initiated");
      DataCompressor.compressExistingData();
      DataDeduplicator.deduplicateExistingData();
      DataArchiver.checkAndArchiveData();
      DataCleanupManager.forceCleanup();
      MemoryOptimizer.performCacheCleanup();
      plugin.getLogger().info("ML data optimization maintenance completed");
   }

   public static class MLTrainingData {
      private final List<Map<String, Double>> normalFeatures;
      private final List<Map<String, Double>> cheaterFeatures;

      public MLTrainingData(List<Map<String, Double>> normalFeatures, List<Map<String, Double>> cheaterFeatures) {
         this.normalFeatures = normalFeatures;
         this.cheaterFeatures = cheaterFeatures;
      }

      public List<Map<String, Double>> getNormalFeatures() {
         return this.normalFeatures;
      }

      public List<Map<String, Double>> getCheaterFeatures() {
         return this.cheaterFeatures;
      }

      public boolean hasEnoughData() {
         return this.normalFeatures.size() >= 3 && this.cheaterFeatures.size() >= 3;
      }
   }
}
