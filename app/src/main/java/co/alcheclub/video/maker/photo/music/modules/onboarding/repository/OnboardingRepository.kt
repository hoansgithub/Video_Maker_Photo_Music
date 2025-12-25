package co.alcheclub.video.maker.photo.music.modules.onboarding.repository

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
}
