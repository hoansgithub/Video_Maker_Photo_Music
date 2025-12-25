package co.alcheclub.video.maker.photo.music

import android.app.Application
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.LogLevel
import co.alcheclub.video.maker.photo.music.di.dataModule
import co.alcheclub.video.maker.photo.music.di.domainModule
import co.alcheclub.video.maker.photo.music.di.presentationModule

/**
 * Application class for Video Maker App
 *
 * Initializes ACCDI (AlcheClub Custom Dependency Injection) on startup.
 * Registered in AndroidManifest.xml with android:name=".VideoMakerApplication"
 */
class VideoMakerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize ACCDI with separated modules
        ACCDI.start(this) {
            modules(
                dataModule,         // Data sources & repositories
                domainModule,       // Use cases & business logic
                presentationModule  // ViewModels
            )
            logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NONE)
        }

        android.util.Log.d("VideoMakerApplication", "ACCDI initialized successfully")
    }

    override fun onTerminate() {
        super.onTerminate()

        // Clean up ACCDI (clears all definitions)
        ACCDI.stop()
        android.util.Log.d("VideoMakerApplication", "ACCDI stopped")
    }
}
