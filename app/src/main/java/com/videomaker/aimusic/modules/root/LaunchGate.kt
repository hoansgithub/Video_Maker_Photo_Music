package com.videomaker.aimusic.modules.root

import com.videomaker.aimusic.navigation.AppRoute

/**
 * Which onboarding step to resume at when a returning user has partial progress.
 */
enum class OnboardingResumeStep {
    /** Resume at OnboardingActivity (welcome pages) or survey if enabled */
    WELCOME_PAGES,
    /** Resume at FeatureSelectionActivity (or GenreTemplateActivity if enabled) */
    FEATURE_SELECTION
}

/**
 * Onboarding progress tracking with resume support.
 *
 * Full onboarding flow:
 * Language Selection → Onboarding (welcome pages) → Feature Selection → Home
 *
 * @param onboardingComplete true if ALL onboarding steps are done
 * @param resumeStep non-null when user has partial progress and should see the welcome-back screen
 */
data class SetupProgress(
    val onboardingComplete: Boolean,
    val resumeStep: OnboardingResumeStep? = null
)

/**
 * Resolve startup route based on onboarding status.
 *
 * @param progress Onboarding completion status
 * @return Starting route: Home if complete, OnboardingWelcomeBack if partial progress,
 *         LanguageSelection if never started
 */
fun resolveStartupRoute(progress: SetupProgress): AppRoute = when {
    progress.onboardingComplete -> AppRoute.Home()
    progress.resumeStep != null -> AppRoute.OnboardingWelcomeBack
    else -> AppRoute.LanguageSelection  // Start from beginning
}
