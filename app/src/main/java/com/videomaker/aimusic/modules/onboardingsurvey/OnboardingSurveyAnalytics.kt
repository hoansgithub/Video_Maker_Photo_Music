package com.videomaker.aimusic.modules.onboardingsurvey

object OnboardingSurveyAnalytics {

    // Feature screen events / params
    const val EVENT_FEATURE_RENDER = "feature_selection_render"
    const val EVENT_FEATURE_SELECT = "feature_selection_select"
    const val EVENT_FEATURE_NEXT = "feature_selection_next"
    const val PARAM_FEATURE = "feature"
    const val PARAM_FEATURE_COUNT = "feature_count"

    // Platform screen events / params
    const val EVENT_PLATFORM_RENDER = "platform_selection_render"
    const val EVENT_PLATFORM_SELECT = "platform_selection_select"
    const val EVENT_PLATFORM_NEXT = "platform_selection_next"
    const val PARAM_PLATFORM = "platform"
    const val PARAM_PLATFORM_COUNT = "platform_count"

    /** Comma-joins selected ids for the *_next event value (mirrors genre_next). */
    fun joinSelection(ids: Collection<String>): String = ids.joinToString(",")
}
