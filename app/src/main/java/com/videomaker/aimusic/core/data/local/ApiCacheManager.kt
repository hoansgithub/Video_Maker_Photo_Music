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

    /**
     * Deletes all locale-dependent cache files (vibe tags, templates, genres).
     * Called when language changes to force re-fetch with new locale.
     */
    suspend fun clearLocalizedCache() {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles { file ->
                file.name.startsWith("vibe_tags_") ||
                file.name.startsWith("templates_") ||
                file.name.startsWith("featured_templates_") ||
                file.name.startsWith("songs_genres_")
            }?.forEach { it.delete() }
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
        /** Dynamic key per locale — genre names will use label_i18n in future */
        fun keySongsGenres(locale: String): String = "songs_genres_${locale}"
        const val KEY_SONGS_SUGGESTED      = "songs_suggested"
        fun keySongsWeeklyRanking(region: String): String = "songs_weekly_ranking_${region}"

        /** Dynamic key per genre — safe for use as a filename. */
        fun keySongsGenre(genre: String): String =
            "songs_genre_${genre.lowercase().replace(' ', '_')}"

        // ── Template cache keys ──────────────────────────────────────────────
        /** Dynamic key per locale — vibe tag names are localized from Supabase */
        fun keyVibeTags(locale: String): String = "vibe_tags_theme_${locale}"

        /** Dynamic key per region + locale — template names use name_i18n */
        fun keyTemplates(region: String, locale: String, limit: Int, offset: Int): String =
            "templates_${region}_${locale}_${limit}_${offset}"

        fun keyTemplatesByTag(region: String, locale: String, tag: String, limit: Int, offset: Int): String =
            "templates_tag_${region}_${locale}_${tag}_${limit}_${offset}"

        fun keyFeaturedTemplates(region: String, locale: String, limit: Int): String =
            "featured_templates_${region}_${locale}_${limit}"
    }
}
