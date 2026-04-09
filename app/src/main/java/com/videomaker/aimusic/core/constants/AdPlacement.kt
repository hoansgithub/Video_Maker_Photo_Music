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
        NATIVE_TEMPLATE_PREVIEWER_LOADING
    )
}
