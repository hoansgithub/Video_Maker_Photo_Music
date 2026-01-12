package com.aimusic.videoeditor.domain.model

/**
 * MusicSong - A song from the music library for video creation
 *
 * Songs are loaded from music_songs.json and displayed in the Gallery tab.
 */
data class MusicSong(
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
