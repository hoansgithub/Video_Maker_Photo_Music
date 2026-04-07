package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * SearchSongsPagedUseCase - Searches songs with pagination
 *
 * NOTE: Repository has hardcoded limit of 30 results (safe fallback).
 * Client-side pagination is acceptable here since repository never returns more than 30 items.
 * TODO: Add paginated searchSongs(query, limit, offset) to repository layer.
 */
class SearchSongsPagedUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<MusicSong>> = repository.searchSongs(query)
        .map { songs -> songs.drop(offset).take(limit) }
}
