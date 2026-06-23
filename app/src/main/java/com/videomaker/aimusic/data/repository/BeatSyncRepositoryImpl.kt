package com.videomaker.aimusic.data.repository

import android.content.Context
import android.util.Log
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.repository.BeatSyncRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Implementation of BeatSyncRepository.
 *
 * Beat JSON files are downloaded from URLs stored in the `beats_url` column of the songs table.
 *
 * Three-layer caching strategy:
 * 1. In-memory cache: Instant access (Map<songId, BeatSyncData>)
 * 2. File cache: <app_cache_dir>/beats_cache/<songId>.json (~50ms)
 * 3. Network download: Fetch from beats_url (~500ms)
 *
 * Beat-sync data is SONG metadata, shared across all projects using that song.
 * Multiple projects with the same song reuse the same cached data.
 *
 * Error handling strategy:
 * - Network errors, timeouts, parse errors -> return Result.failure() (retryable)
 * - Genuinely missing data (no beats_url, empty file) -> return Result.success(null) (legacy fallback)
 * - Only cache successful results and confirmed-missing data (NOT failures)
 */
class BeatSyncRepositoryImpl(
    private val context: Context,
    private val supabaseClient: SupabaseClient
) : BeatSyncRepository {

    private val cacheDir = File(context.cacheDir, "beats_cache")

    // In-memory cache: song ID -> BeatSyncData
    // Shared across all projects using the same song
    // Cleared when app process dies (file cache persists)
    private val memoryCache = mutableMapOf<Long, BeatSyncData?>()

    companion object {
        private const val TAG = "BeatSyncRepository"
        private const val TABLE_SONGS = "songs"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 20_000
    }

    override suspend fun getBeatData(songId: Long, beatsUrl: String?): Result<BeatSyncData?> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Check in-memory cache first (instant, shared across all projects)
            if (memoryCache.containsKey(songId)) {
                Log.d(TAG, "Beat data from memory cache: $songId")
                return@withContext Result.success(memoryCache[songId])
            }

            // 2. Check file cache (fast, persists across app restarts)
            val cacheFile = File(cacheDir, "$songId.json")
            if (cacheFile.exists()) {
                Log.d(TAG, "Loading beat data from file cache: $songId")
                val data = parseJson(cacheFile.readText())
                memoryCache[songId] = data  // Store in memory for next access
                return@withContext Result.success(data)
            }

            // 3. Resolve download URL: use provided beatsUrl or look up from songs table
            val url = if (!beatsUrl.isNullOrEmpty()) {
                beatsUrl
            } else {
                supabaseClient.from(TABLE_SONGS)
                    .select(Columns.raw("beats_url")) {
                        filter { eq("id", songId) }
                        limit(1)
                    }
                    .decodeSingleOrNull<BeatsUrlDto>()
                    ?.beatsUrl
            }

            if (url.isNullOrEmpty()) {
                Log.w(TAG, "No beats URL for song $songId - falling back to legacy mode")
                memoryCache[songId] = null  // Confirmed missing - safe to cache
                return@withContext Result.success(null)
            }

            Log.d(TAG, "Downloading beat data: $songId")
            val bytes = downloadWithTimeout(url)

            if (bytes.isEmpty()) {
                Log.w(TAG, "Beat file not found: $songId.json - falling back to legacy mode")
                memoryCache[songId] = null  // Confirmed empty - safe to cache
                return@withContext Result.success(null) // Graceful degradation
            }

            // Cache to file (persists across app restarts)
            cacheDir.mkdirs()
            cacheFile.writeBytes(bytes)
            Log.d(TAG, "Beat data cached to file: $songId (${bytes.size} bytes)")

            val data = parseJson(bytes.decodeToString())
            Log.d(TAG, "Beat-sync loaded from network: $songId - ${data.beats.size} beats, ${data.bpm} BPM")

            // Store in memory cache for instant access next time
            memoryCache[songId] = data
            Result.success(data)

        } catch (e: Exception) {
            // Network/timeout/parse error -> return failure (retryable)
            // Do NOT cache failures so retry can re-fetch from network
            Log.w(TAG, "Failed to load beat data for $songId: ${e.message}")
            Result.failure(e)
        }
    }

    override fun clearErrorCache(songId: Long) {
        // Remove cached null so the next getBeatData() hits the network
        if (memoryCache[songId] == null && memoryCache.containsKey(songId)) {
            memoryCache.remove(songId)
            Log.d(TAG, "Cleared error cache for song $songId")
        }
    }

    /**
     * Download bytes from URL with connect and read timeouts.
     * Uses HttpURLConnection instead of URL.readBytes() which has no timeout.
     */
    private fun downloadWithTimeout(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP $responseCode for beat data download: $url")
                return byteArrayOf()
            }

            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Clear in-memory cache (e.g., for testing or memory management).
     * File cache remains intact.
     */
    fun clearMemoryCache() {
        memoryCache.clear()
        Log.d(TAG, "In-memory cache cleared")
    }

    /**
     * Parse beat JSON into BeatSyncData.
     *
     * JSON format: { "beats": [[time_s, kick_strength], ...], "bpm": 95.0, "num_beats": 178 }
     *
     * Extracts only time_s (index 0) from each [time_s, kick_strength] pair.
     * Kick_strength is ignored.
     */
    private fun parseJson(json: String): BeatSyncData {
        val obj = JSONObject(json)
        val beatsArray = obj.getJSONArray("beats")

        // beats is [[time_s, kick_s], ...] -- extract only time_s (index 0)
        val beats = (0 until beatsArray.length()).map { i ->
            val pair = beatsArray.getJSONArray(i)
            pair.getDouble(0)  // time in seconds (ignore kick_strength at index 1)
        }

        return BeatSyncData(
            beats = beats,
            bpm = obj.getDouble("bpm"),
            numBeats = obj.optInt("num_beats", beats.size)
        )
    }

    @Serializable
    private data class BeatsUrlDto(
        @SerialName("beats_url") val beatsUrl: String? = null
    )
}
