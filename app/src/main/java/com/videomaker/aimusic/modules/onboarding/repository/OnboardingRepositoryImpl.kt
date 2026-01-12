package com.videomaker.aimusic.modules.onboarding.repository

import com.videomaker.aimusic.core.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Onboarding Repository Implementation (Data Layer)
 * Handles onboarding data operations using PreferencesManager
 *
 * Injected via ACCDI:
 * single<OnboardingRepository> { OnboardingRepositoryImpl(get()) }
 */
class OnboardingRepositoryImpl(
    private val preferencesManager: PreferencesManager
) : OnboardingRepository {

    override suspend fun shouldShowOnboarding(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val shouldShow = !preferencesManager.isOnboardingComplete()
            Result.success(shouldShow)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeOnboarding(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferencesManager.setOnboardingComplete(true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
