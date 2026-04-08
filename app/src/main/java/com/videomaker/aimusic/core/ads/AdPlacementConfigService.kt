package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.PlacementConfigService
import co.alcheclub.lib.acccore.ads.loader.AdPlacementConfig as CorePlacementConfig
import co.alcheclub.lib.acccore.ads.loader.AdUnitConfig as CoreAdUnitConfig
import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import com.videomaker.aimusic.core.constants.AdPlacement
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
