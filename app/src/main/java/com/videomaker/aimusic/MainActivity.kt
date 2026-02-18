package com.videomaker.aimusic

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.videomaker.aimusic.navigation.AppNavigation
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * MainActivity — Main app content host
 *
 * Hosts AppNavigation (Navigation 3) which manages all main app screens.
 *
 * This Activity is launched by:
 * - RootViewActivity  (returning user, all setup complete)
 * - OnboardingActivity (after first-time onboarding completes)
 *
 * Onboarding is handled by OnboardingActivity (separate one-time flow).
 * MainActivity always starts at AppRoute.Home.
 *
 * Extends AppCompatActivity (not ComponentActivity) to support
 * AppCompatDelegate.setApplicationLocales() for per-app language changes.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
