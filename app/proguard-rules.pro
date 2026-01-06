# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ========== DEBUGGING / CRASHLYTICS ==========
# Keep line numbers for crash reports (required for readable Crashlytics stack traces)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========== KOTLIN ==========
# Keep Kotlin metadata for reflection
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.aimusic.videoeditor.**$$serializer { *; }
-keepclassmembers class com.aimusic.videoeditor.** {
    *** Companion;
}
-keepclasseswithmembers class com.aimusic.videoeditor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========== ANDROID ROOM DATABASE ==========
# Keep Room entities - Room uses annotation processing
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * {
    <fields>;
}
-dontwarn androidx.room.paging.**

# Keep Room DAOs - interfaces with SQL queries
-keep interface * extends androidx.room.Dao {
    <methods>;
}

# Keep Room Database class and its DAOs
-keep class com.aimusic.videoeditor.data.local.database.ProjectDatabase {
    <methods>;
}
-keep class com.aimusic.videoeditor.data.local.database.dao.** {
    <methods>;
}
-keep class com.aimusic.videoeditor.data.local.database.entity.** {
    <fields>;
    <init>(...);
}

# ========== PARCELIZE ==========
# Keep Parcelable classes
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========== FIREBASE ==========
# Firebase SDKs handle their own ProGuard rules via consumerProguardFiles
# No additional rules needed

# ========== COMPOSE ==========
# Compose handles its own ProGuard rules
-keep @androidx.compose.runtime.Composable class * {
    <methods>;
}

# ========== ACCDI (Custom DI Framework) ==========
# Keep ACCDI public API and reflection-based components
-keep class co.alcheclub.lib.acccore.di.ACCDI {
    public <methods>;
}
-keep @co.alcheclub.lib.acccore.di.* class * {
    <init>(...);
}
-keepclassmembers class * {
    @co.alcheclub.lib.acccore.di.* <methods>;
    @co.alcheclub.lib.acccore.di.* <fields>;
}

# ========== MEDIA3 ==========
# Media3 provides its own ProGuard rules via consumerProguardFiles
# Keep custom effects if using reflection
-keep class com.aimusic.videoeditor.media.effects.** { *; }

# ========== NAVIGATION ==========
# Navigation Compose handles its own ProGuard rules automatically
# Type-safe navigation with Kotlin Serialization doesn't require additional rules

# ========== GENERAL ==========
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== WORKMANAGER ==========
# Keep Worker classes
-keep class com.aimusic.videoeditor.media.export.VideoExportWorker { *; }
