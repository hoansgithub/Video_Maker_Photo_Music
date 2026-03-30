package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.LikedSongRepository
import kotlinx.coroutines.flow.Flow

class ObserveLikedSongsUseCase(private val repository: LikedSongRepository) {
    operator fun invoke(): Flow<List<MusicSong>> = repository.observeLikedSongs()
    fun ids(): Flow<Set<Long>> = repository.observeLikedSongIds()
}
