package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.PlacementConfigService
import co.alcheclub.lib.acccore.ads.loader.AdPlacementConfig as CorePlacementConfig
import co.alcheclub.lib.acccore.ads.loader.AdUnitConfig as CoreAdUnitConfig
import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ad Placement Configuration Service
 *
 * Manages ad placement registration for Video Maker app.
 * Implements ConfigurableObject for automatic Remote Config updates.
 *
 * ## Architecture:
 * - **Placement Registration**: Registers all placements with PlacementConfigService
 * - **Remote Config Integration**: Auto-discovered by RemoteConfigCoordinator
 *
 * ## Initialization:
 * - Created as singleton in DI (adsModule)
 * - init{} block runs on first access
 * - Registers all placements with local fallback configs
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
    var interstitialIntervalSeconds: Int = 60
        private set  // Only update() can modify

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

        // App Open Ad (shown when app comes to foreground)
        // Managed by AppOpenAdManager lifecycle observer
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.APP_OPEN_AOA,
            type = "appOpen",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3672286423",  // Primary
                "ca-app-pub-7121075950716954/7364624003"   // Secondary
            ),
            enabled = true
        )

        // ============================================
        // INTERSTITIAL ADS
        // ============================================

        // Splash screen interstitial (shown after loading completes)
        // Waterfall: Primary unit → Secondary unit
        registerPlacementWithMultipleUnits(
            placementId = AdPlacement.INTERSTITIAL_SPLASH,
            type = "interstitial",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4247360286",  // Primary
                "ca-app-pub-7121075950716954/6785534926"   // Secondary
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
                "ca-app-pub-7121075950716954/2213834713",  // Primary
                "ca-app-pub-7121075950716954/6699874633"   // Secondary
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
                "ca-app-pub-7121075950716954/9709181357",  // Primary
                "ca-app-pub-7121075950716954/8587671370"   // Secondary
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
                "ca-app-pub-7121075950716954/6949256261",  // Primary
                "ca-app-pub-7121075950716954/1583783907"   // Secondary
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
                "ca-app-pub-7121075950716954/1313786204"  // Primary
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
                "ca-app-pub-7121075950716954/1313786204"  // Primary (same as home)
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
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/4622910597",  // Primary
                "ca-app-pub-7121075950716954/5002184541"   // Secondary
            ),
            enabled = true
        )

        // Onboarding language selector alternative native ad (A/B test variant)
        // Loaded in parallel with primary placement, first to load wins
        // Layout: native_big_bait (same layout, different ad units)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_LANGUAGE_ALT,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/7245204502",  // Primary
                "ca-app-pub-7121075950716954/9871367841"   // Secondary
            ),
            enabled = true
        )

        // Onboarding feature selector native ad (shown at bottom of feature selection screen)
        // High-engagement placement for users selecting video maker features
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/9315495347",  // Primary
                "ca-app-pub-7121075950716954/1976061375"   // Secondary
            ),
            enabled = true
        )

        // Onboarding feature selector alternative native ad (A/B test variant)
        // Loaded in parallel with primary placement, first to load wins
        // Layout: native_big_bait (same layout, different ad units)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/1801306131",  // Primary
                "ca-app-pub-7121075950716954/8645411507"   // Secondary
            ),
            enabled = true
        )

        // Onboarding page 1 native ad (shown at bottom of first welcome page)
        // First page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE1,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8425919653",  // Primary
                "ca-app-pub-7121075950716954/8562155601"   // Secondary
            ),
            enabled = true
        )

        // Onboarding page 2 native ad (shown at bottom of second welcome page)
        // Second page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE2,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/2815004904",  // Primary
                "ca-app-pub-7121075950716954/3373262316"   // Secondary
            ),
            enabled = true
        )

        // Onboarding page 3 native ad (shown at bottom of third welcome page)
        // Third (final) page of the onboarding flow
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_ONBOARDING_PAGE3,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/3417640133",  // Primary
                "ca-app-pub-7121075950716954/6506837908"   // Secondary
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
                "ca-app-pub-7121075950716954/7249073934",  // Primary
                "ca-app-pub-7121075950716954/5724093483"   // Secondary
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
                "ca-app-pub-7121075950716954/6109352277",  // Primary
                "ca-app-pub-7121075950716954/1665574760"   // Secondary
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
                "ca-app-pub-7121075950716954/5203464719",  // Primary
                "ca-app-pub-7121075950716954/1283896429"   // Secondary
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
                "ca-app-pub-7121075950716954/7829628054",  // Primary
                "ca-app-pub-7121075950716954/6101613417"   // Secondary
            ),
            enabled = true
        )

        // Template previewer loading state native ad (shown during loading)
        // Displayed at bottom with "Building Your Feed" message
        // 10s timeout + 2s display = 12s minimum loading time
        // Layout: native_big_bait (large vertical layout with clickbait CTA)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING,
            layoutName = "native_big_bait",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/8424019843",  // Primary
                "ca-app-pub-7121075950716954/1431594877"   // Secondary
            ),
            enabled = true
        )

        // Projects grid native ad (shown as item in staggered projects list)
        // In-feed placement that blends with project cards
        // Only loads when at least 1 project exists
        // Layout: native_project_card (matches ProjectCard layout with media + info)
        // Waterfall: Primary unit → Secondary unit
        registerNativePlacement(
            placementId = AdPlacement.NATIVE_PROJECTS_GRID,
            layoutName = "native_project_card",
            adUnitIds = listOf(
                "ca-app-pub-7121075950716954/6293185105",  // Primary
                "ca-app-pub-7121075950716954/6536223106"   // Secondary
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
        enabled: Boolean = true
    ) {
        try {
            // Register placement with PlacementConfigService
            // It will fetch from Remote Config using exact placementId
            placementConfigService.registerPlacement(placementId)

            // Create ad unit configs (tried in priority order)
            val units = adUnitIds.map { unitId ->
                CoreAdUnitConfig(
                    id = unitId,
                    enabled = enabled
                )
            }

            // Set local fallback config (used when Remote Config unavailable)
            val config = CorePlacementConfig(
                enabled = enabled,
                type = type,
                units = units
            )
            placementConfigService.setLocalConfig(placementId, config)

            // Track successful registration
            registrationCount.incrementAndGet()

            Log.d(TAG, "  ✓ Registered: $placementId")
            Log.d(TAG, "    - Type: $type")
            Log.d(TAG, "    - Units: ${units.size}")
            Log.d(TAG, "    - Enabled: $enabled")
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
        // Update interstitial interval from Remote Config
        val remoteInterval = config.getLong("ad_interstitial_interval_seconds", 60L)
        interstitialIntervalSeconds = remoteInterval.toInt()

        Log.d(TAG, "📊 Remote Config updated")
        Log.d(TAG, "   - Interstitial interval: ${interstitialIntervalSeconds}s")
    }

    /**
     * Get the number of placements that were successfully registered
     *
     * This count is automatically tracked during initialization.
     *
     * @return Number of successfully registered placements
     */
    fun getExpectedPlacementCount(): Int = registrationCount.get()

    companion object {
        private const val TAG = "AdPlacementConfig"
    }
}
