package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.Sticker
import com.videomaker.aimusic.domain.repository.StickerRepository

/** Fetches stickers for a category with pagination. */
class GetStickersByCategoryUseCase(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(categoryId: String, offset: Int, limit: Int): Result<List<Sticker>> =
        repository.getStickersByCategory(categoryId, offset, limit)
}
