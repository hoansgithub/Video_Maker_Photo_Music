# ========== ATTRIBUTES ==========
# Merged into a single line to avoid partial overrides.
# Signature  — required for Ktor TypeInfo / kotlinx.serialization generic type tokens
# Exceptions — keeps checked exception metadata for correct error handling
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,Exceptions
# Renames source file attribute to "SourceFile" while keeping line numbers — standard Crashlytics setup
-renamesourcefileattribute SourceFile

# ========== KOTLIN ==========
-keep class kotlin.Metadata { *; }
# Suppress noisy notes from Kotlin stdlib internals
-dontnote kotlin.**
-dontwarn kotlin.**

# ========== KOTLINX SERIALIZATION ==========
-dontnote kotlinx.serialization.AnnotationsKt
# Suppress warnings from internal serialization classes
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep generated $$serializer classes and Companion serializers for all app models
-keep,includedescriptorclasses class com.videomaker.aimusic.**$$serializer { *; }
-keepclassmembers class com.videomaker.aimusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.videomaker.aimusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========== ROOM DATABASE ==========
# Room uses KSP-generated code at compile time — keep base class for runtime
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; }
-dontwarn androidx.room.paging.**
# Keep DAO interfaces — Room generates implementations via KSP, accessed by interface type at runtime
-keep interface * extends androidx.room.Dao { <methods>; }
# Keep concrete database + DAO + entity classes
-keep class com.videomaker.aimusic.data.local.database.ProjectDatabase { <methods>; }
-keep class com.videomaker.aimusic.data.local.database.dao.** { <methods>; }
-keep class com.videomaker.aimusic.data.local.database.entity.** {
    <fields>;
    <init>(...);
}

# ========== PARCELIZE ==========
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========== WORKMANAGER ==========
# WorkManager instantiates workers via reflection using the (Context, WorkerParameters) constructor.
# Keeping { *; } is too broad — keep only what WorkManager needs.
-keep class com.videomaker.aimusic.media.export.VideoExportWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ========== KTOR ==========
# Ktor ships its own consumer ProGuard rules.
# Signature attribute (kept above) covers TypeInfo generics used by content negotiation.
-dontwarn io.ktor.**

# ========== SUPABASE ==========
# Supabase Kotlin SDK ships its own consumer ProGuard rules.
# App-side @Serializable models (SongDto, etc.) are covered by kotlinx.serialization rules above.
-dontwarn io.github.jan.supabase.**

# ========== COIL ==========
# Coil ships its own consumer ProGuard rules.
# coil-video (VideoFrameDecoder) uses MediaMetadataRetriever — no extra rules needed.
-dontwarn coil.**

# ========== FIREBASE ==========
# Firebase SDKs (Analytics, Crashlytics, RemoteConfig, Performance) ship their own
# consumer ProGuard rules via the google-services and firebase plugins.

# ========== COMPOSE ==========
# Compose ships its own consumer ProGuard rules via the BOM.
# Do NOT add @Composable keep rules here — the compiler plugin handles them.

# ========== MEDIA3 ==========
# Media3 ships its own consumer ProGuard rules.
# Keep custom GlEffect/GlShaderProgram subclasses — Media3 may load them by class name.
-keep class com.videomaker.aimusic.media.effects.** { *; }

# ========== ACCDI (AlcheClub Custom DI) ==========
# ACCDI resolves dependencies via reified inline functions (inlined at compile time).
# Keep the public API and any annotation-driven injection points.
-keep class co.alcheclub.lib.acccore.di.ACCDI { public <methods>; }
-keep @co.alcheclub.lib.acccore.di.* class * { <init>(...); }
-keepclassmembers class * {
    @co.alcheclub.lib.acccore.di.* <methods>;
    @co.alcheclub.lib.acccore.di.* <fields>;
}

# ========== GENERAL ==========
# Keep JNI entry points
-keepclasseswithmembernames class * {
    native <methods>;
}
# Keep custom View constructors for XML inflation
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
# Keep enum members accessed via reflection (e.g. valueOf in switch expressions)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
