package com.videomaker.aimusic.core.cache

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * VideoCacheManager - Manages ExoPlayer video cache for template previews
 *
 * Features:
 * - Persistent disk cache for template videos
 * - LRU eviction when cache exceeds max size
 * - Cache statistics and monitoring
 * - Manual cache clearing
 *
 * Default cache size: 200 MB (stores ~20-30 template videos)
 */
class VideoCacheManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "VideoCacheManager"
        private const val CACHE_DIR_NAME = "template_video_cache"
        private const val DEFAULT_MAX_CACHE_SIZE = 200L * 1024 * 1024 // 200 MB

        @Volatile
        private var instance: SimpleCache? = null

        /**
         * Get singleton SimpleCache instance
         * Thread-safe lazy initialization
         */
        fun getCache(context: Context): SimpleCache {
            return instance ?: synchronized(this) {
                instance ?: createCache(context).also { instance = it }
            }
        }

        private fun createCache(context: Context): SimpleCache {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            val databaseProvider = StandaloneDatabaseProvider(context)
            val evictor = LeastRecentlyUsedCacheEvictor(DEFAULT_MAX_CACHE_SIZE)

            return SimpleCache(cacheDir, evictor, databaseProvider).also {
                Log.d(TAG, "Video cache initialized at: ${cacheDir.absolutePath}")
                Log.d(TAG, "Max cache size: ${DEFAULT_MAX_CACHE_SIZE / 1024 / 1024} MB")
            }
        }

        /**
         * Release cache instance
         * Call this in Application.onTerminate() or when cache is no longer needed
         */
        fun release() {
            synchronized(this) {
                instance?.release()
                instance = null
                Log.d(TAG, "Video cache released")
            }
        }
    }

    private val cache: SimpleCache by lazy { getCache(context) }

    /**
     * Get current cache statistics
     */
    fun getCacheStats(): CacheStats {
        val cacheSpace = cache.cacheSpace
        val keys = cache.keys
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)

        return CacheStats(
            totalSizeBytes = cacheSpace,
            totalSizeMB = cacheSpace / 1024 / 1024,
            cachedVideosCount = keys.size,
            maxSizeMB = DEFAULT_MAX_CACHE_SIZE / 1024 / 1024,
            usagePercentage = (cacheSpace.toFloat() / DEFAULT_MAX_CACHE_SIZE * 100).toInt(),
            cacheDirectory = cacheDir.absolutePath
        )
    }

    /**
     * Clear all cached videos
     */
    fun clearCache() {
        try {
            cache.keys.forEach { key ->
                cache.removeResource(key)
            }
            Log.d(TAG, "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     * Clear specific video from cache
     */
    fun clearVideo(cacheKey: String) {
        try {
            cache.removeResource(cacheKey)
            Log.d(TAG, "Removed video from cache: $cacheKey")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing video from cache", e)
        }
    }

    /**
     * Check if cache size exceeds threshold
     */
    fun isCacheAlmostFull(thresholdPercentage: Int = 90): Boolean {
        val stats = getCacheStats()
        return stats.usagePercentage >= thresholdPercentage
    }

    /**
     * Get formatted cache statistics for logging/debugging
     */
    fun getFormattedStats(): String {
        val stats = getCacheStats()
        return """
            |Video Cache Statistics:
            |  Total Size: ${stats.totalSizeMB} MB / ${stats.maxSizeMB} MB (${stats.usagePercentage}%)
            |  Cached Videos: ${stats.cachedVideosCount}
            |  Location: ${stats.cacheDirectory}
        """.trimMargin()
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val totalSizeBytes: Long,
    val totalSizeMB: Long,
    val cachedVideosCount: Int,
    val maxSizeMB: Long,
    val usagePercentage: Int,
    val cacheDirectory: String
)
