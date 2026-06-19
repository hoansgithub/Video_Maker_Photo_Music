package com.videomaker.aimusic.data.mapper

import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong

/**
 * Maps SongDto (Supabase response) to MusicSong (domain model)
 */
fun SongDto.toMusicSong(): MusicSong {
    val hookTimes = hookStartTimes
        ?.map { it.coerceAtLeast(0L) }
        ?.filter { it > 0L }
        ?: emptyList()

    val primaryHook = hookTimes.firstOrNull()
        ?: (hookStartTimeMs ?: 0L).coerceAtLeast(0L)

    return MusicSong(
        id = id,
        name = name,
        artist = artist,
        mp3Url = mp3Url,
        previewUrl = previewUrl ?: "",
        coverUrl = coverUrl ?: "",
        genres = genres,
        durationMs = durationMs,
        isPremium = isPremium,
        isFeatured = isFeatured,
        isActive = isActive,
        sortOrder = sortOrder,
        usageCount = usageCount,
        hookStartTimeMs = primaryHook,
        hookStartTimes = hookTimes.ifEmpty {
            listOfNotNull(primaryHook.takeIf { it > 0L })
        },
        beatsUrl = beatsUrl ?: ""
    )
}

fun List<SongDto>.toMusicSongs(): List<MusicSong> = map { it.toMusicSong() }
