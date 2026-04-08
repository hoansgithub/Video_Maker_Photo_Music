package com.videomaker.aimusic.core.constants

/**
 * Ad placement IDs for Video Maker app.
 *
 * Each constant represents a specific ad placement location in the app.
 * These IDs MUST match the Remote Config keys exactly.
 *
 * Naming convention: {TYPE}_{SCREEN}_{POSITION}
 * - TYPE: APP_OPEN, INTERSTITIAL, NATIVE, BANNER, REWARDED
 * - SCREEN: SPLASH, HOME, EDITOR, EXPORT, etc.
 * - POSITION: Optional number for multiple placements on same screen
 *
 * Example Remote Config structure:
 * ```json
 * {
 *   "interstitial_editor_enter": {
 *     "enabled": true,
 *     "type": "interstitial",
 *     "units": [...]
 *   }
 * }
 * ```
 */
object AdPlacement {

    // ============================================
    // APP OPEN ADS
    // ============================================

    /**
     * App Open ad shown on cold start (after splash screen).
     * Timing: After UMP consent + Remote Config load, before navigating to first screen.
     */
    const val APP_OPEN_SPLASH = "app_open_splash"

    // ============================================
    // INTERSTITIAL ADS
    // ============================================

    /**
     * Shown when user taps on a photo to enter Editor screen.
     * Frequency: Respect minimum interval (e.g., 60 seconds between shows).
     */
    const val INTERSTITIAL_EDITOR_ENTER = "interstitial_editor_enter"

    /**
     * Shown after video export completes successfully (before showing export result).
     * Timing: After WorkManager export finishes, before ExportResultScreen.
     */
    const val INTERSTITIAL_EXPORT_COMPLETE = "interstitial_export_complete"

    /**
     * Shown when user taps "Back" to exit Editor (if unsaved changes exist).
     * Frequency: Respect minimum interval.
     */
    const val INTERSTITIAL_EDITOR_EXIT = "interstitial_editor_exit"

    /**
     * Shown when user taps a project in Projects tab to resume editing.
     * Frequency: Respect minimum interval.
     */
    const val INTERSTITIAL_PROJECT_OPEN = "interstitial_project_open"

    // ============================================
    // NATIVE ADS
    // ============================================

    /**
     * Native ad in Home tab feed (first position - after first row of content).
     * Layout: native_small_clean or native_big_clean.
     */
    const val NATIVE_HOME_1 = "native_home_1"

    /**
     * Native ad in Home tab feed (second position - after multiple rows).
     * Layout: native_small_clean or native_big_clean.
     */
    const val NATIVE_HOME_2 = "native_home_2"

    /**
     * Native ad in Gallery tab (photo grid).
     * Layout: native_small_clean.
     */
    const val NATIVE_GALLERY_1 = "native_gallery_1"

    /**
     * Native ad in Songs tab (music list).
     * Layout: native_small_clean or native_small_row.
     */
    const val NATIVE_SONGS_1 = "native_songs_1"

    /**
     * Native ad in Projects tab (project list).
     * Layout: native_small_clean or native_small_row.
     */
    const val NATIVE_PROJECTS_1 = "native_projects_1"

    /**
     * Native ad in Settings screen (below app settings).
     * Layout: native_big_clean.
     */
    const val NATIVE_SETTINGS_1 = "native_settings_1"

    // ============================================
    // BANNER ADS
    // ============================================

    /**
     * Banner ad anchored at bottom of Editor screen (non-intrusive).
     * Size: Adaptive banner (matches screen width, 50-90dp height).
     */
    const val BANNER_EDITOR = "banner_editor"

    /**
     * Banner ad on Export screen (while video is generating).
     * Size: Adaptive banner.
     */
    const val BANNER_EXPORT = "banner_export"

    // ============================================
    // REWARDED ADS (Future - Optional)
    // ============================================

    /**
     * Rewarded ad to unlock premium transitions (if free tier limitation added).
     * Reward: Unlock transition set for current video.
     */
    const val REWARDED_UNLOCK_TRANSITIONS = "rewarded_unlock_transitions"

    /**
     * Rewarded ad to remove watermark from exported video (if watermark added to free tier).
     * Reward: Export video without watermark.
     */
    const val REWARDED_REMOVE_WATERMARK = "rewarded_remove_watermark"

    // ============================================
    // ALL PLACEMENTS (for validation)
    // ============================================

    /**
     * List of all ad placement IDs.
     * Used by AdInitializer to validate that all placements are registered.
     */
    val ALL_PLACEMENTS = listOf(
        // App Open
        APP_OPEN_SPLASH,

        // Interstitial
        INTERSTITIAL_EDITOR_ENTER,
        INTERSTITIAL_EXPORT_COMPLETE,
        INTERSTITIAL_EDITOR_EXIT,
        INTERSTITIAL_PROJECT_OPEN,

        // Native
        NATIVE_HOME_1,
        NATIVE_HOME_2,
        NATIVE_GALLERY_1,
        NATIVE_SONGS_1,
        NATIVE_PROJECTS_1,
        NATIVE_SETTINGS_1,

        // Banner
        BANNER_EDITOR,
        BANNER_EXPORT,

        // Rewarded (Future)
        REWARDED_UNLOCK_TRANSITIONS,
        REWARDED_REMOVE_WATERMARK
    )
}
