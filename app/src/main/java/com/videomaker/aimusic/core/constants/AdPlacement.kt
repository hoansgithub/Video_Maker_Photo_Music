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
     * HIGH-priority splash interstitial (first install only).
     * Single high-eCPM ad unit — tried first. If it fails to load, falls back to SPLASH_LOW.
     *
     * Ad unit: ca-app-pub-7121075950716954/9920077454
     *
     * Remote Config key: ad_interstitial_splash_high
     */
    const val INTERSTITIAL_SPLASH_HIGH = "ad_interstitial_splash_high"

    /**
     * LOW-priority splash interstitial fallback (first install only).
     * Single all-fill ad unit — tried only when SPLASH_HIGH fails to load.
     *
     * Ad unit: ca-app-pub-7121075950716954/1830520200
     *
     * Remote Config key: ad_interstitial_splash_low
     */
    const val INTERSTITIAL_SPLASH_LOW = "ad_interstitial_splash_low"

    /**
     * HIGH-priority open-app interstitial (second+ launch).
     * Single high-eCPM ad unit — tried first. If it fails to load, falls back to OPEN_APP_LOW.
     *
     * Ad unit: ca-app-pub-7121075950716954/4748771125 (Inter_high_splash_reopen)
     *
     * Remote Config key: ad_interstitial_open_app_high
     */
    const val INTERSTITIAL_OPEN_APP_HIGH = "ad_interstitial_open_app_high"

    /**
     * LOW-priority open-app interstitial fallback (second+ launch).
     * Single all-fill ad unit — tried only when OPEN_APP_HIGH fails to load.
     *
     * Ad unit: ca-app-pub-7121075950716954/2676684702 (Inter_all_splash_reopen)
     *
     * Remote Config key: ad_interstitial_open_app_low
     */
    const val INTERSTITIAL_OPEN_APP_LOW = "ad_interstitial_open_app_low"

    /**
     * Fullscreen-image interstitial shown at the onboarding ad step
     * (same position as NATIVE_ONBOARDING_FULLSCREEN, but an independent placement).
     *
     * Independent of the native fullscreen ad: each is toggled separately via Firebase.
     * Default disabled — enable on Firebase to show it (and typically disable the native).
     * Shown as its own onboarding page, injected only when this placement is enabled.
     *
     * Ad units (priority order):
     * - Primary (Pro_inter img_high_OB): ca-app-pub-7121075950716954/7891516199
     * - Secondary (Pro_inter img_all_OB): ca-app-pub-7121075950716954/6578434529
     *
     * Remote Config key: ad_interstitial_onboarding
     */
    const val INTERSTITIAL_ONBOARDING = "ad_interstitial_onboarding"

    /**
     * Interstitial ad shown when user presses back from template previewer.
     * Timing: Preloaded at screen launch, shown on back button press if ready.
     * If ad not loaded yet, back navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4441122705
     * - Secondary: ca-app-pub-7121075950716954/7545420894
     *
     * Remote Config key: ad_interstitial_template_previewer_back
     */
    const val INTERSTITIAL_TEMPLATE_PREVIEWER_BACK = "ad_interstitial_template_previewer_back"

    /**
     * Interstitial ad shown while user scrolls through templates in template previewer.
     * Timing: Shown at intervals while browsing templates (vertical scroll/swipe).
     * Frequency controlled by ad_interstitial_interval_seconds Remote Config (default 60s).
     *
     * Behavior:
     * - Shows after user has scrolled for configured interval duration
     * - Non-blocking (doesn't interrupt scroll if ad not ready)
     * - Timer resets after ad shown
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8224075141
     * - Secondary: ca-app-pub-7121075950716954/3051639510
     *
     * Remote Config key: ad_interstitial_template_previewer_scroll
     */
    const val INTERSTITIAL_TEMPLATE_PREVIEWER_SCROLL = "ad_interstitial_template_previewer_scroll"

    /**
     * Interstitial ad shown when user taps "Use this template" in template previewer.
     * Timing: Preloaded at screen launch, shown on "Use this template" tap if ready.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2307503671
     * - Secondary: ca-app-pub-7121075950716954/4561068862
     *
     * Remote Config key: ad_interstitial_template_previewer_use
     */
    const val INTERSTITIAL_TEMPLATE_PREVIEWER_USE = "ad_interstitial_template_previewer_use"

    /**
     * Interstitial ad shown when user presses back or swipes to exit editor screen.
     * Timing: Preloaded at screen launch, shown on back button press/swipe if ready.
     * If ad not loaded yet, back navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9739651039
     * - Secondary: ca-app-pub-7121075950716954/8170207041
     *
     * Remote Config key: ad_interstitial_editor_back
     */
    const val INTERSTITIAL_EDITOR_BACK = "ad_interstitial_editor_back"

    /**
     * Fullscreen-image interstitial shown right after the editor finishes preparing.
     * Timing: preloaded while the editor "preparing" (Loading) screen is showing;
     * the editor (Success) is displayed for 1s, then this interstitial is shown.
     *
     * Ad units (priority order):
     * - Primary (Pro_inter img_high_after prepare): ca-app-pub-7121075950716954/6955884647
     * - Secondary (Pro_inter img_all_after prepare): ca-app-pub-7121075950716954/3004197192
     *
     * Remote Config key: ad_interstitial_editor_after_prepare
     */
    const val INTERSTITIAL_EDITOR_AFTER_PREPARE = "ad_interstitial_editor_after_prepare"

    /**
     * Interstitial ad shown when user taps a template in gallery/home grid.
     * Timing: Shown when tapping template to open previewer.
     * Frequency controlled by ad_interstitial_interval_seconds Remote Config.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8538250004
     * - Secondary: ca-app-pub-7121075950716954/8361778735
     *
     * Remote Config key: ad_interstitial_template_grid_tap
     */
    const val INTERSTITIAL_TEMPLATE_GRID_TAP = "ad_interstitial_template_grid_tap"

    /**
     * Interstitial ad shown when user taps a created project in Library tab to edit.
     * Timing: Preloaded when Projects tab loads, shown on project tap if ready and frequency cap allows.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9761424655
     * - Secondary: ca-app-pub-7121075950716954/7135261314
     *
     * Remote Config key: ad_interstitial_library_project_tap
     */
    const val INTERSTITIAL_LIBRARY_PROJECT_TAP = "ad_interstitial_library_project_tap"

    /**
     * Interstitial ad shown when user taps a template on uninstall confirmation screen.
     * Timing: Preloaded at screen launch, shown on template tap if ready.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1879065208
     * - Secondary: ca-app-pub-7121075950716954/6956966219
     *
     * Remote Config key: ad_interstitial_uninstall_template_tap
     */
    const val INTERSTITIAL_UNINSTALL_TEMPLATE_TAP = "ad_interstitial_uninstall_template_tap"

    /**
     * Interstitial ad shown when user exits the export result screen.
     * Timing: Preloaded when export completes, shown on back/exit if ready.
     * If ad not loaded yet, navigation procee ds normally (non-blocking).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/5408312336
     * - Secondary: ca-app-pub-7121075950716954/5188820489
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
     * - Primary: ca-app-pub-7121075950716954/2782148994
     * - Secondary: ca-app-pub-7121075950716954/7537679430
     *
     * Remote Config key: ad_interstitial_asset_picker_exit
     */
    const val INTERSTITIAL_ASSET_PICKER_EXIT = "ad_interstitial_asset_picker_exit"

    /**
     * App Open Ad - BACKGROUND LAYER (shown when app comes to foreground after full backgrounding).
     * Timing: Preloaded when app goes to background, shown when app returns to foreground.
     * Triggered on onStop/onStart - full app switches (home button, switch app).
     * Automatically managed by AppOpenAdManager (lifecycle-aware).
     *
     * Behavior:
     * - Preloads when app enters background (ProcessLifecycleOwner.onStop)
     * - Shows when app enters foreground (ProcessLifecycleOwner.onStart)
     * - Skipped during splash screen (warm return detection via wasBackgrounded flag)
     * - Skipped when another fullscreen ad is showing
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4550155106
     * - Secondary: ca-app-pub-7121075950716954/7178364047
     *
     * Remote Config key: ad_appopen_aoa
     */
    const val APP_OPEN_AOA = "ad_appopen_aoa"

    /**
     * App Open Ad - FOREGROUND LAYER (shown when app loses/regains focus).
     * Timing: Triggered on onPause/onResume - quick interactions (notification, Recent Apps).
     * Priority system in onResume: Background ad (if available) > Foreground ad (fallback).
     * Automatically managed by AppOpenAdManager (lifecycle-aware).
     *
     * Behavior:
     * - Preloads when app loses focus (ProcessLifecycleOwner.onPause)
     * - Shows when app regains focus (ProcessLifecycleOwner.onResume)
     * - Acts as fallback if background ad is not ready
     * - Skipped when another fullscreen ad is showing
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6000852048
     * - Secondary: ca-app-pub-7121075950716954/9757555227
     *
     * Remote Config key: ad_appopen_foreground
     */
    const val APP_OPEN_FOREGROUND = "ad_appopen_foreground"

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
     * - Primary: ca-app-pub-7121075950716954/6103658351
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
     * - Primary: ca-app-pub-7121075950716954/6103658351
     *
     * Remote Config key: ad_banner_template_previewer
     */
    const val BANNER_TEMPLATE_PREVIEWER = "ad_banner_template_previewer"

    /**
     * Banner ad shown at bottom of asset picker screen (image selector).
     * Timing: Loaded when asset picker is displayed.
     * Displayed below the image grid and selection bar.
     *
     * Features:
     * - Adaptive banner sizing (320dp width default)
     * - Lifecycle-aware cleanup
     *
     * Ad units (priority order):
     * - Primary: TBD (temporary: ca-app-pub-7121075950716954/6103658351)
     *
     * Remote Config key: ad_banner_asset_picker
     */
    const val BANNER_ASSET_PICKER = "ad_banner_asset_picker"

    /**
     * Banner ad shown at bottom of editor screen.
     * Timing: Loaded when editor screen is displayed.
     * Displayed below the Scaffold content, outside the blur effect.
     *
     * Features:
     * - Adaptive banner sizing (320dp width default)
     * - Stays sharp when editor preview is building (outside Scaffold blur)
     * - Lifecycle-aware cleanup
     *
     * Ad units (priority order):
     * - Primary: TBD (temporary: ca-app-pub-7121075950716954/6103658351)
     *
     * Remote Config key: ad_banner_editor
     */
    const val BANNER_EDITOR = "ad_banner_editor"

    /**
     * Banner ad shown at bottom of export/result screen (all states).
     * Timing: Loaded when export screen is displayed.
     * Shown across all export states: Preparing, Processing, Success, Error, Cancelled.
     *
     * Features:
     * - Adaptive banner sizing (320dp width default)
     * - Visible during all export states (not just success)
     * - Lifecycle-aware cleanup
     *
     * Ad units (priority order):
     * - Primary: TBD (temporary: ca-app-pub-7121075950716954/6103658351)
     *
     * Remote Config key: ad_banner_export
     */
    const val BANNER_EXPORT = "ad_banner_export"

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
     * - Primary: ca-app-pub-7121075950716954/3045501749
     * - Secondary: ca-app-pub-7121075950716954/5041109698
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
     * - Primary: ca-app-pub-7121075950716954/6080295985
     * - Secondary: ca-app-pub-7121075950716954/8931032052
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
     * - Primary: ca-app-pub-7121075950716954/3797747576
     * - Secondary: ca-app-pub-7121075950716954/9064413900
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
     * - Primary: ca-app-pub-7121075950716954/3059380976
     * - Secondary: ca-app-pub-7121075950716954/5685544316
     *
     * Remote Config key: ad_native_onboarding_feature_selection_alt
     */
    const val NATIVE_ONBOARDING_FEATURE_SELECTION_ALT = "ad_native_onboarding_feature_selection_alt"

    /**
     * Feature survey screen native (shown after language). Single placement with a waterfall:
     * Pro_NA_high_select (primary) → Pro_NA_all_select (secondary). Reloaded on each selection tap.
     * Ad units: ca-app-pub-7121075950716954/5275683453 → ca-app-pub-7121075950716954/3802133920
     * Remote Config key: ad_native_onboarding_select
     */
    const val NATIVE_ONBOARDING_SELECT = "ad_native_onboarding_select"

    /**
     * ALT native ad for feature survey screen (shown after first selection, replaces NATIVE_ONBOARDING_SELECT).
     * Swapped in after 0.5s IAB viewability delay once the user makes their first selection.
     * Ad units: ca-app-pub-7121075950716954/5275683453 → ca-app-pub-7121075950716954/3802133920
     * Remote Config key: ad_native_onboarding_select_alt
     */
    const val NATIVE_ONBOARDING_SELECT_ALT = "ad_native_onboarding_select_alt"

    /**
     * Platform survey screen native (shown after the feature screen). Single placement waterfall:
     * Pro_NA_high_social (primary) → Pro_NA_all_social (secondary). Reloaded on each selection tap.
     * Ad units: ca-app-pub-7121075950716954/4700282623 → ca-app-pub-7121075950716954/3387200953
     * Remote Config key: ad_native_onboarding_social
     */
    const val NATIVE_ONBOARDING_SOCIAL = "ad_native_onboarding_social"

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
     * - Primary: ca-app-pub-7121075950716954/1923991765
     * - Secondary: ca-app-pub-7121075950716954/1559057460
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
     * - Primary: ca-app-pub-7121075950716954/6984746755
     * - Secondary: ca-app-pub-7121075950716954/9610910095
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
     * - Primary: ca-app-pub-7121075950716954/5340492594
     * - Secondary: ca-app-pub-7121075950716954/6891275199
     *
     * Remote Config key: ad_native_onboarding_page3
     */
    const val NATIVE_ONBOARDING_PAGE3 = "ad_native_onboarding_page3"

    /**
     * Native ad shown on WelcomeBackScreen.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9525024469
     * - Secondary: ca-app-pub-7121075950716954/1552989505
     *
     * Remote Config key: ad_native_welcome_back
     */
    const val NATIVE_WELCOME_BACK = "ad_native_welcome_back"

    /**
     * Native ad shown on OnboardingWelcomeBackScreen (partial onboarding progress).
     * Reuses NATIVE_WELCOME_BACK ad units initially.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9525024469
     * - Secondary: ca-app-pub-7121075950716954/1552989505
     *
     * Remote Config key: ad_native_onboarding_welcome_back
     */
    const val NATIVE_ONBOARDING_WELCOME_BACK = "ad_native_onboarding_welcome_back"

    // ==========================================
    // NATIVE ADS (In-feed, Dialogs, Bottom Sheets, Banners)
    // ==========================================

    /**
     * Home bottom banner native ad
     * Replaces standard banner with a native ad layout
     */
    const val NATIVE_HOME_BANNER = "ad_native_home_banner"

    /**
     * Collapsible native ad shown on Home screen on first entry and reopens.
     * Can be closed/collapsed by the user.
     *
     * Remote Config key: ad_native_home_collapsible
     */
    const val NATIVE_HOME_COLLAPSIBLE = "ad_native_home_collapsible"

    /**
     * Template previewer bottom banner native ad.
     * Replaces standard banner when ad_banner_use_native is true.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1709251222
     * - Secondary: ca-app-pub-7121075950716954/3435442033
     *
     * Remote Config key: ad_native_template_previewer_banner
     */
    const val NATIVE_TEMPLATE_PREVIEWER_BANNER = "ad_native_template_previewer_banner"

    /**
     * Editor bottom banner native ad.
     * Replaces standard banner when ad_banner_use_native is true.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1709251222
     * - Secondary: ca-app-pub-7121075950716954/3435442033
     *
     * Remote Config key: ad_native_editor_banner
     */
    const val NATIVE_EDITOR_BANNER = "ad_native_editor_banner"
    const val NATIVE_EDITOR_LOADING = "ad_native_editor_loading"

    /**
     * Asset picker bottom banner native ad.
     * Replaces standard banner when ad_banner_use_native is true.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1709251222
     * - Secondary: ca-app-pub-7121075950716954/3435442033
     *
     * Remote Config key: ad_native_asset_picker_banner
     */
    const val NATIVE_ASSET_PICKER_BANNER = "ad_native_asset_picker_banner"

    /**
     * Fullscreen native ad shown between onboarding pages.
     * Displayed as a full-screen overlay with close button.
     * Injected after page 1, 2, or 3 (configurable).
     * Layout: native_big_bait (large vertical layout)
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8980354700
     * - Secondary: ca-app-pub-7121075950716954/1293436372
     *
     * Remote Config key: ad_native_onboarding_fullscreen
     * Remote Config extras:
     * - close_delay: Delay in seconds before showing close button (default: 2, Meta ads: 0)
     * - inject_after: Which page to show after (1, 2, or 3, default: 2)
     */
    const val NATIVE_ONBOARDING_FULLSCREEN = "ad_native_onboarding_fullscreen"

    /**
     * Fullscreen native ad shown right after a rewarded ad (post-reward "bait").
     *
     * Preloaded the moment ANY rewarded ad is shown, then displayed immediately
     * as a fullscreen overlay while the reward ad may still be on screen.
     * Suppressed if the rewarded ad closes before this native ad finishes loading.
     * Centralized via RewardedAdPresenter, so every rewarded placement triggers it.
     *
     * Layout: native_full_screen_bait (fullscreen with prominent CTA button)
     *
     * Ad units (priority order):
     * - Primary (Pro_NAFS_high_after RW): ca-app-pub-7121075950716954/5630360530
     * - Secondary (Pro_NAFS_all_after RW): ca-app-pub-7121075950716954/4143842872
     *
     * Remote Config key: ad_native_post_reward
     */
    const val NATIVE_POST_REWARD = "ad_native_post_reward"

    /**
     * Fullscreen native ad shown after splash/open-app interstitial closes (Drama app pattern).
     * Only triggered by INTERSTITIAL_SPLASH_HIGH, SPLASH_LOW, OPEN_APP_HIGH, OPEN_APP_LOW.
     * Preloaded when interstitial is shown, displayed after interstitial closes if ready.
     * Non-blocking: if native ad isn't loaded when interstitial closes, skip silently.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6220436755
     * - Secondary: ca-app-pub-7121075950716954/3594273415
     *
     * Remote Config key: ad_native_after_splash
     */
    const val NATIVE_AFTER_SPLASH = "ad_native_after_splash"

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
     * - Primary: ca-app-pub-7121075950716954/7251804638
     * - Secondary: ca-app-pub-7121075950716954/1185923881
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
     * - Primary: ca-app-pub-7121075950716954/7123126368
     * - Secondary: ca-app-pub-7121075950716954/7176617161
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
     * - Primary: ca-app-pub-7121075950716954/5938722965
     * - Secondary: ca-app-pub-7121075950716954/1171584233
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
     * - Primary: ca-app-pub-7121075950716954/2672745463
     * - Secondary: ca-app-pub-7121075950716954/5298908808
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
     * - Primary: ca-app-pub-7121075950716954/4667301062
     * - Secondary: ca-app-pub-7121075950716954/6996887879
     *
     * Remote Config key: ad_native_projects_grid
     */
    const val NATIVE_PROJECTS_GRID = "ad_native_projects_grid"

    /**
     * Native ad shown in created projects grid, template, song (in-feed placement).
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
     * - Primary: ca-app-pub-7121075950716954/4667301062
     * - Secondary: ca-app-pub-7121075950716954/6996887879
     *
     * Remote Config key: ad_native_library_created_video
     */
    const val NATIVE_LIBRARY_CREATED_VIDEO = "ad_native_library_created_video"

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
     * - Primary: ca-app-pub-7121075950716954/1251475281
     * - Secondary: ca-app-pub-7121075950716954/7733500455
     *
     * Remote Config key: ad_native_gallery_grid
     */
    const val NATIVE_GALLERY_GRID = "ad_native_gallery_grid"

    /**
     * Native ad shown in the featured templates carousel of the gallery screen.
     * Displayed at the 2nd position.
     *
     * Ad units:
     * - Primary: ca-app-pub-7121075950716954/1840370904
     * - Secondary: ca-app-pub-7121075950716954/5831815710
     * 
     * Remote Config key: ad_native_gallery_hot_tpt
     */
    const val NATIVE_GALLERY_HOT_TPT = "ad_native_gallery_hot_tpt"

    /**
     * Native ad shown in songs tab station section (in-feed placement).
     * Displayed as an item within the station songs vertical list.
     * Position: 4th position (index 3), or last position if total items < 3.
     * Persists through genre chip tag filtering.
     *
     * Layout: native_big_bait (large vertical layout with clickbait CTA)
     * - Media view at top
     * - Headline + body text below
     * - CTA button at bottom
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2115862172
     * - Secondary: ca-app-pub-7121075950716954/3794255449
     *
     * Remote Config key: ad_native_songs_station
     */
    const val NATIVE_SONGS_STATION = "ad_native_songs_station"

    /**
     * Native ad inserted every Xth position in the station songs list (in-feed repeating).
     * X is configurable via Remote Config extras "infeed_interval" (default: 10).
     * If total songs < X but >= 1, ad is shown after the last song.
     * Same placement reused across all genre tabs.
     *
     * Layout: native_small_row (horizontal row, matches song list items)
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6943442204
     * - Secondary: ca-app-pub-7121075950716954/5456924546
     *
     * Remote Config key: ad_native_station_infeed
     */
    const val NATIVE_STATION_INFEED = "ad_native_station_infeed"

    /**
     * Native ad inserted every Xth position in the weekly ranking full list.
     * Same config structure as NATIVE_STATION_INFEED.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6943442204
     * - Secondary: ca-app-pub-7121075950716954/5456924546
     *
     * Remote Config key: ad_native_ranking_infeed
     */
    const val NATIVE_RANKING_INFEED = "ad_native_ranking_infeed"

    /**
     * Native ad inserted every Xth position in the suggested songs full list.
     * Same config structure as NATIVE_STATION_INFEED.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6943442204
     * - Secondary: ca-app-pub-7121075950716954/5456924546
     *
     * Remote Config key: ad_native_suggested_infeed
     */
    const val NATIVE_SUGGESTED_INFEED = "ad_native_suggested_infeed"

    /**
     * Native ad inserted every Xth position in search music results.
     * Same config structure as NATIVE_STATION_INFEED.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6943442204
     * - Secondary: ca-app-pub-7121075950716954/5456924546
     *
     * Remote Config key: ad_native_search_music_infeed
     */
    const val NATIVE_SEARCH_MUSIC_INFEED = "ad_native_search_music_infeed"

    /**
     * Native ad inserted every Xth position in the editor music selector (bottom sheet) song list.
     * Same config structure as NATIVE_STATION_INFEED.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/6943442204
     * - Secondary: ca-app-pub-7121075950716954/5456924546
     *
     * Remote Config key: ad_native_editor_music_infeed
     */
    const val NATIVE_EDITOR_MUSIC_INFEED = "ad_native_editor_music_infeed"

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
     * - Primary: ca-app-pub-7121075950716954/9046582121
     * - Secondary: ca-app-pub-7121075950716954/1359663791
     *
     * Remote Config key: ad_native_export_generating
     */
    const val NATIVE_EXPORT_GENERATING = "ad_native_export_generating"

    /**
     * Native "banner" ad shown at the bottom of the export Preparing screen.
     * Replaces the bottom banner (BANNER_EXPORT) only while state is Preparing.
     * Rendered banner-size via the native_small_row layout.
     *
     * Ad units (priority order):
     * - Primary (Pro_NA_high_Bottom BN): ca-app-pub-7121075950716954/1709251222
     * - Secondary (Pro_NA_all_Bottom BN): ca-app-pub-7121075950716954/3435442033
     *
     * Remote Config key: ad_native_export_preparing
     */
    const val NATIVE_EXPORT_PREPARING = "ad_native_export_preparing"

    /**
     * Native ad shown on the export Result (Success) screen,
     * placed right above the "Try Another Templates" section.
     *
     * Ad units (priority order):
     * - Primary (Pro_NA_high_result): ca-app-pub-7121075950716954/6973716424
     * - Secondary (Pro_NA_all_result): ca-app-pub-7121075950716954/8652109693
     *
     * Remote Config key: ad_native_export_result
     */
    const val NATIVE_EXPORT_RESULT = "ad_native_export_result"

    /**
     * Native ad shown in template ratio selection bottom sheet.
     * Timing: Loaded when the select ratio bottom sheet is displayed.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7313933710
     * - Secondary: ca-app-pub-7121075950716954/3818832831
     *
     * Remote Config key: ad_native_template_ratio_sheet
     */
    const val NATIVE_TEMPLATE_RATIO_SHEET = "ad_native_template_ratio_sheet"

    /**
     * Native ad shown at the bottom of the trending template popup (Gallery tab).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7380310494
     * - Secondary: ca-app-pub-7121075950716954/5252536687
     *
     * Remote Config key: ad_native_popup_trending_template
     */
    const val NATIVE_POPUP_TRENDING_TEMPLATE = "ad_native_popup_trending_template"

    /**
     * Native ad shown at the bottom of the trending song popup (Songs tab).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1938387916
     * - Secondary: ca-app-pub-7121075950716954/8994750854
     *
     * Remote Config key: ad_native_popup_trending_song
     */
    const val NATIVE_POPUP_TRENDING_SONG = "ad_native_popup_trending_song"

    /**
     * Native ad shown inside the music player bottom sheet (above the CTA button).
     * Small horizontal banner-style placement that blends with the player controls.
     *
     * Layout: native_small_row (horizontal row matching banner dimensions)
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/4156165171
     * - Secondary: ca-app-pub-7121075950716954/9216920167
     *
     * Remote Config key: ad_native_music_player
     */
    const val NATIVE_MUSIC_PLAYER = "ad_native_music_player"

    /**
     * Native ad shown at bottom of onboarding genre selection screen.
     * NA_high_select_music (primary) → NA_all_select_music (secondary waterfall).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/3016683795  (NA_high_select_music)
     * - Secondary: ca-app-pub-7121075950716954/8624233693  (NA_all_select_music)
     *
     * Remote Config key: ad_native_onboarding_select_music
     */
    const val NATIVE_ONBOARDING_SELECT_MUSIC = "ad_native_onboarding_select_music"

    /**
     * Native ad shown at bottom of onboarding template pick screen.
     * NA_high_select_tpt (primary) → NA_all_select_tpt (secondary waterfall).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/3543297405  (NA_high_select_tpt)
     * - Secondary: ca-app-pub-7121075950716954/7166847649  (NA_all_select_tpt)
     *
     * Remote Config key: ad_native_onboarding_select_tpt
     */
    const val NATIVE_ONBOARDING_SELECT_TPT = "ad_native_onboarding_select_tpt"

    /**
     * Native ad shown at bottom of onboarding personalizing/loading screen.
     * NA_high_personalizing (primary) → NA_all_personalizing (secondary waterfall).
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/8979456910  (NA_high_personalizing)
     * - Secondary: ca-app-pub-7121075950716954/8646238825  (NA_all_personalizing)
     *
     * Remote Config key: ad_native_onboarding_personalizing
     */
    const val NATIVE_ONBOARDING_PERSONALIZING = "ad_native_onboarding_personalizing"

    /**
     * Native ad — onboarding AI creation-style (AI_LEVEL / Start Selection) survey screen.
     * Single placement waterfall, shown until the user makes their first selection.
     * Ad units: ca-app-pub-7121075950716954/7416163032 (Pro_AIMV_NA_high_Start Selection)
     *         → ca-app-pub-7121075950716954/2091839691 (Pro_AIMV_NA_all_Start Selection)
     * RC key: ad_native_onboarding_ai_level
     */
    const val NATIVE_ONBOARDING_AI_LEVEL = "ad_native_onboarding_ai_level"

    /**
     * ALT native ad for AI_LEVEL / Start Selection screen (swapped in after the first selection,
     * replaces NATIVE_ONBOARDING_AI_LEVEL following a 0.5s IAB viewability delay).
     * Ad units: ca-app-pub-7121075950716954/4789999695 (Pro_AIMV_NA_high_Start Selection_alt)
     *         → ca-app-pub-7121075950716954/4814050422 (Pro_AIMV_NA_all_Start Selection_alt)
     * RC key: ad_native_onboarding_ai_level_alt
     */
    const val NATIVE_ONBOARDING_AI_LEVEL_ALT = "ad_native_onboarding_ai_level_alt"

    /**
     * Native ad — onboarding content-filter / age (CONTENT_EXCLUSIVE) screen. Single placement
     * waterfall, shown until the user makes their first selection.
     * Ad units: ca-app-pub-7121075950716954/9874805416 (Pro_AIMV_NA_high_Age)
     *         → ca-app-pub-7121075950716954/8465676358 (Pro_AIMV_NA_all_Age)
     * RC key: ad_native_onboarding_content_exclusive
     */
    const val NATIVE_ONBOARDING_CONTENT_EXCLUSIVE = "ad_native_onboarding_content_exclusive"

    /**
     * ALT native ad for content-filter / age screen (swapped in after the first selection, replaces
     * NATIVE_ONBOARDING_CONTENT_EXCLUSIVE following a 0.5s IAB viewability delay).
     * Ad units: ca-app-pub-7121075950716954/4598428004 (Pro_AIMV_NA_high_Age_alt)
     *         → ca-app-pub-7121075950716954/9683233721 (Pro_AIMV_NA_all_Age_alt)
     * RC key: ad_native_onboarding_content_exclusive_alt
     */
    const val NATIVE_ONBOARDING_CONTENT_EXCLUSIVE_ALT = "ad_native_onboarding_content_exclusive_alt"

    /**
     * Native ad — onboarding photo-privacy (MEDIA_PRIVACY) screen. Single placement waterfall,
     * shown until the user makes their first selection.
     * Ad units: ca-app-pub-7121075950716954/7057070389 (Pro_AIMV_NA_high_Media Privacy)
     *         → ca-app-pub-7121075950716954/8178580360 (Pro_AIMV_NA_all_Media Privacy)
     * RC key: ad_native_onboarding_media_privacy
     */
    const val NATIVE_ONBOARDING_MEDIA_PRIVACY = "ad_native_onboarding_media_privacy"

    /**
     * ALT native ad for photo-privacy screen (swapped in after the first selection, replaces
     * NATIVE_ONBOARDING_MEDIA_PRIVACY following a 0.5s IAB viewability delay).
     * Ad units: ca-app-pub-7121075950716954/3093774644 (Pro_AIMV_NA_high_Media Privacy_alt)
     *         → ca-app-pub-7121075950716954/2926253685 (Pro_AIMV_NA_all_Media Privacy_alt)
     * RC key: ad_native_onboarding_media_privacy_alt
     */
    const val NATIVE_ONBOARDING_MEDIA_PRIVACY_ALT = "ad_native_onboarding_media_privacy_alt"

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
     * - Primary: ca-app-pub-7121075950716954/4370724539  (Pro_RI_high_download)
     * - Secondary: ca-app-pub-7121075950716954/6805316184  (Pro_RI_all_download)
     *
     * Remote Config key: ad_reward_inter_download_video
     */
    const val REWARD_INTER_DOWNLOAD_VIDEO = "ad_reward_inter_download_video"

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
     * - Primary: ca-app-pub-7121075950716954/1902209378
     * - Secondary: ca-app-pub-7121075950716954/5322579631
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
     * - Primary: ca-app-pub-7121075950716954/6550003612
     * - Secondary: ca-app-pub-7121075950716954/6827232991
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
     * - Primary: ca-app-pub-7121075950716954/2285352752
     * - Secondary: ca-app-pub-7121075950716954/3079559673
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

    // ============================================
    // INTERSTITIAL — MUSIC PLAYER "TRY IT"
    // ============================================

    /**
     * Interstitial ad shown when user taps "Try it" in music player for a free/unlocked song.
     * Timing: Preloaded when music player opens, shown on "Try it" tap if ready.
     * If ad not loaded yet, navigation proceeds normally (non-blocking).
     * Premium/locked songs show rewarded ad (REWARD_UNLOCK_SONG) instead.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/7530599719
     * - Secondary: ca-app-pub-7121075950716954/4904436375
     *
     * Remote Config key: ad_interstitial_music_player_try
     */
    const val INTERSTITIAL_MUSIC_PLAYER_TRY = "ad_interstitial_music_player_try"

    // ============================================
    // INTERSTITIAL — PHOTO PICKER DONE (EDIT MODE)
    // ============================================

    /**
     * Interstitial ad shown when user taps "Done" in photo picker while in edit mode.
     * Timing: Loaded on tap with loading overlay, 10s timeout.
     * If ad fails to load, navigation proceeds normally (non-blocking).
     * Only shown in edit mode (replacing images from editor), not create/template modes.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/1254485881
     * - Secondary: ca-app-pub-7121075950716954/9965191360
     *
     * Remote Config key: ad_interstitial_picker_done
     */
    const val INTERSTITIAL_PICKER_DONE = "ad_interstitial_picker_done"

    /**
     * Interstitial ad shown when user completes onboarding ("Get started" / last page CTA).
     * Preloaded on onboarding screen init. Non-blocking: if ad not ready, proceed immediately.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/9773619674
     * - Secondary: ca-app-pub-7121075950716954/2667109676
     *
     * Remote Config key: ad_interstitial_onboarding_complete
     */
    const val INTERSTITIAL_ONBOARDING_COMPLETE = "ad_interstitial_onboarding_complete"

    /**
     * Interstitial ad shown when the app is opened from a notification tap.
     * Timing: Shown with loading overlay immediately when notification intent is detected.
     * Blocks navigation until ad loads or times out (10s).
     *
     * Ad units:
     * - Primary: ca-app-pub-7121075950716954/6137626994
     *
     * Remote Config key: ad_interstitial_notification_open
     */
    const val INTERSTITIAL_NOTIFICATION_OPEN = "ad_interstitial_notification_open"

    // ============================================
    // APP OPEN — POST AD CLICK
    // ============================================

    /**
     * App Open Ad shown when user returns after clicking a banner/native ad.
     * Uses dedicated ad units to allow independent monetization tuning
     * for post-click returns vs normal app opens.
     *
     * Disabled by default — enable via Firebase Remote Config to monetize post-click returns.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/2469844727
     * - Secondary: ca-app-pub-7121075950716954/8843681380
     *
     * Remote Config key: ad_appopen_aoa_after_click
     */
    const val APP_OPEN_AFTER_AD_CLICK = "ad_appopen_aoa_after_click"

    // ============================================
    // INTERSTITIAL — QUALITY UNLOCK
    // ============================================

    /**
     * Interstitial ad shown when user taps Done with locked quality (720p/1080p)
     * and Remote Config routes to interstitial instead of rewarded.
     * Unlock happens when user CLOSES the ad (action callback), not on show.
     *
     * Ad units (priority order):
     * - Primary: ca-app-pub-7121075950716954/3669507647
     * - Secondary: ca-app-pub-7121075950716954/3529906841
     *
     * Remote Config key: ad_interstitial_unlock_quality
     */
    const val INTERSTITIAL_UNLOCK_QUALITY = "ad_interstitial_unlock_quality"

    /**
     * List of all ad placement IDs.
     * Used by AdInitializer to validate that all placements are registered.
     */
    val ALL_PLACEMENTS = listOf(
        APP_OPEN_AOA,
        APP_OPEN_FOREGROUND,
        APP_OPEN_AFTER_AD_CLICK,
        INTERSTITIAL_SPLASH_HIGH,
        INTERSTITIAL_SPLASH_LOW,
        INTERSTITIAL_OPEN_APP_HIGH,
        INTERSTITIAL_OPEN_APP_LOW,
        INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
        INTERSTITIAL_TEMPLATE_PREVIEWER_SCROLL,
        INTERSTITIAL_TEMPLATE_PREVIEWER_USE,
        INTERSTITIAL_EDITOR_BACK,
        INTERSTITIAL_EDITOR_AFTER_PREPARE,
        INTERSTITIAL_ONBOARDING,
        INTERSTITIAL_TEMPLATE_GRID_TAP,
        INTERSTITIAL_LIBRARY_PROJECT_TAP,
        INTERSTITIAL_UNINSTALL_TEMPLATE_TAP,
        INTERSTITIAL_EXPORT_RESULT_EXIT,
        INTERSTITIAL_ASSET_PICKER_EXIT,
        INTERSTITIAL_UNLOCK_QUALITY,   // ← add this line
        BANNER_HOME,
        BANNER_TEMPLATE_PREVIEWER,
        BANNER_ASSET_PICKER,
        BANNER_EDITOR,
        BANNER_EXPORT,
        NATIVE_WELCOME_BACK,
        NATIVE_ONBOARDING_WELCOME_BACK,
        NATIVE_ONBOARDING_LANGUAGE,
        NATIVE_ONBOARDING_LANGUAGE_ALT,
        NATIVE_ONBOARDING_PAGE1,
        NATIVE_ONBOARDING_PAGE2,
        NATIVE_ONBOARDING_PAGE3,
        NATIVE_ONBOARDING_FULLSCREEN,
        NATIVE_POST_REWARD,
        NATIVE_AFTER_SPLASH,
        NATIVE_ONBOARDING_FEATURE_SELECTION,
        NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
        NATIVE_SEARCH_INFEED,
        NATIVE_UNINSTALL_BOTTOM,
        NATIVE_WIDGET_BOTTOM,
        NATIVE_LIBRARY_CREATED_VIDEO,
        NATIVE_HOME_BANNER,
        NATIVE_HOME_COLLAPSIBLE,
        NATIVE_TEMPLATE_PREVIEWER_BANNER,
        NATIVE_EDITOR_BANNER,
        NATIVE_EDITOR_LOADING,
        NATIVE_ASSET_PICKER_BANNER,
        NATIVE_GALLERY_GRID,
        NATIVE_GALLERY_HOT_TPT,
        NATIVE_SONGS_STATION,
        NATIVE_STATION_INFEED,
        NATIVE_RANKING_INFEED,
        NATIVE_SUGGESTED_INFEED,
        NATIVE_SEARCH_MUSIC_INFEED,
        NATIVE_EDITOR_MUSIC_INFEED,
        NATIVE_TEMPLATE_PREVIEWER_LOADING,
        NATIVE_TEMPLATE_RATIO_SHEET,
        NATIVE_POPUP_TRENDING_TEMPLATE,
        NATIVE_POPUP_TRENDING_SONG,
        NATIVE_MUSIC_PLAYER,
        NATIVE_EXPORT_GENERATING,
        NATIVE_EXPORT_PREPARING,
        NATIVE_EXPORT_RESULT,
        NATIVE_ONBOARDING_SELECT_MUSIC,
        NATIVE_ONBOARDING_SELECT_TPT,
        NATIVE_ONBOARDING_PERSONALIZING,
        NATIVE_ONBOARDING_AI_LEVEL,
        NATIVE_ONBOARDING_AI_LEVEL_ALT,
        NATIVE_ONBOARDING_CONTENT_EXCLUSIVE,
        NATIVE_ONBOARDING_CONTENT_EXCLUSIVE_ALT,
        NATIVE_ONBOARDING_MEDIA_PRIVACY,
        NATIVE_ONBOARDING_MEDIA_PRIVACY_ALT,
        NATIVE_ONBOARDING_SELECT,
        NATIVE_ONBOARDING_SELECT_ALT,
        NATIVE_ONBOARDING_SOCIAL,
        REWARD_INTER_DOWNLOAD_VIDEO,
        REWARD_REMOVE_WATERMARK,
        REWARD_UNLOCK_QUALITY,
        REWARD_UNLOCK_EFFECT_SET,
        REWARD_UNLOCK_TEMPLATE,
        REWARD_UNLOCK_SONG,
        INTERSTITIAL_MUSIC_PLAYER_TRY,
        INTERSTITIAL_PICKER_DONE,
        INTERSTITIAL_ONBOARDING_COMPLETE,
        INTERSTITIAL_NOTIFICATION_OPEN
    )
}
