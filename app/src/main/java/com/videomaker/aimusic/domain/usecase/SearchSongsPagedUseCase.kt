package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

class SearchSongsPagedUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<MusicSong>> = repository.searchSongs(query, limit, offset)
}
