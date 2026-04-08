package com.videomaker.aimusic.core.analytics

/**
 * Analytics Event Constants - Video Maker Photo Music
 *
 * Event names and parameter keys for analytics tracking.
 * Currently minimal - will be expanded as features are instrumented.
 *
 * Usage:
 * ```kotlin
 * Analytics.track(
 *     name = AnalyticsEvent.APP_INIT_TIME,
 *     params = mapOf(
 *         AnalyticsEvent.Param.VALUE to durationMs
 *     )
 * )
 * ```
 */
object AnalyticsEvent {

    // ============================================
    // APP LIFECYCLE
    // ============================================
    const val APP_INIT_TIME = "app_init_time"

    // ============================================
    // PARAMETER KEYS
    // ============================================
    object Param {
        const val VALUE = "value"
        const val TYPE = "type"
        const val SOURCE = "source"
    }

    // ============================================
    // SCREEN NAMES
    // ============================================
    object Screen {
        const val ROOT = "root"
        const val LANGUAGE_SELECTION = "language_selection"
        const val ONBOARDING = "onboarding"
        const val FEATURE_SELECTION = "feature_selection"
        const val HOME = "home"
    }
}
