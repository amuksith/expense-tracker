# Keep app data models, Room entities/DAOs, and Moshi JSON models
-keep class com.amuksith.expensetracker.data.** { *; }

-dontobfuscate

# Keep Navigation 3 routes, ViewModels, and Kotlinx Serialization classes across all app packages
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.amuksith.expensetracker.**$$serializer { *; }
-keepclassmembers class com.amuksith.expensetracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.amuksith.expensetracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Moshi generated JsonAdapters (for JSON backup/restore)
-keepclassmembers class * implements com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep AboutLibraries data models
-keep class com.mikepenz.aboutlibraries.entity.** { *; }
-keep class com.mikepenz.aboutlibraries.** { *; }