package com.videomaker.aimusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.videomaker.aimusic.core.analytics.Analytics
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
    private var startupInitialTab: Int by mutableIntStateOf(0)

    // Track if we're ready to show UI (ads must be initialized first on cold start)
    private var isReadyToShowUI: Boolean by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        android.util.Log.d(TAG, "onCreate() called - savedInstanceState=${if (savedInstanceState == null) "null" else "exists"}, intent=$intent")

        if (savedInstanceState == null) {
            startupInitialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0).coerceIn(0, 2)
            handleEntryIntent(intent)
        }

        // Initialize ads if not already done (e.g., cold start via widget/shortcut bypasses RootViewActivity)
        // CRITICAL: Block UI rendering until ads are initialized to prevent race condition
        if (!VideoMakerApplication.isAdsInitialized()) {
            android.util.Log.d(TAG, "❌ Ads not initialized (widget/shortcut cold start) — initializing now")
            VideoMakerApplication.initializeAdsIfNeeded(this) {
                android.util.Log.d(TAG, "✅ Ads initialized via MainActivity (widget/shortcut cold start)")
                // Now it's safe to show UI
                isReadyToShowUI = true
            }
        } else {
            android.util.Log.d(TAG, "✅ Ads already initialized - showing UI immediately")
            // Ads already initialized, safe to show UI immediately
            isReadyToShowUI = true
        }

        setContent {
            // CRITICAL: Only render UI when ads are initialized
            // This prevents NativeAdView from trying to load ads before AdMob SDK is ready
            if (isReadyToShowUI) {
                VideoMakerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            initialHomeTab = startupInitialTab,
                            pendingDeepLink = pendingDeepLink,
                            onDeepLinkConsumed = { pendingDeepLink = null },
                            navigateToUninstall = navigateToUninstall,
                            onUninstallNavigationConsumed = { navigateToUninstall = false }
                        )
                    }
                }
            } else {
                // Show blank screen while waiting for ads to initialize
                // This only happens on cold start via widget/shortcut (takes ~3-5 seconds)
                VideoMakerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // Blank screen - user will see splash screen from system
                    }
                }
            }
        }
    }

    // Called when the app is already running and a shortcut or widget is tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEntryIntent(intent)
    }

    private fun isWidgetIntent(intent: Intent): Boolean {
        return intent.action in WIDGET_ACTIONS
    }

    private fun handleEntryIntent(intent: Intent) {
        trackShortcutIfNeeded(intent)
        when {
            intent.action == ACTION_UNINSTALL_APP -> navigateToUninstall = true
            isWidgetIntent(intent) -> pendingDeepLink = intent
        }
    }

    private fun trackShortcutIfNeeded(intent: Intent) {
        val shortcutId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: return
        val shortcutType = when (shortcutId) {
            "trending_search" -> "trending_search"
            "create_video" -> "create_new_video"
            "choose_video_template" -> "choose_template"
            "uninstall_app" -> "uninstall"
            else -> return
        }
        Analytics.trackShortcutClick(shortcutType)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_UNINSTALL_APP = "com.videomaker.aimusic.action.UNINSTALL_APP"
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"

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
