package com.videomaker.aimusic

import android.app.Activity
import android.app.Application
import com.facebook.ads.AdSettings
import co.alcheclub.lib.acccore.ads.adMobModule
import co.alcheclub.lib.acccore.ads.layout.NativeAdLayoutRegistry
import co.alcheclub.lib.acccore.analytics.AnalyticsCoordinator
import co.alcheclub.lib.acccore.appsflyer.appsFlyerModule
import co.alcheclub.lib.acccore.coreModuleFromDI
import co.alcheclub.lib.acccore.facebook.facebookModule
import co.alcheclub.lib.acccore.firebase.firebaseModule
import co.alcheclub.lib.acccore.monitoring.MessagingService
import co.alcheclub.lib.acccore.monitoring.NotificationConfig
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfigCoordinator
import com.google.android.ump.ConsentDebugSettings
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.core.parameter.parametersOf
import co.alcheclub.lib.acccore.di.koin.getAllSingletons
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
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
import com.videomaker.aimusic.di.adsModule
import com.videomaker.aimusic.core.ads.AdInitializer
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.notification.AppSessionTracker
import com.videomaker.aimusic.core.notification.NotificationScheduleConfigService
import com.videomaker.aimusic.core.ads.VideoMakerNativeAdLayoutProvider
import com.videomaker.aimusic.media.library.MusicSongLibrary
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import com.videomaker.aimusic.media.library.VideoTemplateLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Application class for Video Maker App
 *
 * Initializes Koin (Dependency Injection) on startup.
 * Registered in AndroidManifest.xml with android:name=".VideoMakerApplication"
 *
 * Analytics platforms initialized:
 * - Firebase Analytics (via firebaseModule)
 * - Facebook Analytics (via facebookModule)
 * - AppsFlyer (via appsFlyerModule)
 *
 * Other Firebase modules:
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
        // Track if ads have been initialized (global, survives Activity destruction)
        private val adsInitialized = AtomicBoolean(false)

        // Application-level coroutine scope for ads initialization
        @Volatile
        private var appScope: CoroutineScope? = null

        /**
         * Test device IDs for UMP (User Messaging Platform) debug mode
         *
         * To get your device ID:
         * 1. Run the app once
         * 2. Check logcat for: "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("YOUR_ID")"
         * 3. Add your device ID to this list
         *
         * IMPORTANT: Only used in debug builds (see BuildConfig.DEBUG check below)
         * COMMENTED OUT - Using real production UMP consent
         */
        // private val UMP_TEST_DEVICE_IDS = listOf<String>(
        //     "562AA2413BCC3872B79F7F30261CF7CD"  // Test device for UMP consent
        // )

        /**
         * Test device IDs for Meta Audience Network (Facebook Ads)
         *
         * To get your device ID:
         * 1. Run the app once
         * 2. Check logcat for: "When testing your app with Facebook's ad units you must specify..."
         * 3. Add your device ID to this list
         *
         * IMPORTANT: Only used in debug builds
         */
        private val FACEBOOK_TEST_DEVICE_IDS = listOf<String>(
            "0e397841-83ea-4481-a1db-9b8a116cc539"  // Facebook test device ID
        )

        /**
         * Check if ads have been initialized
         */
        fun isAdsInitialized(): Boolean = adsInitialized.get()

        /**
         * Preload native ad (non-suspend version)
         * Launches coroutine in application scope for background loading
         *
         * @param placement Placement ID to preload
         */
        fun preloadNativeAd(placement: String) {
            appScope?.launch(Dispatchers.IO) {
                try {
                    val adsLoaderService = org.koin.core.context.GlobalContext.get()
                        .get<co.alcheclub.lib.acccore.ads.loader.AdsLoaderService>()
                    adsLoaderService.loadNative(placement)
                    android.util.Log.d("VideoMakerApp", "✅ Native ad preloaded: $placement")
                } catch (e: Exception) {
                    android.util.Log.w("VideoMakerApp", "⚠️ Failed to preload native ad: $placement", e)
                }
            }
        }

        /**
         * Preload native ad (suspend version)
         * Waits for ad to load or fail before returning
         *
         * @param placement Placement ID to preload
         * @return true if ad loaded successfully, false if failed
         */
        suspend fun preloadNativeAdSuspend(placement: String): Boolean {
            return try {
                val adsLoaderService = org.koin.core.context.GlobalContext.get()
                    .get<co.alcheclub.lib.acccore.ads.loader.AdsLoaderService>()
                adsLoaderService.loadNative(placement)
                android.util.Log.d("VideoMakerApp", "✅ Native ad preloaded: $placement")
                true
            } catch (e: Exception) {
                android.util.Log.w("VideoMakerApp", "⚠️ Failed to preload native ad: $placement", e)
                false
            }
        }

        /**
         * Initialize UMP consent and AdMob SDK
         *
         * ⚠️ CRITICAL: This MUST be called BEFORE any ad loading operations!
         * UMP consent must be obtained before requesting ads (GDPR compliance).
         *
         * This should be called from the first Activity (RootActivity).
         * Uses Application-scoped coroutine to survive Activity destruction.
         *
         * @param activity Activity context required for UMP consent form
         * @param onComplete Callback when initialization is complete
         */
        fun initializeAdsIfNeeded(
            activity: Activity,
            onComplete: () -> Unit
        ) {
            android.util.Log.d("VideoMakerApp", "🔄 initializeAdsIfNeeded() called")

            // Skip if already initialized
            if (adsInitialized.get()) {
                android.util.Log.d("VideoMakerApp", "✅ Ads already initialized - skipping")
                onComplete()
                return
            }

            // Check if appScope is available
            val scope = appScope
            if (scope == null) {
                android.util.Log.e("VideoMakerApp", "❌ appScope is null - cannot initialize ads")
                // Mark as initialized to prevent blocking app
                adsInitialized.set(true)
                // CRITICAL: Call completion callback to unblock the app
                android.util.Log.d("VideoMakerApp", "📤 Calling onComplete() callback (appScope was null)")
                onComplete()
                return
            }

            android.util.Log.d("VideoMakerApp", "🔄 appScope available, getting AdMobMediator...")
            val adMobMediator = try {
                org.koin.core.context.GlobalContext.get().get<co.alcheclub.lib.acccore.ads.mediators.admob.AdMobMediator>()
            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApp", "❌ Failed to get AdMobMediator: ${e.message}", e)
                // Mark as initialized to prevent blocking app
                adsInitialized.set(true)
                // Call completion callback to unblock the app
                android.util.Log.d("VideoMakerApp", "📤 Calling onComplete() callback (AdMobMediator failed)")
                onComplete()
                return
            }

            android.util.Log.d("VideoMakerApp", "✅ AdMobMediator obtained, launching coroutine...")

            scope.launch(Dispatchers.Main) {
                try {
                    // Step 1: Wait for Remote Config to fetch/activate BEFORE UMP
                    // This ensures all Remote Config values are available when RootViewModel reads them
                    android.util.Log.d("VideoMakerApp", "⏳ Waiting for Remote Config to be ready...")
                    try {
                        withTimeout(60_000L) {  // 1 minute timeout for Remote Config
                            val remoteConfig = org.koin.core.context.GlobalContext.get()
                                .get<co.alcheclub.lib.acccore.remoteconfig.RemoteConfig>()
                            remoteConfig.fetchAndActivate()
                            android.util.Log.d("VideoMakerApp", "✅ Remote Config ready!")
                        }
                    } catch (e: TimeoutCancellationException) {
                        android.util.Log.w("VideoMakerApp", "⏱️ Remote Config fetch timed out after 60 seconds - continuing anyway")
                    } catch (e: Exception) {
                        android.util.Log.w("VideoMakerApp", "⚠️ Remote Config fetch failed: ${e.message} - continuing anyway")
                    }

                    android.util.Log.d("VideoMakerApp", "🔄 Starting UMP consent flow...")

                    // Step 2 & 3: Initialize UMP + Present consent form (1 minute timeout for BOTH)
                    // Wraps entire consent flow to prevent infinite loading if network hangs
                    try {
                        withTimeout(60_000L) {  // 1 minute for entire consent flow
                            android.util.Log.d("VideoMakerApp", "🔄 Initializing UMP consent...")
                            adMobMediator.initializeUMP(activity)

                            android.util.Log.d("VideoMakerApp", "🔄 Presenting consent form if required...")
                            adMobMediator.presentConsentFormIfRequired(activity)
                        }
                    } catch (e: TimeoutCancellationException) {
                        android.util.Log.w("VideoMakerApp", "⏱️ UMP consent flow timed out after 1 minute")
                    }

                    android.util.Log.d("VideoMakerApp", "🔄 Initializing AdMob SDK...")

                    // Step 4: Initialize AdMob SDK - PRODUCTION MODE (no test devices)
                    val config: Map<String, Any> = emptyMap()
                    // val config: Map<String, Any> = if (BuildConfig.DEBUG) {
                    //     mapOf("testDeviceIds" to UMP_TEST_DEVICE_IDS)
                    // } else {
                    //     emptyMap()
                    // }
                    adMobMediator.initialize(activity.applicationContext, config)

                    adsInitialized.set(true)
                    android.util.Log.d("VideoMakerApp", "✅ Ads initialization complete!")

                } catch (e: Exception) {
                    android.util.Log.e("VideoMakerApp", "❌ Ads initialization error: ${e.message}", e)
                    e.printStackTrace()
                    // Mark as initialized anyway to prevent blocking the app
                    adsInitialized.set(true)
                }

                // Call completion callback
                android.util.Log.d("VideoMakerApp", "📤 Calling onComplete() callback")
                onComplete()
                android.util.Log.d("VideoMakerApp", "✅ onComplete() callback executed")
            }
        }
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

        // Set application scope for static methods (used by initializeAdsIfNeeded)
        appScope = applicationScope

        // Register native ad layout provider BEFORE Koin initialization
        // This ensures native ad layouts are available when ads are initialized
        NativeAdLayoutRegistry.register(VideoMakerNativeAdLayoutProvider())
        android.util.Log.d("VideoMakerApp", "✅ Native ad layout provider registered")

        // Initialize Koin with separated modules
        // ⚠️ CRITICAL: Module ordering matters!
        // 1. firebaseModule - Provides Firebase AnalyticsPlatform & ConfigCenter
        // 2. facebookModule/appsFlyerModule - Provide additional AnalyticsPlatforms
        // 3. adMobModule - Provides AdMob SDK with UMP consent
        // 4. coreModuleFromDI - Creates coordinators that auto-discover all platforms
        // 5. Other modules - Can use coordinators
        startKoin {
            androidContext(this@VideoMakerApplication)
            modules(
                firebaseModule(manualAdImpressionTracking = true),  // ← 1. FIRST: Firebase Analytics, Crashlytics, RemoteConfig, Performance
                facebookModule(isDebug = BuildConfig.DEBUG),        // ← 2. Facebook Analytics only (no login)
                appsFlyerModule(                                    // ← 2. AppsFlyer: Another AnalyticsPlatform
                    devKey = BuildConfig.APPSFLYER_DEV_KEY,
                    isDebug = BuildConfig.DEBUG
                ),
                // AdMob: UMP consent setup - PRODUCTION MODE (no test config)
                // Test mode commented out - using real UMP consent flow
                adMobModule(
                    testDeviceIds = emptyList(),
                    debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
                    // testDeviceIds = if (BuildConfig.DEBUG) UMP_TEST_DEVICE_IDS else emptyList(),
                    // debugGeography = if (BuildConfig.DEBUG) {
                    //     ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                    // } else {
                    //     ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
                    // }
                ),
                coreModuleFromDI(                                   // ← 4. Creates coordinators (with app scope)
                    applicationScope = applicationScope,
                    enableAnalyticsTracking = !BuildConfig.DEBUG,   // Disable tracking in debug
                    enableAnalyticsLogging = BuildConfig.DEBUG      // Enable verbose logs in debug
                ),
                dataModule,         // ← 5. Data sources & repositories
                mediaModule,        // Media processing utilities
                adsModule,          // Ad placement config & helpers
                domainModule,       // Use cases & business logic
                presentationModule  // ViewModels
            )
        }

        runCatching {
            val appSessionTracker = org.koin.core.context.GlobalContext.get().get<AppSessionTracker>()
            ProcessLifecycleOwner.get().lifecycle.addObserver(appSessionTracker)
        }

        // ============================================
        // META AUDIENCE NETWORK TEST DEVICES (Debug Only)
        // ============================================
        // Configure Facebook Audience Network test devices (debug only)
        if (BuildConfig.DEBUG) {
            FACEBOOK_TEST_DEVICE_IDS.forEach { deviceId ->
                AdSettings.addTestDevice(deviceId)
                android.util.Log.d("VideoMakerApp", "📱 Meta Audience Network test device added: $deviceId")
            }
        }

        // ============================================
        // INITIALIZE AD SYSTEM (after Koin)
        // ============================================
        // Initialize ad placements SYNCHRONOUSLY (force registration before any ad loads)
        // This triggers AdInitializer.init{} which validates the setup
        val adInitializer = org.koin.core.context.GlobalContext.get().get<com.videomaker.aimusic.core.ads.AdInitializer>()
        android.util.Log.d("VideoMakerApp", "Ad system initialized: ${adInitializer.getDiagnostics()}")

        // Initialize app open ad manager (lifecycle-based)
        val appOpenAdManager = org.koin.core.context.GlobalContext.get().get<co.alcheclub.lib.acccore.ads.helpers.AppOpenAdManager>()
        appOpenAdManager.setDefaultPlacement(com.videomaker.aimusic.core.constants.AdPlacement.APP_OPEN_AOA)
        android.util.Log.d("VideoMakerApp", "✅ App open ad manager initialized")

        configureNotifications()
        primeNotificationScheduleConfigFromCache()

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

    private fun configureNotifications() {
        runCatching {
            val messaging = get<MessagingService>()
            messaging.configureNotifications(
                NotificationConfig(
                    channelId = "video_maker_notifications",
                    channelName = "Video Maker Notifications",
                    channelDescription = "Trending, reminders, and personalized updates",
                    smallIconResId = R.mipmap.ic_launcher,
                    defaultTitle = getString(R.string.app_name),
                    targetActivityClass = MainActivity::class.java
                )
            )
        }.onFailure {
            android.util.Log.e("VideoMakerApp", "Failed to configure push notifications", it)
        }
    }

    private fun primeNotificationScheduleConfigFromCache() {
        runCatching {
            val remoteConfig = getKoin().get<RemoteConfig>()
            val scheduleConfigService = getKoin().get<NotificationScheduleConfigService>()
            val rawConfig = remoteConfig.getString(RemoteConfigKeys.NOTIFICATION_SCHEDULE_CONFIG)
            scheduleConfigService.applyRawJsonForTesting(rawConfig)
        }.onFailure {
            android.util.Log.w("VideoMakerApp", "Failed to prime notification schedule config", it)
        }
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
