package com.videomaker.aimusic.modules.onboarding.repository

/**
 * Onboarding Repository Interface (Domain Layer)
 * Defines the contract for onboarding data operations
 */
interface OnboardingRepository {

    /**
     * Check if user needs to see onboarding
     * @return true if onboarding should be shown, false otherwise
     */
    suspend fun shouldShowOnboarding(): Result<Boolean>

    /**
     * Mark onboarding as completed
     */
    suspend fun completeOnboarding(): Result<Unit>

    /** Save genre preferences selected on the onboarding genre step. */
    fun savePreferredGenres(genres: List<String>)

    /** Returns genres saved during onboarding. Empty list = no preference. */
    fun getPreferredGenres(): List<String>

    /** Save feature interests selected on the onboarding survey step. */
    suspend fun savePreferredFeatures(features: List<String>)

    /** Returns feature interests saved during onboarding. Empty list = no preference. */
    fun getPreferredFeatures(): List<String>
}
