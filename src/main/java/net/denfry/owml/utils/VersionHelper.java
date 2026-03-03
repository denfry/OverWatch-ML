package net.denfry.owml.utils;

import org.bukkit.Bukkit;

/**
 * Version helper for Minecraft 1.21+.
 * Support for older versions has been dropped.
 */
public class VersionHelper {

    private static String serverVersion;
    private static int majorVersion;
    private static int minorVersion;

    /**
     * Initialize version detection
     */
    public static void initialize() {
        serverVersion = Bukkit.getServer().getVersion();
        parseVersion();
    }

    /**
     * Parse the server version string
     */
    private static void parseVersion() {
        try {
            String versionString = Bukkit.getServer().getBukkitVersion();
            String[] parts = versionString.split("\\.");

            if (parts.length >= 2) {
                majorVersion = Integer.parseInt(parts[0]);
                minorVersion = Integer.parseInt(parts[1]);
            } else {
                // Default to 1.21 if parsing fails
                majorVersion = 1;
                minorVersion = 21;
            }
        } catch (Exception e) {
            majorVersion = 1;
            minorVersion = 21;
        }
    }

    /**
     * Get the server version string
     */
    public static String getServerVersion() {
        return serverVersion != null ? serverVersion : "Unknown";
    }

    /**
     * Get the major version number
     */
    public static int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Get the minor version number
     */
    public static int getMinorVersion() {
        return minorVersion;
    }

    /**
     * Check if the server version is at least the specified version
     */
    public static boolean isVersionAtLeast(int major, int minor) {
        return majorVersion > major || (majorVersion == major && minorVersion >= minor);
    }

    /**
     * Check if the server supports a specific feature.
     * Since we only support 1.21+, most features are natively supported.
     */
    public static boolean supportsFeature(String feature) {
        return true; // 1.21+ supports hex colors, advancements, custom biomes, etc.
    }

    /**
     * Get version-specific information
     */
    public static String getVersionInfo() {
        return String.format("Minecraft %d.%d (%s) - Modern Mode Enabled",
            majorVersion, minorVersion, getServerVersion());
    }
}
