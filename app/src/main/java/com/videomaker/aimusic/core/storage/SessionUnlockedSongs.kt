package com.videomaker.aimusic.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, app-session-scoped unlocked songs.
 *
 * A premium song unlocked by watching a rewarded ad stays unlocked for the lifetime of the
 * current app process — it survives opening/closing the music-search sheet — but is NOT
 * persisted. The set resets on app restart / process death (this is a plain in-memory object).
 *
 * This is intentionally separate from [UnlockedSongsManager], which persists permanent unlocks
 * to SharedPreferences.
 */
object SessionUnlockedSongs {

    private val _unlockedIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedIds: StateFlow<Set<Long>> = _unlockedIds.asStateFlow()

    fun isUnlocked(songId: Long): Boolean = _unlockedIds.value.contains(songId)

    /** Unlock a song for the current app session. */
    fun unlock(songId: Long) {
        if (!_unlockedIds.value.contains(songId)) {
            _unlockedIds.value = _unlockedIds.value + songId
        }
    }

    /** Clear all session unlocks (e.g. for testing). */
    fun clear() {
        _unlockedIds.value = emptySet()
    }
}
