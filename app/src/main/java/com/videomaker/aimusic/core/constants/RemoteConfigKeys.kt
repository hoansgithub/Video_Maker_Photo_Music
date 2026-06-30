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
    // HOME BANNER LIST
    // ============================================

    /**
     * Ordered list of home (Gallery) banners. JSON array of items:
     * ```json
     * [
     *   { "type": "BannerTemplate", "data": { "name": "Summer Vibes", "id": "template_001", "style": 1 } },
     *   { "type": "BannerSong",     "data": { "name": "Blinding Lights", "id": 1001, "style": 2 } }
     * ]
     * ```
     * `type` selects the banner kind, `data.id` fetches the real template/song,
     * `data.style` (1 or 2) picks the UI variant, `data.name` is the banner title.
     * Empty / missing / malformed → legacy featured-templates carousel is shown instead.
     */
    const val HOME_BANNER_LIST = "home_banner_list"

    // ============================================
    // ONBOARDING DYNAMIC CONTENT
    // ============================================

    const val ONBOARDING_PAGE1_TEMPLATE_ID = "onboarding_page1_template_id"
    const val ONBOARDING_PAGE2_SONG_ID = "onboarding_page2_song_id"

    // ============================================
    // ONBOARDING STEP GATES (every step gatable, default=true)
    // ============================================

    const val ONBOARDING_LANGUAGE_SELECTION_ENABLED = "onboarding_language_selection_enabled"
    const val ONBOARDING_WELCOME_PAGE_1_ENABLED = "onboarding_welcome_page_1_enabled"
    const val ONBOARDING_WELCOME_PAGE_2_ENABLED = "onboarding_welcome_page_2_enabled"
    const val ONBOARDING_WELCOME_PAGE_3_ENABLED = "onboarding_welcome_page_3_enabled"
    const val ONBOARDING_FEATURE_SELECT_ENABLED = "onboarding_feature_select_enabled"

    // ============================================
    // ONBOARDING GENRE TEMPLATE STEPS
    // ============================================

    const val ONBOARDING_GENRE_SELECTION_ENABLED = "onboarding_genre_selection_enabled"
    const val ONBOARDING_PERSONALIZING_ENABLED = "onboarding_personalizing_enabled"
    const val ONBOARDING_TEMPLATE_PICK_ENABLED = "onboarding_template_pick_enabled"
    const val ONBOARDING_CONTENT_EXCLUSIVE_ENABLED = "onboarding_content_exclusive_enabled"
    const val ONBOARDING_MEDIA_PRIVACY_ENABLED = "onboarding_media_privacy_enabled"

    // ============================================
    // ONBOARDING SURVEY SCREENS (after language) — AB test on/off per screen
    // ============================================

    const val ONBOARDING_FEATURE_SELECTION_ENABLED = "onboarding_feature_selection_enabled"
    const val ONBOARDING_PLATFORM_SELECTION_ENABLED = "onboarding_platform_selection_enabled"
    const val ONBOARDING_AI_LEVEL_ENABLED = "onboarding_ai_level_enabled"
    const val ONBOARDING_AI_FACE_SWAP_ENABLED = "onboarding_ai_face_swap_enabled"
    const val ONBOARDING_AI_DANCE_ENABLED = "onboarding_ai_dance_enabled"
    const val ONBOARDING_NON_AI_LYRIC_ENABLED = "onboarding_non_ai_lyric_enabled"
    const val ONBOARDING_NON_AI_MUSIC_VIDEO_ENABLED = "onboarding_non_ai_music_video_enabled"

    /**
     * Dynamic sort order for the Feature Survey items (JSON array of item IDs).
     * Items in the array appear first in that order; any remaining items are appended
     * in their hardcoded order.
     *
     * Example: `["ai_dance_video", "music_video_templates", "lyric_videos"]`
     * Empty / missing / malformed → hardcoded order preserved.
     */
    const val ONBOARDING_FEATURE_SURVEY_ORDER = "onboarding_feature_survey_order"

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

    // ============================================
    // AD BANNER → NATIVE TOGGLE
    // ============================================

    /**
     * When true, banner ad placements render as native ads instead.
     * Applies to all banner slots (home, editor, template previewer, asset picker, export).
     *
     * Default: true (use native ads everywhere)
     */
    const val AD_BANNER_USE_NATIVE = "ad_banner_use_native"

    // ============================================
    // RATING POPUP
    // ============================================

    // ============================================
    // AD BOTTOM NAVIGATION BAR PADDING
    // ============================================

    /**
     * When true, bottom-positioned native ads receive [navigationBarsPadding] to prevent
     * overlap with the system navigation bar.
     *
     * Affects all bottom native ads across Home, Export, Uninstall, Widget, WelcomeBack,
     * TemplatePreviewer, and all onboarding screens.
     *
     * Default: false (no extra padding — current behavior)
     */
    const val AD_BOTTOM_NAV_PADDING_ENABLED = "ad_bottom_nav_padding_enabled"

    // ============================================
    // RATING POPUP
    // ============================================

    /**
     * Threshold for template swipes/song nexts to trigger rating popup.
     * Default: 5.
     */
    const val RATING_POPUP_TRIGGER_COUNT = "rating_popup_trigger_count"

    /**
     * Maximum number of times the rating popup can be shown per local day.
     * Default: 3.
     */
    const val RATING_POPUP_DAILY_CAP = "rating_popup_daily_cap"

    // ============================================
    // ONBOARDING RESUME NOTIFICATIONS
    // ============================================

    /** Master toggle for the entire onboarding-resume notification sequence. Default: true. */
    const val OB_RESUME_NOTI_ENABLED = "ob_resume_noti_enabled"

    /** Delay (minutes) before the 1st onboarding-resume notification fires. Default: 5. Set to 0 to disable this notification. */
    const val OB_RESUME_NOTI_1_DELAY_MINUTES = "ob_resume_noti_1_delay_minutes"

    /** Delay (minutes) before the 2nd onboarding-resume notification fires. Default: 5. Set to 0 to disable this notification. */
    const val OB_RESUME_NOTI_2_DELAY_MINUTES = "ob_resume_noti_2_delay_minutes"

    /** Delay (minutes) before the 3rd onboarding-resume notification fires. Default: 5. Set to 0 to disable this notification. */
    const val OB_RESUME_NOTI_3_DELAY_MINUTES = "ob_resume_noti_3_delay_minutes"
}
