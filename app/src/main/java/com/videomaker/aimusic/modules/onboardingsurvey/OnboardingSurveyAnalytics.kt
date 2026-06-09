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

    // AI creation-style screen events / params
    const val EVENT_AI_LEVEL_RENDER = "creation_style_render"
    const val EVENT_AI_LEVEL_SELECT = "creation_style_select"
    const val EVENT_AI_LEVEL_NEXT = "creation_style_next"
    const val PARAM_AI_LEVEL = "option"

    /**
     * Expands selected ids into indexed params: `${paramKey}1` → id1, `${paramKey}2` → id2, …
     * Iteration order is preserved; index starts at 1.
     */
    fun expandSelection(paramKey: String, ids: Collection<String>): Map<String, String> =
        ids.withIndex().associate { (i, id) -> "$paramKey${i + 1}" to id }
}
