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
     * Interstitial ad shown when user closes/exits the asset picker (image selector).
     * Timing: Preloaded at screen launch, shown on back/close if ready.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6949256261
     * - Secondary: ca-app-pub-7121075950716954/1583783907
     *
     * Remote Config key: ad_interstitial_asset_picker_exit
     */
    const val INTERSTITIAL_ASSET_PICKER_EXIT = "ad_interstitial_asset_picker_exit"

    /**
     * App Open Ad shown when app comes to foreground.
     * Timing: Preloaded when app goes to background, shown when app returns to foreground.
     * Automatically managed by AppOpenAdManager (lifecycle-aware).
     *
     * Behavior:
     * - Preloads when app enters background (ProcessLifecycleOwner.onStop)
     * - Shows when app enters foreground (ProcessLifecycleOwner.onStart)
     * - Skipped during splash screen
     * - Skipped when another fullscreen ad is showing
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/3672286423
     * - Secondary: ca-app-pub-7121075950716954/7364624003
     *
     * Remote Config key: ad_appopen_aoa
     */
    const val APP_OPEN_AOA = "ad_appopen_aoa"

    /**
     * Banner ad shown at bottom of home screen (below tab bar).
     * Timing: Loaded when home screen is displayed, cached for reuse.
     * Displayed on main screen below the navigation tab bar.
     *
     * Features:
     * - Adaptive banner sizing (320dp width default)
     * - Automatic caching (no reload on navigation back)
     * - Retry failed loads on activity resume
     * - Lifecycle-aware cleanup
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1313786204
     *
     * Remote Config key: ad_banner_home
     */
    const val BANNER_HOME = "ad_banner_home"

    /**
     * Banner ad shown at bottom of template previewer screen.
     * Timing: Loaded when template previewer is displayed.
     * Displayed below the template preview content.
     *
     * Features:
     * - Adaptive banner sizing (320dp width default)
     * - Lifecycle-aware cleanup
     * - Same configuration as home banner
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1313786204
     *
     * Remote Config key: ad_banner_template_previewer
     */
    const val BANNER_TEMPLATE_PREVIEWER = "ad_banner_template_previewer"

    /**
     * Native ad shown at bottom of onboarding language selector screen.
     * Timing: Loaded when language selector is displayed.
     * High-engagement placement for first-time users.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4622910597
     * - Secondary: ca-app-pub-7121075950716954/5002184541
     *
     * Remote Config key: ad_native_onboarding_language
     */
    const val NATIVE_ONBOARDING_LANGUAGE = "ad_native_onboarding_language"

    /**
     * Alternative native ad for onboarding language selector (A/B test variant).
     * Timing: Loaded in parallel with NATIVE_ONBOARDING_LANGUAGE.
     * First one to load wins and is displayed.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Same layout as primary placement
     * - Different ad units for A/B testing
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7245204502
     * - Secondary: ca-app-pub-7121075950716954/9871367841
     *
     * Remote Config key: ad_native_onboarding_language_alt
     */
    const val NATIVE_ONBOARDING_LANGUAGE_ALT = "ad_native_onboarding_language_alt"

    /**
     * Native ad shown at bottom of onboarding feature selector screen.
     * Timing: Loaded when feature selector is displayed.
     * Shown to users after language selection during onboarding flow.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9315495347
     * - Secondary: ca-app-pub-7121075950716954/1976061375
     *
     * Remote Config key: ad_native_onboarding_feature_selection
     */
    const val NATIVE_ONBOARDING_FEATURE_SELECTION = "ad_native_onboarding_feature_selection"

    /**
     * Alternative native ad for onboarding feature selector (A/B test variant).
     * Timing: Loaded in parallel with NATIVE_ONBOARDING_FEATURE_SELECTION.
     * First one to load wins and is displayed.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Same layout as primary placement
     * - Different ad units for A/B testing
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1801306131
     * - Secondary: ca-app-pub-7121075950716954/8645411507
     *
     * Remote Config key: ad_native_onboarding_feature_selection_alt
     */
    const val NATIVE_ONBOARDING_FEATURE_SELECTION_ALT = "ad_native_onboarding_feature_selection_alt"

    /**
     * Native ad shown at bottom of onboarding page 1.
     * Timing: Loaded when onboarding page 1 is displayed.
     * First welcome page of the onboarding flow.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8425919653
     * - Secondary: ca-app-pub-7121075950716954/8562155601
     *
     * Remote Config key: ad_native_onboarding_page1
     */
    const val NATIVE_ONBOARDING_PAGE1 = "ad_native_onboarding_page1"

    /**
     * Native ad shown at bottom of onboarding page 2.
     * Timing: Loaded when onboarding page 2 is displayed.
     * Second welcome page of the onboarding flow.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2815004904
     * - Secondary: ca-app-pub-7121075950716954/3373262316
     *
     * Remote Config key: ad_native_onboarding_page2
     */
    const val NATIVE_ONBOARDING_PAGE2 = "ad_native_onboarding_page2"

    /**
     * Native ad shown at bottom of onboarding page 3.
     * Timing: Loaded when onboarding page 3 is displayed.
     * Third (final) welcome page of the onboarding flow.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/3417640133
     * - Secondary: ca-app-pub-7121075950716954/6506837908
     *
     * Remote Config key: ad_native_onboarding_page3
     */
    const val NATIVE_ONBOARDING_PAGE3 = "ad_native_onboarding_page3"

    /**
     * Fullscreen native ad shown between onboarding pages.
     * Displayed as a full-screen overlay with close button.
     * Injected after page 1, 2, or 3 (configurable).
     * Layout: native_big_bait (large vertical layout)
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7249073934
     * - Secondary: ca-app-pub-7121075950716954/5724093483
     *
     * Remote Config key: ad_native_onboarding_fullscreen
     * Remote Config extras:
     * - close_delay: Delay in seconds before showing close button (default: 2, Meta ads: 0)
     * - inject_after: Which page to show after (1, 2, or 3, default: 2)
     */
    const val NATIVE_ONBOARDING_FULLSCREEN = "ad_native_onboarding_fullscreen"

    /**
     * Native ad shown in search screens (in-feed at top).
     * Displayed as first item in search results list.
     * Shown on all search states: idle, loading, results, empty.
     *
     * Screens:
     * - Template search screen
     * - Song search screen
     *
     * Layout: Uses custom native ad layout (full width in-feed)
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6109352277
     * - Secondary: ca-app-pub-7121075950716954/1665574760
     *
     * Remote Config key: ad_native_search_infeed
     */
    const val NATIVE_SEARCH_INFEED = "ad_native_search_infeed"

    /**
     * Native ad shown at bottom of uninstall screen.
     * Timing: Loaded when uninstall screen is displayed.
     * Final engagement point before user uninstalls the app.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/5203464719
     * - Secondary: ca-app-pub-7121075950716954/1283896429
     *
     * Remote Config key: ad_native_uninstall_bottom
     */
    const val NATIVE_UNINSTALL_BOTTOM = "ad_native_uninstall_bottom"

    /**
     * Native ad shown below the widget screen.
     * Timing: Loaded when widget screen is displayed.
     * Placement below widget content to maximize visibility.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Attention-grabbing design for maximum engagement
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7829628054
     * - Secondary: ca-app-pub-7121075950716954/6101613417
     *
     * Remote Config key: ad_native_widget_bottom
     */
    const val NATIVE_WIDGET_BOTTOM = "ad_native_widget_bottom"

    /**
     * Native ad shown during template previewer loading state.
     * Timing: Displayed at bottom while content is loading.
     * Waits 10 seconds for ad to load, then displays for 2 more seconds.
     * Total loading time: 12 seconds minimum before showing content.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Shown with "Building Your Feed" loading indicator
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8424019843
     * - Secondary: ca-app-pub-7121075950716954/1431594877
     *
     * Remote Config key: ad_native_template_previewer_loading
     */
    const val NATIVE_TEMPLATE_PREVIEWER_LOADING = "ad_native_template_previewer_loading"

    /**
     * Native ad shown in created projects grid (in-feed placement).
     * Displayed as an item within the staggered projects grid.
     * Only loaded when at least 1 project exists (never shown on empty state).
     *
     * Layout: native_project_card (matches ProjectCard layout)
     * - Media view at top (ad creative)
     * - Icon + headline + CTA at bottom
     * - Ad badge at top-right corner
     * - Blends with project cards in staggered grid
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6293185105
     * - Secondary: ca-app-pub-7121075950716954/6536223106
     *
     * Remote Config key: ad_native_projects_grid
     */
    const val NATIVE_PROJECTS_GRID = "ad_native_projects_grid"

    /**
     * Native ad shown in gallery templates staggered grid (in-feed placement).
     * Displayed as an item within the gallery templates staggered grid.
     * Position: 4th position (index 3), or last position if total items < 3.
     * Persists through vibe chip tag filtering.
     *
     * Layout: native_project_card (9:16 portrait, matches template cards)
     * - Media view at top (ad creative)
     * - Icon + headline + CTA at bottom
     * - Ad badge at top-right corner
     * - Blends with template cards in staggered grid
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8919348440
     * - Secondary: ca-app-pub-7121075950716954/2545511783
     *
     * Remote Config key: ad_native_gallery_grid
     */
    const val NATIVE_GALLERY_GRID = "ad_native_gallery_grid"

    /**
     * Native ad shown in songs tab station section (in-feed placement).
     * Displayed as an item within the station songs vertical list.
     * Position: 4th position (index 3), or last position if total items < 3.
     * Persists through genre chip tag filtering.
     *
     * Layout: native_small_row (horizontal row, matches song list items)
     * - Media view on left (ad creative)
     * - Headline + body text in center
     * - CTA button on right
     * - Blends with song list items
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2788549787
     * - Secondary: ca-app-pub-7121075950716954/1667039805
     *
     * Remote Config key: ad_native_songs_station
     */
    const val NATIVE_SONGS_STATION = "ad_native_songs_station"

    /**
     * Native ad shown during export video generating state.
     * Timing: Displayed at bottom of "Generating" overlay while video is being exported.
     * Waits 10 seconds for ad to load, then displays for 2 more seconds if loaded.
     *
     * Layout: native_big_bait (412:304 ratio, 1.355:1)
     * - Large vertical layout with clickbait CTA button
     * - Shown with "Generating" text and progress indicator above
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4484774830
     * - Secondary: ca-app-pub-7121075950716954/7797838469
     *
     * Remote Config key: ad_native_export_generating
     */
    const val NATIVE_EXPORT_GENERATING = "ad_native_export_generating"

    /**
     * Rewarded ad shown when user wants to download video to gallery.
     * Timing: User clicks download button → dialog appears → user watches ad → download proceeds.
     * User MUST watch the full ad to earn the reward (download permission).
     *
     * Flow:
     * 1. User clicks download button (ad icon instead of download icon)
     * 2. Dialog shows: "Watch an ad to download your video?"
     * 3. User taps "Watch Ad" → Ad loads and plays
     * 4. User watches full ad → onUserEarnedReward callback
     * 5. Download proceeds automatically
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4051154821
     * - Secondary: ca-app-pub-7121075950716954/6681333380
     *
     * Remote Config key: ad_reward_download_video
     */
    const val REWARD_DOWNLOAD_VIDEO = "ad_reward_download_video"

    /**
     * Rewarded ad shown when user taps watermark to remove it.
     * Timing: User taps watermark on video preview → dialog appears → user watches ad → watermark removed.
     * User MUST watch the full ad to earn the reward (watermark removal).
     *
     * Flow:
     * 1. Watermark overlay shown on video preview by default
     * 2. User taps watermark → dialog: "Watch an ad to remove watermark?"
     * 3. User taps "Watch Ad" → Ad loads and plays
     * 4. User watches full ad → onUserEarnedReward callback
     * 5. Watermark removed for current session
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6485746472
     * - Secondary: ca-app-pub-7121075950716954/8206497619
     *
     * Remote Config key: ad_reward_remove_watermark
     */
    const val REWARD_REMOVE_WATERMARK = "ad_reward_remove_watermark"

    /**
     * Rewarded ad shown when user taps "Done" in editor with locked quality (720p/1080p).
     * Timing: User selects 720p/1080p quality → taps Done button → dialog appears → watches ad → unlocked for session.
     * User MUST watch the full ad to earn the reward (quality unlock for current session).
     *
     * Flow:
     * 1. User selects 720p or 1080p quality in editor
     * 2. Done button shows [AD] badge
     * 3. User taps Done → dialog: "Watch an ad to export in high quality?"
     * 4. User taps "Watch Ad" → Ad loads and plays
     * 5. User watches full ad → onUserEarnedReward callback
     * 6. Quality unlocked for current editor session only
     * 7. Done button proceeds to export screen
     * 8. When user leaves editor and comes back, needs to watch ad again
     *
     * Session-based unlock (NOT persistent):
     * - Unlock only valid for current EditorViewModel instance
     * - Reset when user navigates away from editor
     * - User must watch ad again each time they edit a project
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9080595582
     * - Secondary: ca-app-pub-7121075950716954/2706758926
     *
     * Remote Config key: ad_reward_unlock_quality
     */
    const val REWARD_UNLOCK_QUALITY = "ad_reward_unlock_quality"

    /**
     * Rewarded ad shown when user wants to unlock a locked effect set.
     * Timing: User clicks locked effect set → dialog appears → user watches ad → effect set unlocked.
     * User MUST watch the full ad to earn the reward (effect set unlock).
     *
     * Flow:
     * 1. User clicks locked effect set in editor bottom sheet (shows lock icon)
     * 2. Dialog shows: "Watch an ad to unlock this effect set?"
     * 3. User taps "Watch Ad" → Ad loads and plays
     * 4. User watches full ad → onUserEarnedReward callback
     * 5. Effect set is unlocked and stored locally in SharedPreferences
     * 6. Lock icon disappears, user can now use the effect set
     * 7. Unlock persists across app restarts
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1281667047
     * - Secondary: ca-app-pub-7121075950716954/4323092928
     *
     * Remote Config key: ad_reward_unlock_effect_set
     */
    const val REWARD_UNLOCK_EFFECT_SET = "ad_reward_unlock_effect_set"

    /**
     * Rewarded ad shown when user wants to unlock a locked template.
     * Timing: User taps "Use This Template" on locked template → dialog appears → user watches ad → template unlocked.
     * User MUST watch the full ad to earn the reward (template unlock).
     *
     * Flow:
     * 1. User taps "Use This Template" button on template previewer (shows [AD] badge)
     * 2. Dialog shows: "Watch an ad to unlock this template?"
     * 3. User taps "Watch Ad" → Ad loads and plays
     * 4. User watches full ad → onUserEarnedReward callback
     * 5. Template is unlocked and stored locally in SharedPreferences
     * 6. [AD] badge disappears, ratio selection bottom sheet appears
     * 7. Unlock persists across app restarts
     *
     * Ad units (priority order):
     * - Primary: TBD (will be assigned by AdMob)
     * - Secondary: TBD (will be assigned by AdMob)
     *
     * Remote Config key: ad_reward_unlock_template
     */
    const val REWARD_UNLOCK_TEMPLATE = "ad_reward_unlock_template"

    /**
     * Rewarded ad shown when user wants to use a locked song.
     * Timing: User clicks "Use to Create" on locked song → dialog appears → user watches ad → song unlocked.
     * User MUST watch the full ad to earn the reward (song unlock).
     *
     * Flow:
     * 1. User clicks "Use to Create" button on music player (shows [AD] badge)
     * 2. Dialog shows: "Watch an ad to unlock this song?"
     * 3. User taps "Watch Ad" → Ad loads and plays
     * 4. User watches full ad → onUserEarnedReward callback
     * 5. Song is unlocked and stored locally in SharedPreferences
     * 6. [AD] badge disappears, user proceeds to asset picker
     * 7. Unlock persists across app restarts
     *
     * Ad units (priority order):
     * - Primary: TBD (will be assigned by AdMob)
     * - Secondary: TBD (will be assigned by AdMob)
     *
     * Remote Config key: ad_reward_unlock_song
     */
    const val REWARD_UNLOCK_SONG = "ad_reward_unlock_song"

    /**
     * List of all ad placement IDs.
     * Used by AdInitializer to validate that all placements are registered.
     */
    val ALL_PLACEMENTS = listOf(
        APP_OPEN_AOA,
        INTERSTITIAL_SPLASH,
        INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
        INTERSTITIAL_EXPORT_RESULT_EXIT,
        INTERSTITIAL_ASSET_PICKER_EXIT,
        BANNER_HOME,
        BANNER_TEMPLATE_PREVIEWER,
        NATIVE_ONBOARDING_LANGUAGE,
        NATIVE_ONBOARDING_LANGUAGE_ALT,
        NATIVE_ONBOARDING_PAGE1,
        NATIVE_ONBOARDING_PAGE2,
        NATIVE_ONBOARDING_PAGE3,
        NATIVE_ONBOARDING_FULLSCREEN,
        NATIVE_ONBOARDING_FEATURE_SELECTION,
        NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
        NATIVE_SEARCH_INFEED,
        NATIVE_UNINSTALL_BOTTOM,
        NATIVE_WIDGET_BOTTOM,
        NATIVE_PROJECTS_GRID,
        NATIVE_GALLERY_GRID,
        NATIVE_SONGS_STATION,
        NATIVE_TEMPLATE_PREVIEWER_LOADING,
        NATIVE_EXPORT_GENERATING,
        REWARD_DOWNLOAD_VIDEO,
        REWARD_REMOVE_WATERMARK,
        REWARD_UNLOCK_QUALITY,
        REWARD_UNLOCK_EFFECT_SET,
        REWARD_UNLOCK_TEMPLATE,
        REWARD_UNLOCK_SONG
    )
}
