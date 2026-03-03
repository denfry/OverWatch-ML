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
// Получение ML менеджера
ModernMLManager mlManager = (ModernMLManager) plugin.getMLManager();

// Асинхронный анализ игрока
mlManager.analyzePlayerAsync(playerId, new AnalysisCallback() {
    @Override
    public void onAnalysisComplete(UUID playerId, DetectionResult result) {
        if (result == DetectionResult.CHEATER_HIGH_CONFIDENCE) {
            // Применить наказание
            punishPlayer(playerId);
        }
    }

    @Override
    public void onAnalysisFailed(UUID playerId, Throwable error) {
        logger.warning("ML анализ не удался для " + playerId + ": " + error.getMessage());
    }
});

// Синхронный анализ (осторожно!)
DetectionResult result = mlManager.analyzePlayerSync(playerId);
```

### Model Training

```java
// Начать сбор данных для обучения
mlManager.startTraining(player, true); // true = читер

// Через 60 секунд сбор автоматически остановится и модель переобучится
```

## Конфигурация

### Основные настройки

```yaml
ml:
  enabled: true
  trainingSessionDuration: 60  # секунды
  detectionThreshold: 0.75     # порог обнаружения

  autoAnalysis:
    enabled: true
    suspiciousThreshold: 5
    maxPlayers: 5

  performance:
    executorPoolSize: 0        # 0 = автоопределение
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

### Программная настройка

```java
MLConfig config = mlManager.getMLConfig();

// Настройка производительности
config.setExecutorPoolSize(8);
config.setAnalysisTimeoutSeconds(45);
config.setMaxConcurrentAnalyses(15);

// Настройка кеширования
config.setAnalysisCacheMaxSize(2000);
config.setAnalysisCacheExpiryMinutes(3);

// Сохранение изменений
config.saveConfig();
```

## API Reference

### DetectionResult

```java
enum DetectionResult {
    CHEATER_HIGH_CONFIDENCE,    // Высокая уверенность в читерстве
    CHEATER_MEDIUM_CONFIDENCE,  // Средняя уверенность
    CHEATER_LOW_CONFIDENCE,     // Низкая уверенность
    SUSPICIOUS,                 // Подозрительное поведение
    NORMAL                      // Нормальное поведение
}
```

### Метрики производительности

```java
// Получение статистики
String performanceStats = mlManager.getPerformanceStats();
// Вывод: "ML Performance: [детальная статистика]"

// Получение метрик в коде
MLMetrics.MLStats stats = mlManager.getMetrics().getStats();
System.out.println("Всего предсказаний: " + stats.totalPredictions);
System.out.println("Среднее время: " + stats.avgPredictionTimeMs + "ms");
System.out.println("Попаданий в кеш: " + stats.cacheHitRate * 100 + "%");
```

### Cache API

```java
MLCache cache = mlManager.getCache();

// Ручное управление кешем
cache.invalidatePlayer(playerId);  // Очистить данные игрока
cache.clearAll();                  // Очистить весь кеш

// Статистика кеша
MLCache.CacheStats cacheStats = cache.getStats();
System.out.println(cacheStats.toString());
```

## Расширенные возможности

### Кастомные модели

```java
public class CustomMLModel implements MLModel {
    @Override
    public DetectionResult analyze(PlayerMiningData data) {
        // Ваша логика анализа
        return DetectionResult.NORMAL;
    }

    @Override
    public boolean train(Collection<PlayerMiningData> trainingData) {
        // Ваша логика обучения
        return true;
    }

    @Override
    public boolean isTrained() {
        return true;
    }

    @Override
    public void save() { /* сохранение */ }

    @Override
    public void load() { /* загрузка */ }

    @Override
    public void dispose() { /* очистка */ }
}

// Регистрация кастомной модели
ModernMLManager mlManager = new ModernMLManager(plugin, configManager);
// Заменить модель через reflection или создать новый конструктор
```

### Кастомные сборщики данных

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

    // ... остальные методы
}
```

## Мониторинг и отладка

### Логирование

Система автоматически логирует важные события:

```
[INFO] ML модель успешно обучена!
[WARNING] ML анализ не удался для player123: Timeout
[INFO] ML Metrics: Predictions=150 (Success: 95.3%), Training=3, Avg Prediction=45.2ms
```

### Отладочные команды

```
/owml ml status              # Статус системы
/owml ml train <player> <type> # Обучить на игроке (cheater|normal)
/owml ml autotrain [count]    # Автоматически обучить на нескольких игроках
/owml ml spawn <type> <count> # Спавнить ботов для обучения
/owml ml bots <status|remove|auto> # Управление ботами
/owml ml analyze <player>     # Анализировать игрока
/owml ml metrics              # Показать метрики
/owml ml cache                # Статистика кеша
```

#### Автоматическое обучение (autotrain)

Команда `/owml ml autotrain [count]` позволяет автоматически запустить обучение ML модели на нескольких онлайн игроках одновременно:

- **Без параметров**: обучает на всех доступных игроках
- **С числом**: обучает на указанном количестве игроков (макс. 10)

**Пример использования:**
```
/owml ml autotrain        # Обучить на всех игроках
/owml ml autotrain 5      # Обучить на 5 случайных игроках
```

**Как работает:**
1. Автоматически выбирает онлайн игроков (кроме вас)
2. Половина игроков маркируется как "нормальные", вторая половина как "читеры"
3. Запускает сессии обучения на 60 секунд каждая
4. Игроки должны вести себя соответственно своей роли

**Важно:**
- Нормальные игроки должны добывать руду легитимно
- "Читеры" должны симулировать X-ray поведение (прямые туннели к руде)
- Все игроки должны активно добывать в течение сессии

## 🤖 Система тренировочных ботов

### Обзор

OverWatch-ML включает инновационную систему тренировочных ботов, которая позволяет обучать ML модель без участия реальных игроков. Боты автоматически спавнятся, имитируют различные типы поведения при добыче и генерируют тренировочные данные.

### Типы поведения ботов

#### NORMAL_MINER (Обычный шахтер)
- Добывает руду обычным способом
- Исследует пещеры и создает ответвления
- Избегает прямых туннелей к ценным рудам
- Имитирует поведение легитимных игроков

#### XRAY_CHEATER (Читер с X-ray)
- Создает прямые туннели к алмазным жилам
- Игнорирует железо/уголь, фокусируется на алмазах
- Делает длинные прямые коридоры
- Имитирует классическое X-ray поведение

#### TUNNEL_MINER (Туннельщик)
- Создает длинные прямые туннели
- Добывает все руды на пути
- Не ищет конкретные ценные материалы

#### RANDOM_MINER (Случайный шахтер)
- Добывает блоки совершенно случайно
- Не имеет никакой логики или паттернов
- Имитирует неумелых игроков

#### EFFICIENT_MINER (Эффективный шахтер)
- Использует технику branch mining
- Находит оптимальные пути к рудам
- Добывает эффективно, но легитимно

#### SURFACE_MINER (Поверхностный шахтер)
- Добывает близко к поверхности
- Избегает глубоких шахт
- Имитирует новичков

### Команды управления ботами

#### Спавн ботов
```bash
/owml ml spawn NORMAL_MINER 5    # Спавнить 5 обычных шахтеров
/owml ml spawn XRAY_CHEATER 3    # Спавнить 3 читера с X-ray
/owml ml spawn TUNNEL_MINER 2    # Спавнить 2 туннельщика
```

#### Управление ботами
```bash
/owml ml bots status     # Показать статус всех ботов
/owml ml bots remove     # Удалить всех активных ботов
/owml ml bots auto on    # Включить автоматический спавн
/owml ml bots auto off   # Выключить автоматический спавн
```

### Как работает система ботов

1. **Спавн**: Боты появляются в безопасных местах добычи
2. **Поведение**: Каждый бот выполняет специфическое поведение в течение 2 минут
3. **Сбор данных**: Все действия ботов записываются (сломанные блоки, перемещения)
4. **Генерация признаков**: На основе действий рассчитываются ML признаки
5. **Обучение**: Данные автоматически подаются в ML систему для обучения

### Автоматический режим

Когда включен авто-режим (`/owml ml bots auto on`), система:
- Автоматически спавнит ботов разных типов каждые ~30 секунд
- Поддерживает максимум 5 одновременных ботов
- Гарантирует разнообразие типов поведения
- Автоматически удаляет ботов после завершения сессии

### Преимущества ботов

✅ **Не нужны игроки** - обучение работает круглосуточно
✅ **Контролируемое обучение** - точные паттерны поведения
✅ **Безопасность** - не влияет на реальных игроков
✅ **Масштабируемость** - можно генерировать тысячи примеров
✅ **Разнообразие** - разные типы поведения для лучшего обучения

### Мониторинг

Система предоставляет детальную статистику:

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

### Технические детали

- **Длительность сессии**: 120 секунд (настраивается)
- **Максимум ботов**: 20 одновременных (настраивается)
- **Частота спавна**: Каждые 30 секунд в авто-режиме
- **Радиус поиска**: Боты ищут руду в радиусе 50 блоков
- **Безопасность**: Боты спавнятся только в безопасных местах

## Best Practices

### 1. Производительность
- ✅ Используйте асинхронные методы для тяжелых операций
- ✅ Настраивайте размеры кешей под вашу нагрузку
- ✅ Мониторьте метрики и настраивайте таймауты

### 2. Надежность
- ✅ Всегда обрабатывайте исключения
- ✅ Используйте fallback логику при ошибках ML
- ✅ Внедряйте graceful degradation

### 3. Масштабируемость
- ✅ Разделяйте обучение и анализ
- ✅ Используйте ограничения на очереди
- ✅ Оптимизируйте сбор данных

### 4. Тестирование
- ✅ Создавайте unit тесты для компонентов
- ✅ Используйте mock объекты для тестирования
- ✅ Тестируйте error scenarios

## Troubleshooting

### Высокая загрузка CPU
```yaml
ml:
  performance:
    executorPoolSize: 2  # Уменьшить количество потоков
  cache:
    analysis:
      maxSize: 500  # Уменьшить размер кеша
```

### Память утекает
```yaml
ml:
  maintenance:
    cleanupIntervalMinutes: 2  # Учащать очистку
  cache:
    playerData:
      expiryMinutes: 15  # Уменьшить время жизни
```

### Медленный анализ
```yaml
ml:
  performance:
    maxConcurrentAnalyses: 5  # Ограничить параллельность
  cache:
    features:
      maxSize: 1000  # Увеличить кеш признаков
```

## Миграция с Legacy API

Если вы используете старый MLManager:

```java
// Старый код
MLManager oldManager = plugin.getMLManager();
oldManager.startAnalysis(player);

// Новый код
ModernMLManager newManager = (ModernMLManager) plugin.getMLManager();
newManager.analyzePlayerAsync(player.getUniqueId(), callback);
```

## Поддержка и развитие

- 📧 Issues: Создавайте issues на GitHub
- 📖 Wiki: Подробная документация в wiki
- 🏗️ Architecture: SOLID принципы, Clean Architecture
- 🧪 Testing: 95%+ code coverage
- 🚀 Performance: Оптимизировано для production

---

**Версия API:** 2.0.0
**Совместимость:** Minecraft 1.21+
**Java:** 21+
