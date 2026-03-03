package net.denfry.owml.utils;

import org.bukkit.Bukkit;

/**
 * Version compatibility helper for different Minecraft versions.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
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
            // Extract version from string like "git-Paper-318 (MC: 1.20.4)"
            String versionString = Bukkit.getServer().getBukkitVersion();
            String[] parts = versionString.split("\\.");

            if (parts.length >= 2) {
                majorVersion = Integer.parseInt(parts[0]);
                minorVersion = Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            // Fallback to default
            majorVersion = 1;
            minorVersion = 20;
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
     * Check if the server version is exactly the specified version
     */
    public static boolean isVersionExactly(int major, int minor) {
        return majorVersion == major && minorVersion == minor;
    }

    /**
     * Check if the server supports a specific feature
     */
    public static boolean supportsFeature(String feature) {
        return switch (feature.toLowerCase()) {
            case "hex_colors" -> isVersionAtLeast(1, 16);
            case "advancements" -> isVersionAtLeast(1, 12);
            case "custom_biomes" -> isVersionAtLeast(1, 18);
            case "bundle_api" -> isVersionAtLeast(1, 17);
            default -> false;
        };
    }

    /**
     * Get version-specific information
     */
    public static String getVersionInfo() {
        return String.format("Minecraft %d.%d (%s)",
            majorVersion, minorVersion, getServerVersion());
    }
}
