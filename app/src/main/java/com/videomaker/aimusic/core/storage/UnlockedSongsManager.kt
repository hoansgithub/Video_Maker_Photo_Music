package com.videomaker.aimusic.core.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages locally unlocked songs.
 *
 * Songs start as locked (is_premium = true in database).
 * When user watches a rewarded ad, the song is unlocked locally and stored in SharedPreferences.
 * Unlocked state persists across app restarts and device reboots.
 *
 * Storage: SharedPreferences with Set<String> of unlocked song IDs
 */
class UnlockedSongsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Mutex to prevent race conditions on parallel unlock operations
    private val mutex = Mutex()

    // In-memory cache of unlocked song IDs for fast checks
    private val _unlockedSongIds = MutableStateFlow<Set<Long>>(loadUnlockedIds())
    val unlockedSongIds: StateFlow<Set<Long>> = _unlockedSongIds.asStateFlow()

    companion object {
        private const val PREFS_NAME = "unlocked_songs"
        private const val KEY_UNLOCKED_IDS = "unlocked_ids"
    }

    /**
     * Load unlocked song IDs from SharedPreferences.
     */
    private fun loadUnlockedIds(): Set<Long> {
        return prefs.getStringSet(KEY_UNLOCKED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Check if a song is unlocked locally.
     *
     * @param songId The song ID to check
     * @return true if unlocked, false if still locked
     */
    fun isUnlocked(songId: Long): Boolean {
        return _unlockedSongIds.value.contains(songId)
    }

    /**
     * Unlock a song locally after watching rewarded ad.
     * Persists to SharedPreferences and updates in-memory cache.
     * Thread-safe with mutex to prevent race conditions on parallel unlocks.
     *
     * @param songId The song ID to unlock
     */
    suspend fun unlockSong(songId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentIds = _unlockedSongIds.value.toMutableSet()
            if (currentIds.add(songId)) {
                // Save to SharedPreferences
                prefs.edit()
                    .putStringSet(KEY_UNLOCKED_IDS, currentIds.map { it.toString() }.toSet())
                    .apply()

                // Update in-memory cache
                _unlockedSongIds.value = currentIds

                android.util.Log.d("UnlockedSongs", "✅ Unlocked song: $songId")
            }
        }
    }

    /**
     * Clear all unlocked songs (for testing or reset).
     * Thread-safe with mutex to prevent race conditions.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit()
                .clear()
                .apply()
            _unlockedSongIds.value = emptySet()
            android.util.Log.d("UnlockedSongs", "🗑️ Cleared all unlocked songs")
        }
    }

    /**
     * Get count of unlocked songs.
     */
    fun getUnlockedCount(): Int {
        return _unlockedSongIds.value.size
    }
}
