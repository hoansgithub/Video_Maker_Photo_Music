package com.videomaker.aimusic.data.repository

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

/**
 * Implementation of SongRepository using Supabase Postgrest.
 */
class SongRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : SongRepository {

    companion object {
        private const val TABLE_SONGS = "songs"
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
        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SongDto>()

            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
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

    override suspend fun getSongsByGenre(genre: String, limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            // Filter by genre array contains
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

            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
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
}
