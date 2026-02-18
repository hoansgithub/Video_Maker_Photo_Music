package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

class GetWeeklyRankingSongsUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(limit: Int = 9): Result<List<MusicSong>> =
        repository.getFeaturedSongs(limit)
}
