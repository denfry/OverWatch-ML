package net.denfry.owml.detection.xray;

import net.denfry.owml.alerts.StaffAlertManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Feeds {@link XrayRatioTracker} from block breaks and alerts staff when a player's hidden-vein ratio crosses the
 * thresholds. Alert only: the decision stays with staff (there is no automatic punishment here).
 *
 * <p>Thread ownership (Folia/Tessera): break events run on the region thread that owns the block. Neighbours are read
 * only when the same region owns them; a neighbour in another region is treated as "exposed", which can only hide
 * a vein from the count, never invent one.
 */
public final class XrayRatioListener implements Listener {
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final StaffAlertManager alerts;
    private final XrayRatioTracker.Settings settings;
    private final Map<Material, Double> weights;
    private final Map<UUID, Holder> trackers = new ConcurrentHashMap<>();

    private static final class Holder {
        final XrayRatioTracker tracker;
        volatile long lastSeen;

        Holder(XrayRatioTracker tracker) { this.tracker = tracker; }
    }

    public XrayRatioListener(JavaPlugin plugin, StaffAlertManager alerts, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.alerts = alerts;
        XrayRatioTracker.Settings d = XrayRatioTracker.Settings.defaults();
        this.settings = cfg == null ? d : new XrayRatioTracker.Settings(
                cfg.getLong("window-minutes", d.windowMillis() / 60_000L) * 60_000L,
                cfg.getInt("min-stone", d.minStone()),
                cfg.getDouble("min-veins", d.minWeightedVeins()),
                cfg.getDouble("veins-per-100-stone", d.veinsPer100Stone()),
                d.veinRadius(), d.veinMillis(),
                cfg.getLong("realert-minutes", d.realertMillis() / 60_000L) * 60_000L,
                d.dugMemory());
        this.weights = readWeights(cfg == null ? null : cfg.getConfigurationSection("weights"));
    }

    /** Vein weight by rarity: a diamond or debris vein is 1, common ores count fractionally or not at all. */
    static Map<Material, Double> readWeights(ConfigurationSection section) {
        Map<Material, Double> w = new EnumMap<>(Material.class);
        w.put(Material.DIAMOND_ORE, 1.0);
        w.put(Material.DEEPSLATE_DIAMOND_ORE, 1.0);
        w.put(Material.ANCIENT_DEBRIS, 1.0);
        w.put(Material.EMERALD_ORE, 1.0);
        w.put(Material.DEEPSLATE_EMERALD_ORE, 1.0);
        w.put(Material.GOLD_ORE, 0.25);
        w.put(Material.DEEPSLATE_GOLD_ORE, 0.25);
        w.put(Material.LAPIS_ORE, 0.2);
        w.put(Material.DEEPSLATE_LAPIS_ORE, 0.2);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) w.put(m, section.getDouble(key));
            }
        }
        return w;
    }

    static boolean isStoneLike(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || Tag.BASE_STONE_NETHER.isTagged(m) || m == Material.NETHERRACK;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || p.hasPermission("owml.bypass")) return;
        Block b = e.getBlock();
        Material type = b.getType();
        Double weight = weights.get(type);
        boolean stone = weight == null && isStoneLike(type);
        if (weight == null && !stone) return;

        long now = System.currentTimeMillis();
        Holder h = trackers.computeIfAbsent(p.getUniqueId(), k -> new Holder(new XrayRatioTracker(settings)));
        h.lastSeen = now;
        Optional<XrayRatioTracker.Verdict> verdict;
        synchronized (h.tracker) {
            if (stone) {
                h.tracker.onStone(now, b.getX(), b.getY(), b.getZ());
                return;
            }
            boolean exposed = naturallyExposed(b, h.tracker);
            verdict = h.tracker.onOre(now, type.getKey().getKey(), weight, b.getX(), b.getY(), b.getZ(), exposed);
        }
        verdict.ifPresent(v -> alerts.alertStaffWithTeleport(p, b.getLocation(), message(p.getName(), v)));
    }

    /** Some face touches air or fluid that this player did not dig: a cave, water, someone else's tunnel. */
    private static boolean naturallyExposed(Block ore, XrayRatioTracker tracker) {
        for (BlockFace f : FACES) {
            Block n = ore.getRelative(f);
            if (!Bukkit.isOwnedByCurrentRegion(n.getLocation())) return true;
            Material m = n.getType();
            boolean open = m.isAir() || !m.isOccluding();
            if (open && !tracker.dugByPlayer(n.getX(), n.getY(), n.getZ())) return true;
        }
        return false;
    }

    static String message(String player, XrayRatioTracker.Verdict v) {
        String ores = v.veinsByOre().entrySet().stream()
                .map(en -> en.getKey().replace("deepslate_", "").replace("_ore", "") + " " + en.getValue())
                .collect(Collectors.joining(", "));
        return String.format(Locale.ROOT, "Возможен иксрей: %s докопался до %.1f скрытых жил на %d камня за %d ч (%.2f на 100 камня; %s). "
                        + "Проверьте и решите сами, автоматического наказания нет.",
                player, v.weightedVeins(), v.stone(), v.windowMillis() / 3_600_000L, v.veinsPer100Stone(), ores);
    }

    /** Drops trackers of players who have not mined for longer than the window (called from a timer). */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - settings.windowMillis();
        trackers.values().removeIf(h -> h.lastSeen < cutoff);
    }

    public JavaPlugin plugin() { return plugin; }
}
