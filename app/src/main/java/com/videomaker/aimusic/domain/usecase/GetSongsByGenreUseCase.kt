package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

class GetSongsByGenreUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(genre: String, limit: Int = 20): Result<List<MusicSong>> =
        repository.getSongsByGenre(genre, limit)
}
