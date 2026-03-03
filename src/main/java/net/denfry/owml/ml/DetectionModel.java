package net.denfry.owml.ml;

import java.util.Collection;
import net.denfry.owml.detection.DetectionResult;
import net.denfry.owml.detection.PlayerBehaviorProfile;

/**
 * Interface for all Machine Learning models used in detection.
 * All ML models must implement this interface.
 */
public interface DetectionModel {

    /**
     * Analyzes player behavior profile and returns a detection result.
     * 
     * @param profile The behavioral profile to analyze.
     * @return The result of the analysis.
     */
    DetectionResult analyze(PlayerBehaviorProfile profile);

    /**
     * Trains the model on new data.
     * 
     * @param trainingData Collection of training profiles.
     * @return true if training was successful.
     */
    boolean train(Collection<PlayerBehaviorProfile> trainingData);

    /**
     * Checks if the model is trained and ready for use.
     * 
     * @return true if the model is ready.
     */
    boolean isTrained();

    /**
     * Saves the model weights to a file.
     */
    void save();

    /**
     * Loads the model weights from a file.
     */
    void load();

    /**
     * Disposes of model resources.
     */
    void dispose();
}
