package net.denfry.owml.ml;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Интерфейс для асинхронного анализа игроков
 */
public interface AnalysisManager {

    /**
     * Асинхронно анализирует игрока
     * @param playerId UUID игрока
     * @return CompletableFuture с результатом анализа
     */
    CompletableFuture<ReasoningMLModel.DetectionResult> analyzeAsync(UUID playerId);

    /**
     * Синхронно анализирует игрока (только для критических случаев)
     * @param playerId UUID игрока
     * @return результат анализа
     */
    ReasoningMLModel.DetectionResult analyzeSync(UUID playerId);

    /**
     * Получает текущий уровень подозрительности игрока
     * @param playerId UUID игрока
     * @return уровень подозрительности от 0.0 до 1.0
     */
    double getSuspicionScore(UUID playerId);

    /**
     * Проверяет, находится ли игрок под анализом
     * @param playerId UUID игрока
     * @return true если анализ активен
     */
    boolean isUnderAnalysis(UUID playerId);

    /**
     * Запускает анализ игрока
     * @param playerId UUID игрока
     * @param durationInSeconds длительность анализа в секундах
     */
    void startAnalysis(UUID playerId, long durationInSeconds);

    /**
     * Останавливает анализ игрока
     * @param playerId UUID игрока
     */
    void stopAnalysis(UUID playerId);

    /**
     * Получает оставшееся время анализа для игрока
     * @param playerId UUID игрока
     * @return оставшееся время в секундах, или -1 если анализ не активен
     */
    long getRemainingAnalysisTime(UUID playerId);
}
