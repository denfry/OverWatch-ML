package net.denfry.owml.config;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration validator for OverWatch-ML.
 * Validates configuration values and provides safe defaults for invalid entries.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class ConfigValidator {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private final FileConfiguration config;
    private final List<String> validationErrors = new ArrayList<>();
    private final List<String> validationWarnings = new ArrayList<>();

    public ConfigValidator(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Validates the entire configuration and returns validation results.
     *
     * @return ValidationResult containing errors and warnings
     */
    public ValidationResult validate() {
        validationErrors.clear();
        validationWarnings.clear();

        // Validate different sections
        validateDecoySettings();
        validateStaffSettings();
        validateMLSettings();
        validateWorldSettings();
        validateCommandSettings();
        validateAutoSaveSettings();

        return new ValidationResult(validationErrors, validationWarnings);
    }

    /**
     * Validate decoy-related settings.
     */
    private void validateDecoySettings() {
        // Ore threshold
        int oreThreshold = config.getInt("decoy.oreThreshold", 8);
        if (oreThreshold < 1) {
            validationErrors.add("decoy.oreThreshold must be at least 1, got: " + oreThreshold);
        } else if (oreThreshold > 100) {
            validationWarnings.add("decoy.oreThreshold is very high (" + oreThreshold + "), consider lowering for better performance");
        }

        // Time window
        long timeWindowTicks = config.getLong("decoy.timeWindowTicks", 3800L);
        if (timeWindowTicks < 1200L) { // Less than 1 minute
            validationErrors.add("decoy.timeWindowTicks must be at least 1200 (1 minute), got: " + timeWindowTicks);
        } else if (timeWindowTicks > 72000L) { // More than 1 hour
            validationWarnings.add("decoy.timeWindowTicks is very high (" + timeWindowTicks + " ticks), consider lowering");
        }

        // Distance settings
        double distance = config.getDouble("decoy.distance", 2.0);
        if (distance < 0.5) {
            validationErrors.add("decoy.distance must be at least 0.5, got: " + distance);
        } else if (distance > 10.0) {
            validationWarnings.add("decoy.distance is very high (" + distance + "), may cause performance issues");
        }

        // Search radius
        int searchRadius = config.getInt("decoy.searchRadius", 3);
        if (searchRadius < 1) {
            validationErrors.add("decoy.searchRadius must be at least 1, got: " + searchRadius);
        } else if (searchRadius > 20) {
            validationWarnings.add("decoy.searchRadius is very high (" + searchRadius + "), may cause performance issues");
        }

        // Buried threshold
        int buriedThreshold = config.getInt("decoy.buriedThreshold", 4);
        if (buriedThreshold < 0) {
            validationErrors.add("decoy.buriedThreshold cannot be negative, got: " + buriedThreshold);
        } else if (buriedThreshold > 26) { // Maximum adjacent blocks
            validationWarnings.add("decoy.buriedThreshold is very high (" + buriedThreshold + "), may be too restrictive");
        }

        // Revert delay
        long revertDelay = config.getLong("decoy.revertDelay", 1200L);
        if (revertDelay < 600L) { // Less than 30 seconds
            validationErrors.add("decoy.revertDelay must be at least 600 (30 seconds), got: " + revertDelay);
        }
    }

    /**
     * Validate staff-related settings.
     */
    private void validateStaffSettings() {
        // Ore reset time
        long oreResetTime = config.getLong("staff.oreResetTime", 36000L);
        if (oreResetTime < 1200L) { // Less than 1 minute
            validationErrors.add("staff.oreResetTime must be at least 1200 (1 minute), got: " + oreResetTime);
        }

        // Validate ore materials
        List<String> naturalOres = config.getStringList("ores.natural");
        Set<Material> validOres = new HashSet<>();
        Set<String> invalidOres = new HashSet<>();

        for (String oreName : naturalOres) {
            try {
                Material material = net.denfry.owml.utils.MaterialHelper.getMaterial(oreName.toUpperCase());
                if (material != null && material.isBlock()) {
                    validOres.add(material);
                } else if (material == null) {
                    invalidOres.add(oreName + " (material not available in this version)");
                } else {
                    invalidOres.add(oreName + " (not a block)");
                }
            } catch (IllegalArgumentException e) {
                invalidOres.add(oreName + " (invalid material)");
            }
        }

        if (validOres.isEmpty()) {
            validationErrors.add("No valid ores found in ores.natural list. Plugin will not function properly.");
        }

        if (!invalidOres.isEmpty()) {
            validationWarnings.add("Invalid ores in ores.natural: " + String.join(", ", invalidOres));
        }
    }

    /**
     * Validate ML-related settings.
     */
    private void validateMLSettings() {
        // Detection threshold
        double detectionThreshold = config.getDouble("ml.detectionThreshold", 0.75);
        if (detectionThreshold < 0.0 || detectionThreshold > 1.0) {
            validationErrors.add("ml.detectionThreshold must be between 0.0 and 1.0, got: " + detectionThreshold);
        }

        // Training session duration
        int trainingDuration = config.getInt("ml.trainingSessionDuration", 600);
        if (trainingDuration < 60) { // Less than 1 minute
            validationErrors.add("ml.trainingSessionDuration must be at least 60 (1 minute), got: " + trainingDuration);
        } else if (trainingDuration > 3600) { // More than 1 hour
            validationWarnings.add("ml.trainingSessionDuration is very long (" + trainingDuration + " seconds), consider shorter sessions");
        }

        // Position update interval
        int positionInterval = config.getInt("ml.positionUpdateInterval", 5);
        if (positionInterval < 1) {
            validationErrors.add("ml.positionUpdateInterval must be at least 1, got: " + positionInterval);
        } else if (positionInterval > 20) {
            validationWarnings.add("ml.positionUpdateInterval is high (" + positionInterval + "), may cause performance issues");
        }

        // Auto analysis settings
        if (config.getBoolean("ml.autoAnalysis.enabled", true)) {
            int suspiciousThreshold = config.getInt("ml.autoAnalysis.suspiciousThreshold", 15);
            if (suspiciousThreshold < 1) {
                validationErrors.add("ml.autoAnalysis.suspiciousThreshold must be at least 1, got: " + suspiciousThreshold);
            }

            int maxPlayers = config.getInt("ml.autoAnalysis.maxPlayers", 5);
            if (maxPlayers < 1) {
                validationErrors.add("ml.autoAnalysis.maxPlayers must be at least 1, got: " + maxPlayers);
            }
        }
    }

    /**
     * Validate world-related settings.
     */
    private void validateWorldSettings() {
        List<String> disabledWorlds = config.getStringList("disabled-worlds.worlds");
        Set<String> invalidWorlds = new HashSet<>();

        for (String worldName : disabledWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                invalidWorlds.add(worldName);
            }
        }

        if (!invalidWorlds.isEmpty()) {
            validationWarnings.add("Disabled worlds that don't exist: " + String.join(", ", invalidWorlds));
        }
    }

    /**
     * Validate command-related settings.
     */
    private void validateCommandSettings() {
        // Command hiding messages
        String errorLine1 = config.getString("command-hiding.messages.error-line1", "");
        String errorLine2 = config.getString("command-hiding.messages.error-line2", "");

        if (errorLine1.isEmpty() || errorLine2.isEmpty()) {
            validationWarnings.add("Command hiding error messages are empty, using defaults");
        }
    }

    /**
     * Validate auto-save settings.
     */
    private void validateAutoSaveSettings() {
        // Stats auto-save
        if (config.getBoolean("stats.autoSave.enabled", true)) {
            int statsInterval = config.getInt("stats.autoSave.intervalMinutes", 10);
            if (statsInterval < 1) {
                validationErrors.add("stats.autoSave.intervalMinutes must be at least 1, got: " + statsInterval);
            } else if (statsInterval > 60) {
                validationWarnings.add("stats.autoSave.intervalMinutes is very high (" + statsInterval + "), consider lower values");
            }
        }

        // Suspicious auto-save
        if (config.getBoolean("suspicious.autoSave.enabled", true)) {
            int suspiciousInterval = config.getInt("suspicious.autoSave.intervalMinutes", 10);
            if (suspiciousInterval < 1) {
                validationErrors.add("suspicious.autoSave.intervalMinutes must be at least 1, got: " + suspiciousInterval);
            }
        }

        // Punishment auto-save
        if (config.getBoolean("punishment.autoSave.enabled", true)) {
            int punishmentInterval = config.getInt("punishment.autoSave.intervalMinutes", 10);
            if (punishmentInterval < 1) {
                validationErrors.add("punishment.autoSave.intervalMinutes must be at least 1, got: " + punishmentInterval);
            }
        }
    }

    /**
     * Log validation results.
     */
    public void logResults() {
        if (!validationErrors.isEmpty()) {
            MessageManager.log("error", "Configuration validation found {COUNT} errors:", "COUNT", String.valueOf(validationErrors.size()));
            for (String error : validationErrors) {
                MessageManager.log("error", "  - {ERROR}", "ERROR", error);
            }
        }

        if (!validationWarnings.isEmpty()) {
            MessageManager.log("warning", "Configuration validation found {COUNT} warnings:", "COUNT", String.valueOf(validationWarnings.size()));
            for (String warning : validationWarnings) {
                MessageManager.log("warning", "  - {WARNING}", "WARNING", warning);
            }
        }

        if (validationErrors.isEmpty() && validationWarnings.isEmpty()) {
            MessageManager.log("info", "Configuration validation passed with no errors or warnings");
        }
    }

    /**
     * Result of configuration validation.
     */
    public static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(List<String> errors, List<String> warnings) {
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }

        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean isValid() { return errors.isEmpty(); }
    }
}
