package net.denfry.owml.ml.v2.filters;

public class InstantFilterRules {
   public static int evaluateXray(boolean decoyTouched, int valuableOres, int blocksBroken, int minBlocks, double maxValuableRatio) {
      int points = 0;
      if (decoyTouched) {
         points += 100;
      }

      if (blocksBroken >= Math.max(1, minBlocks) && (double)valuableOres / blocksBroken > maxValuableRatio) {
         points += 35;
      }

      return points;
   }

   public static int evaluateCombat(
      double yawChange,
      double distanceToTarget,
      double maxReach,
      int ping,
      int ticksInAir,
      boolean allHeadshots,
      double headshotDeviation,
      double speed,
      double maxAllowedSpeed
   ) {
      int points = 0;
      if (Math.abs(yawChange) > 180.0) {
         points += 100;
      }

      double pingCompensatedReach = maxReach + 0.5 + ping * 0.005;
      if (distanceToTarget > pingCompensatedReach) {
         points += 45;
      }

      if (ticksInAir > 20) {
         points += 50;
      }

      if (allHeadshots && headshotDeviation < 2.0) {
         points += 40;
      }

      if (speed > maxAllowedSpeed) {
         points += 35;
      }

      return points;
   }
}
