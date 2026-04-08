package com.videomaker.aimusic.core.analytics

import co.alcheclub.lib.acccore.analytics.AnalyticsCoordinator

/**
 * Analytics Helper - Video Maker Photo Music
 *
 * Simple wrapper around ACCCore's AnalyticsCoordinator.
 * Automatically broadcasts to all registered analytics platforms:
 * - Firebase Analytics (active)
 * - Facebook Analytics (active)
 * - AppsFlyer (active)
 *
 * Usage:
 * ```kotlin
 * // Track event with parameters
 * Analytics.track(
 *     name = AnalyticsEvent.TEMPLATE_SELECT,
 *     params = mapOf(
 *         AnalyticsEvent.Param.VALUE to templateId,
 *         AnalyticsEvent.Param.TYPE to "birthday"
 *     )
 * )
 *
 * // Track event without parameters
 * Analytics.track(AnalyticsEvent.PROJECT_CREATE)
 *
 * // Track screen view
 * Analytics.trackScreenView(
 *     screenName = AnalyticsEvent.Screen.EDITOR,
 *     screenClass = "EditorScreen"
 * )
 *
 * // Track with sanitized user input
 * Analytics.track(
 *     name = AnalyticsEvent.SEARCH_SUBMIT,
 *     params = mapOf(
 *         AnalyticsEvent.Param.VALUE to Analytics.sanitize(searchQuery)
 *     )
 * )
 * ```
 */
object Analytics {

    /**
     * Sanitize a string for analytics parameters.
     *
     * - Removes special characters (keeps alphanumeric, spaces, hyphens, underscores)
     * - Truncates to maxLength characters
     *
     * @param value The string to sanitize
     * @param maxLength Maximum length (default 100)
     * @return Sanitized string safe for analytics
     */
    fun sanitize(value: String, maxLength: Int = 100): String {
        return value
            .replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "")
            .take(maxLength)
            .trim()
    }

    /**
     * Track an analytics event.
     *
     * Broadcasts to all registered analytics platforms via ACCCore's AnalyticsCoordinator.
     * Active platforms: Firebase Analytics, Facebook Analytics, AppsFlyer
     *
     * @param name Event name (use AnalyticsEvent constants)
     * @param params Event parameters (use AnalyticsEvent.Param constants for keys)
     */
    fun track(name: String, params: Map<String, Any>? = null) {
        val coordinator = org.koin.core.context.GlobalContext.get().get<AnalyticsCoordinator>()
        coordinator.track(name, params)
    }

    /**
     * Track a screen view event.
     *
     * Calls AnalyticsCoordinator.trackScreenView() which broadcasts to all
     * registered analytics platforms. Each platform adapter translates to
     * its native format:
     * - Firebase: FirebaseAnalytics.logEvent(SCREEN_VIEW) with SCREEN_NAME/SCREEN_CLASS
     * - Facebook: AppEventsLogger with screen event
     * - AppsFlyer: Custom event with screen parameters
     *
     * @param screenName The screen name (use AnalyticsEvent.Screen constants)
     * @param screenClass The screen class name (e.g., "GalleryScreen", "EditorScreen")
     */
    fun trackScreenView(screenName: String, screenClass: String) {
        val coordinator = org.koin.core.context.GlobalContext.get().get<AnalyticsCoordinator>()
        coordinator.trackScreenView(screenName, screenClass)
    }
}
