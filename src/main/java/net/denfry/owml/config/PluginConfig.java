package net.denfry.owml.config;

import org.bukkit.Material;
import java.util.Set;
import java.util.List;

public record PluginConfig(
    DecoySettings decoy,
    StaffSettings staff,
    MLSettings ml,
    AutoSaveSettings autoSave,
    CommandHidingSettings commandHiding,
    DisabledWorldsSettings disabledWorlds,
    WebhookSettings webhook,
    Set<Material> naturalOres,
    boolean debugEnabled,
    String language
) {
    public record DecoySettings(
        boolean enabled,
        int oreThreshold,
        long timeWindowTicks,
        double distance,
        double fieldOffset,
        double maxDistance,
        long revertDelay,
        boolean warnOnDecoy,
        boolean requireBuried,
        int searchRadius,
        int buriedThreshold
    ) {}

    public record StaffSettings(
        boolean oreAlerts,
        long oreResetTime,
        boolean alertEnabled
    ) {}

    public record MLSettings(
        boolean enabled,
        double detectionThreshold,
        int trainingSessionDuration,
        int positionUpdateInterval,
        AutoAnalysisSettings autoAnalysis
    ) {
        public record AutoAnalysisSettings(
            boolean enabled,
            int suspiciousThreshold,
            int maxPlayers
        ) {}
    }

    public record AutoSaveSettings(
        SaveSettings stats,
        SaveSettings suspicious,
        SaveSettings punishment
    ) {
        public record SaveSettings(
            boolean enabled,
            int intervalMinutes,
            boolean logging
        ) {}
    }

    public record CommandHidingSettings(
        boolean enabled,
        String errorLine1,
        String errorLine2
    ) {}

    public record DisabledWorldsSettings(
        boolean enabled,
        Set<String> worlds
    ) {}

    public record WebhookSettings(
        String url,
        Set<String> enabledAlerts
    ) {}
}
