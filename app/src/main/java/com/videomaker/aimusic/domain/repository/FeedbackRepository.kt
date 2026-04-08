package com.videomaker.aimusic.domain.repository

/**
 * Repository interface for submitting user feedback.
 */
interface FeedbackRepository {
    /**
     * Submit user feedback to the backend.
     *
     * @param feedbackText Feedback content (may be empty, will be replaced with default)
     * @param satisfactionResponse User's initial response: "good" or "not_really"
     * @param starRating Optional star rating (1-5) if user rated
     */
    suspend fun submitFeedback(
        feedbackText: String,
        satisfactionResponse: String?,
        starRating: Int?
    ): Result<Unit>
}
