package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

class GetSuggestedSongsUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(limit: Int = 10): Result<List<MusicSong>> =
        repository.getRandomSongs(limit)
}
