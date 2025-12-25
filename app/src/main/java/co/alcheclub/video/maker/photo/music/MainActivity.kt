package co.alcheclub.video.maker.photo.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.alcheclub.lib.acccore.di.viewModel
import co.alcheclub.video.maker.photo.music.modules.root.RootViewModel
import co.alcheclub.video.maker.photo.music.navigation.AppNavigation
import co.alcheclub.video.maker.photo.music.ui.theme.VideoMakerTheme

/**
 * MainActivity - Single Entry Point for Single-Activity Architecture
 *
 * This is the ONLY Activity in the app.
 * All screens are Composables managed by a single NavHost.
 *
 * Responsibilities:
 * - Install Android 12+ splash screen
 * - Initialize RootViewModel with Activity context
 * - Host AppNavigation with all app routes
 *
 * Flow:
 * 1. User taps app icon
 * 2. System shows splash screen (installSplashScreen())
 * 3. RootViewModel checks onboarding status
 * 4. NavHost navigates to appropriate screen:
 *    - Onboarding (first-time user)
 *    - Home (returning user)
 */
class MainActivity : ComponentActivity() {

    // Root ViewModel injected via ACCDI
    private val rootViewModel: RootViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Initialize app (onboarding check)
        rootViewModel.initializeApp(this)

        // Update Activity reference
        rootViewModel.updateActivityRef(this)

        setContent {
            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        rootViewModel = rootViewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update Activity reference in case of recreation
        rootViewModel.updateActivityRef(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear Activity reference to prevent memory leak
        if (isFinishing) {
            rootViewModel.clearActivityRef()
        }
    }
}
