package net.denfry.owml.ml.impl;

import java.util.Collection;
import net.denfry.owml.ml.MLModel;
import net.denfry.owml.ml.PlayerMiningData;
import net.denfry.owml.ml.ReasoningMLModel;

/**
 * Адаптер для существующей ReasoningMLModel - обеспечивает совместимость с новым интерфейсом
 */
public class ReasoningMLModelAdapter implements MLModel {

    private final ReasoningMLModel model;

    public ReasoningMLModelAdapter(ReasoningMLModel model) {
        this.model = model;
    }

    @Override
    public ReasoningMLModel.DetectionResult analyze(PlayerMiningData playerData) {
        try {
            return model.predict(playerData.getFeatures());
        } catch (Exception e) {
            // Graceful degradation - return normal result on error
            return new ReasoningMLModel.DetectionResult(0.0, "Analysis failed", new java.util.ArrayList<>());
        }
    }

    @Override
    public boolean train(Collection<PlayerMiningData> trainingData) {
        try {
            return model.train();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isTrained() {
        return model.isTrained();
    }

    @Override
    public void save() {
        // ReasoningMLModel doesn't have save functionality
        // This is a no-op implementation for interface compatibility
    }

    @Override
    public void load() {
        // ReasoningMLModel doesn't have load functionality
        // This is a no-op implementation for interface compatibility
    }

    @Override
    public void dispose() {
        // ReasoningMLModel does not require explicit resource cleanup
    }

    /**
     * Получает оригинальную модель
     */
    public ReasoningMLModel getOriginalModel() {
        return model;
    }
}
