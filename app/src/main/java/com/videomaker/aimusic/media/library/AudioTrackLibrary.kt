package com.videomaker.aimusic.media.library

import com.videomaker.aimusic.domain.model.AudioTrack

/**
 * AudioTrackLibrary - Provides all available bundled audio tracks
 *
 * Sample background music tracks bundled with the app.
 */
object AudioTrackLibrary {

    private val tracks = listOf(
        AudioTrack(
            id = "track1",
            name = "Track 1",
            assetPath = "audio/track1.mp3",
            durationMs = 0L, // TODO: Get actual duration
            isPremium = false
        ),
        AudioTrack(
            id = "track2",
            name = "Track 2",
            assetPath = "audio/track2.mp3",
            durationMs = 0L,
            isPremium = false
        )
    )

    fun getAll(): List<AudioTrack> = tracks

    fun getById(id: String): AudioTrack? = tracks.find { it.id == id }

    fun getFree(): List<AudioTrack> = tracks.filter { !it.isPremium }

    fun getPremium(): List<AudioTrack> = tracks.filter { it.isPremium }
}
