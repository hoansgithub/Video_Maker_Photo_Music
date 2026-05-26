package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.data.mapper.toMusicSongs
import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.serialization.json.Json

/**
 * BundledContentLibrary — small curated "GL default" content shipped in the APK.
 *
 * Used by the geo-aware loaders as a *timeout fallback*: when live content does not
 * return within the configured window (slow / no network), the screen shows this
 * bundled set so it never appears empty.
 *
 * Asset files (in app/src/main/assets/):
 * - default_global_songs.json     → 5 songs, mp3 + cover bundled offline
 * - default_global_templates.json → 3 templates (TODO)
 *
 * JSON shape mirrors the Supabase tables (snake_case) so it decodes straight into
 * [SongDto] and reuses the same domain mapper.
 */
object BundledContentLibrary {

    private const val ASSET_SONGS = "default_global_songs.json"
    private const val TAG = "BundledContentLibrary"

    private var context: Context? = null

    @Volatile
    private var cachedSongs: List<MusicSong>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun getDefaultSongs(): List<MusicSong> {
        cachedSongs?.let { return it }
        val ctx = context ?: return emptyList()
        return try {
            val jsonString = ctx.assets.open(ASSET_SONGS).bufferedReader().use { it.readText() }
            val songs = json.decodeFromString<List<SongDto>>(jsonString).toMusicSongs()
            cachedSongs = songs
            songs
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load $ASSET_SONGS", e)
            emptyList()
        }
    }
}
