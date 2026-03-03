package net.denfry.owml.managers;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.config.ConfigManager;

/**
 * @deprecated Use IStatsService from OverWatchContext
 */
@Deprecated
public class StatsManager {

    private static IStatsService getService() {
        return OverWatchML.getInstance().getContext().getStatsService();
    }

    public static StatsManager getInstance() {
        return new StatsManager();
    }

    public static void initialize(OverWatchML pluginInstance, ConfigManager configManager) {
        // No-op, initialization handled by OverWatchContext
    }

    public static void addOreMined(UUID playerId, Material ore) {
        getService().addOreMined(playerId, ore);
    }

    public static Map<Material, Integer> getOreStats(UUID playerId) {
        return getService().getOreStats(playerId);
    }

    public static boolean hasStats(UUID playerId) {
        return !getService().getOreStats(playerId).isEmpty();
    }

    public static Set<UUID> getAllPlayerIds() {
        return getService().getAllPlayerIds();
    }

    public static void updateAutoSaveSettings() {
        // Auto-save logic should be moved to a separate Task/Service
    }

    public static void forceSave() {
        getService().save();
    }

    public static void saveAllData() {
        getService().save();
    }

    public static int getTotalOresMined(UUID playerId) {
        return getService().getTotalOresMined(playerId);
    }
}
