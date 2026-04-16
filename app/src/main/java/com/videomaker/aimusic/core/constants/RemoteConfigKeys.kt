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

    /**
     * App initialization timeout in milliseconds.
     *
     * Maximum time to wait for initialization operations (Remote Config fetch,
     * ad preloading, status checks) before proceeding to navigation.
     *
     * If initialization takes longer than this timeout, the app proceeds anyway
     * to prevent users from getting stuck on loading screen.
     *
     * Default: 45000 (45 seconds)
     * Recommended range: 30000-60000 (30-60 seconds)
     *
     * Usage:
     * ```kotlin
     * val timeout = remoteConfig.getLong(RemoteConfigKeys.APP_INIT_TIMEOUT_MS, 45000L)
     * withTimeout(timeout) { /* initialization */ }
     * ```
     */
    const val APP_INIT_TIMEOUT_MS = "app_init_timeout_ms"

    /**
     * Notification schedule configuration (JSON object).
     *
     * Structure:
     * ```json
     * {
     *   "trending_song_daily": {"hour": 19, "minute": 2},
     *   "viral_template_daily": {"hour": 20, "minute": 2},
     *   "quick_save_delay_minutes": 30,
     *   "share_encouragement_delay_minutes": 720,
     *   "forgotten_masterpiece_delay_minutes": 1440,
     *   "abandoned_same_session_delay_minutes": 2,
     *   "abandoned_cold_session_delay_minutes": 15,
     *   "draft_completion_nudge_delay_minutes": 15
     * }
     * ```
     * Delay values are validated in code and must stay within `0..10080` minutes
     * before conversion to milliseconds.
     */
    const val NOTIFICATION_SCHEDULE_CONFIG = "notification_schedule_config"

}
