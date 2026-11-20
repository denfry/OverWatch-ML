package net.denfry.owml.monitoring;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.Schedulers;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class MiningTelemetry implements Listener {
   private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
   private static final BlockFace[] FACES = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
   private final OverWatchML plugin;
   private final File dir;
   private final Set<Material> trackedOres;
   private final Map<MiningTelemetry.Key, MiningTelemetry.Bucket> buckets = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<String> alertRows = new ConcurrentLinkedQueue<>();
   private final AtomicLong seen = new AtomicLong();
   private final AtomicLong counted = new AtomicLong();

   public MiningTelemetry(OverWatchML plugin, Set<Material> trackedOres) {
      this.plugin = plugin;
      this.dir = new File(plugin.getDataFolder(), "telemetry");
      this.trackedOres = trackedOres;
   }

   public void start(int flushMinutes) {
      if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
         this.plugin.getLogger().warning("telemetry: cannot create " + this.dir);
      }

      long ticks = Math.max(1, flushMinutes) * 60L * 20L;
      Schedulers.asyncTimer(this::flush, ticks, ticks);
      this.plugin.getLogger().info("Телеметрия копания: " + this.dir.getPath() + " (сброс раз в " + flushMinutes + " мин)");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent e) {
      this.seen.incrementAndGet();
      Player p = e.getPlayer();
      if (p.getGameMode() == GameMode.SURVIVAL && !p.hasPermission("owml.bypass")) {
         Block b = e.getBlock();
         Material type = b.getType();
         boolean ore = this.trackedOres.contains(type);
         boolean stone = !ore && isStoneLike(type);
         if (ore || stone) {
            this.counted.incrementAndGet();
            long hour = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis());
            MiningTelemetry.Bucket bucket = this.buckets
               .computeIfAbsent(new MiningTelemetry.Key(p.getUniqueId(), p.getName(), b.getWorld().getName(), hour), k -> new MiningTelemetry.Bucket());
            int y = b.getY();
            synchronized (bucket) {
               bucket.minY = Math.min(bucket.minY, (long)y);
               bucket.maxY = Math.max(bucket.maxY, (long)y);
               if (stone) {
                  bucket.stone++;
                  bucket.sumY += y;
               } else {
                  long[] c = bucket.ores.computeIfAbsent(type, k -> new long[2]);
                  c[0]++;
                  if (hidden(b)) {
                     c[1]++;
                  }
               }
            }
         }
      }
   }

   public void recordAlert(String kind, String player, Location at, String message) {
      String pos = at != null && at.getWorld() != null ? at.getWorld().getName() + "," + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() : ",,,";
      this.alertRows.add(Instant.now().atZone(ZONE).toLocalDateTime().withNano(0) + "," + csv(kind) + "," + csv(player) + "," + pos + "," + csv(message));
   }

   private static boolean hidden(Block b) {
      for (BlockFace f : FACES) {
         Material m = b.getRelative(f).getType();
         if (m.isAir() || m == Material.WATER || m == Material.LAVA || !m.isOccluding()) {
            return false;
         }
      }

      return true;
   }

   private static boolean isStoneLike(Material m) {
      return Tag.BASE_STONE_OVERWORLD.isTagged(m) || Tag.BASE_STONE_NETHER.isTagged(m) || m == Material.NETHERRACK || m == Material.COBBLED_DEEPSLATE;
   }

   public synchronized void flush() {
      this.flush(false);
   }

   public synchronized void flush(boolean all) {
      long currentHour = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis());
      List<String> rows = new ArrayList<>();

      for (Entry<MiningTelemetry.Key, MiningTelemetry.Bucket> e : this.buckets.entrySet()) {
         MiningTelemetry.Key k = e.getKey();
         if (all || k.hour() < currentHour) {
            MiningTelemetry.Bucket b = this.buckets.remove(k);
            if (b != null) {
               synchronized (b) {
                  long oreTotal = 0L;
                  long hiddenTotal = 0L;
                  StringBuilder detail = new StringBuilder();

                  for (Entry<Material, long[]> o : b.ores.entrySet()) {
                     oreTotal += ((long[])o.getValue())[0];
                     hiddenTotal += ((long[])o.getValue())[1];
                     if (detail.length() > 0) {
                        detail.append(' ');
                     }

                     detail.append(o.getKey().name().toLowerCase(Locale.ROOT)).append('=').append(o.getValue()[0]).append('/').append(o.getValue()[1]);
                  }

                  String hourText = Instant.ofEpochMilli(TimeUnit.HOURS.toMillis(k.hour())).atZone(ZONE).toLocalDateTime().toString();
                  rows.add(
                     hourText
                        + ","
                        + k.player()
                        + ","
                        + csv(k.name())
                        + ","
                        + csv(k.world())
                        + ","
                        + b.stone
                        + ","
                        + oreTotal
                        + ","
                        + hiddenTotal
                        + ","
                        + (b.minY == Long.MAX_VALUE ? "" : b.minY)
                        + ","
                        + (b.maxY == Long.MIN_VALUE ? "" : b.maxY)
                        + ","
                        + (b.stone > 0L ? String.format(Locale.ROOT, "%.1f", (double)b.sumY / b.stone) : "")
                        + ","
                        + csv(detail.toString())
                  );
               }
            }
         }
      }

      this.append("mining", "hour,uuid,player,world,stone,ores,hidden_ores,min_y,max_y,avg_stone_y,ores_mined/hidden", rows);
      this.plugin
         .getLogger()
         .info(
            "Телеметрия: событий ломания "
               + this.seen.getAndSet(0L)
               + ", учтено камня/руды "
               + this.counted.getAndSet(0L)
               + ", записано строк "
               + rows.size()
               + ", открытых часов "
               + this.buckets.size()
               + ", алертов "
               + this.alertRows.size()
         );
      List<String> alerts = new ArrayList<>();

      String r;
      while ((r = this.alertRows.poll()) != null) {
         alerts.add(r);
      }

      this.append("alerts", "time,kind,player,world,x,y,z,message", alerts);
   }

   private void append(String prefix, String header, List<String> rows) {
      if (!rows.isEmpty()) {
         File f = new File(this.dir, prefix + "-" + LocalDate.now(ZONE) + ".csv");
         boolean fresh = !f.exists();

         try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (fresh) {
               w.write(header);
               w.newLine();
            }

            for (String r : rows) {
               w.write(r);
               w.newLine();
            }
         } catch (IOException var11) {
            this.plugin.getLogger().warning("telemetry: " + f.getName() + ": " + var11.getMessage());
         }
      }
   }

   private static String csv(String s) {
      if (s == null) {
         return "";
      } else {
         String v = s.replaceAll("§.", "").replace('\n', ' ').replace('\r', ' ');
         return !v.contains(",") && !v.contains("\"") ? v : "\"" + v.replace("\"", "\"\"") + "\"";
      }
   }

   private static final class Bucket {
      long stone;
      long minY = Long.MAX_VALUE;
      long maxY = Long.MIN_VALUE;
      long sumY;
      final Map<Material, long[]> ores = new EnumMap<>(Material.class);
   }

   private record Key(UUID player, String name, String world, long hour) {
   }
}
