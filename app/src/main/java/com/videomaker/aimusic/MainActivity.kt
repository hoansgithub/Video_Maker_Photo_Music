package com.videomaker.aimusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.videomaker.aimusic.navigation.AppNavigation
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.widget.appwidget.WidgetActions

/**
 * MainActivity — Main app content host
 *
 * Hosts AppNavigation (Navigation 3) which manages all main app screens.
 *
 * This Activity is launched by:
 * - RootViewActivity  (returning user, all setup complete)
 * - OnboardingActivity (after first-time onboarding completes)
 * - Home screen widgets (via deep-link intents)
 *
 * Onboarding is handled by OnboardingActivity (separate one-time flow).
 * MainActivity always starts at AppRoute.Home.
 *
 * Extends AppCompatActivity (not ComponentActivity) to support
 * AppCompatDelegate.setApplicationLocales() for per-app language changes.
 */
class MainActivity : AppCompatActivity() {

    // Observed by AppNavigation to trigger deep-link navigation.
    // Set only on first launch (not rotation) and on new widget intents.
    private var pendingDeepLink: Intent? by mutableStateOf(null)

    // Set to true when launched via the "Uninstall App" shortcut.
    private var navigateToUninstall: Boolean by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            when {
                intent.action == ACTION_UNINSTALL_APP -> navigateToUninstall = true
                isWidgetIntent(intent) -> pendingDeepLink = intent
            }
        }

        setContent {
            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        pendingDeepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                        navigateToUninstall = navigateToUninstall,
                        onUninstallNavigationConsumed = { navigateToUninstall = false }
                    )
                }
            }
        }
    }

    // Called when the app is already running and a shortcut or widget is tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            intent.action == ACTION_UNINSTALL_APP -> navigateToUninstall = true
            isWidgetIntent(intent) -> pendingDeepLink = intent
        }
    }

    private fun isWidgetIntent(intent: Intent): Boolean {
        return intent.action in WIDGET_ACTIONS
    }

    companion object {
        const val ACTION_UNINSTALL_APP = "com.videomaker.aimusic.action.UNINSTALL_APP"

        private val WIDGET_ACTIONS = setOf(
            WidgetActions.ACTION_OPEN_SEARCH,
            WidgetActions.ACTION_OPEN_TRENDING_TEMPLATE,
            WidgetActions.ACTION_OPEN_TRENDING_SONG,
            WidgetActions.ACTION_OPEN_TEMPLATE_DETAIL,
            WidgetActions.ACTION_OPEN_TEMPLATE_WITH_SONG,
            WidgetActions.ACTION_OPEN_SONG_PLAYER,
            WidgetActions.ACTION_CREATE_VIDEO
        )
    }
}
