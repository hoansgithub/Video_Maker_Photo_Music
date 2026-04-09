package com.videomaker.aimusic.core.analytics

import co.alcheclub.lib.acccore.analytics.AnalyticsCoordinator
import java.util.LinkedHashSet
import java.util.UUID

/**
 * Analytics Helper - Video Maker Photo Music
 *
 * Centralized analytics entry point with:
 * - Low-level passthrough to AnalyticsCoordinator
 * - Typed tracking APIs for normalized schema
 * - Runtime policies (normal / impression dedupe / deferred-on-missing-data)
 */
object Analytics {

    private const val DEFAULT_DEFER_TTL_MS = 10_000L

    enum class TrackingPolicy {
        NORMAL,
        IMPRESSION,
        DEFER_IF_MISSING
    }

    private data class PendingEvent(
        val eventName: String,
        val params: MutableMap<String, Any>,
        val requiredParams: Set<String>,
        val dedupeKey: String?,
        val expiresAtMs: Long,
        val correlationId: String
    )

    private val stateLock = Any()
    private val impressionDedupeKeys = LinkedHashSet<String>()
    private val pendingEvents = mutableListOf<PendingEvent>()
    private val locationAwareTemplateSongEvents = setOf(
        AnalyticsEvent.TEMPLATE_IMPRESSION,
        AnalyticsEvent.TEMPLATE_CLICK,
        AnalyticsEvent.TEMPLATE_PREVIEW,
        AnalyticsEvent.TEMPLATE_OPTION,
        AnalyticsEvent.TEMPLATE_FAVORITE,
        AnalyticsEvent.TEMPLATE_UNFAVORITE,
        AnalyticsEvent.TEMPLATE_SHARE,
        AnalyticsEvent.TEMPLATE_REPORT,
        AnalyticsEvent.TEMPLATE_SELECT,
        AnalyticsEvent.SONG_IMPRESSION,
        AnalyticsEvent.SONG_OPTION,
        AnalyticsEvent.SONG_CLICK,
        AnalyticsEvent.SONG_PLAY,
        AnalyticsEvent.SONG_PAUSE,
        AnalyticsEvent.SONG_PREVIEW,
        AnalyticsEvent.SONG_FAVORITE,
        AnalyticsEvent.SONG_UNFAVORITE,
        AnalyticsEvent.SONG_SHARE,
        AnalyticsEvent.SONG_SELECT
    )

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

    fun newScreenSessionId(): String = UUID.randomUUID().toString()

    /**
     * Track an analytics event.
     *
     * Broadcasts to all registered analytics platforms via ACCCore's AnalyticsCoordinator.
     * Currently active: Firebase Analytics
     * Ready to enable: Meta/Facebook Analytics, AppsFlyer
     *
     * @param name Event name (use AnalyticsEvent constants)
     * @param params Event parameters (use AnalyticsEvent.Param constants for keys)
     */
    fun track(name: String, params: Map<String, Any>? = null) {
        pruneExpiredPendingEvents()
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
     * - Meta: AppEventsLogger with screen event (when enabled)
     * - AppsFlyer: Custom event (when enabled)
     *
     * @param screenName The screen name (use AnalyticsEvent.Screen constants)
     * @param screenClass The screen class name (e.g., "GalleryScreen", "EditorScreen")
     */
    fun trackScreenView(screenName: String, screenClass: String) {
        val coordinator = org.koin.core.context.GlobalContext.get().get<AnalyticsCoordinator>()
        coordinator.trackScreenView(screenName, screenClass)
    }

    fun clearRuntimeState() {
        synchronized(stateLock) {
            impressionDedupeKeys.clear()
            pendingEvents.clear()
        }
    }

    fun resolveDeferred(
        correlationId: String,
        supplementalParams: Map<String, Any>
    ) {
        if (correlationId.isBlank()) return
        val normalizedSupplemental = normalizeParams(supplementalParams)
        if (normalizedSupplemental.isEmpty()) return

        val readyToSend = mutableListOf<Pair<String, Map<String, Any>>>()
        synchronized(stateLock) {
            pruneExpiredPendingEventsLocked(System.currentTimeMillis())
            val iterator = pendingEvents.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending.correlationId != correlationId) continue

                pending.params.putAll(normalizedSupplemental)
                if (missingRequired(pending.requiredParams, pending.params).isEmpty()) {
                    val finalParams = pending.params.toMutableMap()
                    finalParams.remove(AnalyticsEvent.Param.SCREEN_SESSION_ID)
                    if (pending.dedupeKey == null || impressionDedupeKeys.add(pending.dedupeKey)) {
                        readyToSend += pending.eventName to finalParams
                    }
                    iterator.remove()
                }
            }
        }

        readyToSend.forEach { (eventName, eventParams) ->
            track(eventName, eventParams)
        }
    }

    // ============================================
    // TYPED APIS - PHASE 2 / 3 / 5 FOUNDATION
    // ============================================

    fun trackTabView(tabName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TAB_VIEW,
            params = mapOf(AnalyticsEvent.Param.TAB_NAME to tabName),
            requiredParams = setOf(AnalyticsEvent.Param.TAB_NAME),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTabSwitch(from: String, to: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TAB_SWITCH,
            params = mapOf(
                AnalyticsEvent.Param.FROM to from,
                AnalyticsEvent.Param.TO to to
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.FROM,
                AnalyticsEvent.Param.TO
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackGallerySwipe(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.GALLERY_SWIPE,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateGenreClick(
        genreId: String,
        genreName: String,
        location: String
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_GENRE_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.GENRE_ID to genreId,
                AnalyticsEvent.Param.GENRE_NAME to genreName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.GENRE_ID,
                AnalyticsEvent.Param.GENRE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongTabSwipe(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_TAB_SWIPE,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongGenreClick(
        genreId: String,
        genreName: String,
        location: String
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_GENRE_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.GENRE_ID to genreId,
                AnalyticsEvent.Param.GENRE_NAME to genreName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.GENRE_ID,
                AnalyticsEvent.Param.GENRE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackLibraryClick(from: String, to: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.LIBRARY_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.FROM to from,
                AnalyticsEvent.Param.TO to to
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.FROM,
                AnalyticsEvent.Param.TO
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateImpression(
        templateId: String,
        templateName: String,
        location: String,
        screenSessionId: String
    ) {
        val params = mapOf(
            AnalyticsEvent.Param.TEMPLATE_ID to templateId,
            AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
            AnalyticsEvent.Param.LOCATION to location,
            AnalyticsEvent.Param.SCREEN_SESSION_ID to screenSessionId
        )
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_IMPRESSION,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.IMPRESSION
        )
    }

    fun trackSongImpression(
        songId: String,
        songName: String,
        location: String,
        screenSessionId: String
    ) {
        val params = mapOf(
            AnalyticsEvent.Param.SONG_ID to songId,
            AnalyticsEvent.Param.SONG_NAME to songName,
            AnalyticsEvent.Param.LOCATION to location,
            AnalyticsEvent.Param.SCREEN_SESSION_ID to screenSessionId
        )
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_IMPRESSION,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.IMPRESSION
        )
    }

    fun trackTemplateClick(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplatePreview(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_PREVIEW,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateFavorite(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_FAVORITE,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateUnfavorite(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_UNFAVORITE,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateSelect(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongClick(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongPlay(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_PLAY,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongPause(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_PAUSE,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongPreview(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_PREVIEW,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongFavorite(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_FAVORITE,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongUnfavorite(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_UNFAVORITE,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongSelect(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackCreationStart(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.CREATION_START,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioClick(ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_CLICK,
            params = mapOf(AnalyticsEvent.Param.RATIO_SIZE to ratioSize),
            requiredParams = setOf(AnalyticsEvent.Param.RATIO_SIZE),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioSelect(ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_SELECT,
            params = mapOf(AnalyticsEvent.Param.RATIO_SIZE to ratioSize),
            requiredParams = setOf(AnalyticsEvent.Param.RATIO_SIZE),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackMediaRender() {
        trackWithPolicy(
            eventName = AnalyticsEvent.MEDIA_RENDER,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackMediaSelect() {
        trackWithPolicy(
            eventName = AnalyticsEvent.MEDIA_SELECT,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackMediaUnselect() {
        trackWithPolicy(
            eventName = AnalyticsEvent.MEDIA_UNSELECT,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackMediaComplete(mediaQuantity: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.MEDIA_COMPLETE,
            params = mapOf(AnalyticsEvent.Param.MEDIA_QUANTITY to mediaQuantity),
            requiredParams = setOf(AnalyticsEvent.Param.MEDIA_QUANTITY),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchOpen(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_OPEN,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchType(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_TYPE,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchSubmit(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_SUBMIT,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchResultView(keyword: String, templateCount: Int, musicCount: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_RESULT_VIEW,
            params = mapOf(
                AnalyticsEvent.Param.KEYWORD to keyword,
                AnalyticsEvent.Param.TEMPLATE_COUNT to templateCount,
                AnalyticsEvent.Param.MUSIC_COUNT to musicCount
            ),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchNoResult(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_NO_RESULT,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchClick(type: String, keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.TYPE to type,
                AnalyticsEvent.Param.KEYWORD to keyword
            ),
            requiredParams = setOf(AnalyticsEvent.Param.TYPE, AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchSuggestClick(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_SUGGEST_CLICK,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchRecentDelete(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_RECENT_DELETE,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchCancel(keyword: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_CANCEL,
            params = mapOf(AnalyticsEvent.Param.KEYWORD to keyword),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSettingOpen(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SETTING_OPEN,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSettingView(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SETTING_VIEW,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSettingOptionClick(option: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SETTING_OPTION_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.OPTION to option,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.OPTION,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateOption(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_OPTION,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateShare(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_SHARE,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackTemplateReport(templateId: String, templateName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.TEMPLATE_REPORT,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongOption(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_OPTION,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongShare(songId: String, songName: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_SHARE,
            params = mapOf(
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoGenerate(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_GENERATE,
            params = buildVideoParams(
                videoId = videoId,
                templateId = templateId,
                songId = songId,
                quality = quality,
                duration = duration,
                ratioSize = ratioSize,
                volume = volume,
                mediaQuality = mediaQuality,
                mediaQuantity = mediaQuantity
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoGenerateComplete(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_GENERATE_COMPLETE,
            params = buildVideoParams(
                videoId = videoId,
                templateId = templateId,
                songId = songId,
                quality = quality,
                duration = duration,
                ratioSize = ratioSize,
                volume = volume,
                mediaQuality = mediaQuality,
                mediaQuantity = mediaQuantity
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoPreview(videoId: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_PREVIEW,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoPreviewComplete(videoId: String, templateId: String? = null) {
        val params = linkedMapOf<String, Any>(
            AnalyticsEvent.Param.VIDEO_ID to videoId
        ).apply {
            if (!templateId.isNullOrBlank()) {
                put(AnalyticsEvent.Param.TEMPLATE_ID, templateId)
            }
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_PREVIEW_COMPLETE,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoPlay(videoId: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_PLAY,
            params = mapOf(AnalyticsEvent.Param.VIDEO_ID to videoId),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoPause(videoId: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_PAUSE,
            params = mapOf(AnalyticsEvent.Param.VIDEO_ID to videoId),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEffectEdit(videoId: String, templateId: String? = null) {
        val params = linkedMapOf<String, Any>(AnalyticsEvent.Param.VIDEO_ID to videoId).apply {
            if (!templateId.isNullOrBlank()) {
                put(AnalyticsEvent.Param.TEMPLATE_ID, templateId)
            }
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.EFFECT_EDIT,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEffectClick(videoId: String, effectId: String, effectName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.EFFECT_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.EFFECT_ID to effectId,
                AnalyticsEvent.Param.EFFECT_NAME to effectName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.EFFECT_ID,
                AnalyticsEvent.Param.EFFECT_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEffectSelect(videoId: String, effectId: String, effectName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.EFFECT_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.EFFECT_ID to effectId,
                AnalyticsEvent.Param.EFFECT_NAME to effectName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.EFFECT_ID,
                AnalyticsEvent.Param.EFFECT_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEffectClose(videoId: String, effectId: String? = null, effectName: String? = null) {
        val params = linkedMapOf<String, Any>(
            AnalyticsEvent.Param.VIDEO_ID to videoId
        ).apply {
            if (!effectId.isNullOrBlank()) put(AnalyticsEvent.Param.EFFECT_ID, effectId)
            if (!effectName.isNullOrBlank()) put(AnalyticsEvent.Param.EFFECT_NAME, effectName)
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.EFFECT_CLOSE,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackDurationEdit(videoId: String, durationNumber: Long) {
        trackWithPolicy(
            eventName = AnalyticsEvent.DURATION_EDIT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.DURATION_NUMBER to durationNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackDurationClick(videoId: String, durationNumber: Long) {
        trackWithPolicy(
            eventName = AnalyticsEvent.DURATION_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.DURATION_NUMBER to durationNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackDurationSelect(videoId: String, durationNumber: Long) {
        trackWithPolicy(
            eventName = AnalyticsEvent.DURATION_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.DURATION_NUMBER to durationNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackDurationClose(videoId: String, durationNumber: Long) {
        trackWithPolicy(
            eventName = AnalyticsEvent.DURATION_CLOSE,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.DURATION_NUMBER to durationNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioEdit(videoId: String, ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_EDIT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.RATIO_SIZE to ratioSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.RATIO_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioClick(videoId: String, ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.RATIO_SIZE to ratioSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.RATIO_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioSelect(videoId: String, ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.RATIO_SIZE to ratioSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.RATIO_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRatioClose(videoId: String, ratioSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATIO_CLOSE,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.RATIO_SIZE to ratioSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.RATIO_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVolumeEdit(videoId: String, volumeNumber: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VOLUME_EDIT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.VOLUME_NUMBER to volumeNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVolumeClick(videoId: String, volumeNumber: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VOLUME_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.VOLUME_NUMBER to volumeNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVolumeSelect(videoId: String, volumeNumber: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VOLUME_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.VOLUME_NUMBER to volumeNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVolumeClose(videoId: String, volumeNumber: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VOLUME_CLOSE,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.VOLUME_NUMBER to volumeNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongEdit(videoId: String, songId: String, songName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_EDIT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEditorSongClick(videoId: String, songId: String, songName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackEditorSongSelect(videoId: String, songId: String, songName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.SONG_ID,
                AnalyticsEvent.Param.SONG_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSongClose(videoId: String, songId: String, songName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SONG_CLOSE,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.SONG_ID to songId,
                AnalyticsEvent.Param.SONG_NAME to songName
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackQualityEdit(videoId: String, qualityNumber: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.QUALITY_EDIT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.QUALITY_NUMBER to qualityNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackQualityClick(videoId: String, qualityNumber: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.QUALITY_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.QUALITY_NUMBER to qualityNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackQualitySelect(videoId: String, qualityNumber: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.QUALITY_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.QUALITY_NUMBER to qualityNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackQualityClose(videoId: String, qualityNumber: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.QUALITY_CLOSE,
            params = mapOf(
                AnalyticsEvent.Param.VIDEO_ID to videoId,
                AnalyticsEvent.Param.QUALITY_NUMBER to qualityNumber
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoExport(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_EXPORT,
            params = buildVideoParams(
                videoId = videoId,
                templateId = templateId,
                songId = songId,
                quality = quality,
                duration = duration,
                ratioSize = ratioSize,
                volume = volume,
                mediaQuality = mediaQuality,
                mediaQuantity = mediaQuantity
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoExportComplete(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_EXPORT_COMPLETE,
            params = buildVideoParams(
                videoId = videoId,
                templateId = templateId,
                songId = songId,
                quality = quality,
                duration = duration,
                ratioSize = ratioSize,
                volume = volume,
                mediaQuality = mediaQuality,
                mediaQuantity = mediaQuantity
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoShare(
        videoId: String,
        location: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        val params = buildVideoParams(
            videoId = videoId,
            templateId = templateId,
            songId = songId,
            quality = quality,
            duration = duration,
            ratioSize = ratioSize,
            volume = volume,
            mediaQuality = mediaQuality,
            mediaQuantity = mediaQuantity
        ).toMutableMap()
        params[AnalyticsEvent.Param.LOCATION] = location
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_SHARE,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoDownload(
        videoId: String,
        location: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ) {
        val params = buildVideoParams(
            videoId = videoId,
            templateId = templateId,
            songId = songId,
            quality = quality,
            duration = duration,
            ratioSize = ratioSize,
            volume = volume,
            mediaQuality = mediaQuality,
            mediaQuantity = mediaQuantity
        ).toMutableMap()
        params[AnalyticsEvent.Param.LOCATION] = location
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_DOWNLOAD,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoClick(
        videoId: String,
        location: String,
        templateId: String? = null,
        songId: String? = null
    ) {
        val params = linkedMapOf<String, Any>(
            AnalyticsEvent.Param.VIDEO_ID to videoId,
            AnalyticsEvent.Param.LOCATION to location
        ).apply {
            if (!templateId.isNullOrBlank()) put(AnalyticsEvent.Param.TEMPLATE_ID, templateId)
            if (!songId.isNullOrBlank()) put(AnalyticsEvent.Param.SONG_ID, songId)
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_CLICK,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.VIDEO_ID,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoOption(videoId: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_OPTION,
            params = mapOf(AnalyticsEvent.Param.VIDEO_ID to videoId),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackVideoDelete(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null
    ) {
        trackWithPolicy(
            eventName = AnalyticsEvent.VIDEO_DELETE,
            params = buildVideoParams(
                videoId = videoId,
                templateId = templateId,
                songId = songId,
                quality = quality,
                duration = duration,
                ratioSize = ratioSize,
                volume = volume,
                mediaQuality = mediaQuality
            ),
            requiredParams = setOf(AnalyticsEvent.Param.VIDEO_ID),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackExitClick(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.EXIT_CLICK,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackExitPopupShow(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.EXIT_POPUP_SHOW,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackExitDiscard(videoId: String?, location: String) {
        val params = linkedMapOf<String, Any>(AnalyticsEvent.Param.LOCATION to location).apply {
            if (!videoId.isNullOrBlank()) put(AnalyticsEvent.Param.VIDEO_ID, videoId)
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.EXIT_DISCARD,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackExitContinue(location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.EXIT_CONTINUE,
            params = mapOf(AnalyticsEvent.Param.LOCATION to location),
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackExitSave(videoId: String?, location: String) {
        val params = linkedMapOf<String, Any>(AnalyticsEvent.Param.LOCATION to location).apply {
            if (!videoId.isNullOrBlank()) put(AnalyticsEvent.Param.VIDEO_ID, videoId)
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.EXIT_SAVE,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.LOCATION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetView(widgetType: String, widgetSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_VIEW,
            params = mapOf(
                AnalyticsEvent.Param.WIDGET_TYPE to widgetType,
                AnalyticsEvent.Param.WIDGET_SIZE to widgetSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.WIDGET_TYPE,
                AnalyticsEvent.Param.WIDGET_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetClick(entryPoint: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.ENTRY_POINT to entryPoint,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.ENTRY_POINT,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetSelect(widgetType: String, widgetSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_SELECT,
            params = mapOf(
                AnalyticsEvent.Param.WIDGET_TYPE to widgetType,
                AnalyticsEvent.Param.WIDGET_SIZE to widgetSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.WIDGET_TYPE,
                AnalyticsEvent.Param.WIDGET_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetAdd(widgetType: String, widgetSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_ADD,
            params = mapOf(
                AnalyticsEvent.Param.WIDGET_TYPE to widgetType,
                AnalyticsEvent.Param.WIDGET_SIZE to widgetSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.WIDGET_TYPE,
                AnalyticsEvent.Param.WIDGET_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetImpression(widgetType: String, widgetSize: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_IMPRESSION,
            params = mapOf(
                AnalyticsEvent.Param.WIDGET_TYPE to widgetType,
                AnalyticsEvent.Param.WIDGET_SIZE to widgetSize
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.WIDGET_TYPE,
                AnalyticsEvent.Param.WIDGET_SIZE
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackWidgetOpen(widgetType: String, widgetSize: String, deepLinkTarget: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.WIDGET_OPEN,
            params = mapOf(
                AnalyticsEvent.Param.WIDGET_TYPE to widgetType,
                AnalyticsEvent.Param.WIDGET_SIZE to widgetSize,
                AnalyticsEvent.Param.DEEP_LINK_TARGET to deepLinkTarget
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.WIDGET_TYPE,
                AnalyticsEvent.Param.WIDGET_SIZE,
                AnalyticsEvent.Param.DEEP_LINK_TARGET
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateView(logicRender: String, location: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_VIEW,
            params = mapOf(
                AnalyticsEvent.Param.LOGIC_RENDER to logicRender,
                AnalyticsEvent.Param.LOCATION to location
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.LOGIC_RENDER,
                AnalyticsEvent.Param.LOCATION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateClick(option: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_CLICK,
            params = mapOf(AnalyticsEvent.Param.OPTION to option),
            requiredParams = setOf(AnalyticsEvent.Param.OPTION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateStar(star: Int, option: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_STAR,
            params = mapOf(
                AnalyticsEvent.Param.STAR to star,
                AnalyticsEvent.Param.OPTION to option
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.STAR,
                AnalyticsEvent.Param.OPTION
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateRateUsButtonClick(option: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_RATE_US_BUTTON_CLICK,
            params = mapOf(AnalyticsEvent.Param.OPTION to option),
            requiredParams = setOf(AnalyticsEvent.Param.OPTION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateFlowContinue(option: String, star: Int? = null) {
        val params = linkedMapOf<String, Any>(AnalyticsEvent.Param.OPTION to option).apply {
            if (star != null) put(AnalyticsEvent.Param.STAR, star)
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_FLOW_CONTINUE,
            params = params,
            requiredParams = setOf(AnalyticsEvent.Param.OPTION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateReason(option: String, star: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_REASON,
            params = mapOf(
                AnalyticsEvent.Param.OPTION to option,
                AnalyticsEvent.Param.STAR to star
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.OPTION,
                AnalyticsEvent.Param.STAR
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackReasonClick(des: String, option: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.REASON_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.DES to des,
                AnalyticsEvent.Param.OPTION to option
            ),
            requiredParams = setOf(AnalyticsEvent.Param.OPTION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateDone(option: String, star: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_DONE,
            params = mapOf(
                AnalyticsEvent.Param.OPTION to option,
                AnalyticsEvent.Param.STAR to star
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.OPTION,
                AnalyticsEvent.Param.STAR
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackRateSubmit(des: String, option: String, star: Int) {
        trackWithPolicy(
            eventName = AnalyticsEvent.RATE_SUBMIT,
            params = mapOf(
                AnalyticsEvent.Param.DES to des,
                AnalyticsEvent.Param.OPTION to option,
                AnalyticsEvent.Param.STAR to star
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.OPTION,
                AnalyticsEvent.Param.STAR
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackPermissionRender() {
        trackWithPolicy(
            eventName = AnalyticsEvent.PERMISSION_RENDER,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackPermissionClick(button: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.PERMISSION_CLICK,
            params = mapOf(AnalyticsEvent.Param.BUTTON to button),
            requiredParams = setOf(AnalyticsEvent.Param.BUTTON),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackPermissionGotoSetting() {
        trackWithPolicy(
            eventName = AnalyticsEvent.PERMISSION_GOTO_SETTING,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackPermissionCheck(allow: Boolean) {
        trackWithPolicy(
            eventName = AnalyticsEvent.PERMISSION_CHECK,
            params = mapOf(
                AnalyticsEvent.Param.ALLOW to if (allow) AnalyticsEvent.Value.YesNo.Y else AnalyticsEvent.Value.YesNo.N
            ),
            requiredParams = setOf(AnalyticsEvent.Param.ALLOW),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackReportRender(templateId: String, templateName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.REPORT_RENDER,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackReportSelectReason(templateId: String, templateName: String, reason: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.REPORT_SELECT_REASON,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
                AnalyticsEvent.Param.REASON to reason
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.REASON
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackReportSubmit(
        templateId: String,
        templateName: String,
        reason: String,
        otherText: String? = null
    ) {
        val params = linkedMapOf<String, Any>(
            AnalyticsEvent.Param.TEMPLATE_ID to templateId,
            AnalyticsEvent.Param.TEMPLATE_NAME to templateName,
            AnalyticsEvent.Param.REASON to reason
        ).apply {
            if (!otherText.isNullOrBlank()) {
                put(AnalyticsEvent.Param.OTHER_TEXT, otherText)
            }
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.REPORT_SUBMIT,
            params = params,
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME,
                AnalyticsEvent.Param.REASON
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackReportDone(templateId: String, templateName: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.REPORT_DONE,
            params = mapOf(
                AnalyticsEvent.Param.TEMPLATE_ID to templateId,
                AnalyticsEvent.Param.TEMPLATE_NAME to templateName
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.TEMPLATE_ID,
                AnalyticsEvent.Param.TEMPLATE_NAME
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchRecentView(keywords: List<String>) {
        val params = linkedMapOf<String, Any>().apply {
            keywords.getOrNull(0)?.let { put(AnalyticsEvent.Param.KEYWORD_1, it) }
            keywords.getOrNull(1)?.let { put(AnalyticsEvent.Param.KEYWORD_2, it) }
            keywords.getOrNull(2)?.let { put(AnalyticsEvent.Param.KEYWORD_3, it) }
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_RECENT_VIEW,
            params = params,
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchSuggestView(keywords: List<String>) {
        val params = linkedMapOf<String, Any>().apply {
            keywords.getOrNull(0)?.let { put(AnalyticsEvent.Param.KEYWORD_1, it) }
            keywords.getOrNull(1)?.let { put(AnalyticsEvent.Param.KEYWORD_2, it) }
            keywords.getOrNull(2)?.let { put(AnalyticsEvent.Param.KEYWORD_3, it) }
        }
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_SUGGEST_VIEW,
            params = params,
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackSearchSeeMore(keyword: String, section: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SEARCH_SEE_MORE,
            params = mapOf(
                AnalyticsEvent.Param.KEYWORD to keyword,
                AnalyticsEvent.Param.SECTION to section
            ),
            requiredParams = setOf(AnalyticsEvent.Param.KEYWORD),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackShortcutMenuImpression() {
        trackWithPolicy(
            eventName = AnalyticsEvent.SHORTCUT_MENU_IMPRESSION,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackShortcutClick(shortcutType: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.SHORTCUT_CLICK,
            params = mapOf(AnalyticsEvent.Param.SHORTCUT_TYPE to shortcutType),
            requiredParams = setOf(AnalyticsEvent.Param.SHORTCUT_TYPE),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackUninstallView() {
        trackWithPolicy(
            eventName = AnalyticsEvent.UNINSTALL_VIEW,
            params = emptyMap(),
            requiredParams = emptySet(),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackUninstallContentClick(section: String, id: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.UNINSTALL_CONTENT_CLICK,
            params = mapOf(
                AnalyticsEvent.Param.SECTION to section,
                AnalyticsEvent.Param.ID to id
            ),
            requiredParams = setOf(
                AnalyticsEvent.Param.SECTION,
                AnalyticsEvent.Param.ID
            ),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackUninstallSeeMore(section: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.UNINSTALL_SEE_MORE,
            params = mapOf(AnalyticsEvent.Param.SECTION to section),
            requiredParams = setOf(AnalyticsEvent.Param.SECTION),
            policy = TrackingPolicy.NORMAL
        )
    }

    fun trackUninstallCtaClick(type: String) {
        trackWithPolicy(
            eventName = AnalyticsEvent.UNINSTALL_CTA_CLICK,
            params = mapOf(AnalyticsEvent.Param.TYPE to type),
            requiredParams = setOf(AnalyticsEvent.Param.TYPE),
            policy = TrackingPolicy.NORMAL
        )
    }

    // ============================================
    // INTERNALS
    // ============================================

    private fun trackWithPolicy(
        eventName: String,
        params: Map<String, Any>,
        requiredParams: Set<String>,
        policy: TrackingPolicy,
        dedupeKey: String? = null
    ) {
        val normalizedParams = normalizeParams(params).toMutableMap()
        normalizeLocationForTracking(eventName, normalizedParams)
        val missingParams = missingRequired(requiredParams, normalizedParams)

        when (policy) {
            TrackingPolicy.NORMAL -> {
                if (missingParams.isNotEmpty()) return
                normalizedParams.remove(AnalyticsEvent.Param.SCREEN_SESSION_ID)
                track(eventName, normalizedParams)
            }

            TrackingPolicy.IMPRESSION -> {
                if (missingParams.isNotEmpty()) return
                val key = dedupeKey ?: buildDefaultDedupeKey(eventName, normalizedParams)
                synchronized(stateLock) {
                    if (!impressionDedupeKeys.add(key)) return
                }
                normalizedParams.remove(AnalyticsEvent.Param.SCREEN_SESSION_ID)
                track(eventName, normalizedParams)
            }

            TrackingPolicy.DEFER_IF_MISSING -> {
                if (missingParams.isEmpty()) {
                    normalizedParams.remove(AnalyticsEvent.Param.SCREEN_SESSION_ID)
                    track(eventName, normalizedParams)
                    return
                }
                val correlationId =
                    normalizedParams[AnalyticsEvent.Param.CORRELATION_ID]?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString()

                normalizedParams[AnalyticsEvent.Param.CORRELATION_ID] = correlationId
                synchronized(stateLock) {
                    pruneExpiredPendingEventsLocked(System.currentTimeMillis())
                    pendingEvents += PendingEvent(
                        eventName = eventName,
                        params = normalizedParams,
                        requiredParams = requiredParams,
                        dedupeKey = dedupeKey,
                        expiresAtMs = System.currentTimeMillis() + DEFAULT_DEFER_TTL_MS,
                        correlationId = correlationId
                    )
                }
            }
        }
    }

    private fun pruneExpiredPendingEvents() {
        synchronized(stateLock) {
            pruneExpiredPendingEventsLocked(System.currentTimeMillis())
        }
    }

    private fun pruneExpiredPendingEventsLocked(now: Long) {
        pendingEvents.removeAll { it.expiresAtMs <= now }
    }

    private fun missingRequired(
        required: Set<String>,
        params: Map<String, Any>
    ): Set<String> {
        return required.filterTo(mutableSetOf()) { key ->
            val value = params[key] ?: return@filterTo true
            value is String && value.isBlank()
        }
    }

    private fun normalizeParams(params: Map<String, Any>): Map<String, Any> {
        if (params.isEmpty()) return emptyMap()
        val normalized = LinkedHashMap<String, Any>(params.size)
        params.forEach { (key, value) ->
            when (value) {
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) {
                        normalized[key] = trimmed
                    }
                }
                else -> normalized[key] = value
            }
        }
        return normalized
    }

    private fun buildDefaultDedupeKey(eventName: String, params: Map<String, Any>): String {
        val itemId =
            params[AnalyticsEvent.Param.TEMPLATE_ID]
                ?: params[AnalyticsEvent.Param.SONG_ID]
                ?: params[AnalyticsEvent.Param.VIDEO_ID]
                ?: params[AnalyticsEvent.Param.ID]
                ?: "na"
        val location = params[AnalyticsEvent.Param.LOCATION] ?: "na"
        val screenSessionId = params[AnalyticsEvent.Param.SCREEN_SESSION_ID] ?: "global"
        return "$eventName|$itemId|$location|$screenSessionId"
    }

    private fun buildVideoParams(
        videoId: String,
        templateId: String? = null,
        songId: String? = null,
        quality: String? = null,
        duration: Long? = null,
        ratioSize: String? = null,
        volume: Int? = null,
        mediaQuality: String? = null,
        mediaQuantity: Int? = null
    ): Map<String, Any> {
        return linkedMapOf<String, Any>(
            AnalyticsEvent.Param.VIDEO_ID to videoId
        ).apply {
            if (!templateId.isNullOrBlank()) put(AnalyticsEvent.Param.TEMPLATE_ID, templateId)
            if (!songId.isNullOrBlank()) put(AnalyticsEvent.Param.SONG_ID, songId)
            if (!quality.isNullOrBlank()) put(AnalyticsEvent.Param.QUALITY, quality)
            if (duration != null) put(AnalyticsEvent.Param.DURATION, duration)
            if (!ratioSize.isNullOrBlank()) put(AnalyticsEvent.Param.RATIO_SIZE, ratioSize)
            if (volume != null) put(AnalyticsEvent.Param.VOLUME, volume)
            if (!mediaQuality.isNullOrBlank()) put(AnalyticsEvent.Param.MEDIA_QUALITY, mediaQuality)
            if (mediaQuantity != null) put(AnalyticsEvent.Param.MEDIA_QUANTITY, mediaQuantity)
        }
    }

    private fun normalizeLocationForTracking(eventName: String, params: MutableMap<String, Any>) {
        val hasLocation = params.containsKey(AnalyticsEvent.Param.LOCATION)
        if (!hasLocation) return

        val shouldNormalize =
            eventName in locationAwareTemplateSongEvents || eventName == AnalyticsEvent.GALLERY_SWIPE
        if (!shouldNormalize) return

        val rawLocation = params[AnalyticsEvent.Param.LOCATION]?.toString()?.trim().orEmpty()
        if (rawLocation.isEmpty()) return

        params[AnalyticsEvent.Param.LOCATION] = when (rawLocation) {
            AnalyticsEvent.Value.Location.TEMPLATE_PREVIEW -> AnalyticsEvent.Value.Location.PREVIEW_SWIPE
            AnalyticsEvent.Value.Location.LIBRARY -> AnalyticsEvent.Value.Location.LIBRARY_RCM
            AnalyticsEvent.Value.Location.RESULT -> AnalyticsEvent.Value.Location.RESULT_RCM
            AnalyticsEvent.Value.Location.SEARCH -> AnalyticsEvent.Value.Location.SEARCH_RESULT
            "result_recommendation" -> AnalyticsEvent.Value.Location.RESULT_RCM
            "uninstall_retain_page" -> AnalyticsEvent.Value.Location.UNINSTALL
            else -> rawLocation
        }
    }
}
