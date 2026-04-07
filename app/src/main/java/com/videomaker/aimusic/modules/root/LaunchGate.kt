package com.videomaker.aimusic.modules.root

import com.videomaker.aimusic.navigation.AppRoute

data class SetupProgress(
    val needsLanguageSelection: Boolean,
    val needsOnboarding: Boolean,
    val needsFeatureSelection: Boolean
)

fun resolveStartupRoute(progress: SetupProgress): AppRoute = when {
    progress.needsLanguageSelection -> AppRoute.LanguageSelection
    progress.needsOnboarding -> AppRoute.Onboarding
    progress.needsFeatureSelection -> AppRoute.FeatureSelection
    else -> AppRoute.Home()
}
