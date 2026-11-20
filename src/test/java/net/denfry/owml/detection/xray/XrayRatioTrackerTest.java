package net.denfry.owml.detection.xray;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XrayRatioTrackerTest {

    private static final long MIN = 60_000L;

    /** Mines a straight 1x2 tunnel of {@code length} steps along +x from {@code x0}; returns the next x. */
    private static int tunnel(XrayRatioTracker t, long now, int x0, int y, int z, int length) {
        for (int i = 0; i < length; i++) {
            t.onStone(now, x0 + i, y, z);
            t.onStone(now, x0 + i, y + 1, z);
        }
        return x0 + length;
    }

    /** Breaks a vein of {@code size} diamonds next to the tunnel end. */
    private static Optional<XrayRatioTracker.Verdict> vein(XrayRatioTracker t, long now, int x, int y, int z, int size, boolean exposed) {
        Optional<XrayRatioTracker.Verdict> last = Optional.empty();
        for (int i = 0; i < size; i++) {
            Optional<XrayRatioTracker.Verdict> v = t.onOre(now + i * 500L, "deepslate_diamond_ore", 1.0, x, y + (i % 2), z + i / 2, exposed);
            if (v.isPresent()) last = v;
        }
        return last;
    }

    @Test
    public void xrayTunnelsStraightToVeinsAndIsFlagged() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        long now = 0;
        int x = 0;
        Optional<XrayRatioTracker.Verdict> verdict = Optional.empty();
        for (int vein = 0; vein < 5; vein++) {
            x = tunnel(t, now, x, -58, 0, 20);           // ~40 stone per vein
            Optional<XrayRatioTracker.Verdict> v = vein(t, now, x, -58, 1, 4, false);
            if (v.isPresent()) verdict = v;
            now += 3 * MIN;
        }
        assertTrue("x-ray pace must be flagged", verdict.isPresent());
        assertTrue(verdict.get().veinsPer100Stone() >= 1.0);
        assertEquals(Integer.valueOf(verdict.get().veinsByOre().get("deepslate_diamond_ore")),
                Integer.valueOf((int) Math.round(verdict.get().weightedVeins())));
    }

    @Test
    public void stripMinerAtTheMeasuredServerPaceIsNotFlagged() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        long now = 0;
        int x = 0;
        for (int vein = 0; vein < 8; vein++) {
            x = tunnel(t, now, x, -58, 0, 300);          // 600 stone per vein, still 15x the measured server average
            assertFalse(vein(t, now, x, -58, 1, 5, false).isPresent());
            now += 10 * MIN;
        }
    }

    @Test
    public void caveOresExposedByAirTheyDidNotDigDoNotCount() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        long now = 0;
        int x = tunnel(t, now, 0, -58, 0, 80);
        for (int vein = 0; vein < 10; vein++) {
            assertFalse(vein(t, now, x + vein * 10, -50, 5, 4, true).isPresent());
            now += MIN;
        }
        assertEquals(0.0, t.weightedVeins(), 1.0E-9);
    }

    @Test
    public void oneVeinCountsOnce() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        vein(t, 0, 10, -58, 0, 8, false);
        assertEquals(1.0, t.weightedVeins(), 1.0E-9);
    }

    @Test
    public void oldMiningLeavesTheWindow() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        int x = tunnel(t, 0, 0, -58, 0, 50);
        vein(t, 0, x, -58, 1, 3, false);
        t.onStone(4 * 60 * MIN, 500, -58, 0);
        assertEquals(1, t.stone());
        assertEquals(0.0, t.weightedVeins(), 1.0E-9);
    }

    @Test
    public void alertIsNotRepeatedForTheSameLevel() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        long now = 0;
        int x = 0;
        int alerts = 0;
        for (int vein = 0; vein < 4; vein++) {
            x = tunnel(t, now, x, -58, 0, 20);
            if (vein(t, now, x, -58, 1, 4, false).isPresent()) alerts++;
            now += MIN;
        }
        assertEquals(1, alerts);
    }

    @Test
    public void staffMessageNamesTheNumbers() {
        XrayRatioTracker.Verdict v = new XrayRatioTracker.Verdict(4.0, 160, 2.5,
                java.util.Map.of("deepslate_diamond_ore", 4), 3 * 3_600_000L);
        String m = XrayRatioListener.message("Steve", v);
        assertTrue(m, m.contains("Steve") && m.contains("4.0") && m.contains("160") && m.contains("2.50") && m.contains("diamond 4"));
    }

    @Test
    public void ownTunnelIsRemembered() {
        XrayRatioTracker t = new XrayRatioTracker(XrayRatioTracker.Settings.defaults());
        t.onStone(0, 3, 10, -4);
        assertTrue(t.dugByPlayer(3, 10, -4));
        assertFalse(t.dugByPlayer(3, 11, -4));
    }
}
