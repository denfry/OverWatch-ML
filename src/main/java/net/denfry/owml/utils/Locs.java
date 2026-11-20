package net.denfry.owml.utils;

import org.bukkit.Location;

public final class Locs {
   private Locs() {
   }

   public static double distance(Location a, Location b) {
      return a != null && b != null && a.getWorld() != null && b.getWorld() != null && a.getWorld().equals(b.getWorld()) ? a.distance(b) : 0.0;
   }
}
