package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.LikedSongRepository

class UnlikeSongUseCase(private val repository: LikedSongRepository) {
    suspend operator fun invoke(songId: Long) = repository.unlikeSong(songId)
}
