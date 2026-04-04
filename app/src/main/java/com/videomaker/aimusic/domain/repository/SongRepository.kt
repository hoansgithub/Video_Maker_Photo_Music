package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre

/**
 * Repository for fetching songs from Supabase.
 */
interface SongRepository {
    /**
     * Get featured/trending songs (high sort_order)
     */
    suspend fun getFeaturedSongs(limit: Int = 10, offset: Int = 0): Result<List<MusicSong>>

    /**
     * Get a single song by ID
     */
    suspend fun getSongById(id: Long): Result<MusicSong>

    /**
     * Search songs by name or artist
     */
    suspend fun searchSongs(query: String): Result<List<MusicSong>>

    /**
     * Search songs by name or artist with pagination
     */
    suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<List<MusicSong>>

    /**
     * Get all distinct genre strings from active songs, sorted alphabetically.
     */
    suspend fun getGenres(): Result<List<SongGenre>>

    /**
     * Get songs by genre
     */
    suspend fun getSongsByGenre(genre: String, limit: Int = 20): Result<List<MusicSong>>

    /**
     * Get paginated songs
     */
    suspend fun getSongsPaged(offset: Int, limit: Int): Result<List<MusicSong>>

    /**
     * Get suggested songs personalised by [preferredGenres] with pagination.
     * Falls back to top songs by sort_order when no genres are provided.
     *
     * @param preferredGenres List of genre IDs to filter by
     * @param offset Starting position for pagination
     * @param limit Number of items to return
     */
    suspend fun getSuggestedSongs(
        preferredGenres: List<String>,
        offset: Int = 0,
        limit: Int = 10
    ): Result<List<MusicSong>>

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
