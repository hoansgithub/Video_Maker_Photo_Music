package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.data.mapper.toMusicSong
import com.videomaker.aimusic.data.mapper.toMusicSongs
import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.TextSearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Implementation of SongRepository using Supabase Postgrest.
 */
class SongRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager
) : SongRepository {

    companion object {
        private const val TABLE_SONGS = "songs"
        private const val TABLE_GENRES = "genres"
        private const val ERROR_LOAD_FAILED = "Failed to load songs"
        private const val ERROR_NOT_FOUND = "Song not found"
    }

    override suspend fun getAllSongs(): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                }
                .decodeList<SongDto>()

            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getFeaturedSongs(limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.KEY_SONGS_WEEKLY_RANKING
        apiCacheManager.get<List<MusicSong>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter { eq("is_active", true) }
                    order("sort_order", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SongDto>()
                .toMusicSongs()

            apiCacheManager.put(cacheKey, songs)
            Result.success(songs)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongById(id: Long): Result<MusicSong> = withContext(Dispatchers.IO) {
        try {
            val song = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("id", id)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<SongDto>()

            if (song != null) {
                Result.success(song.toMusicSong())
            } else {
                Result.failure(Exception(ERROR_NOT_FOUND))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /**
     * Multi-tier search:
     * Tier 1: Full-text search (fast, GIN index, English/Latin)
     * Tier 2: ILIKE fallback (slower, all languages incl. CJK)
     * Deduplicates results across tiers.
     */
    override suspend fun searchSongs(query: String): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            val searchQuery = query.trim()
            if (searchQuery.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val escapedQuery = searchQuery.replace("'", "''")
            val seenIds = mutableSetOf<Long>()
            val results = mutableListOf<MusicSong>()

            // Tier 1: Full-text search with OR logic (fast, uses GIN index)
            // "love story" → "love:* | story:*"
            val tsQuery = searchQuery
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" | ") { "${it.replace("'", "''")}:*" }

            try {
                val ftsResults = supabaseClient.from(TABLE_SONGS)
                    .select {
                        filter {
                            eq("is_active", true)
                            textSearch("search_vector", tsQuery, TextSearchType.NONE, "simple")
                        }
                        order("sort_order", Order.DESCENDING)
                        limit(30)
                    }
                    .decodeList<SongDto>()

                ftsResults.forEach { dto ->
                    val song = dto.toMusicSong()
                    if (seenIds.add(song.id)) {
                        results.add(song)
                    }
                }
            } catch (_: Exception) {
                // FTS may fail for some queries, continue to Tier 2
            }

            // Tier 2: ILIKE fallback (all languages, CJK support)
            if (results.size < 30) {
                val remaining = 30 - results.size
                val ilikeResults = supabaseClient.from(TABLE_SONGS)
                    .select {
                        filter {
                            eq("is_active", true)
                            or {
                                ilike("name", "%$escapedQuery%")
                                ilike("artist", "%$escapedQuery%")
                            }
                        }
                        order("sort_order", Order.DESCENDING)
                        limit(remaining.toLong())
                    }
                    .decodeList<SongDto>()

                ilikeResults.forEach { dto ->
                    val song = dto.toMusicSong()
                    if (seenIds.add(song.id)) {
                        results.add(song)
                    }
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /**
     * Fetches genres from the `genres` table, filtered by type = "genre" to exclude
     * country codes and tags. Returns only active genre IDs, sorted by popularity (sort_order DESC).
     */
    override suspend fun getGenres(): Result<List<String>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.KEY_SONGS_GENRES
        apiCacheManager.get<List<String>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val genres = supabaseClient.from(TABLE_GENRES)
                .select(Columns.raw("id")) {
                    filter {
                        eq("type", "genre")
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                }
                .decodeList<GenreDto>()
                .map { it.id }

            apiCacheManager.put(cacheKey, genres)
            Result.success(genres)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<String>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongsByGenre(genre: String, limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.keySongsGenre(genre)
        apiCacheManager.get<List<MusicSong>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                        contains("genres", listOf(genre))
                    }
                    order("sort_order", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SongDto>()
                .toMusicSongs()

            apiCacheManager.put(cacheKey, songs)
            Result.success(songs)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongsPaged(offset: Int, limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<SongDto>()

            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /** Minimal DTO for genres-only queries — avoids transferring full song rows. */
    @Serializable
    private data class SongGenresDto(
        val genres: List<String> = emptyList()
    )

    /** DTO for genres table row (id, display_name, type, sort_order, is_active). */
    @Serializable
    private data class GenreDto(
        val id: String,
        @SerialName("display_name")
        val displayName: String = "",
        val type: String = "",
        @SerialName("sort_order")
        val sortOrder: Int = 0,
        @SerialName("is_active")
        val isActive: Boolean = true
    )

    /**
     * Cache key: songs_suggested
     * Genre-aware: filters by preferredGenres overlap when provided,
     * otherwise falls back to top songs by sort_order.
     */
    override suspend fun getSuggestedSongs(
        preferredGenres: List<String>,
        limit: Int
    ): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.KEY_SONGS_SUGGESTED
        apiCacheManager.get<List<MusicSong>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            // DB stores genres in lowercase (e.g. "pop", "hip-hop") — normalise before querying
            val normalised = preferredGenres.map { it.lowercase() }
            val songs = if (normalised.isNotEmpty()) {
                supabaseClient.from(TABLE_SONGS)
                    .select {
                        filter {
                            eq("is_active", true)
                            overlaps("genres", normalised)
                        }
                        order("sort_order", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<SongDto>()
                    .toMusicSongs()
            } else {
                supabaseClient.from(TABLE_SONGS)
                    .select {
                        filter { eq("is_active", true) }
                        order("sort_order", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<SongDto>()
                    .toMusicSongs()
            }

            apiCacheManager.put(cacheKey, songs)
            Result.success(songs)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun clearCache() {
        apiCacheManager.clearSongCache()
    }

    /**
     * Over-fetches (limit × 5, max 50) then shuffles client-side.
     * Avoids a custom DB function while still feeling random across sessions.
     * NOT cached — randomness is the point.
     */
    override suspend fun getRandomSongs(limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            val fetchCount = (limit * 5L).coerceAtMost(50L)
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                    limit(fetchCount)
                }
                .decodeList<SongDto>()

            Result.success(songs.shuffled().take(limit).toMusicSongs())
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }
}
