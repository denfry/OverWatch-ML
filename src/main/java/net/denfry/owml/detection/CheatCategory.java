package net.denfry.owml.detection;

/**
 * Standardized cheat categories for internal and external correlation.
 */
public enum CheatCategory {
    XRAY("xray"),
    COMBAT("combat"),
    AIMBOT("aimbot"),
    MOVEMENT("movement"),
    AUTOCLICKER("autoclicker"),
    SCAFFOLD("scaffold"),
    WORLD("world"),
    FAST_BREAK("fast_break"),
    BAD_PACKETS("bad_packets"),
    OTHER("other");

    private final String key;

    CheatCategory(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static CheatCategory fromKey(String key) {
        for (CheatCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return OTHER;
    }
}
