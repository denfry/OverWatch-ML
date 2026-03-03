package net.denfry.owml.ml;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Интерфейс для сбора данных игроков - обеспечивает тестируемость
 */
public interface DataCollector {

    /**
     * Начинает сбор данных для игрока
     * @param player игрок
     * @param isCheater метка для обучения (true - читер, false - нормальный игрок)
     */
    void startCollecting(Player player, boolean isCheater);

    /**
     * Останавливает сбор данных для игрока
     * @param player игрок
     * @return собранные данные или null если сбор не производился
     */
    PlayerMiningData stopCollecting(Player player);

    /**
     * Проверяет, ведется ли сбор данных для игрока
     * @param playerId UUID игрока
     * @return true если сбор данных активен
     */
    boolean isCollecting(UUID playerId);

    /**
     * Добавляет данные о сломанном блоке
     * @param player игрок
     * @param blockType тип блока
     * @param location координаты
     */
    void addBlockBreak(Player player, String blockType, String location);

    /**
     * Добавляет данные о движении игрока
     * @param player игрок
     * @param fromX начальная X координата
     * @param fromY начальная Y координата
     * @param fromZ начальная Z координата
     * @param toX конечная X координата
     * @param toY конечная Y координата
     * @param toZ конечная Z координата
     */
    void addMovement(Player player, double fromX, double fromY, double fromZ,
                    double toX, double toY, double toZ);

    /**
     * Получает текущие данные игрока
     * @param playerId UUID игрока
     * @return данные игрока или null
     */
    PlayerMiningData getCurrentData(UUID playerId);

    /**
     * Очищает старые данные для экономии памяти
     */
    void cleanupOldData();
}
