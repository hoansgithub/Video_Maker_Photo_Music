package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable

/**
 * MusicSong - A song from the music library for video creation
 *
 * Songs are loaded from Supabase `songs` table and displayed in Gallery/Songs tabs.
 */
@Serializable
data class MusicSong(
    val id: Long,
    val name: String,
    val artist: String,
    val mp3Url: String = "",
    val previewUrl: String = "",
    val coverUrl: String = "",
    val genres: List<String> = emptyList(),
    val durationMs: Int? = null,
    val isPremium: Boolean = false,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
) {
    /**
     * Formatted duration string (e.g., "3:45")
     */
    val formattedDuration: String
        get() {
            val duration = durationMs ?: return ""
            val minutes = duration / 60000
            val seconds = (duration % 60000) / 1000
            return "%d:%02d".format(minutes, seconds)
        }
}
