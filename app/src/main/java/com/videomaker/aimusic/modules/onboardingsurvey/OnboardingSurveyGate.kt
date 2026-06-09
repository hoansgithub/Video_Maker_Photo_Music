package com.videomaker.aimusic.modules.onboardingsurvey

import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys

/** Maps each survey step to its primary native ad placement. */
fun OnboardingSurveyStep.primaryAdPlacement(): String = when (this) {
    OnboardingSurveyStep.FEATURE -> AdPlacement.NATIVE_ONBOARDING_SELECT
    OnboardingSurveyStep.PLATFORM -> AdPlacement.NATIVE_ONBOARDING_SOCIAL
    OnboardingSurveyStep.AI_LEVEL -> AdPlacement.NATIVE_ONBOARDING_AI_LEVEL
}

/** ALT placement for dual-ad swap steps; null for steps without a swap. */
fun OnboardingSurveyStep.altAdPlacement(): String? = when (this) {
    OnboardingSurveyStep.FEATURE -> AdPlacement.NATIVE_ONBOARDING_SELECT_ALT
    OnboardingSurveyStep.AI_LEVEL -> AdPlacement.NATIVE_ONBOARDING_AI_LEVEL_ALT
    OnboardingSurveyStep.PLATFORM -> null
}

/**
 * Single source of truth for which survey steps are enabled and how to advance between them.
 * Used by both LanguageSelectionActivity (whether to launch the survey at all) and
 * OnboardingSurveyViewModel (which step to show).
 */
object OnboardingSurveyGate {

    /** Ordered list of enabled steps: [FEATURE?, PLATFORM?]. Defaults to enabled when the RC key is absent. */
    fun enabledSteps(remoteConfig: RemoteConfig): List<OnboardingSurveyStep> = buildList {
        if (remoteConfig.stepEnabled(RemoteConfigKeys.ONBOARDING_FEATURE_SELECTION_ENABLED)) {
            add(OnboardingSurveyStep.FEATURE)
        }
        if (remoteConfig.stepEnabled(RemoteConfigKeys.ONBOARDING_PLATFORM_SELECTION_ENABLED)) {
            add(OnboardingSurveyStep.PLATFORM)
        }
        if (remoteConfig.stepEnabled(RemoteConfigKeys.ONBOARDING_AI_LEVEL_ENABLED)) {
            add(OnboardingSurveyStep.AI_LEVEL)
        }
    }

    fun isAnyEnabled(remoteConfig: RemoteConfig): Boolean = enabledSteps(remoteConfig).isNotEmpty()

    /** Returns the step after [current] in [steps], or null when [current] is the last / not present. */
    fun nextStep(steps: List<OnboardingSurveyStep>, current: OnboardingSurveyStep?): OnboardingSurveyStep? {
        val index = steps.indexOf(current)
        if (index < 0) return null
        return steps.getOrNull(index + 1)
    }
}

/**
 * Firebase RC getBoolean(key) returns false silently for missing keys (default is only used in
 * the exception path). This treats an unpublished key as enabled (true) — same convention as the
 * genre-template flow.
 */
private fun RemoteConfig.stepEnabled(key: String): Boolean =
    if (key in getAllKeys()) getBoolean(key, true) else true
