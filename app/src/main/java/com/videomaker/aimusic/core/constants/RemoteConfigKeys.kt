package com.videomaker.aimusic.core.constants

/**
 * Remote Config Keys - Video Maker Photo Music
 *
 * Constants for Firebase Remote Config parameters.
 * Currently minimal - will be expanded as features are controlled via Remote Config.
 *
 * Usage:
 * ```kotlin
 * val isFeatureEnabled = remoteConfig.getBoolean(RemoteConfigKeys.FEATURE_ENABLED)
 * ```
 */
object RemoteConfigKeys {

    // ============================================
    // AD CONFIGURATION
    // ============================================

    /**
     * Ad placements configuration (JSON object).
     *
     * Structure:
     * ```json
     * {
     *   "interstitial_editor_enter": {
     *     "enabled": true,
     *     "type": "interstitial",
     *     "units": [
     *       {"network": "admob", "unitId": "ca-app-pub-xxx/yyy"}
     *     ]
     *   },
     *   "native_home_1": {
     *     "enabled": true,
     *     "type": "native",
     *     "units": [...],
     *     "extras": {"layout": "native_small_clean"}
     *   }
     * }
     * ```
     */
    const val AD_PLACEMENTS = "ad_placements"

    /**
     * Global ad settings (JSON object).
     *
     * Structure:
     * ```json
     * {
     *   "interstitial_min_interval": "60",
     *   "ad_load_timeout": "10",
     *   "enable_test_ads": "false"
     * }
     * ```
     */
    const val AD_GLOBAL_SETTINGS = "ad_global_settings"

    // ============================================
    // APP BEHAVIOR
    // ============================================
    // Placeholder for future Remote Config flags
    // Example: const val NEW_FEATURE_ENABLED = "new_feature_enabled"

}
