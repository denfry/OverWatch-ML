package net.denfry.owml.detection.xray;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hidden-vein ratio for one player: how many valuable ore veins the player reached by digging, per 100 stone-like
 * blocks mined, over a rolling time window.
 *
 * <p>The signal: an x-ray user tunnels straight to ores nobody could see, so veins come at a few dozen stone each.
 * A strip miner or a caver finds the same ores at hundreds to thousands of stone per vein (main, 11.2026: active
 * miners at ~0.2 diamond blocks per 100 stone, i.e. ~0.04 veins). Ores that were already exposed by a cave, water or
 * another player's tunnel are not counted, so cavers do not inflate the ratio; an ore exposed only by this player's
 * own digging counts, which covers both x-ray tunnels and honest strip mining - the ratio separates those two.
 *
 * <p>Ores within {@link Settings#veinRadius} of an ore of the same kind broken in the last {@link Settings#veinMillis}
 * belong to one vein and count once. Pure logic, no Bukkit: the listener supplies positions and exposure.
 *
 * <p>Thread ownership: one instance per player, used by whichever thread fires that player's break events; callers
 * synchronize on the instance.
 */
public final class XrayRatioTracker {

    /** Thresholds; defaults are 20x above the measured ratio of active honest miners on main. */
    public record Settings(long windowMillis, int minStone, double minWeightedVeins, double veinsPer100Stone,
                           int veinRadius, long veinMillis, long realertMillis, int dugMemory) {
        public static Settings defaults() {
            return new Settings(3 * 3_600_000L, 150, 3.0, 1.0, 2, 60_000L, 30 * 60_000L, 4096);
        }
    }

    /** Why the player was flagged: numbers for the staff message and the telemetry row. */
    public record Verdict(double weightedVeins, int stone, double veinsPer100Stone, Map<String, Integer> veinsByOre,
                          long windowMillis) {}

    private record Event(long at, int stone, double weight, String ore) {}

    private record Vein(String ore, int x, int y, int z, long at) {}

    private final Settings settings;
    private final ArrayDeque<Event> window = new ArrayDeque<>();
    private int stone;
    private double weightedVeins;
    private final Map<String, Integer> veinsByOre = new HashMap<>();

    /** Blocks this player broke recently: air next to an ore is "own digging" only if it is in here. */
    private final ArrayDeque<Long> dugOrder = new ArrayDeque<>();
    private final Set<Long> dug = new HashSet<>();
    private final ArrayDeque<Vein> recentVeinOres = new ArrayDeque<>();
    private long lastAlertAt = Long.MIN_VALUE / 2;
    private double lastAlertVeins;

    public XrayRatioTracker(Settings settings) {
        this.settings = settings;
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** True when the player broke the block at this position recently (their own tunnel). */
    public boolean dugByPlayer(int x, int y, int z) {
        return dug.contains(pack(x, y, z));
    }

    public void onStone(long now, int x, int y, int z) {
        remember(x, y, z);
        evict(now);
        window.addLast(new Event(now, 1, 0, null));
        stone++;
    }

    /**
     * @param weight            rarity weight of this ore kind (diamond 1.0); 0 ignores the ore
     * @param naturallyExposed  the ore touched air/fluid that this player did not dig (cave, water, someone else's tunnel)
     * @return a verdict when the ratio crossed the thresholds and the player was not alerted recently for the same level
     */
    public Optional<Verdict> onOre(long now, String ore, double weight, int x, int y, int z, boolean naturallyExposed) {
        remember(x, y, z);
        evict(now);
        boolean sameVein = false;
        for (Vein v : recentVeinOres) {
            if (now - v.at() <= settings.veinMillis() && v.ore().equals(ore)
                    && Math.abs(v.x() - x) <= settings.veinRadius() && Math.abs(v.y() - y) <= settings.veinRadius()
                    && Math.abs(v.z() - z) <= settings.veinRadius()) {
                sameVein = true;
                break;
            }
        }
        recentVeinOres.addLast(new Vein(ore, x, y, z, now));
        while (recentVeinOres.size() > 64 || (!recentVeinOres.isEmpty() && now - recentVeinOres.peekFirst().at() > settings.veinMillis())) {
            recentVeinOres.removeFirst();
        }
        if (sameVein || naturallyExposed || weight <= 0) return Optional.empty();

        window.addLast(new Event(now, 0, weight, ore));
        weightedVeins += weight;
        veinsByOre.merge(ore, 1, Integer::sum);
        return evaluate(now);
    }

    public int stone() { return stone; }

    public double weightedVeins() { return weightedVeins; }

    private Optional<Verdict> evaluate(long now) {
        if (stone < settings.minStone() || weightedVeins < settings.minWeightedVeins()) return Optional.empty();
        double ratio = weightedVeins * 100.0 / stone;
        if (ratio < settings.veinsPer100Stone()) return Optional.empty();
        // one alert per level: again only after the cooldown or when the player found clearly more since
        if (now - lastAlertAt < settings.realertMillis() && weightedVeins < lastAlertVeins + settings.minWeightedVeins()) {
            return Optional.empty();
        }
        lastAlertAt = now;
        lastAlertVeins = weightedVeins;
        return Optional.of(new Verdict(weightedVeins, stone, ratio, Map.copyOf(veinsByOre), settings.windowMillis()));
    }

    private void remember(int x, int y, int z) {
        long key = pack(x, y, z);
        if (dug.add(key)) {
            dugOrder.addLast(key);
            if (dugOrder.size() > settings.dugMemory()) dug.remove(dugOrder.removeFirst());
        }
    }

    private void evict(long now) {
        while (!window.isEmpty() && now - window.peekFirst().at() > settings.windowMillis()) {
            Event e = window.removeFirst();
            stone -= e.stone();
            if (e.ore() != null) {
                weightedVeins -= e.weight();
                veinsByOre.computeIfPresent(e.ore(), (k, v) -> v <= 1 ? null : v - 1);
            }
        }
        if (weightedVeins < 1.0E-9) weightedVeins = 0;
    }
}
