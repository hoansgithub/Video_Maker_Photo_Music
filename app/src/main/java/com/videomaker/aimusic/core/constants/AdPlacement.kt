package com.videomaker.aimusic.core.constants

/**
 * Ad placement IDs for Video Maker app.
 *
 * Each constant represents a specific ad placement location in the app.
 * These IDs MUST match the Remote Config keys exactly.
 *
 * Example Remote Config structure:
 * ```json
 * {
 *   "interstitial_splash": {
 *     "enabled": true,
 *     "type": "interstitial",
 *     "units": [
 *       {
 *         "network": "admob",
 *         "unitId": "ca-app-pub-xxx/yyy",
 *         "enabled": true
 *       }
 *     ]
 *   }
 * }
 * ```
 */
object AdPlacement {

    /**
     * Interstitial ad shown after splash screen loading completes.
     * Timing: After all data loaded (Remote Config, status checks), before navigating to next screen.
     * Shown once per app session (splash screen only appears once).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4247360286
     * - Secondary: ca-app-pub-7121075950716954/6785534926
     *
     * Remote Config key: ad_interstitial_splash
     */
    const val INTERSTITIAL_SPLASH = "ad_interstitial_splash"

    /**
     * Interstitial ad shown when user presses back from template previewer.
     * Timing: Preloaded at screen launch, shown on back button press if ready.
     * If ad not loaded yet, back navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2213834713
     * - Secondary: ca-app-pub-7121075950716954/6699874633
     *
     * Remote Config key: ad_interstitial_template_previewer_back
     */
    const val INTERSTITIAL_TEMPLATE_PREVIEWER_BACK = "ad_interstitial_template_previewer_back"

    /**
     * Interstitial ad shown when user exits the export result screen.
     * Timing: Preloaded when export completes, shown on back/exit if ready.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9709181357
     * - Secondary: ca-app-pub-7121075950716954/8587671370
     *
     * Remote Config key: ad_interstitial_export_result_exit
     */
    const val INTERSTITIAL_EXPORT_RESULT_EXIT = "ad_interstitial_export_result_exit"

    /**
     * List of all ad placement IDs.
     * Used by AdInitializer to validate that all placements are registered.
     */
    val ALL_PLACEMENTS = listOf(
        INTERSTITIAL_SPLASH,
        INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
        INTERSTITIAL_EXPORT_RESULT_EXIT
    )
}
