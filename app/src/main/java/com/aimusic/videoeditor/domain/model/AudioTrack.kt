package com.aimusic.videoeditor.domain.model

/**
 * AudioTrack - Bundled background music track
 *
 * Sample audio tracks bundled with the app.
 *
 * @param id Unique identifier
 * @param name Display name
 * @param assetPath Path in assets folder (e.g., "audio/track1.mp3")
 * @param durationMs Track duration in milliseconds
 * @param isPremium Whether this track requires premium access
 */
data class AudioTrack(
    val id: String,
    val name: String,
    val assetPath: String,
    val durationMs: Long = 0L,
    val isPremium: Boolean = false
)
