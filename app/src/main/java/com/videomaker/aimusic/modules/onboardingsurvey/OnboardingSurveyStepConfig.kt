package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.AdPlacement

@Immutable
data class OnboardingSurveyStepConfig(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val items: List<SurveyItem>,
    val placement: String,
    val eventRender: String,
    val eventSelect: String,
    val eventNext: String,
    val paramKey: String,
    val countKey: String,
)

val FEATURE_CONFIG = OnboardingSurveyStepConfig(
    titleRes = R.string.survey_feature_title,
    subtitleRes = R.string.survey_feature_subtitle,
    items = listOf(
        SurveyItem("ai_dance_video", R.string.survey_feature_ai_dance, R.drawable.img_ai_dance),
        SurveyItem("music_video_templates", R.string.survey_feature_music_video, R.drawable.img_music_video),
        SurveyItem("ai_hair_swap", R.string.survey_feature_ai_hair, R.drawable.img_ai_hair),
        SurveyItem("lyric_videos", R.string.survey_feature_lyric, R.drawable.img_lyric_videos),
        SurveyItem("ai_avatar", R.string.survey_feature_ai_avatar, R.drawable.img_ai_avatar),
        SurveyItem("beat_sync_effect", R.string.survey_feature_beat_sync, R.drawable.img_beat_sync_effect),
        SurveyItem("explore_later", R.string.survey_feature_explore_later, R.drawable.img_explore_later),
    ),
    placement = AdPlacement.NATIVE_ONBOARDING_SELECT,
    eventRender = OnboardingSurveyAnalytics.EVENT_FEATURE_RENDER,
    eventSelect = OnboardingSurveyAnalytics.EVENT_FEATURE_SELECT,
    eventNext = OnboardingSurveyAnalytics.EVENT_FEATURE_NEXT,
    paramKey = OnboardingSurveyAnalytics.PARAM_FEATURE,
    countKey = OnboardingSurveyAnalytics.PARAM_FEATURE_COUNT,
)

val PLATFORM_CONFIG = OnboardingSurveyStepConfig(
    titleRes = R.string.survey_platform_title,
    subtitleRes = R.string.survey_platform_subtitle,
    items = listOf(
        SurveyItem("whatsapp", R.string.survey_platform_whatsapp, R.drawable.ic_whats_app),
        SurveyItem("instagram", R.string.survey_platform_instagram, R.drawable.ic_instagram),
        SurveyItem("tiktok", R.string.survey_platform_tiktok, R.drawable.ic_tik_tok),
        SurveyItem("facebook", R.string.survey_platform_facebook, R.drawable.ic_facebook),
        SurveyItem("youtube_short", R.string.survey_platform_youtube, R.drawable.ic_youtube),
        SurveyItem("snapchat", R.string.survey_platform_snapchat, R.drawable.ic_snapchat),
        SurveyItem("keep_personally", R.string.survey_platform_personally, R.drawable.ic_personally),
    ),
    placement = AdPlacement.NATIVE_ONBOARDING_SOCIAL,
    eventRender = OnboardingSurveyAnalytics.EVENT_PLATFORM_RENDER,
    eventSelect = OnboardingSurveyAnalytics.EVENT_PLATFORM_SELECT,
    eventNext = OnboardingSurveyAnalytics.EVENT_PLATFORM_NEXT,
    paramKey = OnboardingSurveyAnalytics.PARAM_PLATFORM,
    countKey = OnboardingSurveyAnalytics.PARAM_PLATFORM_COUNT,
)

fun configFor(step: OnboardingSurveyStep): OnboardingSurveyStepConfig = when (step) {
    OnboardingSurveyStep.FEATURE -> FEATURE_CONFIG
    OnboardingSurveyStep.PLATFORM -> PLATFORM_CONFIG
    OnboardingSurveyStep.AI_LEVEL ->
        error("AI_LEVEL renders via AiLevelScreen, not OnboardingSurveyList")
}
