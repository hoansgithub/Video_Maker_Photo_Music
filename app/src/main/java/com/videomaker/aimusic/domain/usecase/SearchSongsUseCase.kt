package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

class SearchSongsUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(query: String): Result<List<MusicSong>> =
        repository.searchSongs(query)
}