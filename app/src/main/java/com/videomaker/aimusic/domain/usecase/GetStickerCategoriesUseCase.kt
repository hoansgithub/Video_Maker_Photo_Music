package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.StickerCategory
import com.videomaker.aimusic.domain.repository.StickerRepository

/** Fetches sticker categories (tabs) with pagination. */
class GetStickerCategoriesUseCase(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(offset: Int, limit: Int): Result<List<StickerCategory>> =
        repository.getCategories(offset, limit)
}
