package com.videomaker.aimusic.modules.genretemplate

import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys

/**
 * Ordered enabled-steps + advancement for the GenreTemplate flow.
 * Order is fixed: GENRE_SELECTION → TEMPLATE_PICK → CONTENT_EXCLUSIVE → MEDIA_PRIVACY.
 * A step defaults to enabled when its RC key is absent (same convention as the survey gate).
 */
/** Maps each step to its primary native ad placement. */
fun GenreTemplateStep.primaryAdPlacement(): String = when (this) {
    GenreTemplateStep.GENRE_SELECTION -> AdPlacement.NATIVE_ONBOARDING_SELECT_MUSIC
    GenreTemplateStep.TEMPLATE_PICK -> AdPlacement.NATIVE_ONBOARDING_SELECT_TPT
    GenreTemplateStep.MEDIA_PRIVACY -> AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY
    GenreTemplateStep.CONTENT_EXCLUSIVE -> AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE
}

/** ALT placement for dual-ad swap steps; null for steps without a swap. */
fun GenreTemplateStep.altAdPlacement(): String? = when (this) {
    GenreTemplateStep.MEDIA_PRIVACY -> AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY_ALT
    GenreTemplateStep.CONTENT_EXCLUSIVE -> AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE_ALT
    else -> null
}

object GenreTemplateGate {

    fun enabledSteps(remoteConfig: RemoteConfig): List<GenreTemplateStep> = buildList {
        if (remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_GENRE_SELECTION_ENABLED)) {
            add(GenreTemplateStep.GENRE_SELECTION)
        }
        if (remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_TEMPLATE_PICK_ENABLED)) {
            add(GenreTemplateStep.TEMPLATE_PICK)
        }
        if (remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_MEDIA_PRIVACY_ENABLED)) {
            add(GenreTemplateStep.MEDIA_PRIVACY)
        }
        if (remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_CONTENT_EXCLUSIVE_ENABLED)) {
            add(GenreTemplateStep.CONTENT_EXCLUSIVE)
        }
    }

    fun isAnyEnabled(remoteConfig: RemoteConfig): Boolean = enabledSteps(remoteConfig).isNotEmpty()

    /** Step after [current] in [steps], or null when [current] is the last / not present. */
    fun nextStep(steps: List<GenreTemplateStep>, current: GenreTemplateStep?): GenreTemplateStep? {
        val index = steps.indexOf(current)
        if (index < 0) return null
        return steps.getOrNull(index + 1)
    }
}
