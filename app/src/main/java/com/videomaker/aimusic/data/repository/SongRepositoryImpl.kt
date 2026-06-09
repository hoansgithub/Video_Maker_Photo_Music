package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.core.util.I18nHelper
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
import kotlinx.serialization.json.JsonObject
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
    private val regionProvider: RegionProvider,
    private val languageManager: LanguageManager
) : SongRepository {

    companion object {
        private const val TABLE_SONGS = "songs"
        private const val TABLE_GENRES = "genres"
        private const val FN_SEARCH_SONGS = "search_songs"
        private const val FN_SONGS_SORTED = "get_songs_sorted"
        private const val FN_SONGS_BY_GENRES_SORTED = "get_songs_by_genres_sorted"
        private const val FN_GENRES_BY_POPULARITY = "get_genres_by_popularity"
        private const val ERROR_LOAD_FAILED = "Failed to load songs"
        private const val ERROR_NOT_FOUND = "Song not found"
        private const val MEMORY_SONG_CACHE_LIMIT = 500
    }

    // Reuse list-loaded songs for subsequent getSongById calls.
    private val songsByIdMemoryCache = java.util.concurrent.ConcurrentHashMap<Long, MusicSong>()

    private fun cacheSongsInMemory(songs: List<MusicSong>) {
        if (songs.isEmpty()) return
        if (songsByIdMemoryCache.size > MEMORY_SONG_CACHE_LIMIT) {
            songsByIdMemoryCache.clear()
        }
        songs.forEach { song -> songsByIdMemoryCache[song.id] = song }
    }

    private fun cacheSongInMemory(song: MusicSong) {
        if (songsByIdMemoryCache.size > MEMORY_SONG_CACHE_LIMIT) {
            songsByIdMemoryCache.clear()
        }
        songsByIdMemoryCache[song.id] = song
    }


    override suspend fun getFeaturedSongs(limit: Int, offset: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val region = regionProvider.getRegionCode()
        // Include limit in cache key to avoid conflicts between home preview (9) and full list (20)
        val cacheKey = "${ApiCacheManager.keySongsWeeklyRanking(region)}_${limit}"

        // Only cache first page (offset = 0)
        if (offset == 0) {
            apiCacheManager.get<List<MusicSong>>(cacheKey)
                ?.let {
                    cacheSongsInMemory(it)
                    return@withContext Result.success(it)
                }
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

            cacheSongsInMemory(songs)

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
                    ?.let {
                        cacheSongsInMemory(it)
                        return@withContext Result.success(it)
                    }
            }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongById(id: Long): Result<MusicSong> = withContext(Dispatchers.IO) {
        songsByIdMemoryCache[id]?.let { cachedSong ->
            // Trust in-memory cache only when hook is present; otherwise refetch to self-heal
            // from legacy cached list payloads that may not contain hook_start_time yet.
            if (cachedSong.hookStartTimeMs > 0L) {
                return@withContext Result.success(cachedSong)
            }
        }

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
                val mappedSong = song.toMusicSong()
                cacheSongInMemory(mappedSong)
                Result.success(mappedSong)
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
            val mappedSongs = songs.toMusicSongs()
            cacheSongsInMemory(mappedSongs)
            Result.success(mappedSongs)
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /**
     * Fetches genres from the `genres` table, filtered by type = "genre" to exclude
     * country codes and tags. Returns active genres as SongGenre objects (id + displayName),
     * sorted by popularity (sort_order DESC).
     *
     * Note: Currently using display_name. When label_i18n is populated, genre names
     * will be localized and cache key already includes locale for future compatibility.
     */
    override suspend fun getGenres(): Result<List<SongGenre>> = withContext(Dispatchers.IO) {
        val region = regionProvider.getRegionCode()
        val locale = languageManager.getSelectedLanguage()
        val cacheKey = ApiCacheManager.keySongsGenres(locale, region)
        apiCacheManager.get<List<SongGenre>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            // Sorted by region priority: genres with songs in user's region first,
            // then remaining genres by static sort_order. No filtering — all genres shown.
            val genres = supabaseClient.postgrest
                .rpc(FN_GENRES_BY_POPULARITY, buildJsonObject {
                    put("p_region", region)
                })
                .decodeList<GenreDto>()
                .map {
                    val localizedName = I18nHelper.getLocalizedValue(
                        i18nData = it.labelI18n,
                        locale = locale,
                        fallback = it.displayName.ifEmpty { it.id }
                    )
                    SongGenre(id = it.id, displayName = localizedName)
                }

            apiCacheManager.put(cacheKey, genres)
            Result.success(genres)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<SongGenre>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun getSongsByGenre(genre: String, limit: Int, offset: Int): Result<List<MusicSong>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.keySongsGenre(genre)

        // Only use cache for first page (offset = 0)
        if (offset == 0) {
            apiCacheManager.get<List<MusicSong>>(cacheKey)
                ?.let {
                    cacheSongsInMemory(it)
                    return@withContext Result.success(it)
                }
        }

        try {
            val songs = supabaseClient.from(TABLE_SONGS)
                .select {
                    filter {
                        eq("is_active", true)
                        contains("genres", listOf(genre))
                    }
                    order("sort_order", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<SongDto>()
                .toMusicSongs()

            cacheSongsInMemory(songs)

            // Only cache first page
            if (offset == 0) {
                apiCacheManager.put(cacheKey, songs)
            }
            Result.success(songs)
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "getSongsByGenre failed for genre=$genre: ${e.message}")
            // Only return stale cache for first page
            if (offset == 0) {
                apiCacheManager.getStale<List<MusicSong>>(cacheKey)
                    ?.let {
                        cacheSongsInMemory(it)
                        return@withContext Result.success(it)
                    }
            }
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

            val mappedSongs = songs.toMusicSongs()
            cacheSongsInMemory(mappedSongs)
            Result.success(mappedSongs)
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    /** DTO for genres table row (id, display_name, label_i18n, type, sort_order, is_active). */
    @Serializable
    private data class GenreDto(
        val id: String,
        @SerialName("display_name")
        val displayName: String = "",
        @SerialName("label_i18n")
        val labelI18n: JsonObject? = null,
        val type: String = "",
        @SerialName("sort_order")
        val sortOrder: Int = 0,
        @SerialName("is_active")
        val isActive: Boolean = true,
        @SerialName("total_usage_count")
        val totalUsageCount: Long = 0
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
        val cacheKey = "songs_suggested_v2_${region}_${genresKey}_${limit}"

        // Only cache first page (offset = 0)
        if (offset == 0) {
            apiCacheManager.get<List<MusicSong>>(cacheKey)
                ?.let {
                    cacheSongsInMemory(it)
                    return@withContext Result.success(it)
                }
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

            cacheSongsInMemory(songs)

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
                    ?.let {
                        cacheSongsInMemory(it)
                        return@withContext Result.success(it)
                    }
            }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun incrementUseCount(songId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Increment usage_count by 1 using Supabase RPC function
                supabaseClient.postgrest.rpc(
                    "increment_song_usage_count",
                    buildJsonObject {
                        put("song_id", songId)
                    }
                )
                android.util.Log.d("SongRepository", "✅ Incremented usage_count for song: $songId")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("SongRepository", "❌ Failed to increment usage_count: ${e.message}")
                Result.failure(e)
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
                    limit(fetchCount)
                }
                .decodeList<SongDto>()

            val mappedSongs = songs.shuffled().take(limit).toMusicSongs()
            cacheSongsInMemory(mappedSongs)
            Result.success(mappedSongs)
        } catch (e: Exception) {
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }
}
