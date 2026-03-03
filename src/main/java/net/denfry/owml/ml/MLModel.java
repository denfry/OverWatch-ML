package net.denfry.owml.ml;


import java.util.Collection;

/**
 * Интерфейс для ML модели - обеспечивает тестируемость и модульность
 */
public interface MLModel {

    /**
     * Анализирует данные игрока и возвращает результат обнаружения
     * @param playerData данные игрока
     * @return результат анализа
     */
    ReasoningMLModel.DetectionResult analyze(PlayerMiningData playerData);

    /**
     * Обучает модель на новых данных
     * @param trainingData коллекция обучающих данных
     * @return true если обучение прошло успешно
     */
    boolean train(Collection<PlayerMiningData> trainingData);

    /**
     * Проверяет, обучена ли модель
     * @return true если модель готова к использованию
     */
    boolean isTrained();

    /**
     * Сохраняет модель в файл
     */
    void save();

    /**
     * Загружает модель из файла
     */
    void load();

    /**
     * Очищает ресурсы модели
     */
    void dispose();
}
