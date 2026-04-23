package com.videomaker.aimusic.data.repository

import android.content.Context
import android.util.Log
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.repository.BeatSyncRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Implementation of BeatSyncRepository using Supabase Storage.
 *
 * Beat JSON files are stored in Supabase storage bucket: beats-cache
 * Path format: beats-cache/<song_id>.json
 *
 * Three-layer caching strategy:
 * 1. In-memory cache: Instant access (Map<songId, BeatSyncData>)
 * 2. File cache: <app_cache_dir>/beats_cache/<songId>.json (~50ms)
 * 3. Supabase download: Network fetch (~500ms)
 *
 * Beat-sync data is SONG metadata, shared across all projects using that song.
 * Multiple projects with the same song reuse the same cached data.
 *
 * Graceful degradation strategy:
 * - Network errors, 404s, parsing errors → return null (NOT failure)
 * - Null triggers fallback to legacy fixed-duration mode
 * - No user-facing errors, only logged warnings
 */
class BeatSyncRepositoryImpl(
    private val context: Context,
    private val supabaseClient: SupabaseClient
) : BeatSyncRepository {

    private val cacheDir = File(context.cacheDir, "beats_cache")

    // In-memory cache: song ID → BeatSyncData
    // Shared across all projects using the same song
    // Cleared when app process dies (file cache persists)
    private val memoryCache = mutableMapOf<Long, BeatSyncData?>()

    companion object {
        private const val TAG = "BeatSyncRepository"
        private const val BUCKET_NAME = "beats-cache"
    }

    override suspend fun getBeatData(songId: Long): Result<BeatSyncData?> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Check in-memory cache first (instant, shared across all projects)
            if (memoryCache.containsKey(songId)) {
                Log.d(TAG, "✅ Beat data from memory cache: $songId")
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

            // 3. Download from Supabase storage (slow, network required)
            Log.d(TAG, "Downloading beat data from Supabase: $songId")
            val bytes = supabaseClient.storage
                .from(BUCKET_NAME)
                .downloadPublic("$songId.json")

            if (bytes.isEmpty()) {
                Log.w(TAG, "Beat file not found: $songId.json - falling back to legacy mode")
                memoryCache[songId] = null  // Cache the null result to avoid repeated downloads
                return@withContext Result.success(null) // Graceful degradation
            }

            // Cache to file (persists across app restarts)
            cacheDir.mkdirs()
            cacheFile.writeBytes(bytes)
            Log.d(TAG, "Beat data cached to file: $songId (${bytes.size} bytes)")

            val data = parseJson(bytes.decodeToString())
            Log.d(TAG, "✅ Beat-sync loaded from network: $songId - ${data.beats.size} beats, ${data.bpm} BPM")

            // Store in memory cache for instant access next time
            memoryCache[songId] = data
            Result.success(data)

        } catch (e: Exception) {
            // ANY error → return null (graceful degradation, NOT a failure)
            Log.w(TAG, "Failed to load beat data for $songId: ${e.message} - falling back to legacy mode")
            memoryCache[songId] = null  // Cache the failure to avoid repeated attempts
            Result.success(null)
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

        // beats is [[time_s, kick_s], ...] — extract only time_s (index 0)
        val beats = (0 until beatsArray.length()).map { i ->
            val pair = beatsArray.getJSONArray(i)
            pair.getDouble(0)  // time in seconds (ignore kick_strength at index 1)
        }

        return BeatSyncData(
            beats = beats,
            bpm = obj.getDouble("bpm"),
            numBeats = obj.getInt("num_beats")
        )
    }
}
