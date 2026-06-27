package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.videomaker.aimusic.core.popup.TrendingPopupDailySnapshot
import com.videomaker.aimusic.core.popup.TrendingPopupTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Manages SharedPreferences for the app
 * Injected as a singleton via ACCDI
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val stringSetLock = Any()
    private val trendingPopupJson = Json { ignoreUnknownKeys = true }

    private fun trendingPopupKey(tab: TrendingPopupTab): String = when (tab) {
        TrendingPopupTab.GALLERY -> KEY_TRENDING_POPUP_TEMPLATE_SNAPSHOT
        TrendingPopupTab.SONGS -> KEY_TRENDING_POPUP_SONG_SNAPSHOT
    }

    /**
     * Read the per-tab trending-popup snapshot, or `null` if absent / unparseable.
     * Callers should also treat an `epochDay != today` snapshot as empty (cap reset).
     */
    fun getTrendingPopupSnapshot(tab: TrendingPopupTab): TrendingPopupDailySnapshot? {
        val raw = prefs.getString(trendingPopupKey(tab), null) ?: return null
        return runCatching { trendingPopupJson.decodeFromString<TrendingPopupDailySnapshot>(raw) }
            .getOrNull()
    }

    fun setTrendingPopupSnapshot(tab: TrendingPopupTab, snapshot: TrendingPopupDailySnapshot) {
        val raw = trendingPopupJson.encodeToString(snapshot)
        prefs.edit { putString(trendingPopupKey(tab), raw) }
    }

    fun getTrendingPopupFocusCount(tab: TrendingPopupTab): Int {
        val key = when (tab) {
            TrendingPopupTab.GALLERY -> KEY_TRENDING_POPUP_GALLERY_FOCUS_COUNT
            TrendingPopupTab.SONGS -> KEY_TRENDING_POPUP_SONGS_FOCUS_COUNT
        }
        return prefs.getInt(key, 0)
    }

    fun incrementTrendingPopupFocusCount(tab: TrendingPopupTab) {
        val key = when (tab) {
            TrendingPopupTab.GALLERY -> KEY_TRENDING_POPUP_GALLERY_FOCUS_COUNT
            TrendingPopupTab.SONGS -> KEY_TRENDING_POPUP_SONGS_FOCUS_COUNT
        }
        val current = prefs.getInt(key, 0)
        prefs.edit { putInt(key, current + 1) }
    }

    companion object {
        private const val PREFS_NAME = "video_maker_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_PREFERRED_GENRES = "preferred_genres"
        private const val KEY_PREFERRED_FEATURES = "preferred_features"
        private const val KEY_FEATURE_SELECTION_COMPLETE = "feature_selection_complete"
        private const val KEY_HOME_INITIAL_TAB_FROM_ONBOARDING = "home_initial_tab_from_onboarding"
        private const val KEY_USER_REGION = "user_region"
        private const val KEY_RATING_VIDEO_CREATE_COUNT = "rating_video_create_count"
        private const val KEY_RATING_SHOWN_COUNT = "rating_shown_count"
        private const val KEY_RATING_COMPLETED = "rating_completed"
        private const val KEY_RATING_SWIPE_TEMPLATE_COUNT = "rating_swipe_template_count"
        private const val KEY_RATING_NEXT_SONG_COUNT = "rating_next_song_count"
        private const val KEY_RATING_HAS_TRIGGERED_ON_SELECT = "rating_has_triggered_on_select"
        private const val KEY_RATING_LAST_TRIGGERED_HOME_SESSION_ID = "rating_last_triggered_home_session_id"
        private const val KEY_RATING_HOME_FOCUS_COUNT = "rating_home_focus_count"
        private const val KEY_RATING_DAILY_SHOWN_EPOCH_DAY = "rating_daily_shown_epoch_day"
        private const val KEY_RATING_DAILY_SHOWN_COUNT = "rating_daily_shown_count"
        private const val KEY_NOTIFICATION_PERMISSION_REQUEST_COUNT = "notification_permission_request_count"
        private const val KEY_NOTIFICATION_PERMISSION_BLOCKED = "notification_permission_blocked"
        private const val KEY_OB_RESUME_FIRED_COUNT = "ob_resume_fired_count"
        private const val KEY_NOTIFICATION_DAILY_SHOWN_EPOCH_DAY = "notification_daily_shown_epoch_day"
        private const val KEY_NOTIFICATION_DAILY_SHOWN_COUNT = "notification_daily_shown_count"
        private const val KEY_NOTIFICATION_TYPE_DAILY_SHOWN_EPOCH_DAY_PREFIX = "notification_type_daily_epoch_day_"
        private const val KEY_NOTIFICATION_TYPE_DAILY_SHOWN_COUNT_PREFIX = "notification_type_daily_count_"
        private const val KEY_NOTIFICATION_LAST_SHOWN_AT_MS = "notification_last_shown_at_ms"
        private const val KEY_NOTIFICATION_ITEM_LAST_SHOWN_PREFIX = "notification_item_last_shown_"
        private const val KEY_NOTIFICATION_LAST_TAP_PREFIX = "notification_last_tap_"
        private const val KEY_TRENDING_SONG_SNAPSHOT_DATE = "trending_song_snapshot_date"
        private const val KEY_TRENDING_SONG_SNAPSHOT_ID = "trending_song_snapshot_id"
        private const val KEY_TRENDING_SONG_SNAPSHOT_USAGE = "trending_song_snapshot_usage"
        private const val KEY_VIRAL_TEMPLATE_SNAPSHOT_DATE = "viral_template_snapshot_date"
        private const val KEY_VIRAL_TEMPLATE_SNAPSHOT_ID = "viral_template_snapshot_id"
        private const val KEY_VIRAL_TEMPLATE_SNAPSHOT_USAGE = "viral_template_snapshot_usage"
        private const val KEY_TRENDING_POPUP_TEMPLATE_SNAPSHOT = "trending_popup_template_snapshot"
        private const val KEY_TRENDING_POPUP_SONG_SNAPSHOT = "trending_popup_song_snapshot"
        private const val KEY_TRENDING_POPUP_GALLERY_FOCUS_COUNT = "trending_popup_gallery_focus_count"
        private const val KEY_TRENDING_POPUP_SONGS_FOCUS_COUNT = "trending_popup_songs_focus_count"
        private const val KEY_APP_SESSION_ID = "notification_app_session_id"
        private const val KEY_APP_LAST_BACKGROUND_AT_MS = "notification_app_last_background_at_ms"
        private const val KEY_VIDEO_REMINDER_GENERATED_AT_PREFIX = "video_reminder_generated_at_"
        private const val KEY_VIDEO_REMINDER_TEMPLATE_ID_PREFIX = "video_reminder_template_id_"
        private const val KEY_VIDEO_REMINDER_SONG_ID_PREFIX = "video_reminder_song_id_"
        private const val KEY_VIDEO_REMINDER_THUMBNAIL_URI_PREFIX = "video_reminder_thumbnail_uri_"
        private const val KEY_VIDEO_REMINDER_SAVED_AT_PREFIX = "video_reminder_saved_at_"
        private const val KEY_VIDEO_REMINDER_SHARED_AT_PREFIX = "video_reminder_shared_at_"
        private const val KEY_VIDEO_REMINDER_LAST_OPENED_AT_PREFIX = "video_reminder_last_opened_at_"
        private const val KEY_ACTIVE_VIDEO_REMINDER_IDS = "active_video_reminder_ids"
        private const val KEY_DRAFT_REMINDER_TEMPLATE_ID_PREFIX = "draft_reminder_template_id_"
        private const val KEY_DRAFT_REMINDER_SONG_ID_PREFIX = "draft_reminder_song_id_"
        private const val KEY_DRAFT_REMINDER_EXITED_AT_PREFIX = "draft_reminder_exited_at_"
        private const val KEY_DRAFT_REMINDER_EXIT_SESSION_PREFIX = "draft_reminder_exit_session_"
        private const val KEY_DRAFT_REMINDER_SELECTED_COUNT_PREFIX = "draft_reminder_selected_count_"
        private const val KEY_DRAFT_REMINDER_LAST_ABANDONED_SHOWN_PREFIX = "draft_reminder_last_abandoned_shown_"
        private const val KEY_DRAFT_REMINDER_LAST_DRAFT_NUDGE_SHOWN_PREFIX = "draft_reminder_last_draft_nudge_shown_"
        private const val KEY_ACTIVE_DRAFT_REMINDER_IDS = "active_draft_reminder_ids"
        private const val KEY_MEDIA_FULL_PERMISSION_REQUEST_COUNT = "media_full_permission_request_count"
        private const val KEY_MEDIA_FULL_PERMISSION_BLOCKED = "media_full_permission_blocked"
        private const val KEY_PENDING_DEEP_LINK_ACTION = "pending_deep_link_action"
        private const val KEY_PENDING_DEEP_LINK_TEMPLATE_ID = "pending_deep_link_template_id"
        private const val KEY_PENDING_DEEP_LINK_SONG_ID = "pending_deep_link_song_id"
        private const val RECENT_SEARCHES_DELIMITER = "\u001F" // Unit Separator
        private const val GENRES_DELIMITER = ","
        private const val MAX_RECENT_SEARCHES = 3 // FIFO: First In First Out
        private const val KEY_SEEN_TEMPLATE_IDS = "seen_template_ids"
        private const val MAX_SEEN_TEMPLATE_IDS = 200
    }

    /** Music genre preferences selected during onboarding. Empty = no preference set. */
    fun getPreferredGenres(): List<String> {
        val raw = prefs.getString(KEY_PREFERRED_GENRES, null) ?: return emptyList()
        return raw.split(GENRES_DELIMITER).filter { it.isNotBlank() }
    }

    fun setPreferredGenres(genres: List<String>) {
        prefs.edit { putString(KEY_PREFERRED_GENRES, genres.joinToString(GENRES_DELIMITER)) }
    }

    /** Feature interests selected during onboarding survey. */
    fun getPreferredFeatures(): List<String> {
        val raw = prefs.getString(KEY_PREFERRED_FEATURES, null) ?: return emptyList()
        return raw.split(GENRES_DELIMITER).filter { it.isNotBlank() }
    }

    fun setPreferredFeatures(features: List<String>) {
        prefs.edit { putString(KEY_PREFERRED_FEATURES, features.joinToString(GENRES_DELIMITER)) }
    }

    fun isFeatureSelectionComplete(): Boolean {
        return prefs.getBoolean(KEY_FEATURE_SELECTION_COMPLETE, false)
    }

    fun setFeatureSelectionComplete(complete: Boolean) {
        prefs.edit { putBoolean(KEY_FEATURE_SELECTION_COMPLETE, complete) }
    }

    fun getHomeInitialTabFromOnboarding(): Int {
        return prefs.getInt(KEY_HOME_INITIAL_TAB_FROM_ONBOARDING, 0)
    }

    fun setHomeInitialTabFromOnboarding(tab: Int) {
        prefs.edit { putInt(KEY_HOME_INITIAL_TAB_FROM_ONBOARDING, tab) }
    }

    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    /**
     * Mark onboarding as complete
     * FIXED: Runs .commit() on IO thread to prevent ANR while ensuring immediate write
     * to prevent onboarding loop if app is killed before write completes
     */
    suspend fun setOnboardingComplete(complete: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).commit()
    }

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
        }
        return isFirst
    }

    /**
     * Get recent search queries (most recent first)
     */
    suspend fun getRecentSearches(): List<String> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return@withContext emptyList()
        raw.split(RECENT_SEARCHES_DELIMITER).filter { it.isNotBlank() }
    }

    /**
     * Add a search query to recent searches (most recent first, max 3)
     * ✅ FIXED: Runs on IO dispatcher to avoid ANR
     */
    suspend fun addRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext

        val current = getRecentSearches().toMutableList()
        current.remove(trimmed) // Remove duplicate if exists
        current.add(0, trimmed) // Add to front

        val capped = current.take(MAX_RECENT_SEARCHES)
        prefs.edit {
            putString(KEY_RECENT_SEARCHES, capped.joinToString(RECENT_SEARCHES_DELIMITER))
        }
    }

    /**
     * Remove a specific search query from recent searches
     * ✅ FIXED: Runs on IO dispatcher to avoid ANR
     */
    suspend fun removeRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val current = getRecentSearches().toMutableList()
        current.remove(query)
        prefs.edit {
            putString(KEY_RECENT_SEARCHES, current.joinToString(RECENT_SEARCHES_DELIMITER))
        }
    }

    /**
     * Clear all recent searches
     * ✅ FIXED: Runs on IO dispatcher to avoid ANR
     */
    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        prefs.edit { remove(KEY_RECENT_SEARCHES) }
    }

    fun getUserRegion(): String? =
        prefs.getString(KEY_USER_REGION, null)

    fun setUserRegion(region: String) =
        prefs.edit().putString(KEY_USER_REGION, region).apply()

    // ============================================
    // Rating preferences
    // ============================================

    var ratingVideoCreateCount: Int
        get() = prefs.getInt(KEY_RATING_VIDEO_CREATE_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_RATING_VIDEO_CREATE_COUNT, value) }

    var ratingShownCount: Int
        get() = prefs.getInt(KEY_RATING_SHOWN_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_RATING_SHOWN_COUNT, value) }

    var ratingCompleted: Boolean
        get() = prefs.getBoolean(KEY_RATING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_RATING_COMPLETED, value) }

    var ratingSwipeTemplateCount: Int
        get() = prefs.getInt(KEY_RATING_SWIPE_TEMPLATE_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_RATING_SWIPE_TEMPLATE_COUNT, value) }

    var ratingNextSongCount: Int
        get() = prefs.getInt(KEY_RATING_NEXT_SONG_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_RATING_NEXT_SONG_COUNT, value) }

    var ratingHasTriggeredOnSelect: Boolean
        get() = prefs.getBoolean(KEY_RATING_HAS_TRIGGERED_ON_SELECT, false)
        set(value) = prefs.edit { putBoolean(KEY_RATING_HAS_TRIGGERED_ON_SELECT, value) }

    var ratingLastTriggeredHomeSessionId: Long
        get() = prefs.getLong(KEY_RATING_LAST_TRIGGERED_HOME_SESSION_ID, 0L)
        set(value) = prefs.edit { putLong(KEY_RATING_LAST_TRIGGERED_HOME_SESSION_ID, value) }

    var ratingHomeFocusCount: Int
        get() = prefs.getInt(KEY_RATING_HOME_FOCUS_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_RATING_HOME_FOCUS_COUNT, value) }

    fun getRatingDailyShownCount(nowMs: Long = System.currentTimeMillis()): Int {
        val today = epochDay(nowMs)
        val storedDay = prefs.getLong(KEY_RATING_DAILY_SHOWN_EPOCH_DAY, Long.MIN_VALUE)
        if (storedDay != today) return 0
        return prefs.getInt(KEY_RATING_DAILY_SHOWN_COUNT, 0)
    }

    fun incrementRatingDailyShownCount(nowMs: Long = System.currentTimeMillis()): Int {
        val today = epochDay(nowMs)
        val currentCount = getRatingDailyShownCount(nowMs)
        val nextCount = currentCount + 1
        prefs.edit {
            putLong(KEY_RATING_DAILY_SHOWN_EPOCH_DAY, today)
            putInt(KEY_RATING_DAILY_SHOWN_COUNT, nextCount)
        }
        return nextCount
    }

    // ============================================
    // Notification permission preferences
    // ============================================

    fun getNotificationPermissionRequestCount(): Int =
        prefs.getInt(KEY_NOTIFICATION_PERMISSION_REQUEST_COUNT, 0)

    fun setNotificationPermissionRequestCount(count: Int) {
        prefs.edit { putInt(KEY_NOTIFICATION_PERMISSION_REQUEST_COUNT, count.coerceAtLeast(0)) }
    }

    fun isNotificationPermissionBlockedAfterSecondDeny(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_BLOCKED, false)

    fun setNotificationPermissionBlockedAfterSecondDeny(blocked: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_BLOCKED, blocked) }
    }

    fun clearNotificationPermissionStateOnGrant() {
        prefs.edit {
            remove(KEY_NOTIFICATION_PERMISSION_BLOCKED)
        }
    }

    /**
     * How many onboarding-resume notifications have actually been posted (0..3).
     * Advanced only when a notification is successfully shown, so the sequence
     * stays strictly ordered 1 -> 2 -> 3.
     */
    var obResumeFiredCount: Int
        get() = prefs.getInt(KEY_OB_RESUME_FIRED_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_OB_RESUME_FIRED_COUNT, value.coerceIn(0, 3)) }

    fun getNotificationDailyShownCount(nowMs: Long = System.currentTimeMillis()): Int {
        val today = epochDay(nowMs)
        val storedDay = prefs.getLong(KEY_NOTIFICATION_DAILY_SHOWN_EPOCH_DAY, Long.MIN_VALUE)
        if (storedDay != today) return 0
        return prefs.getInt(KEY_NOTIFICATION_DAILY_SHOWN_COUNT, 0)
    }

    fun incrementNotificationDailyShownCount(nowMs: Long = System.currentTimeMillis()): Int {
        val today = epochDay(nowMs)
        val currentCount = getNotificationDailyShownCount(nowMs)
        val nextCount = currentCount + 1
        prefs.edit {
            putLong(KEY_NOTIFICATION_DAILY_SHOWN_EPOCH_DAY, today)
            putInt(KEY_NOTIFICATION_DAILY_SHOWN_COUNT, nextCount)
        }
        return nextCount
    }

    fun getNotificationLastShownAtMs(): Long? {
        val value = prefs.getLong(KEY_NOTIFICATION_LAST_SHOWN_AT_MS, -1L)
        return value.takeIf { it > 0L }
    }

    fun setNotificationLastShownAtMs(value: Long) {
        prefs.edit { putLong(KEY_NOTIFICATION_LAST_SHOWN_AT_MS, value.coerceAtLeast(0L)) }
    }

    fun getNotificationTypeDailyShownCount(
        notificationType: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val today = epochDay(nowMs)
        val dayKey = typeEpochDayKey(notificationType)
        val countKey = typeCountKey(notificationType)
        val storedDay = prefs.getLong(dayKey, Long.MIN_VALUE)
        if (storedDay != today) return 0
        return prefs.getInt(countKey, 0)
    }

    fun incrementNotificationTypeDailyShownCount(
        notificationType: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val today = epochDay(nowMs)
        val dayKey = typeEpochDayKey(notificationType)
        val countKey = typeCountKey(notificationType)
        val nextCount = getNotificationTypeDailyShownCount(notificationType, nowMs) + 1
        prefs.edit {
            putLong(dayKey, today)
            putInt(countKey, nextCount)
        }
        return nextCount
    }

    fun getNotificationItemLastShownAtMs(notificationType: String, itemId: Long): Long? {
        return getNotificationItemLastShownAtMs(notificationType, itemId.toString())
    }

    fun getNotificationItemLastShownAtMs(notificationType: String, itemId: String): Long? {
        val value = prefs.getLong(itemKey(notificationType, itemId), -1L)
        return value.takeIf { it > 0L }
    }

    fun setNotificationItemLastShownAtMs(notificationType: String, itemId: Long, value: Long) {
        setNotificationItemLastShownAtMs(notificationType, itemId.toString(), value)
    }

    fun setNotificationItemLastShownAtMs(notificationType: String, itemId: String, value: Long) {
        prefs.edit {
            putLong(
                itemKey(notificationType, itemId),
                value.coerceAtLeast(0L)
            )
        }
    }

    fun recordNotificationShown(notificationType: String, itemId: Long, shownAtMs: Long) {
        recordNotificationShown(notificationType, itemId.toString(), shownAtMs)
    }

    fun recordNotificationShown(notificationType: String, itemId: String, shownAtMs: Long) {
        incrementNotificationDailyShownCount(shownAtMs)
        incrementNotificationTypeDailyShownCount(notificationType, shownAtMs)
        setNotificationLastShownAtMs(shownAtMs)
        setNotificationItemLastShownAtMs(notificationType, itemId, shownAtMs)
    }

    fun recordNotificationTap(notificationType: String, itemId: String, tappedAtMs: Long) {
        prefs.edit {
            putLong(notificationTapKey(notificationType, itemId), tappedAtMs.coerceAtLeast(0L))
        }
    }

    fun getNotificationTapAtMs(notificationType: String, itemId: String): Long? {
        val value = prefs.getLong(notificationTapKey(notificationType, itemId), -1L)
        return value.takeIf { it > 0L }
    }

    fun clearNotificationTap(notificationType: String, itemId: String) {
        prefs.edit { remove(notificationTapKey(notificationType, itemId)) }
    }

    data class TrendingSongSnapshot(
        val localDate: String,
        val songId: Long,
        val usageCount: Int
    )

    fun getTrendingSongSnapshot(): TrendingSongSnapshot? {
        val localDate = prefs.getString(KEY_TRENDING_SONG_SNAPSHOT_DATE, null) ?: return null
        val songId = prefs.getLong(KEY_TRENDING_SONG_SNAPSHOT_ID, -1L)
        if (songId <= 0L) return null
        return TrendingSongSnapshot(
            localDate = localDate,
            songId = songId,
            usageCount = prefs.getInt(KEY_TRENDING_SONG_SNAPSHOT_USAGE, 0).coerceAtLeast(0)
        )
    }

    fun getTrendingSongSnapshotForDate(localDate: String): TrendingSongSnapshot? {
        return getTrendingSongSnapshot()?.takeIf { it.localDate == localDate }
    }

    fun setTrendingSongSnapshot(localDate: String, songId: Long, usageCount: Int) {
        prefs.edit {
            putString(KEY_TRENDING_SONG_SNAPSHOT_DATE, localDate)
            putLong(KEY_TRENDING_SONG_SNAPSHOT_ID, songId.coerceAtLeast(0L))
            putInt(KEY_TRENDING_SONG_SNAPSHOT_USAGE, usageCount.coerceAtLeast(0))
        }
    }

    fun getTrendingSongSnapshotSongId(): Long? {
        return getTrendingSongSnapshotForDate(todayLocalDateString())?.songId
    }

    fun setTrendingSongSnapshotSongId(songId: Long) {
        setTrendingSongSnapshot(
            localDate = todayLocalDateString(),
            songId = songId,
            usageCount = 0
        )
    }

    data class ViralTemplateSnapshot(
        val localDate: String,
        val templateId: String,
        val usageCount: Long
    )

    fun getViralTemplateSnapshot(): ViralTemplateSnapshot? {
        val localDate = prefs.getString(KEY_VIRAL_TEMPLATE_SNAPSHOT_DATE, null) ?: return null
        val templateId = prefs.getString(KEY_VIRAL_TEMPLATE_SNAPSHOT_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return ViralTemplateSnapshot(
            localDate = localDate,
            templateId = templateId,
            usageCount = prefs.getLong(KEY_VIRAL_TEMPLATE_SNAPSHOT_USAGE, 0L).coerceAtLeast(0L)
        )
    }

    fun getViralTemplateSnapshotForDate(localDate: String): ViralTemplateSnapshot? {
        return getViralTemplateSnapshot()?.takeIf { it.localDate == localDate }
    }

    fun setViralTemplateSnapshot(localDate: String, templateId: String, usageCount: Long) {
        prefs.edit {
            putString(KEY_VIRAL_TEMPLATE_SNAPSHOT_DATE, localDate)
            putString(KEY_VIRAL_TEMPLATE_SNAPSHOT_ID, templateId)
            putLong(KEY_VIRAL_TEMPLATE_SNAPSHOT_USAGE, usageCount.coerceAtLeast(0L))
        }
    }

    fun getAppSessionId(): Long {
        return prefs.getLong(KEY_APP_SESSION_ID, 0L).coerceAtLeast(0L)
    }

    fun bumpAppSessionId(): Long {
        val next = getAppSessionId() + 1L
        prefs.edit { putLong(KEY_APP_SESSION_ID, next) }
        return next
    }

    fun getLastAppBackgroundAtMs(): Long? {
        val value = prefs.getLong(KEY_APP_LAST_BACKGROUND_AT_MS, -1L)
        return value.takeIf { it > 0L }
    }

    fun setLastAppBackgroundAtMs(backgroundAtMs: Long) {
        prefs.edit { putLong(KEY_APP_LAST_BACKGROUND_AT_MS, backgroundAtMs.coerceAtLeast(0L)) }
    }

    data class VideoReminderState(
        val projectId: String,
        val generatedAtMs: Long,
        val templateId: String?,
        val songId: Long?,
        val thumbnailUri: String?,
        val savedAtMs: Long?,
        val sharedAtMs: Long?,
        val lastOpenedAtMs: Long?
    )

    fun upsertVideoReminderState(
        projectId: String,
        generatedAtMs: Long,
        templateId: String?,
        songId: Long?,
        thumbnailUri: String?
    ) {
        if (projectId.isBlank()) return
        prefs.edit {
            putLong(videoGeneratedAtKey(projectId), generatedAtMs.coerceAtLeast(0L))
            putString(videoTemplateIdKey(projectId), templateId)
            putLong(videoSongIdKey(projectId), songId ?: -1L)
            putString(videoThumbnailKey(projectId), thumbnailUri)
        }
    }

    fun getActiveVideoReminderIds(): Set<String> {
        return prefs.getStringSet(KEY_ACTIVE_VIDEO_REMINDER_IDS, emptySet())
            ?.filterNot { it.isBlank() }
            ?.toSet()
            ?: emptySet()
    }

    fun addActiveVideoReminderId(projectId: String) {
        val normalized = projectId.trim()
        if (normalized.isBlank()) return
        mutateStringSet(KEY_ACTIVE_VIDEO_REMINDER_IDS) { current ->
            current.add(normalized)
        }
    }

    fun removeActiveVideoReminderId(projectId: String) {
        val normalized = projectId.trim()
        if (normalized.isBlank()) return
        mutateStringSet(KEY_ACTIVE_VIDEO_REMINDER_IDS) { current ->
            current.remove(normalized)
        }
    }

    fun getVideoReminderState(projectId: String): VideoReminderState? {
        if (projectId.isBlank()) return null
        val generatedAt = prefs.getLong(videoGeneratedAtKey(projectId), -1L)
        if (generatedAt <= 0L) return null
        val songIdValue = prefs.getLong(videoSongIdKey(projectId), -1L)
        val savedAtValue = prefs.getLong(videoSavedAtKey(projectId), -1L)
        val sharedAtValue = prefs.getLong(videoSharedAtKey(projectId), -1L)
        val openedAtValue = prefs.getLong(videoLastOpenedAtKey(projectId), -1L)
        return VideoReminderState(
            projectId = projectId,
            generatedAtMs = generatedAt,
            templateId = prefs.getString(videoTemplateIdKey(projectId), null),
            songId = songIdValue.takeIf { it > 0L },
            thumbnailUri = prefs.getString(videoThumbnailKey(projectId), null),
            savedAtMs = savedAtValue.takeIf { it > 0L },
            sharedAtMs = sharedAtValue.takeIf { it > 0L },
            lastOpenedAtMs = openedAtValue.takeIf { it > 0L }
        )
    }

    fun markVideoSaved(projectId: String, savedAtMs: Long) {
        if (projectId.isBlank()) return
        prefs.edit { putLong(videoSavedAtKey(projectId), savedAtMs.coerceAtLeast(0L)) }
    }

    fun markVideoShared(projectId: String, sharedAtMs: Long) {
        if (projectId.isBlank()) return
        prefs.edit { putLong(videoSharedAtKey(projectId), sharedAtMs.coerceAtLeast(0L)) }
    }

    fun markVideoOpened(projectId: String, openedAtMs: Long) {
        if (projectId.isBlank()) return
        prefs.edit { putLong(videoLastOpenedAtKey(projectId), openedAtMs.coerceAtLeast(0L)) }
    }

    data class DraftReminderState(
        val draftId: String,
        val templateId: String?,
        val songId: Long?,
        val exitedAtMs: Long,
        val exitSessionId: Long,
        val selectedPhotoCount: Int,
        val lastAbandonedShownAtMs: Long?,
        val lastDraftNudgeShownAtMs: Long?
    )

    fun upsertDraftReminderState(
        draftId: String,
        templateId: String?,
        songId: Long?,
        exitedAtMs: Long,
        exitSessionId: Long,
        selectedPhotoCount: Int
    ) {
        if (draftId.isBlank()) return
        val normalized = normalizeDraftId(draftId)
        prefs.edit {
            putString(draftTemplateIdKey(normalized), templateId)
            putLong(draftSongIdKey(normalized), songId ?: -1L)
            putLong(draftExitedAtKey(normalized), exitedAtMs.coerceAtLeast(0L))
            putLong(draftExitSessionKey(normalized), exitSessionId.coerceAtLeast(0L))
            putInt(draftSelectedCountKey(normalized), selectedPhotoCount.coerceAtLeast(0))
        }
    }

    fun getActiveDraftReminderIds(): Set<String> {
        return prefs.getStringSet(KEY_ACTIVE_DRAFT_REMINDER_IDS, emptySet())
            ?.filterNot { it.isBlank() }
            ?.toSet()
            ?: emptySet()
    }

    fun addActiveDraftReminderId(draftId: String) {
        val normalized = draftId.trim()
        if (normalized.isBlank()) return
        mutateStringSet(KEY_ACTIVE_DRAFT_REMINDER_IDS) { current ->
            current.add(normalized)
        }
    }

    fun removeActiveDraftReminderId(draftId: String) {
        val normalized = draftId.trim()
        if (normalized.isBlank()) return
        mutateStringSet(KEY_ACTIVE_DRAFT_REMINDER_IDS) { current ->
            current.remove(normalized)
        }
    }

    fun getDraftReminderState(draftId: String): DraftReminderState? {
        if (draftId.isBlank()) return null
        val normalized = normalizeDraftId(draftId)
        val exitedAt = prefs.getLong(draftExitedAtKey(normalized), -1L)
        if (exitedAt <= 0L) return null
        val songIdValue = prefs.getLong(draftSongIdKey(normalized), -1L)
        val abandonedShown = prefs.getLong(draftLastAbandonedShownKey(normalized), -1L)
        val draftNudgeShown = prefs.getLong(draftLastDraftNudgeShownKey(normalized), -1L)
        return DraftReminderState(
            draftId = draftId,
            templateId = prefs.getString(draftTemplateIdKey(normalized), null),
            songId = songIdValue.takeIf { it > 0L },
            exitedAtMs = exitedAt,
            exitSessionId = prefs.getLong(draftExitSessionKey(normalized), 0L).coerceAtLeast(0L),
            selectedPhotoCount = prefs.getInt(draftSelectedCountKey(normalized), 0).coerceAtLeast(0),
            lastAbandonedShownAtMs = abandonedShown.takeIf { it > 0L },
            lastDraftNudgeShownAtMs = draftNudgeShown.takeIf { it > 0L }
        )
    }

    fun markDraftAbandonedShown(draftId: String, shownAtMs: Long) {
        if (draftId.isBlank()) return
        prefs.edit {
            putLong(
                draftLastAbandonedShownKey(normalizeDraftId(draftId)),
                shownAtMs.coerceAtLeast(0L)
            )
        }
    }

    fun markDraftNudgeShown(draftId: String, shownAtMs: Long) {
        if (draftId.isBlank()) return
        prefs.edit {
            putLong(
                draftLastDraftNudgeShownKey(normalizeDraftId(draftId)),
                shownAtMs.coerceAtLeast(0L)
            )
        }
    }

    fun clearDraftReminderState(draftId: String) {
        if (draftId.isBlank()) return
        val normalized = normalizeDraftId(draftId)
        prefs.edit {
            remove(draftTemplateIdKey(normalized))
            remove(draftSongIdKey(normalized))
            remove(draftExitedAtKey(normalized))
            remove(draftExitSessionKey(normalized))
            remove(draftSelectedCountKey(normalized))
            remove(draftLastAbandonedShownKey(normalized))
            remove(draftLastDraftNudgeShownKey(normalized))
        }
    }

    // ============================================
    // Media full-permission preferences
    // ============================================

    fun getMediaFullPermissionRequestCount(): Int =
        prefs.getInt(KEY_MEDIA_FULL_PERMISSION_REQUEST_COUNT, 0)

    fun setMediaFullPermissionRequestCount(count: Int) {
        prefs.edit { putInt(KEY_MEDIA_FULL_PERMISSION_REQUEST_COUNT, count.coerceAtLeast(0)) }
    }

    fun isMediaFullPermissionBlockedAfterSecondDeny(): Boolean =
        prefs.getBoolean(KEY_MEDIA_FULL_PERMISSION_BLOCKED, false)

    fun setMediaFullPermissionBlockedAfterSecondDeny(blocked: Boolean) {
        prefs.edit { putBoolean(KEY_MEDIA_FULL_PERMISSION_BLOCKED, blocked) }
    }

    fun clearMediaFullPermissionStateOnGrant() {
        prefs.edit {
            remove(KEY_MEDIA_FULL_PERMISSION_BLOCKED)
            remove(KEY_MEDIA_FULL_PERMISSION_REQUEST_COUNT)
        }
    }

    // ============================================
    // Pending deep link (widget/shortcut before onboarding)
    // ============================================

    data class PendingDeepLink(
        val action: String,
        val templateId: String?,
        val songId: Long?
    )

    /**
     * Save a widget/shortcut deep link to be consumed after onboarding completes.
     * Stores action + typed extras individually for correct serialization.
     */
    fun setPendingDeepLink(action: String?, templateId: String?, songId: Long?) {
        prefs.edit {
            if (action != null) putString(KEY_PENDING_DEEP_LINK_ACTION, action)
            else remove(KEY_PENDING_DEEP_LINK_ACTION)
            if (templateId != null) putString(KEY_PENDING_DEEP_LINK_TEMPLATE_ID, templateId)
            else remove(KEY_PENDING_DEEP_LINK_TEMPLATE_ID)
            if (songId != null && songId > 0L) putLong(KEY_PENDING_DEEP_LINK_SONG_ID, songId)
            else remove(KEY_PENDING_DEEP_LINK_SONG_ID)
        }
    }

    /**
     * Atomically read and clear the pending deep link (action + all extras).
     * Returns null if no pending deep link is stored.
     */
    fun consumePendingDeepLink(): PendingDeepLink? {
        val action = prefs.getString(KEY_PENDING_DEEP_LINK_ACTION, null) ?: return null
        val templateId = prefs.getString(KEY_PENDING_DEEP_LINK_TEMPLATE_ID, null)
        val songId = prefs.getLong(KEY_PENDING_DEEP_LINK_SONG_ID, -1L).takeIf { it > 0L }
        prefs.edit {
            remove(KEY_PENDING_DEEP_LINK_ACTION)
            remove(KEY_PENDING_DEEP_LINK_TEMPLATE_ID)
            remove(KEY_PENDING_DEEP_LINK_SONG_ID)
        }
        return PendingDeepLink(action, templateId, songId)
    }

    /**
     * Clear all preferences (for testing/logout)
     */
    fun clear() {
        prefs.edit { clear() }
    }

    private fun itemKey(notificationType: String, itemId: Long): String {
        return itemKey(notificationType, itemId.toString())
    }

    private fun itemKey(notificationType: String, itemId: String): String {
        return "$KEY_NOTIFICATION_ITEM_LAST_SHOWN_PREFIX${notificationType.lowercase(Locale.ROOT)}_${itemId.lowercase(Locale.ROOT)}"
    }

    private fun typeEpochDayKey(notificationType: String): String {
        return "$KEY_NOTIFICATION_TYPE_DAILY_SHOWN_EPOCH_DAY_PREFIX${notificationType.lowercase(Locale.ROOT)}"
    }

    private fun typeCountKey(notificationType: String): String {
        return "$KEY_NOTIFICATION_TYPE_DAILY_SHOWN_COUNT_PREFIX${notificationType.lowercase(Locale.ROOT)}"
    }

    private fun notificationTapKey(notificationType: String, itemId: String): String {
        return "$KEY_NOTIFICATION_LAST_TAP_PREFIX${notificationType.lowercase(Locale.ROOT)}_${itemId.lowercase(Locale.ROOT)}"
    }

    private fun videoGeneratedAtKey(projectId: String): String = "$KEY_VIDEO_REMINDER_GENERATED_AT_PREFIX$projectId"
    private fun videoTemplateIdKey(projectId: String): String = "$KEY_VIDEO_REMINDER_TEMPLATE_ID_PREFIX$projectId"
    private fun videoSongIdKey(projectId: String): String = "$KEY_VIDEO_REMINDER_SONG_ID_PREFIX$projectId"
    private fun videoThumbnailKey(projectId: String): String = "$KEY_VIDEO_REMINDER_THUMBNAIL_URI_PREFIX$projectId"
    private fun videoSavedAtKey(projectId: String): String = "$KEY_VIDEO_REMINDER_SAVED_AT_PREFIX$projectId"
    private fun videoSharedAtKey(projectId: String): String = "$KEY_VIDEO_REMINDER_SHARED_AT_PREFIX$projectId"
    private fun videoLastOpenedAtKey(projectId: String): String = "$KEY_VIDEO_REMINDER_LAST_OPENED_AT_PREFIX$projectId"

    private fun draftTemplateIdKey(draftId: String): String = "$KEY_DRAFT_REMINDER_TEMPLATE_ID_PREFIX$draftId"
    private fun draftSongIdKey(draftId: String): String = "$KEY_DRAFT_REMINDER_SONG_ID_PREFIX$draftId"
    private fun draftExitedAtKey(draftId: String): String = "$KEY_DRAFT_REMINDER_EXITED_AT_PREFIX$draftId"
    private fun draftExitSessionKey(draftId: String): String = "$KEY_DRAFT_REMINDER_EXIT_SESSION_PREFIX$draftId"
    private fun draftSelectedCountKey(draftId: String): String = "$KEY_DRAFT_REMINDER_SELECTED_COUNT_PREFIX$draftId"
    private fun draftLastAbandonedShownKey(draftId: String): String = "$KEY_DRAFT_REMINDER_LAST_ABANDONED_SHOWN_PREFIX$draftId"
    private fun draftLastDraftNudgeShownKey(draftId: String): String = "$KEY_DRAFT_REMINDER_LAST_DRAFT_NUDGE_SHOWN_PREFIX$draftId"

    private fun mutateStringSet(
        key: String,
        mutator: (MutableSet<String>) -> Unit
    ) {
        synchronized(stringSetLock) {
            val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
            mutator(current)
            prefs.edit(commit = true) {
                putStringSet(key, current)
            }
        }
    }

    private fun normalizeDraftId(draftId: String): String {
        return draftId
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "_")
    }

    private fun epochDay(nowMs: Long): Long {
        return Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
    }

    private fun todayLocalDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    fun getSeenTemplateIds(): Set<String> {
        synchronized(stringSetLock) {
            return prefs.getStringSet(KEY_SEEN_TEMPLATE_IDS, emptySet()) ?: emptySet()
        }
    }

    fun addSeenTemplateIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        mutateStringSet(KEY_SEEN_TEMPLATE_IDS) { current ->
            // Add new IDs (they become most recent)
            current.addAll(ids)
            // Cap at MAX_SEEN_TEMPLATE_IDS using a simple retention policy if it exceeds limit
            if (current.size > MAX_SEEN_TEMPLATE_IDS) {
                val kept = current.toList().takeLast(MAX_SEEN_TEMPLATE_IDS).toSet()
                current.clear()
                current.addAll(kept)
            }
        }
    }

    fun clearSeenTemplateIds() {
        synchronized(stringSetLock) {
            prefs.edit(commit = true) {
                remove(KEY_SEEN_TEMPLATE_IDS)
            }
        }
    }
}
