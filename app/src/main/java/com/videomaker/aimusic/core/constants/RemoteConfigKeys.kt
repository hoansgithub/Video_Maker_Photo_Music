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

    // ============================================
    // ONBOARDING DYNAMIC CONTENT
    // ============================================

    const val ONBOARDING_PAGE1_TEMPLATE_ID = "onboarding_page1_template_id"
    const val ONBOARDING_PAGE2_SONG_ID = "onboarding_page2_song_id"

    // ============================================
    // ONBOARDING GENRE TEMPLATE STEPS
    // ============================================

    const val ONBOARDING_GENRE_SELECTION_ENABLED = "onboarding_genre_selection_enabled"
    const val ONBOARDING_PERSONALIZING_ENABLED = "onboarding_personalizing_enabled"
    const val ONBOARDING_TEMPLATE_PICK_ENABLED = "onboarding_template_pick_enabled"

    // ============================================
    // TRENDING POPUP
    // ============================================

    /**
     * Minimum interval between trending-popup displays, per tab.
     *
     * JSON object: `{"hour": H, "minute": M}` — total interval is `H*60 + M` minutes.
     * Both 0 → disabled (acts as kill-switch).
     *
     * Examples:
     * - 2 minutes (test): `{"hour":0,"minute":2}`
     * - 30 minutes (test): `{"hour":0,"minute":30}`
     * - 5 hours (prod): `{"hour":5,"minute":0}`
     * - 1 hour 30 min: `{"hour":1,"minute":30}`
     * - Kill switch: `{"hour":0,"minute":0}`
     *
     * Default: `{"hour":5,"minute":0}` (5 hours).
     */
    const val TRENDING_POPUP_INTERVAL = "trending_popup_interval"

    /**
     * Maximum number of trending popups shown per tab per local day.
     * Default: 3.
     */
    const val TRENDING_POPUP_DAILY_CAP = "trending_popup_daily_cap"

}
