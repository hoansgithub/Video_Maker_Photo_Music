package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.loader.PlacementConfigService
import java.util.concurrent.atomic.AtomicReference

/**
 * Ad System Initializer
 *
 * Centralizes ad system initialization to ensure correct dependency order.
 * This class exists to make initialization explicit and fail-fast if something is wrong.
 *
 * ## Critical Initialization Order:
 * 1. **AdPlacementConfigService** - Registers all placements with PlacementConfigService
 *    (happens in init{} block automatically)
 * 2. **PlacementConfigService** - Receives registrations and manages configs
 * 3. **AdsLoaderService** - Uses registered placements to load ads
 *
 * ## Why This Exists:
 * - DI singletons are lazy (only created when requested)
 * - Auto-discovery (getAllSingletons()) only returns already-created instances
 * - Without explicit initialization, AdPlacementConfigService might never be created
 * - Result: 0 placements registered → all ads fail with "placement disabled"
 *
 * This class forces creation in the correct order and validates the result.
 *
 * ## Usage in VideoMakerApplication.onCreate():
 * ```kotlin
 * val adInitializer = ACCDI.get<AdInitializer>()
 * Log.d(TAG, "Ad system initialized: ${adInitializer.getDiagnostics()}")
 * ```
 *
 * @param adPlacementConfigService Registers placements and provides global settings
 * @param placementConfigService ACCCore service that manages per-placement configs
 * @param adsLoaderService ACCCore service that loads and presents ads
 */
class AdInitializer(
    private val adPlacementConfigService: AdPlacementConfigService,
    private val placementConfigService: PlacementConfigService,
    private val adsLoaderService: AdsLoaderService
) {
    companion object {
        private const val TAG = "AdInitializer"
    }

    // Cache diagnostics after initialization to avoid repeated expensive calls
    private val diagnosticsCache = AtomicReference<Map<String, Any>>(emptyMap())

    init {
        Log.d(TAG, "🔨 Initializing ad system...")

        // Validate that AdPlacementConfigService registered placements
        validatePlacementRegistration()

        // Just accessing adsLoaderService ensures it's created
        // AdsLoaderService initialization happens in DI (setAdMediator called in apply{})
        Log.d(TAG, "✅ AdsLoaderService is instantiated")

        // Cache diagnostics after successful initialization
        updateDiagnosticsCache()

        Log.d(TAG, "✅ Ad system initialized successfully")
    }

    /**
     * Update diagnostics cache with current state
     * Called after initialization and when state changes
     */
    private fun updateDiagnosticsCache() {
        diagnosticsCache.set(
            mapOf(
                "registered_placements" to placementConfigService.getRegisteredPlacements().size,
                "expected_placements" to adPlacementConfigService.getExpectedPlacementCount(),
                "interstitial_interval_seconds" to adPlacementConfigService.interstitialIntervalSeconds,
                "initialization_status" to "success"
            )
        )
    }

    /**
     * Validate that all placements were registered
     *
     * AdPlacementConfigService.init{} should have registered all placements.
     * The expected count is automatically tracked during registration.
     */
    private fun validatePlacementRegistration() {
        val registeredPlacements = placementConfigService.getRegisteredPlacements()
        val actualCount = registeredPlacements.size
        val expectedCount = adPlacementConfigService.getExpectedPlacementCount()

        when {
            actualCount == 0 && expectedCount == 0 -> {
                Log.d(TAG, "ℹ️ No placements registered (waiting for setup)")
            }
            actualCount == 0 && expectedCount > 0 -> {
                Log.e(TAG, "❌ CRITICAL: Expected $expectedCount placements but none registered!")
                Log.e(TAG, "AdPlacementConfigService.init{} may not have run")
                throw IllegalStateException(
                    "Ad initialization failed: Expected $expectedCount placements but none registered."
                )
            }
            actualCount < expectedCount -> {
                Log.w(TAG, "⚠️ Warning: Only $actualCount/$expectedCount placements registered")
                Log.w(TAG, "Some ads may not work correctly")
            }
            actualCount == expectedCount -> {
                Log.d(TAG, "✅ All $expectedCount placements registered")
            }
            else -> {
                Log.w(TAG, "⚠️ Warning: $actualCount placements registered (expected $expectedCount)")
            }
        }

        // Log registered placement IDs for debugging
        if (actualCount > 0) {
            Log.d(TAG, "Registered placements: ${registeredPlacements.joinToString(", ")}")
        }
    }

    /**
     * Get the global interstitial interval in seconds
     *
     * This is a convenience method to access the global setting
     * managed by AdPlacementConfigService.
     *
     * @return Interstitial interval in seconds (default: 60)
     */
    fun getInterstitialIntervalSeconds(): Int {
        return adPlacementConfigService.interstitialIntervalSeconds
    }

    /**
     * Check if a specific placement is registered and enabled
     *
     * Useful for debugging why a specific ad isn't showing.
     *
     * @param placementId The placement ID to check (e.g., "ad_interstitial_splash")
     * @return true if placement is registered and enabled, false otherwise
     */
    fun isPlacementReady(placementId: String): Boolean =
        placementConfigService.getConfig(placementId)?.enabled == true

    /**
     * Get diagnostic information about the ad system
     *
     * Returns cached diagnostics for thread-safe, efficient access.
     * Useful for debugging and support.
     *
     * @return Map of diagnostic info (placement count, interval, etc.)
     */
    fun getDiagnostics(): Map<String, Any> = diagnosticsCache.get()
}
