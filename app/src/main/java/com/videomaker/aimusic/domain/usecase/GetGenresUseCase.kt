package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.SongRepository

class GetGenresUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(): Result<List<String>> = repository.getGenres()
}
