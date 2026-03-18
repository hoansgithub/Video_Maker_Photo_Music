package com.videomaker.aimusic.core.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based API cache using Android's cacheDir.
 * - Persistent across app restarts
 * - Auto-cleared by system when storage is low
 * - Simple TTL-based expiration (file lastModified timestamp)
 */
class ApiCacheManager(context: Context) {

    @PublishedApi
    internal val cacheDir = File(context.applicationContext.cacheDir, "api").apply { mkdirs() }

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Returns cached data if it exists and is within [ttlMillis].
     * Returns null on cache miss, expiry, or any read error.
     */
    /**
     * Returns cached data if it exists and is within [ttlMillis].
     * Returns null on cache miss or expiry — but does NOT delete the file on expiry,
     * so stale data remains available for [getStale] fallback on network errors.
     */
    suspend inline fun <reified T> get(key: String, ttlMillis: Long = DEFAULT_TTL): T? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "$key.json")
                if (!file.exists()) return@withContext null

                val age = System.currentTimeMillis() - file.lastModified()
                if (age > ttlMillis) return@withContext null  // expired — but file kept for stale fallback

                json.decodeFromString<T>(file.readText())
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Returns cached data regardless of age — used as a last-resort fallback
     * when the network is unavailable and the TTL has expired.
     * Stale data > error screen.
     */
    suspend inline fun <reified T> getStale(key: String): T? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "$key.json")
                if (!file.exists()) return@withContext null
                json.decodeFromString<T>(file.readText())
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Writes [data] to disk under [key]. Write failures are silently ignored
     * so a cache write never breaks the happy path.
     */
    suspend inline fun <reified T> put(key: String, data: T) {
        withContext(Dispatchers.IO) {
            try {
                File(cacheDir, "$key.json").writeText(json.encodeToString(data))
            } catch (_: Exception) {
                // Non-fatal — cache write failure does not affect the caller
            }
        }
    }

    /**
     * Deletes all cache files whose names start with "songs_".
     * Scoped so a songs refresh never evicts unrelated cached data.
     */
    suspend fun clearSongCache() {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles { file -> file.name.startsWith("songs_") }
                ?.forEach { it.delete() }
        }
    }

    /** Deletes all cache files whose names start with "templates_". */
    suspend fun clearTemplateCache() {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles { file -> file.name.startsWith("templates_") }
                ?.forEach { it.delete() }
        }
    }

    /** Deletes every file in the cache directory. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        /** Default TTL: 24 hours */
        const val DEFAULT_TTL = 24L * 60 * 60 * 1000

        // ── Song cache keys ──────────────────────────────────────────────────
        const val KEY_SONGS_GENRES         = "songs_genres"
        const val KEY_SONGS_SUGGESTED      = "songs_suggested"
        const val KEY_SONGS_WEEKLY_RANKING = "songs_weekly_ranking"

        /** Dynamic key per genre — safe for use as a filename. */
        fun keySongsGenre(genre: String): String =
            "songs_genre_${genre.lowercase().replace(' ', '_')}"

        // ── Template cache keys ──────────────────────────────────────────────
        const val KEY_VIBE_TAGS = "vibe_tags_theme"
        fun keyTemplates(limit: Int, offset: Int): String = "templates_${limit}_${offset}"
        fun keyTemplatesByTag(tag: String, limit: Int, offset: Int): String =
            "templates_tag_${tag}_${limit}_${offset}"
    }
}
