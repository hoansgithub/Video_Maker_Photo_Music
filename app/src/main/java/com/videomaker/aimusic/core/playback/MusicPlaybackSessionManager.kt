package com.videomaker.aimusic.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-session-scoped registry shared across all MusicPlayerBottomSheet instances.
 *
 * Lives as a Koin singleton so state persists for the OS process lifetime — matches
 * the "session" wording in the next/previous spec. Process death resets everything.
 *
 * - [impressedSongIds]: every song that has fired `song_impression` (any location,
 *   including `song_player` from next/prev clicks). Used by `onNext()` to skip
 *   already-heard songs.
 * - [usedGenreIds]: genres the player has already pivoted to when the current
 *   category was exhausted. Used by `onNext()` to pick a still-unused genre.
 */
class MusicPlaybackSessionManager {

    private val _impressedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val impressedSongIds: StateFlow<Set<Long>> = _impressedSongIds.asStateFlow()

    private val _usedGenreIds = MutableStateFlow<Set<String>>(emptySet())
    val usedGenreIds: StateFlow<Set<String>> = _usedGenreIds.asStateFlow()

    fun markImpressed(songId: Long) {
        _impressedSongIds.update { it + songId }
    }

    fun markGenreUsed(genreId: String) {
        _usedGenreIds.update { it + genreId }
    }

    fun resetUsedGenres() {
        _usedGenreIds.value = emptySet()
    }

    fun resetSession() {
        _impressedSongIds.value = emptySet()
        _usedGenreIds.value = emptySet()
    }
}
