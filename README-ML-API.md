# OverWatch-ML ML API Documentation

## Overview

OverWatch-ML includes a powerful machine learning system for detecting cheaters in Minecraft. The system is built on modern principles with emphasis on performance, reliability and maintainability.

## Architecture

### Core Components

#### 1. ModernMLManager

Main ML system manager, coordinating all components.

```java
public class ModernMLManager implements Listener {
    // Asynchronous processing of all ML operations
    public void analyzePlayerAsync(UUID playerId, AnalysisCallback callback);

    // Synchronous analysis (critical cases only)
    public ReasoningMLModel.DetectionResult analyzePlayerSync(UUID playerId);

    // Training management
    public void startTraining(Player player, boolean isCheater);
    public void stopTraining(UUID playerId);
}
```

#### 2. Interfaces for Modularity

```java
public interface MLModel {
    DetectionResult analyze(PlayerMiningData data);
    boolean train(Collection<PlayerMiningData> trainingData);
    boolean isTrained();
}

public interface DataCollector {
    void startCollecting(Player player, boolean isCheater);
    PlayerMiningData stopCollecting(Player player);
    boolean isCollecting(UUID playerId);
}

public interface AnalysisManager {
    CompletableFuture<DetectionResult> analyzeAsync(UUID playerId);
    double getSuspicionScore(UUID playerId);
    boolean isUnderAnalysis(UUID playerId);
}
```

## Quick Start

### Basic Usage

```java
// Getting the ML manager
ModernMLManager mlManager = (ModernMLManager) plugin.getMLManager();

// Asynchronous player analysis
mlManager.analyzePlayerAsync(playerId, new AnalysisCallback() {
    @Override
    public void onAnalysisComplete(UUID playerId, DetectionResult result) {
        if (result == DetectionResult.CHEATER_HIGH_CONFIDENCE) {
            // Apply punishment
            punishPlayer(playerId);
        }
    }

    @Override
    public void onAnalysisFailed(UUID playerId, Throwable error) {
        logger.warning("ML analysis failed for " + playerId + ": " + error.getMessage());
    }
});

// Synchronous analysis (use with caution!)
DetectionResult result = mlManager.analyzePlayerSync(playerId);
```

### Model Training

```java
// Start collecting training data
mlManager.startTraining(player, true); // true = cheater

// After 60 seconds, collection will automatically stop and the model will retrain
```

## Configuration

### Core Settings

```yaml
ml:
  enabled: true
  trainingSessionDuration: 60  # seconds
  detectionThreshold: 0.75     # detection threshold

  autoAnalysis:
    enabled: true
    suspiciousThreshold: 5
    maxPlayers: 5

  performance:
    executorPoolSize: 0        # 0 = auto-detection
    analysisTimeoutSeconds: 30
    maxConcurrentAnalyses: 10

  cache:
    analysis:
      maxSize: 1000
      expiryMinutes: 5
    features:
      maxSize: 500
      expiryMinutes: 10
    playerData:
      maxSize: 200
      expiryMinutes: 30

  maintenance:
    cleanupIntervalMinutes: 5
    metricsReportIntervalMinutes: 15
    maxTrainingQueueSize: 1000
    maxAnalysisQueueSize: 500
```

### Programmatic Configuration

```java
MLConfig config = mlManager.getMLConfig();

// Performance Configuration
config.setExecutorPoolSize(8);
config.setAnalysisTimeoutSeconds(45);
config.setMaxConcurrentAnalyses(15);

// Cache Configuration
config.setAnalysisCacheMaxSize(2000);
config.setAnalysisCacheExpiryMinutes(3);

// Saving changes
config.saveConfig();
```

## API Reference

### DetectionResult

```java
enum DetectionResult {
    CHEATER_HIGH_CONFIDENCE,    // High confidence in cheating
    CHEATER_MEDIUM_CONFIDENCE,  // Medium confidence
    CHEATER_LOW_CONFIDENCE,     // Low confidence
    SUSPICIOUS,                 // Suspicious behavior
    NORMAL                      // Normal behavior
}
```

### Performance Metrics

```java
// Getting statistics
String performanceStats = mlManager.getPerformanceStats();
// Output: "ML Performance: [detailed statistics]"

// Getting metrics in code
MLMetrics.MLStats stats = mlManager.getMetrics().getStats();
System.out.println("Total predictions: " + stats.totalPredictions);
System.out.println("Average time: " + stats.avgPredictionTimeMs + "ms");
System.out.println("Cache hits: " + stats.cacheHitRate * 100 + "%");
```

### Cache API

```java
MLCache cache = mlManager.getCache();

// Manual cache management
cache.invalidatePlayer(playerId);  // Clear player data
cache.clearAll();                  // Clear entire cache

// Cache statistics
MLCache.CacheStats cacheStats = cache.getStats();
System.out.println(cacheStats.toString());
```

## Advanced Features

### Custom Models

```java
public class CustomMLModel implements MLModel {
    @Override
    public DetectionResult analyze(PlayerMiningData data) {
        // Your analysis logic
        return DetectionResult.NORMAL;
    }

    @Override
    public boolean train(Collection<PlayerMiningData> trainingData) {
        // Your training logic
        return true;
    }

    @Override
    public boolean isTrained() {
        return true;
    }

    @Override
    public void save() { /* saving */ }

    @Override
    public void load() { /* loading */ }

    @Override
    public void dispose() { /* disposal */ }
}

// Registering a custom model
ModernMLManager mlManager = new ModernMLManager(plugin, configManager);
// Replace model via reflection or create a new constructor
```

### Custom Data Collectors

```java
public class AdvancedDataCollector implements DataCollector {
    private final Map<UUID, CustomPlayerData> collectingData = new ConcurrentHashMap<>();

    @Override
    public void startCollecting(Player player, boolean isCheater) {
        collectingData.put(player.getUniqueId(),
            new CustomPlayerData(player.getUniqueId(), isCheater));
    }

    @Override
    public PlayerMiningData stopCollecting(Player player) {
        return collectingData.remove(player.getUniqueId());
    }

    // ... other methods
}
```

## Monitoring and Debugging

### Logging

The system automatically logs important events:

```
[INFO] ML model successfully trained!
[WARNING] ML analysis failed for player123: Timeout
[INFO] ML Metrics: Predictions=150 (Success: 95.3%), Training=3, Avg Prediction=45.2ms
```

### Debug Commands

```
/owml ml status              # System status
/owml ml train <player> <type> # Train on player (cheater|normal)
/owml ml autotrain [count]    # Automatically train on multiple players
/owml ml spawn <type> <count> # Spawn bots for training
/owml ml bots <status|remove|auto> # Bot management
/owml ml analyze <player>     # Analyze player
/owml ml metrics              # Show metrics
/owml ml cache                # Cache statistics
```

#### Automatic Training (autotrain)

The `/owml ml autotrain [count]` command allows you to automatically start ML model training on multiple online players simultaneously:

- **Without parameters**: trains on all available players
- **With a number**: trains on the specified number of players (max. 10)

**Usage Example:**
```
/owml ml autotrain        # Train on all players
/owml ml autotrain 5      # Train on 5 random players
```

**How it works:**
1. Automatically selects online players (except you)
2. Half of the players are marked as "normal", the other half as "cheaters"
3. Starts training sessions for 60 seconds each
4. Players must behave according to their role

**Important:**
- Normal players must mine ore legitimately
- "Cheaters" must simulate X-ray behavior (straight tunnels to ore)
- All players must actively mine during the session

## 🤖 Training Bot System

### Overview

OverWatch-ML includes an innovative training bot system that allows training the ML model without real player involvement. Bots spawn automatically, simulate various mining behaviors, and generate training data.

### Bot Behavior Types

#### NORMAL_MINER
- Mines ore in a normal way
- Explores caves and creates branches
- Avoids straight tunnels to valuable ores
- Simulates legitimate player behavior

#### XRAY_CHEATER
- Creates straight tunnels to diamond veins
- Ignores iron/coal, focuses on diamonds
- Makes long straight corridors
- Simulates classic X-ray behavior

#### TUNNEL_MINER
- Creates long straight tunnels
- Mines all ores in its path
- Does not seek specific valuable materials

#### RANDOM_MINER
- Mines blocks completely randomly
- Has no logic or patterns
- Simulates unskilled players

#### EFFICIENT_MINER
- Uses branch mining technique
- Finds optimal paths to ores
- Mines efficiently but legitimately

#### SURFACE_MINER
- Mines close to the surface
- Avoids deep mines
- Simulates beginners

### Bot Management Commands

#### Bot Spawning
```bash
/owml ml spawn NORMAL_MINER 5    # Spawn 5 normal miners
/owml ml spawn XRAY_CHEATER 3    # Spawn 3 x-ray cheaters
/owml ml spawn TUNNEL_MINER 2    # Spawn 2 tunnelers
```

#### Bot Management
```bash
/owml ml bots status     # Show status of all bots
/owml ml bots remove     # Remove all active bots
/owml ml bots auto on    # Enable automatic spawn
/owml ml bots auto off   # Disable automatic spawn
```

### How the Bot System Works

1. **Spawn**: Bots appear in safe mining locations
2. **Behavior**: Each bot performs specific behavior for 2 minutes
3. **Data Collection**: All bot actions are recorded (broken blocks, movements)
4. **Feature Generation**: ML features are calculated based on actions
5. **Training**: Data is automatically fed into the ML system for training

### Automatic Mode

When auto-mode (`/owml ml bots auto on`) is enabled, the system:
- Automatically spawns different types of bots every ~30 seconds
- Maintains a maximum of 5 concurrent bots
- Guarantees a variety of behavior types
- Automatically removes bots after the session ends

### Bot Advantages

✅ **No players needed** - training works 24/7
✅ **Controlled training** - precise behavior patterns
✅ **Safety** - does not affect real players
✅ **Scalability** - can generate thousands of examples
✅ **Diversity** - different behavior types for better training

### Monitoring

The system provides detailed statistics:

```
Training Bots Status
  Active Bots: 3
  Total Spawned: 127
  Training Sessions: 124
  Auto Training: true
  Max Concurrent: 5
  Session Duration: 120s
  Spawn Locations: 4

  Active Types:
    • normal miner: 1
    • xray cheater: 2
```

### Technical Details

- **Session duration**: 120 seconds (configurable)
- **Max bots**: 20 concurrent (configurable)
- **Spawn frequency**: Every 30 seconds in auto-mode
- **Search radius**: Bots search for ore within a 50-block radius
- **Safety**: Bots spawn only in safe locations

## Best Practices

### 1. Performance
- ✅ Use asynchronous methods for heavy operations
- ✅ Configure cache sizes for your workload
- ✅ Monitor metrics and configure timeouts

### 2. Reliability
- ✅ Always handle exceptions
- ✅ Use fallback logic on ML errors
- ✅ Implement graceful degradation

### 3. Scalability
- ✅ Separate training and analysis
- ✅ Use queue limits
- ✅ Optimize data collection

### 4. Testing
- ✅ Create unit tests for components
- ✅ Use mock objects for testing
- ✅ Test error scenarios

## Troubleshooting

### High CPU Load
```yaml
ml:
  performance:
    executorPoolSize: 2  # Reduce thread count
  cache:
    analysis:
      maxSize: 500  # Reduce cache size
```

### Memory Leak
```yaml
ml:
  maintenance:
    cleanupIntervalMinutes: 2  # Frequent cleanup
  cache:
    playerData:
      expiryMinutes: 15  # Reduce lifetime
```

### Slow Analysis
```yaml
ml:
  performance:
    maxConcurrentAnalyses: 5  # Limit parallelism
  cache:
    features:
      maxSize: 1000  # Increase feature cache
```

## Migration from Legacy API

If you are using the old MLManager:

```java
// Old code
MLManager oldManager = plugin.getMLManager();
oldManager.startAnalysis(player);

// New code
ModernMLManager newManager = (ModernMLManager) plugin.getMLManager();
newManager.analyzePlayerAsync(player.getUniqueId(), callback);
```

## Support and Development

- 📧 Issues: Create issues on GitHub
- 📖 Wiki: Detailed documentation in wiki
- 🏗️ Architecture: SOLID principles, Clean Architecture
- 🧪 Testing: 95%+ code coverage
- 🚀 Performance: Optimized for production

---

**API Version:** 2.0.0
**Compatibility:** Minecraft 1.21+
**Java:** 21+
