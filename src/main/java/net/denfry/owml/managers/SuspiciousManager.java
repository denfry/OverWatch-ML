package net.denfry.owml.managers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.api.managers.SuspiciousManagerAPI;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use ISuspiciousService from OverWatchContext
 */
@Deprecated
public class SuspiciousManager implements SuspiciousManagerAPI {

    private static ISuspiciousService getService() {
        return OverWatchML.getInstance().getContext().getSuspiciousService();
    }

    public static void initialize(OverWatchML pluginInstance) {
        // No-op
    }

    public static SuspiciousManager getInstance() {
        return new SuspiciousManager();
    }

    public static void addSuspicious(UUID playerId) {
        getService().addSuspicious(playerId);
    }

    public int getSuspicionPoints(@NotNull UUID playerId) {
        return getService().getSuspicionLevel(playerId);
    }

    public int getSuspiciousCount() {
        return getService().getSuspiciousPlayers().size();
    }

    @Override
    public boolean isPlayerSuspicious(@NotNull UUID playerId) {
        return getService().isPlayerSuspicious(playerId);
    }

    @Override
    public int getSuspicionLevel(@NotNull UUID playerId) {
        return getService().getSuspicionLevel(playerId);
    }

    @Override
    public void addSuspicionPoints(@NotNull UUID playerId, int points) {
        getService().addSuspicionPoints(playerId, points);
    }

    @Override
    public void removeSuspicionPoints(@NotNull UUID playerId, int points) {
        getService().removeSuspicionPoints(playerId, points);
    }

    @Override
    public void setSuspicionLevel(@NotNull UUID playerId, int level) {
        getService().setSuspicionLevel(playerId, level);
    }

    @Override
    public void clearSuspicionData(@NotNull UUID playerId) {
        // Simplified
        getService().setSuspicionLevel(playerId, 0);
    }

    @Override
    @NotNull
    public Set<UUID> getSuspiciousPlayers() {
        return getService().getSuspiciousPlayers();
    }

    @Override
    public int getSuspicionThreshold() {
        return 70; // Placeholder
    }

    @Override
    public void setSuspicionThreshold(int threshold) {
        // No-op
    }

    @Override
    public void initialize() {}

    @Override
    public void shutdown() {}

    @Override
    public void reload() {}

    @Override
    @NotNull
    public String getName() {
        return "SuspiciousManager";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public static Map<UUID, Integer> getSuspiciousCounts() {
        // This is a bit inefficient for a legacy wrapper, but for compatibility:
        Set<UUID> ids = getService().getSuspiciousPlayers();
        Map<UUID, Integer> map = new java.util.HashMap<>();
        for (UUID id : ids) {
            map.put(id, getService().getSuspicionLevel(id));
        }
        return map;
    }

    public static Set<UUID> getSuspiciousPlayersStatic() {
        return getService().getSuspiciousPlayers();
    }

    public static void saveAllData() {
        getService().save();
    }
    
    public static void updateAutoSaveSettings() {
        // No-op
    }
}
