package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.repository.EffectSetRepository

/**
 * Use case for fetching effect sets with pagination.
 *
 * Delegates to EffectSetRepository for data access.
 */
class GetEffectSetsPagedUseCase(
    private val repository: EffectSetRepository
) {
    suspend operator fun invoke(offset: Int, limit: Int): Result<List<EffectSet>> =
        repository.getEffectSetsPaged(offset, limit)
}
