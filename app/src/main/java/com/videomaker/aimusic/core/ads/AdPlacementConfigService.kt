package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.PlacementConfigService
import co.alcheclub.lib.acccore.ads.loader.AdPlacementConfig as CorePlacementConfig
import co.alcheclub.lib.acccore.ads.loader.AdUnitConfig as CoreAdUnitConfig
import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.VideoQuality
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ad Placement Configuration Service
 *
 * Manages ad placement registration for Video Maker app.
 * Implements ConfigurableObject for automatic Remote Config updates.
 * Registration happens centrally in VideoMakerApplication.kt.
 *
 * ## Architecture:
 * - **Placement Registration**: Registers all placements with PlacementConfigService
 * - **Remote Config Integration**: Explicitly registered in VideoMakerApplication
 *
 * ## Initialization:
 * - Created as singleton in DI (adsModule)
 * - Explicitly registered in VideoMakerApplication.onCreate()
 * - init{} block runs on first access
 * - Registers all placements with local fallback configs
 *
 * ## Registration Strategy:
 * - **Centralized**: Explicit list in VideoMakerApplication.kt
 * - **Clear**: Makes dependencies visible and prevents forgetting services
 * - **Monitored**: Analytics events track registration success/failure
 *
 * ## Remote Config Keys:
 * - `ad_interstitial_interval_seconds`: Global interstitial frequency cap (default 60s)
 *
 * ## Usage:
 * ```kotlin
 * val adConfig = ACCDI.get<AdPlacementConfigService>()
 * ```
 *
 * @param placementConfigService ACCCore-Ads service for per-placement configuration
 */
class AdPlacementConfigService(
    private val placementConfigService: PlacementConfigService
) : ConfigurableObject {

    companion object {
        private const val TAG = "AdPlacementConfig"

        /**
         * Default interstitial interval in seconds
         * Used when Remote Config key is not available
         */
        private const val DEFAULT_INTERSTITIAL_INTERVAL = 10

        /**
         * Remote Config key for global interstitial interval
         */
        private const val KEY_INTERSTITIAL_INTERVAL = "ad_interstitial_interval_seconds"

        private const val KEY_QUALITY_720P_AD_TYPE = "ad_quality_720p_type"
        private const val KEY_QUALITY_1080P_AD_TYPE = "ad_quality_1080p_type"
        private const val DEFAULT_QUALITY_AD_TYPE = "rewarded"

    }

    /**
     * Track number of successfully registered placements
     * Thread-safe counter that gets incremented each time registerPlacement() succeeds
     */
    private val registrationCount = AtomicInteger(0)

    /**
     * Global interstitial ad interval in seconds
     * Thread-safe property that can be updated from Remote Config
     * Default: 60 seconds between interstitial ads
     */
    var interstitialIntervalSeconds: Int = DEFAULT_INTERSTITIAL_INTERVAL
        private set  // Only update() can modify

    private var quality720pAdType: String = DEFAULT_QUALITY_AD_TYPE
    private var quality1080pAdType: String = DEFAULT_QUALITY_AD_TYPE
    init {
        try {
            registerAllPlacements()
            val count = registrationCount.get()
            Log.d(TAG, "✅ Initialized with $count ad placements")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Critical: Failed to initialize placements", e)
            throw e  // Re-throw - app cannot serve ads without this
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "❌ Invalid placement configuration", e)
            throw e  // Re-throw - configuration is broken
        }
    }

    /**
     * Register all ad placements with local fallback configurations
     *
     * PlacementConfigService fetches configs from Remote Config using exact placement IDs.
     * Local configs are used as fallback when Remote Config is unavailable.
     *
     * Waterfall Priority:
     * - Multiple ad unit IDs are tried in order (first to last)
     * - If first unit fails to load, next unit is attempted automatically
     * - Remote Config can override both priority order and enable/disable individual units
     */
    private fun registerAllPlacements() {
        Log.d(TAG, "🔨 Starting placement registration...")

        // ============================================
        // APP OPEN ADS
        // ============================================

        // App Open Ad - Background layer (shown when app comes to foreground)
        // Triggered on onStop/onStart - full app switches (home button, switch app)
        // Managed by AppOpenAdManager lifecycle observer
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.APP_OPEN_AOA,
            type = "appOpen",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4550155106",  // Primary
                "ca-app-pub-7121075950716954/7178364047"   // Secondary
            ),
            enabled = true
        )

        // App Open Ad - Foreground layer (shown when app loses/regains focus)
        // Triggered on onPause/onResume - quick interactions (notification, Recent Apps)
        // Priority system in onResume: Background ad (if available) > Foreground ad (fallback)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.APP_OPEN_FOREGROUND,
            type = "appOpen",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6000852048",  // Primary
                "ca-app-pub-7121075950716954/9757555227"   // Secondary
            ),
            enabled = true
        )

        // ============================================
        // INTERSTITIAL ADS
        // ============================================

        // Splash screen interstitial - first app launch only
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_SPLASH,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9920077454",  // Primary
                "ca-app-pub-7121075950716954/1830520200"   // Secondary
            ),
            enabled = true
        )

        // Open app interstitial - second launch onwards (separate ad unit for independent tracking)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_OPEN_APP,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4748771125",  // Inter_high_splash_reopen (Primary)
                "ca-app-pub-7121075950716954/2676684702"   // Inter_all_splash_reopen (Secondary)
            ),
            enabled = true
        )

        // Template previewer back button interstitial (shown when user presses back)
        // Preloaded at screen launch, non-blocking if not ready
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4441122705",  // Primary
                "ca-app-pub-7121075950716954/7545420894"   // Secondary
            ),
            enabled = true
        )

        // Template previewer scroll interstitial (shown while browsing templates)
        // Frequency controlled by ad_interstitial_interval_seconds (default 60s)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_SCROLL,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8224075141",  // Primary
                "ca-app-pub-7121075950716954/3051639510"   // Secondary
            ),
            enabled = true
        )

        // Editor back button interstitial (shown when exiting editor screen)
        // Preloaded on screen entry, non-blocking if not ready
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_EDITOR_BACK,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9739651039",  // Primary
                "ca-app-pub-7121075950716954/8170207041"   // Secondary
            ),
            enabled = true
        )

        // Template grid tap interstitial (shown when tapping template in gallery/home)
        // Frequency controlled by ad_interstitial_interval_seconds
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8538250004",  // Primary
                "ca-app-pub-7121075950716954/8361778735"   // Secondary
            ),
            enabled = true
        )

        // Library project tap interstitial (shown when tapping a created project in Library tab)
        // Frequency controlled by ad_interstitial_interval_seconds
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_LIBRARY_PROJECT_TAP,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9761424655",  // Primary
                "ca-app-pub-7121075950716954/7135261314"   // Secondary
            ),
            enabled = true
        )

        // Uninstall template tap interstitial (shown when tapping template on uninstall screen)
        // Preloaded at screen launch, shown every time (bypasses frequency cap)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_UNINSTALL_TEMPLATE_TAP,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1879065208",  // Primary
                "ca-app-pub-7121075950716954/6956966219"   // Secondary
            ),
            enabled = true
        )

        // Export result exit interstitial (shown when user exits export result screen)
        // Preloaded when export completes, non-blocking if not ready
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_EXPORT_RESULT_EXIT,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/5408312336",  // Primary
                "ca-app-pub-7121075950716954/5188820489"   // Secondary
            ),
            enabled = true
        )

        // Asset picker exit interstitial (shown when user closes image selector)
        // Preloaded at screen launch, non-blocking if not ready
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_ASSET_PICKER_EXIT,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2782148994",  // Primary
                "ca-app-pub-7121075950716954/7537679430"   // Secondary
            ),
            enabled = true
        )

        // ============================================
        // BANNER ADS
        // ============================================

        // Home screen banner (shown at bottom of main screen, below tab bar)
        // Adaptive sizing, cached for reuse across navigation
        // Waterfall: Primary unit only
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.BANNER_HOME,
            type = "banner",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6103658351"  // Primary
            ),
            enabled = true
        )

        // Template previewer banner (shown at bottom of template preview screen)
        // Same configuration as home banner, different placement for tracking
        // Waterfall: Primary unit only
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.BANNER_TEMPLATE_PREVIEWER,
            type = "banner",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6103658351"  // Primary (same as home)
            ),
            enabled = true
        )

        // Asset picker banner (shown at bottom of image selector screen)
        // Temporary: shares home banner unit until dedicated unit is assigned
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.BANNER_ASSET_PICKER,
            type = "banner",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6103658351"  // Temporary (same as home)
            ),
            enabled = true
        )

        // Editor banner (shown at bottom of editor screen)
        // Temporary: shares home banner unit until dedicated unit is assigned
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.BANNER_EDITOR,
            type = "banner",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6103658351"  // Temporary (same as home)
            ),
            enabled = true
        )

        // Export/result banner (shown at bottom of export screen, all states)
        // Temporary: shares home banner unit until dedicated unit is assigned
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.BANNER_EXPORT,
            type = "banner",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6103658351"  // Temporary (same as home)
            ),
            enabled = true
        )

        // ============================================
        // NATIVE ADS
        // ============================================

        // Onboarding language selector native ad (shown at bottom of language screen)
        // High-engagement placement for first-time users
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_LANGUAGE,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3045501749",  // Primary
                "ca-app-pub-7121075950716954/5041109698"   // Secondary
            ),
            enabled = true
        )

        // Onboarding language selector alternative native ad (A/B test variant)
        // Loaded in parallel with primary placement, first to load wins
        // Layout: native_big_bait (same layout, different ad units)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_LANGUAGE_ALT,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6080295985",  // Primary
                "ca-app-pub-7121075950716954/8931032052"   // Secondary
            ),
            enabled = true
        )

        // Onboarding feature selector native ad (shown at bottom of feature selection screen)
        // High-engagement placement for users selecting video maker features
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3797747576",  // Primary
                "ca-app-pub-7121075950716954/9064413900"   // Secondary
            ),
            enabled = true
        )

        // Onboarding feature selector alternative native ad (A/B test variant)
        // Loaded in parallel with primary placement, first to load wins
        // Layout: native_big_bait (same layout, different ad units)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3059380976",  // Primary
                "ca-app-pub-7121075950716954/5685544316"   // Secondary
            ),
            enabled = true
        )

        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_SELECT_MUSIC,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3016683795",  // NA_high_select_music
                "ca-app-pub-7121075950716954/8624233693"   // NA_all_select_music
            ),
            enabled = true
        )

        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_SELECT_TPT,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3543297405",  // NA_high_select_tpt
                "ca-app-pub-7121075950716954/7166847649"   // NA_all_select_tpt
            ),
            enabled = true
        )

        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PERSONALIZING,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8979456910",  // NA_high_personalizing
                "ca-app-pub-7121075950716954/8646238825"   // NA_all_personalizing
            ),
            enabled = true
        )

        // Onboarding page 1 native ad (shown at bottom of first welcome page)
        // First page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE1,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1923991765",  // Primary
                "ca-app-pub-7121075950716954/1559057460"   // Secondary
            ),
            enabled = true
        )

        // Onboarding page 2 native ad (shown at bottom of second welcome page)
        // Second page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE2,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6984746755",  // Primary
                "ca-app-pub-7121075950716954/9610910095"   // Secondary
            ),
            enabled = true
        )

        // Onboarding page 3 native ad (shown at bottom of third welcome page)
        // Third (final) page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE3,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/5340492594",  // Primary
                "ca-app-pub-7121075950716954/6891275199"   // Secondary
            ),
            enabled = true
        )

        // Fullscreen native ad shown between onboarding pages
        // Displayed as full-screen overlay with close button
        // Layout: native_full_screen_bait (fullscreen with prominent CTA button)
        // Remote Config extras: close_delay, inject_after
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_FULLSCREEN,
            layoutName = "native_full_screen_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8980354700",  // Primary
                "ca-app-pub-7121075950716954/1293436372"   // Secondary
            ),
            enabled = true
        )

        // Search in-feed native ad (template search + song search)
        // Displayed at top of search results on all search states
        // Layout: native_small_row (horizontal row matching search list items)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_SEARCH_INFEED,
            layoutName = "native_small_row",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/7251804638",  // Primary
                "ca-app-pub-7121075950716954/1185923881"   // Secondary
            ),
            enabled = true
        )

        // Uninstall screen native ad (shown at bottom before uninstalling)
        // Final engagement point before user uninstalls the app
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_UNINSTALL_BOTTOM,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/7123126368",  // Primary
                "ca-app-pub-7121075950716954/7176617161"   // Secondary
            ),
            enabled = true
        )

        // Widget screen native ad (shown below widget content)
        // High-engagement placement for users exploring widgets
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_WIDGET_BOTTOM,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/5938722965",  // Primary
                "ca-app-pub-7121075950716954/1171584233"   // Secondary
            ),
            enabled = true
        )

        // Welcome Back screen native ad (shown when reopening app, session >= 2)
        // High-engagement placement for returning users
        // Layout: native_big_bait_reversed
        // Waterfall: Primary unit -> Secondary unit (temporarily reusing widget units)
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_WELCOME_BACK,
            layoutName = "native_big_bait_reversed",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9525024469",  // Primary
                "ca-app-pub-7121075950716954/1552989505"   // Secondary
            ),
            enabled = true
        )

        // Template previewer loading state native ad (shown during loading)
        // Displayed at bottom with "Building Your Feed" message
        // Timing: 2s display (preloaded at home launch, video buffers in parallel)
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2672745463",  // Primary
                "ca-app-pub-7121075950716954/5298908808"   // Secondary
            ),
            enabled = true
        )

        // Template ratio sheet native ad (shown in bottom sheet)
        // High-engagement placement when selecting ratio to create template
        // Layout: native_small_bait (small vertical layout to fit in bottom sheet)
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_TEMPLATE_RATIO_SHEET,
            layoutName = "native_small_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/7313933710",  // Primary
                "ca-app-pub-7121075950716954/3818832831"   // Secondary
            ),
            enabled = true
        )

        // Projects grid native ad (shown as item in staggered projects list)
        // In-feed placement that blends with project cards
        // Only loads when at least 1 project exists
        // Layout: native_project_card (matches ProjectCard layout with media + info)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_LIBRARY_CREATED_VIDEO,
            layoutName = "native_project_card",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4667301062",  // Pro_NA_high_liked content
                "ca-app-pub-7121075950716954/6996887879"   // Pro_NA_all_liked content
            ),
            enabled = true
        )

        // Gallery templates grid native ad (shown as item in staggered templates grid)
        // In-feed placement that blends with template cards
        // Position: 4th position (index 3), or last if < 3 items
        // Persists through vibe chip tag filtering
        // Layout: native_project_card (9:16 portrait, matches template cards)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_GALLERY_GRID,
            layoutName = "native_project_card",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1251475281",  // Primary
                "ca-app-pub-7121075950716954/7733500455"   // Secondary
            ),
            enabled = true
        )

        // Featured templates carousel native ad (shown at 2nd position)
        // Layout: native_small_clean (matches 16:9 carousel ratio)
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_GALLERY_HOT_TPT,
            layoutName = "native_small_clean",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1840370904",  // Primary
                "ca-app-pub-7121075950716954/5831815710"   // Secondary
            ),
            enabled = true
        )

        // Songs station native ad (shown as item in station songs list)
        // In-feed placement that blends with song list items
        // Position: 4th position (index 3), or last if < 3 items
        // Persists through genre chip tag filtering
        // Layout: native_small_row (horizontal row, matches song items)
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_SONGS_STATION,
            layoutName = "native_small_row",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2115862172",  // Primary
                "ca-app-pub-7121075950716954/3794255449"   // Secondary
            ),
            enabled = true
        )

        // Trending template popup native ad (bottom of "Don't miss it" popup, Gallery tab)
        // TEMP units borrowed from NATIVE_ONBOARDING_SELECT_TPT: onboarding-only,
        // never loads concurrently with Gallery browsing → avoids ad-unit contention.
        // TODO: replace with dedicated AdMob native units.
        // Layout: native_big_bait (matches remote_config_defaults.xml)
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_POPUP_TRENDING_TEMPLATE,
            layoutName = "native_small_row",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1192669490",  // Primary (NA_high_lib)
                "ca-app-pub-7121075950716954/7566506155"   // Secondary (NA_all_lib)
            ),
            enabled = true
        )

        // Trending song popup native ad (bottom of "Don't miss it" popup, Songs tab)
        // TEMP units borrowed from NATIVE_ONBOARDING_SELECT_MUSIC: onboarding-only,
        // never loads concurrently with Songs browsing → avoids ad-unit contention.
        // TODO: replace with dedicated AdMob native units.
        // Layout: native_big_bait (matches remote_config_defaults.xml)
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_POPUP_TRENDING_SONG,
            layoutName = "native_small_row",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1192669490",  // NA_high_select_music
                "ca-app-pub-7121075950716954/7566506155"   // NA_all_select_music
            ),
            enabled = true
        )

        // Home Banner Native Ad (replaces standard banner)
        // Shown at the bottom of the Home screen, shared across tabs
        // Layout: native_small_row (horizontal row) to fit the banner dimensions
        // Waterfall: Primary unit -> Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_HOME_BANNER,
            layoutName = "native_small_row",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1192669490",  // Primary (NA_high_lib)
                "ca-app-pub-7121075950716954/7566506155"   // Secondary (NA_all_lib)
            ),
            enabled = true
        )

        // Export generating native ad (shown during video export)
        // Displayed at bottom with "Generating" text and progress
        // 10s timeout + 2s display if ad loads
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_EXPORT_GENERATING,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9046582121",  // Primary
                "ca-app-pub-7121075950716954/1359663791"   // Secondary
            ),
            enabled = true
        )

        // ============================================
        // REWARDED ADS
        // ============================================

        // Rewarded interstitial for video download (shown when user clicks download button)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_INTER_DOWNLOAD_VIDEO,
            type = "rewardedInterstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4370724539",  // Pro_RI_high_download
                "ca-app-pub-7121075950716954/6805316184"   // Pro_RI_all_download
            ),
            extras = mapOf(
                "gating_enabled" to BuildConfig.DEBUG,                      // true in debug, false in release
                "interval_seconds" to if (BuildConfig.DEBUG) 10L else 60L, // 10s in debug for quick testing
                "cap" to if (BuildConfig.DEBUG) 3 else 5,
                "countdown_seconds" to 5
            ),
            enabled = true
        )

        // Rewarded ad for watermark removal (shown when user taps watermark overlay)
        // User must watch full ad to earn reward (watermark removal)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_REMOVE_WATERMARK,
            type = "reward",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1902209378",  // Primary
                "ca-app-pub-7121075950716954/5322579631"   // Secondary
            ),
            enabled = true
        )

        // Rewarded ad for quality unlock (shown when user taps Done with 720p/1080p selected)
        // User must watch full ad to unlock quality for current editor session only
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_UNLOCK_QUALITY,
            type = "reward",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6550003612",  // Primary
                "ca-app-pub-7121075950716954/6827232991"   // Secondary
            ),
            enabled = true
        )

        // Rewarded ad for effect set unlock (shown when user clicks locked effect set)
        // User must watch full ad to earn reward (effect set unlock - stored locally)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
            type = "reward",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2285352752",  // Primary
                "ca-app-pub-7121075950716954/3079559673"   // Secondary
            ),
            enabled = true
        )

        // Rewarded ad for template unlock (shown when user selects ratio for locked template)
        // User must watch full ad to earn reward (template unlock - stored locally)
        // Waterfall: Primary unit → Secondary unit (same as effect set unlock)
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_UNLOCK_TEMPLATE,
            type = "reward",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2285352752",  // Primary (shared with effect set)
                "ca-app-pub-7121075950716954/3079559673"   // Secondary (shared with effect set)
            ),
            enabled = true
        )

        // Reward: Unlock Song (uses same ad units as unlock template)
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.REWARD_UNLOCK_SONG,
            type = "reward",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2285352752",  // Primary (same as template/effect)
                "ca-app-pub-7121075950716954/3079559673"   // Secondary
            ),
            enabled = true
        )

        // ============================================
        // INTERSTITIAL — QUALITY UNLOCK
        // ============================================

        // Interstitial for quality unlock (shown instead of RW when Remote Config = "interstitial")
        // Placeholder unit IDs — replace via Firebase after AdMob assigns dedicated units
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_UNLOCK_QUALITY,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2782148994",  // Placeholder (borrowed from ASSET_PICKER_EXIT)
                "ca-app-pub-7121075950716954/7537679430"   // Placeholder Secondary
            ),
            enabled = true
        )

        val count = registrationCount.get()
        Log.d(TAG, "✅ Registered $count ad placements with local fallback configs")
    }

    /**
     * Register a placement with multiple ad units (waterfall/priority)
     *
     * @param placementId Placement identifier (must match Remote Config key)
     * @param type Ad type: "banner", "interstitial", "native", "reward", "appOpen", "rewardedInterstitial"
     * @param adUnitIds List of AdMob ad unit IDs (tried in order, first to last)
     * @param enabled Whether this placement is enabled by default
     */
    private fun registerPlacementWithMultipleUnits(
        placementId: String,
        type: String,
        adUnitIds: List<String>,
        extras: Map<String, Any> = emptyMap(),
        enabled: Boolean = true
    ) {
        try {
            // Register placement with PlacementConfigService
            // It will fetch from Remote Config using exact placementId
            placementConfigService.registerPlacement(placementId)

            // Convert extras to typed JsonPrimitive so all getExtra* methods work
            val jsonExtras = extras.mapValues { (_, v) ->
                when (v) {
                    is Boolean -> JsonPrimitive(v)
                    is Long    -> JsonPrimitive(v)
                    is Int     -> JsonPrimitive(v)
                    is String  -> JsonPrimitive(v)
                    else       -> JsonPrimitive(v.toString())
                }
            }

            // Create ad unit configs (tried in priority order)
            val units = adUnitIds.map { unitId ->
                CoreAdUnitConfig(
                    id = unitId,
                    enabled = enabled,
                    extras = jsonExtras
                )
            }

            // Set local fallback config (used when Remote Config unavailable)
            val config = CorePlacementConfig(
                enabled = enabled,
                type = type,
                units = units,
                extras = jsonExtras
            )
            placementConfigService.setLocalConfig(placementId, config)

            // Track successful registration
            registrationCount.incrementAndGet()

            Log.d(TAG, "  ✓ Registered: $placementId")
            Log.d(TAG, "    - Type: $type")
            Log.d(TAG, "    - Units: ${units.size}")
            Log.d(TAG, "    - Enabled: $enabled")
            Log.d(TAG, "    - Extras: $extras")
            Log.d(TAG, "    - Ad Unit IDs: ${adUnitIds.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "  ✗ Failed to register: $placementId", e)
            e.printStackTrace()
        }
    }

    /**
     * Register a native ad placement with layout configuration
     *
     * @param placementId Placement identifier (must match Remote Config key)
     * @param layoutName Native ad layout name (e.g., "native_big_bait", "native_small_clean")
     * @param adUnitIds List of AdMob ad unit IDs (tried in order, first to last)
     * @param enabled Whether this placement is enabled by default
     */
    private fun registerNativePlacement(
        placementId: String,
        layoutName: String,
        adUnitIds: List<String>,
        enabled: Boolean = true
    ) {
        try {
            // Register placement with PlacementConfigService
            placementConfigService.registerPlacement(placementId)

            // Create ad unit configs (tried in priority order)
            val units = adUnitIds.map { unitId ->
                CoreAdUnitConfig(
                    id = unitId,
                    enabled = enabled
                )
            }

            // Set local fallback config with layout in extras
            val config = CorePlacementConfig(
                enabled = enabled,
                type = "native",
                units = units,
                extras = mapOf("layout" to JsonPrimitive(layoutName))
            )
            placementConfigService.setLocalConfig(placementId, config)

            // Track successful registration
            registrationCount.incrementAndGet()

            Log.d(TAG, "  ✓ Registered: $placementId")
            Log.d(TAG, "    - Type: native")
            Log.d(TAG, "    - Layout: $layoutName")
            Log.d(TAG, "    - Units: ${units.size}")
            Log.d(TAG, "    - Enabled: $enabled")
            Log.d(TAG, "    - Ad Unit IDs: ${adUnitIds.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "  ✗ Failed to register: $placementId", e)
            e.printStackTrace()
        }
    }

    /**
     * Update configuration from Remote Config
     *
     * Called automatically by RemoteConfigCoordinator when config changes.
     *
     * @param config Remote Config container with latest values
     */
    override suspend fun update(config: ConfigContainer) {
        // Update interstitial interval from Remote Config (no validation - Remote Config decides)
        val remoteInterval = config.getLong(KEY_INTERSTITIAL_INTERVAL, DEFAULT_INTERSTITIAL_INTERVAL.toLong())
        interstitialIntervalSeconds = remoteInterval.toInt()

        // Log whether using remote or default value
        if (remoteInterval != DEFAULT_INTERSTITIAL_INTERVAL.toLong()) {
            Log.d(TAG, "📊 Remote Config updated - Interstitial interval: ${interstitialIntervalSeconds}s (from Remote Config)")
        } else {
            Log.d(TAG, "📊 Remote Config updated - Interstitial interval: ${interstitialIntervalSeconds}s (using local default)")
        }

        quality720pAdType = config.getString(KEY_QUALITY_720P_AD_TYPE, DEFAULT_QUALITY_AD_TYPE)
        quality1080pAdType = config.getString(KEY_QUALITY_1080P_AD_TYPE, DEFAULT_QUALITY_AD_TYPE)
        Log.d(TAG, "📊 Quality ad types — 720p: $quality720pAdType, 1080p: $quality1080pAdType")

    }

    /**
     * Get the number of placements that were successfully registered
     *
     * This count is automatically tracked during initialization.
     *
     * @return Number of successfully registered placements
     */
    fun getExpectedPlacementCount(): Int = registrationCount.get()

    /**
     * Check if a placement is enabled
     *
     * Checks both Remote Config and local fallback config.
     * Returns false if placement is not registered or explicitly disabled.
     *
     * @param placementId The placement ID to check
     * @return true if placement is enabled, false otherwise
     */
    fun isPlacementEnabled(placementId: String): Boolean {
        return placementConfigService.getConfig(placementId)?.enabled == true
    }

    /**
     * Returns the ad type to use for quality unlock: "interstitial" or "rewarded".
     * Controlled by Remote Config; falls back to "rewarded" if key not present.
     */
    fun getAdTypeForQuality(quality: VideoQuality): String {
        return when (quality) {
            VideoQuality.HD_720 -> quality720pAdType
            VideoQuality.FHD_1080 -> quality1080pAdType
        }
    }

}
