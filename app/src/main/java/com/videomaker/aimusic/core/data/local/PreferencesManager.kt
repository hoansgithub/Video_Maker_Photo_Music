package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages SharedPreferences for the app
 * Injected as a singleton via ACCDI
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

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
        private const val RECENT_SEARCHES_DELIMITER = "\u001F" // Unit Separator
        private const val GENRES_DELIMITER = ","
        private const val MAX_RECENT_SEARCHES = 3 // FIFO: First In First Out
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
     * Uses .commit() for immediate synchronous write to prevent onboarding loop
     * if app is killed before async .apply() completes
     */
    fun setOnboardingComplete(complete: Boolean) {
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
    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return raw.split(RECENT_SEARCHES_DELIMITER).filter { it.isNotBlank() }
    }

    /**
     * Add a search query to recent searches (most recent first, max 3)
     */
    @Synchronized
    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

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
     */
    @Synchronized
    fun removeRecentSearch(query: String) {
        val current = getRecentSearches().toMutableList()
        current.remove(query)
        prefs.edit {
            putString(KEY_RECENT_SEARCHES, current.joinToString(RECENT_SEARCHES_DELIMITER))
        }
    }

    /**
     * Clear all recent searches
     */
    @Synchronized
    fun clearRecentSearches() {
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

    /**
     * Clear all preferences (for testing/logout)
     */
    fun clear() {
        prefs.edit { clear() }
    }
}
