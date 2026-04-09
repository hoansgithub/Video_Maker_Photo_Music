package com.videomaker.aimusic.modules.root

import com.videomaker.aimusic.navigation.AppRoute

/**
 * Simplified onboarding progress tracking.
 *
 * We only care if onboarding is complete or not.
 * If not complete, always start from the beginning (Language Selection).
 *
 * Full onboarding flow:
 * Language Selection → Onboarding (welcome pages) → Feature Selection → Home
 */
data class SetupProgress(
    val onboardingComplete: Boolean
)

/**
 * Resolve startup route based on onboarding status.
 *
 * @param progress Onboarding completion status
 * @return Starting route: LanguageSelection if not complete, Home if complete
 */
fun resolveStartupRoute(progress: SetupProgress): AppRoute = when {
    progress.onboardingComplete -> AppRoute.Home()
    else -> AppRoute.LanguageSelection  // Start from beginning
}
