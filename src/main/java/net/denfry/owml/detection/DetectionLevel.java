package net.denfry.owml.detection;

public enum DetectionLevel {
   SAFE(0, "Safe", "✅"),
   LOW(1, "Low Risk", "\ud83d\udfe1"),
   MEDIUM(2, "Medium Risk", "\ud83d\udfe0"),
   HIGH(3, "High Risk", "\ud83d\udd34"),
   CRITICAL(4, "Critical Risk", "\ud83d\udea8");

   private final int severity;
   private final String displayName;
   private final String icon;

   private DetectionLevel(int severity, String displayName, String icon) {
      this.severity = severity;
      this.displayName = displayName;
      this.icon = icon;
   }

   public int getSeverity() {
      return this.severity;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public String getIcon() {
      return this.icon;
   }

   public boolean isMoreSevereThan(DetectionLevel other) {
      return this.severity > other.severity;
   }

   public String getColorCode() {
      return switch (this) {
         case SAFE -> "&a";
         case LOW -> "&e";
         case MEDIUM -> "&6";
         case HIGH -> "&c";
         case CRITICAL -> "&4";
      };
   }
}
