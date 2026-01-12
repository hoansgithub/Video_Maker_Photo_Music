package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.domain.model.MusicSong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MusicSongLibrary - Loads music songs from JSON
 *
 * Songs are displayed in the Gallery and Songs tabs for video creation.
 */
object MusicSongLibrary {

    private var context: Context? = null
    private var cachedSongs: List<MusicSong>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initialize the library with application context.
     * Must be called before using other methods.
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Load songs from JSON file
     */
    private fun loadSongs(): List<MusicSong> {
        cachedSongs?.let { return it }

        val ctx = context ?: throw IllegalStateException(
            "MusicSongLibrary not initialized. Call init(context) first."
        )

        return try {
            val jsonString = ctx.assets.open("music_songs.json")
                .bufferedReader()
                .use { it.readText() }

            val songsJson = json.decodeFromString<List<MusicSongJson>>(jsonString)
            val songs = songsJson.map { jsonSong ->
                MusicSong(
                    id = jsonSong.id,
                    name = jsonSong.name,
                    artist = jsonSong.artist,
                    mp3Url = jsonSong.mp3Url,
                    previewUrl = jsonSong.previewUrl,
                    coverUrl = jsonSong.coverUrl,
                    categories = jsonSong.categories,
                    isPremium = jsonSong.isPremium,
                    isActive = jsonSong.isActive
                )
            }.filter { it.isActive }

            cachedSongs = songs
            songs
        } catch (e: Exception) {
            android.util.Log.e("MusicSongLibrary", "Failed to load songs", e)
            emptyList()
        }
    }

    fun getAll(): List<MusicSong> = loadSongs()

    fun getById(id: Int): MusicSong? = loadSongs().find { it.id == id }

    fun getByCategory(category: String): List<MusicSong> =
        loadSongs().filter { it.categories.contains(category) }

    fun getFree(): List<MusicSong> = loadSongs().filter { !it.isPremium }

    fun getPremium(): List<MusicSong> = loadSongs().filter { it.isPremium }

    /**
     * Get trending songs (first 12 songs for display)
     */
    fun getTrending(limit: Int = 3): List<MusicSong> = loadSongs().take(limit)

    /**
     * Get top songs (first N songs for ranking display)
     */
    fun getTop(limit: Int = 12): List<MusicSong> = loadSongs().take(limit)

    /**
     * Clear cached songs (useful for development hot reload)
     */
    fun clearCache() {
        cachedSongs = null
    }
}

// ============================================
// JSON DATA CLASSES
// ============================================

@Serializable
private data class MusicSongJson(
    val id: Int,
    val name: String,
    val artist: String,
    val mp3Url: String = "",
    val previewUrl: String = "",
    val coverUrl: String = "",
    val categories: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)
