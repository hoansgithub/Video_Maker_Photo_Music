package com.videomaker.aimusic.media.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

// ============================================
// AUDIO PREVIEW CACHE
// Disk-backed LRU cache for song preview URLs.
// Follows the same SimpleCache pattern used by
// the video reel player — 50 MB, auto-recovery
// on corrupted SQLite index.
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPreviewCache(context: Context) {

    private val appContext = context.applicationContext

    val simpleCache: SimpleCache by lazy {
        val cacheDir = File(appContext.cacheDir, "audio_preview_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(50L * 1024 * 1024) // 50 MB
        try {
            SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(appContext))
        } catch (e: Exception) {
            // Auto-recovery: wipe corrupted cache index and retry once
            cacheDir.deleteRecursively()
            SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(appContext))
        }
    }

    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}