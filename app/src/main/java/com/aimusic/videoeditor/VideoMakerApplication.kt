package com.aimusic.videoeditor

import android.app.Application
import co.alcheclub.lib.acccore.analytics.AnalyticsCoordinator
import co.alcheclub.lib.acccore.coreModuleFromDI
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.LogLevel
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.firebase.firebaseModule
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfigCoordinator
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.aimusic.videoeditor.di.dataModule
import com.aimusic.videoeditor.di.domainModule
import com.aimusic.videoeditor.di.mediaModule
import com.aimusic.videoeditor.di.presentationModule
import com.aimusic.videoeditor.media.library.MusicSongLibrary
import com.aimusic.videoeditor.media.library.TransitionSetLibrary
import com.aimusic.videoeditor.media.library.TransitionShaderLibrary
import com.aimusic.videoeditor.media.library.VideoTemplateLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Application class for Video Maker App
 *
 * Initializes ACCDI (AlcheClub Custom Dependency Injection) on startup.
 * Registered in AndroidManifest.xml with android:name=".VideoMakerApplication"
 *
 * Firebase modules initialized:
 * - Firebase Analytics (via firebaseModule)
 * - Firebase Crashlytics (via firebaseModule)
 * - Firebase Remote Config (via firebaseModule)
 * - Firebase Performance (via firebaseModule)
 */
class VideoMakerApplication : Application(), ImageLoaderFactory {

    // Application-scoped coroutine scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Create optimized ImageLoader for Coil
     * - 25% heap for memory cache (balanced for image-heavy gallery)
     * - 100MB disk cache for network images
     * - Crossfade enabled for smooth transitions
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available heap
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .crossfade(true)
            .crossfade(200)
            .respectCacheHeaders(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize ACCDI with separated modules
        // ⚠️ CRITICAL: Module ordering matters!
        // 1. firebaseModule - Provides Firebase AnalyticsPlatform & ConfigCenter
        // 2. coreModuleFromDI - Creates coordinators that auto-discover all platforms
        // 3. Other modules - Can use coordinators
        ACCDI.start(this) {
            modules(
                firebaseModule,                                   // Firebase Analytics, Crashlytics, RemoteConfig, Performance
                coreModuleFromDI(
                    applicationScope = applicationScope,
                    enableAnalyticsTracking = !BuildConfig.DEBUG, // Disable tracking in debug
                    enableAnalyticsLogging = BuildConfig.DEBUG    // Enable verbose logs in debug
                ),
                dataModule,         // Data sources & repositories
                mediaModule,        // Media processing utilities
                domainModule,       // Use cases & business logic
                presentationModule  // ViewModels
            )
            logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NONE)
        }

        android.util.Log.d("VideoMakerApplication", "ACCDI initialized with Firebase support")

        // Auto-register services with coordinators
        applicationScope.launch {
            try {
                // ============================================
                // AUTO-DISCOVER ALL SERVICES FROM DI CONTAINER
                // ============================================
                val analyticsCoordinator = ACCDI.get<AnalyticsCoordinator>()
                val remoteConfigCoordinator = ACCDI.get<RemoteConfigCoordinator>()

                // Get all singleton instances for automatic discovery
                val singletons = ACCDI.getAllSingletons()

                // Automatic discovery: searches ALL singletons for service implementations
                // - AnalyticsCoordinator finds: TrackableService implementations
                // - RemoteConfigCoordinator finds: ConfigurableObject implementations
                // Safe: Uses WeakReference (no retain cycles), no memory leaks
                analyticsCoordinator.registerAll(singletons)
                remoteConfigCoordinator.registerAll(singletons)

                android.util.Log.d("VideoMakerApplication", "Auto-registered all TrackableServices and ConfigurableObjects from DI container")
            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApplication", "Failed to register services: ${e.message}", e)
            }
        }

        // Initialize TransitionShaderLibrary to load shaders from assets
        TransitionShaderLibrary.init(this)
        // Pre-load transitions in background to avoid lag when settings opens
        TransitionShaderLibrary.preload()
        android.util.Log.d("VideoMakerApplication", "TransitionShaderLibrary initialized and preloading")

        // Initialize TransitionSetLibrary (depends on TransitionShaderLibrary)
        TransitionSetLibrary.init(this)
        android.util.Log.d("VideoMakerApplication", "TransitionSetLibrary initialized")

        // Initialize MusicSongLibrary to load songs from assets
        MusicSongLibrary.init(this)
        android.util.Log.d("VideoMakerApplication", "MusicSongLibrary initialized")

        // Initialize VideoTemplateLibrary to load templates from assets
        VideoTemplateLibrary.init(this)
        android.util.Log.d("VideoMakerApplication", "VideoTemplateLibrary initialized")
    }

    override fun onTerminate() {
        super.onTerminate()

        android.util.Log.d("VideoMakerApplication", "App terminating - cleaning up resources")

        // Close coordinators to cancel all jobs
        runBlocking {
            try {
                ACCDI.getOrNull<AnalyticsCoordinator>()?.close()
                ACCDI.getOrNull<RemoteConfigCoordinator>()?.close()
                android.util.Log.d("VideoMakerApplication", "Coordinators closed")
            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApplication", "Error closing coordinators: ${e.message}")
            }
        }

        // Cancel application scope (cancels all coroutines)
        applicationScope.cancel()
        android.util.Log.d("VideoMakerApplication", "Application scope cancelled")

        // Clean up ACCDI (clears all definitions)
        ACCDI.stop()
        android.util.Log.d("VideoMakerApplication", "ACCDI stopped")
    }
}
