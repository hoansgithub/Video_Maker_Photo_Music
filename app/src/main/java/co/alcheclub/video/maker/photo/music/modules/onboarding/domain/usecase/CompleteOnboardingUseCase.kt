package co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase

import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepository

/**
 * Use Case: Mark onboarding as complete
 *
 * Injected via ACCDI:
 * factory { CompleteOnboardingUseCase(get()) }
 */
class CompleteOnboardingUseCase(
    private val repository: OnboardingRepository
) {
    /**
     * Execute the use case using operator invoke
     */
    suspend operator fun invoke(): Result<Unit> {
        return repository.completeOnboarding()
    }
}
