package com.videomaker.aimusic.data.mapper

import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong

/**
 * Maps SongDto (Supabase response) to MusicSong (domain model)
 */
fun SongDto.toMusicSong(): MusicSong = MusicSong(
    id = id,
    name = name,
    artist = artist,
    mp3Url = mp3Url,
    previewUrl = previewUrl ?: "",
    coverUrl = coverUrl ?: "",
    genres = genres,
    durationMs = durationMs,
    isPremium = isPremium,
    isActive = isActive,
    sortOrder = sortOrder
)

fun List<SongDto>.toMusicSongs(): List<MusicSong> = map { it.toMusicSong() }
