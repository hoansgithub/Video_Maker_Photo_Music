package com.videomaker.aimusic.modules.onboardingsurvey

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSurveyAnalyticsTest {

    @Test
    fun `expandSelection produces indexed params in iteration order`() {
        assertEquals(
            mapOf(
                "feature1" to "ai_dance_video",
                "feature2" to "music_video_templates",
                "feature3" to "ai_avatar",
            ),
            OnboardingSurveyAnalytics.expandSelection(
                OnboardingSurveyAnalytics.PARAM_FEATURE,
                listOf("ai_dance_video", "music_video_templates", "ai_avatar"),
            ),
        )
    }

    @Test
    fun `expandSelection of single id produces single indexed param`() {
        assertEquals(
            mapOf("platform1" to "whatsapp"),
            OnboardingSurveyAnalytics.expandSelection(
                OnboardingSurveyAnalytics.PARAM_PLATFORM,
                listOf("whatsapp"),
            ),
        )
    }

    @Test
    fun `expandSelection of empty is empty map`() {
        assertEquals(
            emptyMap<String, String>(),
            OnboardingSurveyAnalytics.expandSelection(
                OnboardingSurveyAnalytics.PARAM_FEATURE,
                emptyList(),
            ),
        )
    }
}
