package co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase

import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepository

/**
 * Use Case: Check if onboarding should be shown
 *
 * Injected via ACCDI:
 * factory { CheckOnboardingStatusUseCase(get()) }
 */
class CheckOnboardingStatusUseCase(
    private val repository: OnboardingRepository
) {
    /**
     * Execute the use case using operator invoke
     * @return true if onboarding should be shown, false otherwise
     */
    suspend operator fun invoke(): Result<Boolean> {
        return repository.shouldShowOnboarding()
    }
}
