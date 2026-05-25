package com.videomaker.aimusic.modules.onboardingsurvey

import com.videomaker.aimusic.core.constants.AdPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSurveyStepConfigTest {

    @Test
    fun `feature config has the seven expected ids in order`() {
        assertEquals(
            listOf(
                "ai_dance_video", "music_video_templates", "ai_hair_swap", "lyric_videos",
                "ai_avatar", "beat_sync_effect", "explore_later"
            ),
            FEATURE_CONFIG.items.map { it.id }
        )
        assertEquals(AdPlacement.NATIVE_ONBOARDING_SELECT, FEATURE_CONFIG.primaryPlacement)
        assertEquals(AdPlacement.NATIVE_ONBOARDING_SELECT_ALT, FEATURE_CONFIG.altPlacement)
    }

    @Test
    fun `platform config has the seven expected ids in order`() {
        assertEquals(
            listOf(
                "whatsapp", "instagram", "tiktok", "facebook",
                "youtube_short", "snapchat", "keep_personally"
            ),
            PLATFORM_CONFIG.items.map { it.id }
        )
        assertEquals(AdPlacement.NATIVE_ONBOARDING_SOCIAL, PLATFORM_CONFIG.primaryPlacement)
        assertEquals(AdPlacement.NATIVE_ONBOARDING_SOCIAL_ALT, PLATFORM_CONFIG.altPlacement)
    }

    @Test
    fun `configFor maps each step to its config`() {
        assertEquals(FEATURE_CONFIG, configFor(OnboardingSurveyStep.FEATURE))
        assertEquals(PLATFORM_CONFIG, configFor(OnboardingSurveyStep.PLATFORM))
    }
}
