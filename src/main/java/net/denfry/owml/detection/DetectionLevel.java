package net.denfry.owml.detection;

/**
 * Detection confidence levels with severity scoring.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.2
 */
public enum DetectionLevel {
    SAFE(0, "Safe", "вњ…"),
    LOW(1, "Low Risk", "рџџЎ"),
    MEDIUM(2, "Medium Risk", "рџџ "),
    HIGH(3, "High Risk", "рџ”ґ"),
    CRITICAL(4, "Critical Risk", "рџљЁ");

    private final int severity;
    private final String displayName;
    private final String icon;

    DetectionLevel(int severity, String displayName, String icon) {
        this.severity = severity;
        this.displayName = displayName;
        this.icon = icon;
    }

    public int getSeverity() { return severity; }
    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }

    /**
     * Check if this level is more severe than another
     */
    public boolean isMoreSevereThan(DetectionLevel other) {
        return this.severity > other.severity;
    }

    /**
     * Get color code for this level
     */
    public String getColorCode() {
        return switch (this) {
            case SAFE -> "&a";
            case LOW -> "&e";
            case MEDIUM -> "&6";
            case HIGH -> "&c";
            case CRITICAL -> "&4";
        };
    }
}
