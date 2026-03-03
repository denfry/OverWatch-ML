package net.denfry.owml.detection;

/**
 * Types of detection algorithms used in the advanced detection engine.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public enum DetectionType {
    BEHAVIORAL("Behavioral Analysis", "Analyzes player behavior patterns"),
    PATTERN("Pattern Recognition", "Detects suspicious mining patterns"),
    ANOMALY("Anomaly Detection", "Identifies statistical anomalies"),
    MULTI_ACCOUNT("Multi-Account Detection", "Detects multiple accounts from same IP/network"),
    PATH_PREDICTION("Path Prediction", "Analyzes movement and mining path efficiency"),
    COMBINED("Combined Analysis", "Weighted combination of multiple detection methods"),
    CUSTOM("Custom Detection", "Custom user-defined detection algorithm"),
    UNKNOWN("Unknown", "Unknown detection type");

    private final String displayName;
    private final String description;

    DetectionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
