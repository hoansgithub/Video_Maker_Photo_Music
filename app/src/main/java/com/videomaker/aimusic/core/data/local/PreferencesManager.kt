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
        private const val RECENT_SEARCHES_DELIMITER = "\u001F" // Unit Separator
        private const val GENRES_DELIMITER = ","
        private const val MAX_RECENT_SEARCHES = 10
    }

    /** Music genre preferences selected during onboarding. Empty = no preference set. */
    fun getPreferredGenres(): List<String> {
        val raw = prefs.getString(KEY_PREFERRED_GENRES, null) ?: return emptyList()
        return raw.split(GENRES_DELIMITER).filter { it.isNotBlank() }
    }

    fun setPreferredGenres(genres: List<String>) {
        prefs.edit { putString(KEY_PREFERRED_GENRES, genres.joinToString(GENRES_DELIMITER)) }
    }

    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    /**
     * Mark onboarding as complete
     */
    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, complete) }
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
     * Add a search query to recent searches (most recent first, max 10)
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

    /**
     * Clear all preferences (for testing/logout)
     */
    fun clear() {
        prefs.edit { clear() }
    }
}
