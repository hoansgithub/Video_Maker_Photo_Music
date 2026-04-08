package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.FeedbackRepository

/**
 * Submits user feedback with a safe default for empty text.
 */
class SubmitFeedbackUseCase(
    private val feedbackRepository: FeedbackRepository
) {
    suspend operator fun invoke(
        feedbackText: String,
        satisfactionResponse: String? = null,
        starRating: Int? = null
    ): Result<Unit> {
        val finalFeedback = feedbackText.trim().ifBlank {
            "No additional feedback provided"
        }
        return feedbackRepository.submitFeedback(
            feedbackText = finalFeedback,
            satisfactionResponse = satisfactionResponse,
            starRating = starRating
        )
    }
}
