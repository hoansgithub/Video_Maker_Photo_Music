package com.videomaker.aimusic.modules.onboardingsurvey

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSurveyAnalyticsTest {

    @Test
    fun `joinSelection comma-joins in iteration order`() {
        assertEquals(
            "ai_dance_video,music_video_templates,ai_avatar",
            OnboardingSurveyAnalytics.joinSelection(
                listOf("ai_dance_video", "music_video_templates", "ai_avatar")
            )
        )
    }

    @Test
    fun `joinSelection of single id has no comma`() {
        assertEquals("whatsapp", OnboardingSurveyAnalytics.joinSelection(listOf("whatsapp")))
    }

    @Test
    fun `joinSelection of empty is empty string`() {
        assertEquals("", OnboardingSurveyAnalytics.joinSelection(emptyList()))
    }
}
