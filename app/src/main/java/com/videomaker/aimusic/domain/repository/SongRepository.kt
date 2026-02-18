package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.MusicSong

/**
 * Repository for fetching songs from Supabase.
 */
interface SongRepository {
    /**
     * Get all active songs, sorted by sort_order
     */
    suspend fun getAllSongs(): Result<List<MusicSong>>

    /**
     * Get featured/trending songs (high sort_order)
     */
    suspend fun getFeaturedSongs(limit: Int = 10): Result<List<MusicSong>>

    /**
     * Get a single song by ID
     */
    suspend fun getSongById(id: Long): Result<MusicSong>

    /**
     * Search songs by name or artist
     */
    suspend fun searchSongs(query: String): Result<List<MusicSong>>

    /**
     * Get all distinct genre strings from active songs, sorted alphabetically.
     */
    suspend fun getGenres(): Result<List<String>>

    /**
     * Get songs by genre
     */
    suspend fun getSongsByGenre(genre: String, limit: Int = 20): Result<List<MusicSong>>

    /**
     * Get paginated songs
     */
    suspend fun getSongsPaged(offset: Int, limit: Int): Result<List<MusicSong>>

    /**
     * Get suggested songs personalised by [preferredGenres].
     * Falls back to top songs by sort_order when no genres are provided.
     */
    suspend fun getSuggestedSongs(preferredGenres: List<String>, limit: Int = 10): Result<List<MusicSong>>

    /**
     * Get a random selection of songs (over-fetches then shuffles client-side)
     */
    suspend fun getRandomSongs(limit: Int = 10): Result<List<MusicSong>>

    /**
     * Clears all cached song data from disk.
     * Call before pull-to-refresh to force fresh network fetches.
     */
    suspend fun clearCache()
}
