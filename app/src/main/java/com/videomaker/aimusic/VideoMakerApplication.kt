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
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    // ✅ Guard to ensure shader preloading happens only once (not on every foreground)
    private val shaderPreloadedOnce = AtomicBoolean(false)

    // Tracks whether the app has been backgrounded at least once (warm return detection)
    // Set to true in onStop, consumed atomically in AppOpenAdManager.shouldShowCallback
    private val wasBackgrounded = AtomicBoolean(false)

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
                } catch (e: AdsLoaderException) {
                    android.util.Log.w("VideoMakerApp", "⚠️ Failed to preload native ad: $placement - ${e.message}")
                } catch (e: Exception) {
                    android.util.Log.e("VideoMakerApp", "⚠️ Unexpected error preloading native ad: $placement", e)
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
            } catch (e: AdsLoaderException) {
                android.util.Log.w("VideoMakerApp", "⚠️ Failed to preload native ad: $placement - ${e.message}")
                false
            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApp", "⚠️ Unexpected error preloading native ad: $placement", e)
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

            // ✅ CRITICAL ANR FIX: Use Dispatchers.IO instead of Dispatchers.Main
            // MobileAds.initialize() internally loads DEX files which blocks threads
            // Running on IO thread prevents main thread ANR during DEX loading
            scope.launch(Dispatchers.IO) {
                try {
                    // Step 1: Wait for Remote Config to fetch/activate BEFORE UMP
                    // This ensures all Remote Config values are available when RootViewModel reads them
                    // Remote Config network fetch should run on IO thread (already correct)
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

                    // Step 2 & 3: Initialize UMP + Present consent form
                    // ⚠️ MUST run on Main thread - requires Activity context for UI operations
                    // Wraps entire consent flow to prevent infinite loading if network hangs
                    withContext(Dispatchers.Main) {
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
                    }

                    android.util.Log.d("VideoMakerApp", "🔄 Initializing AdMob SDK...")

                    // Step 4: Initialize AdMob SDK - PRODUCTION MODE (no test devices)
                    // ✅ Runs on IO thread - this is where DEX loading happens (ANR fix)
                    // ✅ 60 second timeout to prevent infinite loading
                    try {
                        withTimeout(60_000L) {  // 1 minute timeout for AdMob SDK initialization
                            val config: Map<String, Any> = emptyMap()
                            // val config: Map<String, Any> = if (BuildConfig.DEBUG) {
                            //     mapOf("testDeviceIds" to UMP_TEST_DEVICE_IDS)
                            // } else {
                            //     emptyMap()
                            // }
                            adMobMediator.initialize(activity.applicationContext, config)
                            android.util.Log.d("VideoMakerApp", "✅ Ads initialization complete!")
                        }
                    } catch (e: TimeoutCancellationException) {
                        android.util.Log.w("VideoMakerApp", "⏱️ AdMob SDK initialization timed out after 60 seconds - continuing anyway")
                    }

                    adsInitialized.set(true)

                } catch (e: Exception) {
                    android.util.Log.e("VideoMakerApp", "❌ Ads initialization error: ${e.message}", e)
                    e.printStackTrace()
                    // Mark as initialized anyway to prevent blocking the app
                    adsInitialized.set(true)
                }

                // Call completion callback on Main thread
                withContext(Dispatchers.Main) {
                    android.util.Log.d("VideoMakerApp", "📤 Calling onComplete() callback")
                    onComplete()
                    android.util.Log.d("VideoMakerApp", "✅ onComplete() callback executed")
                }
            }
        }
    }

    /**
     * Create optimized ImageLoader for Coil
     * ✅ COLD START FIX: Reduced memory cache and disk cache for low-RAM devices
     * - 20% heap for memory cache (reduced from 25% to reduce memory pressure)
     * - 50MB disk cache for network images (reduced from 100MB for low-RAM devices)
     * - Crossfade enabled for smooth transitions
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // 20% of available heap (reduced for low-RAM devices)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB (reduced for low-RAM devices)
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

        // ✅ COLD START FIX: Defer shader preloading until app is in foreground
        // This reduces Application.onCreate() time on low-RAM devices
        // PROCESS-LIFETIME OBSERVER: Intentionally not removed (matches ProcessLifecycleOwner lifetime)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                // Only preload once (not on every foreground) - compareAndSet is thread-safe
                if (shaderPreloadedOnce.compareAndSet(false, true)) {
                    TransitionShaderLibrary.preload()
                    android.util.Log.d("VideoMakerApp", "✅ Deferred shader preloading started (first time)")
                }
            }
        })

        // AOA_MINIMIZE: track when app goes to background (warm return detection)
        // PROCESS-LIFETIME OBSERVER: intentionally not removed (matches ProcessLifecycleOwner lifetime)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                wasBackgrounded.set(true)
            }
        })

        // ============================================
        // META AUDIENCE NETWORK TEST DEVICES (Debug Only)
        // ============================================
        // Configure Facebook Audience Network test devices (debug only)
        if (BuildConfig.DEBUG) {
            FACEBOOK_TEST_DEVICE_IDS.forEach { deviceId ->
                AdSettings.addTestDevice(deviceId)
                android.util.Log.d("VideoMakerApp", "📱 Meta Audience Network test device added: $deviceId")
            }

            // ============================================
            // DEBUG: Force clear API cache on app start (for VPN testing)
            // ============================================
            applicationScope.launch {
                runCatching {
                    val apiCacheManager = get<com.videomaker.aimusic.core.data.local.ApiCacheManager>()
                    apiCacheManager.clearTemplateCache()
                    apiCacheManager.clearSongCache()
                    android.util.Log.w("VideoMakerApp", "🧹 [DEBUG] API cache force-cleared on app start")
                }.onFailure { e ->
                    android.util.Log.e("VideoMakerApp", "❌ Failed to clear cache: ${e.message}")
                }
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
        appOpenAdManager.setForegroundPlacement(com.videomaker.aimusic.core.constants.AdPlacement.APP_OPEN_AOA)
        appOpenAdManager.setShouldShowCallback { wasBackgrounded.getAndSet(false) }
        android.util.Log.d("VideoMakerApp", "✅ App open ad manager initialized (minimize mode)")

        configureNotifications()
        primeNotificationScheduleConfigFromCache()

        // ============================================
        // CENTRALIZED SERVICE REGISTRATION
        // ============================================
        // ✅ COLD START OPTIMIZATION: CoreModule handles platform initialization automatically
        // Events tracked before init (e.g., SPLASH_SHOW) are buffered and flushed automatically
        // Performance improvement: 50-150ms (fast devices) to 100-300ms (ARM Cortex-A53)
        // Platform initialization: ACCCore CoreModule (automatic async)
        // Service registration: Application (explicit centralized list)

        // Create coordinators synchronously (lightweight, no blocking)
        // Platform initialization happens automatically in CoreModule
        val analyticsCoordinator = get<AnalyticsCoordinator>()
        val remoteConfigCoordinator = get<RemoteConfigCoordinator>()

        // CRITICAL: Fetch Remote Config BEFORE registering services
        // This ensures all services receive initial config on first install
        // Bug fix learned from Drama app production issues
        applicationScope.launch {
            try {
                android.util.Log.d("VideoMakerApp", "🚀 Step 1: Fetching Remote Config...")

                // Fetch and activate Remote Config (with timeout)
                try {
                    kotlinx.coroutines.withTimeout(15000L) {  // 15s timeout (same as RootViewModel)
                        val remoteConfig = get<co.alcheclub.lib.acccore.remoteconfig.RemoteConfig>()
                        val result = remoteConfig.fetchAndActivate()

                        when {
                            result.isSuccess && result.getOrNull() == true -> {
                                android.util.Log.d("VideoMakerApp", "✅ Remote Config fetched and activated")
                            }
                            result.isSuccess && result.getOrNull() == false -> {
                                android.util.Log.w("VideoMakerApp", "⚠️ Remote Config returned false (using cached values)")
                            }
                            result.isFailure -> {
                                android.util.Log.w("VideoMakerApp", "⚠️ Remote Config fetch failed: ${result.exceptionOrNull()?.message}")
                            }
                            else -> {
                                android.util.Log.w("VideoMakerApp", "⚠️ Remote Config returned null")
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.w("VideoMakerApp", "⏱️ Remote Config fetch timeout after 15s - continuing with defaults")
                } catch (e: Exception) {
                    android.util.Log.e("VideoMakerApp", "❌ Remote Config fetch error: ${e.message}", e)
                }

                android.util.Log.d("VideoMakerApp", "🚀 Step 2: Registering services...")

                // CENTRALIZED REGISTRATION: All ConfigurableObjects in one place
                // Now services will receive the fetched config via firstOrNull()
                val configurableObjects = listOf(
                    // App ConfigurableObjects
                    get<com.videomaker.aimusic.core.ads.AdPlacementConfigService>(),
                    get<com.videomaker.aimusic.core.language.LanguageConfigService>(),
                    get<com.videomaker.aimusic.core.notification.NotificationScheduleConfigService>(),
                    // ACCCore ConfigurableObjects
                    get<co.alcheclub.lib.acccore.ads.loader.PlacementConfigService>()
                )

                android.util.Log.d("VideoMakerApp", "✅ Registering ${configurableObjects.size} ConfigurableObject(s):")
                configurableObjects.forEach { obj ->
                    android.util.Log.d("VideoMakerApp", "   - ${obj::class.simpleName}")
                }

                // Register all ConfigurableObjects with RemoteConfigCoordinator
                remoteConfigCoordinator.registerAll(configurableObjects)

                // Register all TrackableServices with AnalyticsCoordinator
                val koin = org.koin.core.context.GlobalContext.get()
                val singletons = koin.getAllSingletons()
                analyticsCoordinator.registerAll(singletons)
                android.util.Log.d("VideoMakerApp", "✅ Registered ${singletons.size} singleton(s) with AnalyticsCoordinator")

                android.util.Log.d("VideoMakerApp", "✅ Centralized registration complete")

                // Track successful registration for monitoring
                analyticsCoordinator.track(
                    co.alcheclub.lib.acccore.analytics.AnalyticsEvent(
                        name = "remote_config_registration_complete",
                        params = mapOf(
                            "configurable_objects" to configurableObjects.size.toString(),
                            "total_singletons" to singletons.size.toString()
                        )
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("VideoMakerApp", "❌ Centralized registration failed: ${e.message}", e)

                // Track failure for production monitoring
                analyticsCoordinator.track(
                    co.alcheclub.lib.acccore.analytics.AnalyticsEvent(
                        name = "remote_config_registration_failed",
                        params = mapOf(
                            "error" to (e.message ?: "Unknown error"),
                            "error_type" to e.javaClass.simpleName
                        )
                    )
                )
            }
        }

        // Initialize TransitionShaderLibrary to load shaders from assets
        TransitionShaderLibrary.init(this)
        // ✅ COLD START FIX: Preloading deferred to ProcessLifecycleOwner.onStart()
        // (see lifecycle observer registration above)

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
