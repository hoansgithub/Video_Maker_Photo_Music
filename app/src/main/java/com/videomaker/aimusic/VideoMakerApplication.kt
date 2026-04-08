package com.videomaker.aimusic

import android.app.Application
import co.alcheclub.lib.acccore.ads.adMobModule
import co.alcheclub.lib.acccore.analytics.AnalyticsCoordinator
import co.alcheclub.lib.acccore.coreModuleFromDI
import co.alcheclub.lib.acccore.firebase.firebaseModule
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfigCoordinator
import com.google.android.ump.ConsentDebugSettings
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.core.parameter.parametersOf
import co.alcheclub.lib.acccore.di.koin.getAllSingletons
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.videomaker.aimusic.di.dataModule
import com.videomaker.aimusic.di.domainModule
import com.videomaker.aimusic.di.mediaModule
import com.videomaker.aimusic.di.presentationModule
import com.videomaker.aimusic.media.library.MusicSongLibrary
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import com.videomaker.aimusic.media.library.VideoTemplateLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application class for Video Maker App
 *
 * Initializes Koin (Dependency Injection) on startup.
 * Registered in AndroidManifest.xml with android:name=".VideoMakerApplication"
 *
 * Firebase modules initialized:
 * - Firebase Analytics (via firebaseModule)
 * - Firebase Crashlytics (via firebaseModule)
 * - Firebase Remote Config (via firebaseModule)
 * - Firebase Performance (via firebaseModule)
 *
 * AdMob modules initialized:
 * - AdMob SDK with UMP consent (via adMobModule)
 */
class VideoMakerApplication : Application(), ImageLoaderFactory {

    // Application-scoped coroutine scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        /**
         * Test device IDs for UMP (User Messaging Platform) debug mode
         *
         * To get your device ID:
         * 1. Run the app once
         * 2. Check logcat for: "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("YOUR_ID")"
         * 3. Add your device ID to this list
         *
         * IMPORTANT: Only used in debug builds (see BuildConfig.DEBUG check below)
         */
        private val UMP_TEST_DEVICE_IDS = listOf<String>(
            // Add your test device IDs here
            // Example: "038F441054F01155CAC59935A542BCA0"
        )
    }

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
            .components {
                add(ImageDecoderDecoder.Factory())
            }
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

        // Initialize Koin with separated modules
        // ⚠️ CRITICAL: Module ordering matters!
        // 1. firebaseModule - Provides Firebase AnalyticsPlatform & ConfigCenter
        // 2. coreModuleFromDI - Creates coordinators that auto-discover all platforms
        // 3. adMobModule - Provides AdMob SDK with UMP consent
        // 4. Other modules - Can use coordinators
        startKoin {
            androidContext(this@VideoMakerApplication)
            modules(
                firebaseModule(manualAdImpressionTracking = true),  // Firebase Analytics, Crashlytics, RemoteConfig, Performance
                coreModuleFromDI(
                    applicationScope = applicationScope,
                    enableAnalyticsTracking = !BuildConfig.DEBUG, // Disable tracking in debug
                    enableAnalyticsLogging = BuildConfig.DEBUG    // Enable verbose logs in debug
                ),
                // AdMob: UMP consent setup
                // DEBUG_GEOGRAPHY_EEA forces consent form to show (simulates user in Europe)
                // Requires GDPR message to be published in AdMob Console → Privacy & Messaging
                adMobModule(
                    testDeviceIds = if (BuildConfig.DEBUG) UMP_TEST_DEVICE_IDS else emptyList(),
                    debugGeography = if (BuildConfig.DEBUG) {
                        ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                    } else {
                        ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
                    }
                ),
                dataModule,         // Data sources & repositories
                mediaModule,        // Media processing utilities
                domainModule,       // Use cases & business logic
                presentationModule  // ViewModels
            )
        }


        // Auto-register services with coordinators
        applicationScope.launch {
            try {
                // ============================================
                // AUTO-DISCOVER ALL SERVICES FROM DI CONTAINER
                // ============================================
                val analyticsCoordinator = get<AnalyticsCoordinator>()
                val remoteConfigCoordinator = get<RemoteConfigCoordinator>()

                // Get all singleton instances for automatic discovery
                val singletons = getKoin().getAllSingletons()

                // Automatic discovery: searches ALL singletons for service implementations
                // - AnalyticsCoordinator finds: TrackableService implementations
                // - RemoteConfigCoordinator finds: ConfigurableObject implementations
                // Safe: Uses WeakReference (no retain cycles), no memory leaks
                analyticsCoordinator.registerAll(singletons)
                remoteConfigCoordinator.registerAll(singletons)

            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApplication", "Failed to register services: ${e.message}", e)
            }
        }

        // Initialize TransitionShaderLibrary to load shaders from assets
        TransitionShaderLibrary.init(this)
        // Pre-load transitions in background to avoid lag when settings opens
        TransitionShaderLibrary.preload()

        // Initialize TransitionSetLibrary (depends on TransitionShaderLibrary)
        TransitionSetLibrary.init(this)

        // Initialize MusicSongLibrary to load songs from assets
        MusicSongLibrary.init(this)

        // Initialize VideoTemplateLibrary to load templates from assets
        VideoTemplateLibrary.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()


        // Close coordinators (fire-and-forget — OS kills process shortly after onTerminate)
        runCatching { getKoin().getOrNull<AnalyticsCoordinator>()?.close() }
        runCatching { getKoin().getOrNull<RemoteConfigCoordinator>()?.close() }

        // Release SimpleCache SQLite connection for clean shutdown
        runCatching { getKoin().getOrNull<com.videomaker.aimusic.media.audio.AudioPreviewCache>()?.simpleCache?.release() }

        // Cancel application scope (cancels all coroutines)
        applicationScope.cancel()

        // Clean up Koin (clears all definitions)
        stopKoin()
    }
}
