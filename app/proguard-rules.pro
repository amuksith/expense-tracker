# Room entities and DAOs need to be kept for GSON/Moshi and reflection
-keep class com.amuksith.expensetracker.data.** { *; }

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
