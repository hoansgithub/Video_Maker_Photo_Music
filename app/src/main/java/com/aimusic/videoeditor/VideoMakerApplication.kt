package com.aimusic.videoeditor

import android.app.Application
import co.alcheclub.lib.acccore.coreModuleFromDI
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.LogLevel
import co.alcheclub.lib.acccore.firebase.firebaseModule
import com.aimusic.videoeditor.di.dataModule
import com.aimusic.videoeditor.di.domainModule
import com.aimusic.videoeditor.di.presentationModule
import com.aimusic.videoeditor.media.library.TransitionSetLibrary
import com.aimusic.videoeditor.media.library.TransitionShaderLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Application class for Video Maker App
 *
 * Initializes ACCDI (AlcheClub Custom Dependency Injection) on startup.
 * Registered in AndroidManifest.xml with android:name=".VideoMakerApplication"
 */
class VideoMakerApplication : Application() {

    // Application-scoped coroutine scope for long-running operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
                domainModule,       // Use cases & business logic
                presentationModule  // ViewModels
            )
            logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NONE)
        }

        android.util.Log.d("VideoMakerApplication", "✅ ACCDI initialized with Firebase support")

        // Initialize TransitionShaderLibrary to load shaders from assets
        TransitionShaderLibrary.init(this)
        // Pre-load transitions in background to avoid lag when settings opens
        TransitionShaderLibrary.preload()
        android.util.Log.d("VideoMakerApplication", "✅ TransitionShaderLibrary initialized and preloading")

        // Initialize TransitionSetLibrary (depends on TransitionShaderLibrary)
        TransitionSetLibrary.init(this)
        android.util.Log.d("VideoMakerApplication", "✅ TransitionSetLibrary initialized")
    }

    override fun onTerminate() {
        super.onTerminate()

        android.util.Log.d("VideoMakerApplication", "🛑 App terminating - cleaning up resources")

        // Cancel application scope (cancels all coroutines)
        applicationScope.cancel()
        android.util.Log.d("VideoMakerApplication", "✅ Application scope cancelled")

        // Clean up ACCDI (clears all definitions)
        ACCDI.stop()
        android.util.Log.d("VideoMakerApplication", "✅ ACCDI stopped")
    }
}
