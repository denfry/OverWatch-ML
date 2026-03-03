package net.denfry.owml.detection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of detection analysis containing confidence level, reasons, and additional data.
 * Converted to Java Record for Java 21+ efficiency.
 *
 * @author OverWatch Team
 * @version 1.1.0
 * @since 1.8.2
 */
public record DetectionResult(
    DetectionLevel level,
    double confidence,
    List<String> reasons,
    DetectionType detectionType,
    Map<String, Object> additionalData,
    long timestamp
) {

    public DetectionResult {
        confidence = Math.max(0.0, Math.min(1.0, confidence)); // Clamp to [0,1]
        reasons = reasons != null ? List.copyOf(reasons) : Collections.emptyList();
        additionalData = additionalData != null ? Map.copyOf(additionalData) : Collections.emptyMap();
    }

    public DetectionResult(DetectionLevel level, double confidence, List<String> reasons,
                          DetectionType detectionType, Map<String, Object> additionalData) {
        this(level, confidence, reasons, detectionType, additionalData, System.currentTimeMillis());
    }

    /**
     * Create a safe (no detection) result
     */
    public static DetectionResult createSafe() {
        return new DetectionResult(DetectionLevel.SAFE, 0.0, Collections.emptyList(),
                                 DetectionType.UNKNOWN, Collections.emptyMap(), System.currentTimeMillis());
    }

    /**
     * Create a high confidence detection result
     */
    public static DetectionResult createHighConfidence(DetectionLevel level, String reason, DetectionType type) {
        return new DetectionResult(level, 0.8, List.of(reason), type, Collections.emptyMap(), System.currentTimeMillis());
    }

    // Compatibility getters for existing code
    public DetectionLevel getLevel() { return level; }
    public double getConfidence() { return confidence; }
    public List<String> getReasons() { return reasons; }
    public DetectionType getDetectionType() { return detectionType; }
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public long getTimestamp() { return timestamp; }

    /**
     * Check if this result indicates suspicious behavior
     */
    public boolean isSuspicious() {
        return level != DetectionLevel.SAFE;
    }

    /**
     * Get confidence as percentage
     */
    public int getConfidencePercent() {
        return (int) (confidence * 100);
    }
}