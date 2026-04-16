package com.videomaker.aimusic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.notification.NotificationConversionTracker
import com.videomaker.aimusic.core.notification.NotificationDeepLinkFactory
import com.videomaker.aimusic.core.notification.NotificationIntentExtras
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.navigation.AppNavigation
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.widget.appwidget.WidgetActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

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

    private val notificationScheduler: NotificationScheduler by inject()
    private val notificationConversionTracker: NotificationConversionTracker by inject()

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

        notificationScheduler.scheduleDailyBootstrap()

        if (savedInstanceState == null) {
            startupInitialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0).coerceIn(0, 2)
            handleEntryIntent(intent)
        } else {
            // Restore state after process death
            startupInitialTab = savedInstanceState.getInt(KEY_STARTUP_TAB, 0)
            navigateToUninstall = savedInstanceState.getBoolean(KEY_NAVIGATE_UNINSTALL, false)
            // Use API 33+ method or fallback to legacy method
            pendingDeepLink = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(KEY_PENDING_DEEP_LINK, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable(KEY_PENDING_DEEP_LINK)
            }
        }

        // Initialize ads if not already done (e.g., cold start via widget/shortcut bypasses RootViewActivity)
        // CRITICAL: Block UI rendering until ads are initialized to prevent race condition
        if (!VideoMakerApplication.isAdsInitialized()) {
            android.util.Log.d(TAG, "❌ Ads not initialized (widget/shortcut cold start) — initializing now")

            // ✅ FIX: Add timeout fallback (65 seconds = UMP timeout 60s + 5s buffer)
            lifecycleScope.launch {
                delay(AD_INIT_TIMEOUT_MS)
                if (!isReadyToShowUI && !isFinishing && !isDestroyed) {
                    android.util.Log.w(TAG, "⏱️ Ad initialization timeout - showing UI anyway")
                    isReadyToShowUI = true
                }
            }

            VideoMakerApplication.initializeAdsIfNeeded(this) {
                // ✅ FIX: Check Activity lifecycle before updating state (prevent memory leak)
                if (!isFinishing && !isDestroyed) {
                    android.util.Log.d(TAG, "✅ Ads initialized via MainActivity (widget/shortcut cold start)")
                    // Now it's safe to show UI
                    isReadyToShowUI = true
                } else {
                    android.util.Log.w(TAG, "⚠️ Activity destroyed - skipping UI update")
                }
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
                // ✅ FIX: Show loading indicator instead of blank screen
                // This only happens on cold start via widget/shortcut (takes ~3-5 seconds)
                VideoMakerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.loading),
                                modifier = Modifier.padding(top = 80.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ✅ FIX: Save state to handle process death
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STARTUP_TAB, startupInitialTab)
        outState.putBoolean(KEY_NAVIGATE_UNINSTALL, navigateToUninstall)
        pendingDeepLink?.let { outState.putParcelable(KEY_PENDING_DEEP_LINK, it) }
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
        trackNotificationClickIfNeeded(intent)
        when {
            intent.action == ACTION_UNINSTALL_APP -> navigateToUninstall = true
            intent.action == NotificationDeepLinkFactory.ACTION_NOTIF_TRENDING_SONG -> pendingDeepLink = intent
            intent.action == NotificationDeepLinkFactory.ACTION_NOTIF_VIRAL_TEMPLATE -> pendingDeepLink = intent
            intent.action == NotificationDeepLinkFactory.ACTION_NOTIF_MY_VIDEO -> pendingDeepLink = intent
            intent.action == NotificationDeepLinkFactory.ACTION_NOTIF_RESUME_TEMPLATE -> pendingDeepLink = intent
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

    private fun trackNotificationClickIfNeeded(intent: Intent) {
        if (intent.action !in NOTIFICATION_ACTIONS) return
        val type = intent.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_TYPE)
            ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            ?: return
        val itemId = intent.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_ITEM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val itemType = intent.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_ITEM_TYPE)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val cta = intent.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_CTA) ?: "open_notification"
        val destination = intent.getStringExtra(
            NotificationIntentExtras.EXTRA_NOTIFICATION_DEEP_LINK_DESTINATION
        ) ?: "home"
        val tappedAt = System.currentTimeMillis()
        Analytics.trackNotificationClick(
            type = type.analyticsValue,
            itemId = itemId,
            itemType = itemType,
            cta = cta,
            deepLinkDestination = destination,
            tappedAt = tappedAt
        )
        runCatching {
            notificationConversionTracker.recordTap(type = type, itemId = itemId, tappedAtMs = tappedAt)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_UNINSTALL_APP = "com.videomaker.aimusic.action.UNINSTALL_APP"
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"

        // SavedInstanceState keys
        private const val KEY_STARTUP_TAB = "startup_tab"
        private const val KEY_NAVIGATE_UNINSTALL = "navigate_uninstall"
        private const val KEY_PENDING_DEEP_LINK = "pending_deep_link"

        // Ad initialization timeout (65 seconds = UMP timeout 60s + 5s buffer)
        private const val AD_INIT_TIMEOUT_MS = 65_000L

        private val WIDGET_ACTIONS = setOf(
            WidgetActions.ACTION_OPEN_SEARCH,
            WidgetActions.ACTION_OPEN_TRENDING_TEMPLATE,
            WidgetActions.ACTION_OPEN_TRENDING_SONG,
            WidgetActions.ACTION_OPEN_TEMPLATE_DETAIL,
            WidgetActions.ACTION_OPEN_TEMPLATE_WITH_SONG,
            WidgetActions.ACTION_OPEN_SONG_PLAYER,
            WidgetActions.ACTION_CREATE_VIDEO
        )

        private val NOTIFICATION_ACTIONS = setOf(
            NotificationDeepLinkFactory.ACTION_NOTIF_TRENDING_SONG,
            NotificationDeepLinkFactory.ACTION_NOTIF_VIRAL_TEMPLATE,
            NotificationDeepLinkFactory.ACTION_NOTIF_MY_VIDEO,
            NotificationDeepLinkFactory.ACTION_NOTIF_RESUME_TEMPLATE
        )
    }
}
