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
     * Get songs by genre
     */
    suspend fun getSongsByGenre(genre: String, limit: Int = 20): Result<List<MusicSong>>

    /**
     * Get paginated songs
     */
    suspend fun getSongsPaged(offset: Int, limit: Int): Result<List<MusicSong>>
}
