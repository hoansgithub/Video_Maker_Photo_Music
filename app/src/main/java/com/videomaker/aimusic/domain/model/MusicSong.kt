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
    val sortOrder: Int = 0,
    val usageCount: Int = 0
) {
    /** Formatted duration string (e.g., "3:45") */
    val formattedDuration: String
        get() {
            val duration = durationMs ?: return ""
            val minutes = duration / 60000
            val seconds = (duration % 60000) / 1000
            return "%d:%02d".format(minutes, seconds)
        }

    /** Compact usage count string (e.g., 12500 → "12.5k", 1200000 → "1.2M") */
    val formattedUsageCount: String
        get() = when {
            usageCount >= 1_000_000 ->
                if (usageCount % 1_000_000 == 0) "${usageCount / 1_000_000}M"
                else "%.1fM".format(usageCount / 1_000_000f)
            usageCount >= 1_000 ->
                if (usageCount % 1_000 == 0) "${usageCount / 1_000}k"
                else "%.1fk".format(usageCount / 1_000f)
            else -> usageCount.toString()
        }
}
