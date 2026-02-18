package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.SongRepository

class ClearSongCacheUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke() = repository.clearCache()
}
