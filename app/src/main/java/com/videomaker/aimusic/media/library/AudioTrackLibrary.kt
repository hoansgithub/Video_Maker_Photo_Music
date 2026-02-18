package com.videomaker.aimusic.media.library

import com.videomaker.aimusic.domain.model.AudioTrack

/**
 * AudioTrackLibrary - Registry for audio tracks available in the editor
 *
 * Bundled asset tracks have been removed. Songs are now sourced from Supabase
 * via MusicSong and the SongRepository. This library remains as an empty
 * stub so call sites in CompositionFactory and SettingsPanel compile without
 * changes while the migration to remote tracks is completed.
 */
object AudioTrackLibrary {

    private val tracks = emptyList<AudioTrack>()

    fun getAll(): List<AudioTrack> = tracks

    fun getById(id: String): AudioTrack? = tracks.find { it.id == id }

    fun getFree(): List<AudioTrack> = tracks.filter { !it.isPremium }

    fun getPremium(): List<AudioTrack> = tracks.filter { it.isPremium }
}
