package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.data.mapper.toMusicSong
import com.videomaker.aimusic.data.mapper.toMusicSongs
import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.repository.SongRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Implementation of SongRepository using Supabase Postgrest.
 */
class SongRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val regionProvider: RegionProvider
) : SongRepository {

    companion object {
        private const val TABLE_SONGS = "songs"
        private const val TABLE_GENRES = "genres"
        private const val FN_SEARCH_SONGS = "search_songs"
        private const val FN_SONGS_SORTED = "get_songs_sorted"
        private const val FN_SONGS_BY_GENRES_SORTED = "get_songs_by_genres_sorted"
        private const val ERROR_LOAD_FAILED = "Failed to load songs"
        private const val ERROR_NOT_FOUND = "Song not found"
    }


    override suspend fun getFeaturedSongs(limit: Int, offset: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val region = regionProvider.getRegionCode()
        // Include limit in cache key to avoid conflicts between home preview (9) and full list (20)
        val cacheKey = "${ApiCacheManager.keySongsWeeklyRanking(region)}_${limit}"

        // Only cache first page (offset = 0)
        if (offset == 0) {
            apiCacheManager.get<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
        }

        try {
            val songs = supabaseClient.postgrest
                .rpc(FN_SONGS_SORTED, buildJsonObject {
                    put("p_region", region)
                    put("p_limit", limit)
                    put("p_offset", offset)
                })
                .decodeList<SongDto>()
                .toMusicSongs()


            // Only cache first page
            if (offset == 0) {
                apiCacheManager.put(cacheKey, songs)
            }
            Result.success(songs)
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "getFeaturedSongs failed: ${e.message}")
            // Only return stale cache for first page
            if (offset == 0) {
                apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
            }
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
     * Delegates to the `search_songs` Supabase function.
     *
     * The DB function runs FTS (GIN index) + ILIKE + genre-contains in a single
     * query with server-side dedup and ranking — 1 round trip instead of 3.
     */
    override suspend fun searchSongs(query: String): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())

        try {
            val songs = supabaseClient.postgrest
                .rpc(FN_SEARCH_SONGS, buildJsonObject {
                    put("search_query", q)
                    put("result_limit", 30)
                })
                .decodeList<SongDto>()
            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /**
     * Fetches genres from the `genres` table, filtered by type = "genre" to exclude
     * country codes and tags. Returns active genres as SongGenre objects (id + displayName),
     * sorted by popularity (sort_order DESC).
     *
     * Note: display_name is used as the human-readable label.
     * TODO: When label_i18n column is populated in DB, use label_i18n[locale] instead of display_name.
     */
    override suspend fun getGenres(): Result<List<SongGenre>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.KEY_SONGS_GENRES
        apiCacheManager.get<List<SongGenre>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val genres = supabaseClient.from(TABLE_GENRES)
                .select(Columns.raw("id, display_name")) {
                    filter {
                        eq("type", "genre")
                        eq("is_active", true)
                    }
                    order("sort_order", Order.DESCENDING)
                    limit(100)  // ✅ FIX: Prevent fetching billions of genres
                }
                .decodeList<GenreDto>()
                .map { SongGenre(id = it.id, displayName = it.displayName.ifEmpty { it.id }) }

            apiCacheManager.put(cacheKey, genres)
            Result.success(genres)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<SongGenre>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongsByGenre(genre: String, limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.keySongsGenre(genre)
        apiCacheManager.get<List<MusicSong>>(cacheKey)
            ?.let {
                return@withContext Result.success(it)
            }

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
            android.util.Log.e("SongRepository", "getSongsByGenre failed for genre=$genre: ${e.message}")
            apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongsPaged(offset: Int, limit: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        try {
            val region = regionProvider.getRegionCode()
            val songs = supabaseClient.postgrest
                .rpc(FN_SONGS_SORTED, buildJsonObject {
                    put("p_region", region)
                    put("p_limit", limit)
                    put("p_offset", offset)
                })
                .decodeList<SongDto>()

            Result.success(songs.toMusicSongs())
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

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
     * Fetches suggested songs with region ordering and genre filtering.
     * Delegates to the `get_songs_by_genres_sorted` Supabase RPC function.
     *
     * The DB function prioritizes user region over "all" region (like templates)
     * and filters by genre overlap when preferredGenres is provided.
     *
     * Cache key: songs_suggested (first page only)
     */
    override suspend fun getSuggestedSongs(
        preferredGenres: List<String>,
        offset: Int,
        limit: Int
    ): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val region = regionProvider.getRegionCode()

        // Cache key includes limit and genres to avoid conflicts between
        // Home screen preview (limit=10) and full list (limit=20)
        val genresKey = preferredGenres.sorted().joinToString(",").ifEmpty { "all" }
        val cacheKey = "songs_suggested_${region}_${genresKey}_${limit}"

        // Only cache first page (offset = 0)
        if (offset == 0) {
            apiCacheManager.get<List<MusicSong>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
        }

        try {
            // DB stores genres in lowercase (e.g. "pop", "hip-hop") — normalise before querying
            val normalised = preferredGenres.map { it.lowercase() }

            val genresArray = buildJsonArray {
                normalised.forEach { genre -> add(JsonPrimitive(genre)) }
            }


            val songs = supabaseClient.postgrest
                .rpc(FN_SONGS_BY_GENRES_SORTED, buildJsonObject {
                    put("p_region", region)
                    put("p_genres", genresArray)
                    put("p_limit", limit)
                    put("p_offset", offset)
                })
                .decodeList<SongDto>()
                .toMusicSongs()


            // Only cache first page
            if (offset == 0) {
                apiCacheManager.put(cacheKey, songs)
            }
            Result.success(songs)
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "getSuggestedSongs failed: ${e.message}")
            // Only return stale cache for first page
            if (offset == 0) {
                apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
            }
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
