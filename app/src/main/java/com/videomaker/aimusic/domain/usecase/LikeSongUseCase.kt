package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository

class LikeSongUseCase(private val repository: LikedSongRepository) {
    suspend operator fun invoke(song: MusicSong) = repository.likeSong(song)
}
