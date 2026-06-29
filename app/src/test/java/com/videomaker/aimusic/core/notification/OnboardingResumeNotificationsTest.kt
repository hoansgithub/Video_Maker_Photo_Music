package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingResumeNotificationsTest {

    @Test
    fun nextAttempt_advances_then_stops_at_three() {
        assertEquals(1, OnboardingResumeNotifications.nextAttempt(0))
        assertEquals(2, OnboardingResumeNotifications.nextAttempt(1))
        assertEquals(3, OnboardingResumeNotifications.nextAttempt(2))
        assertNull(OnboardingResumeNotifications.nextAttempt(3))
        assertNull(OnboardingResumeNotifications.nextAttempt(99))
    }

    @Test
    fun delayMinutesKey_maps_each_attempt() {
        assertEquals(RemoteConfigKeys.OB_RESUME_NOTI_1_DELAY_MINUTES, OnboardingResumeNotifications.delayMinutesKey(1))
        assertEquals(RemoteConfigKeys.OB_RESUME_NOTI_2_DELAY_MINUTES, OnboardingResumeNotifications.delayMinutesKey(2))
        assertEquals(RemoteConfigKeys.OB_RESUME_NOTI_3_DELAY_MINUTES, OnboardingResumeNotifications.delayMinutesKey(3))
    }

    @Test
    fun buildPayload_attempt1_uses_bundled_trigger_images_not_song_cover() {
        val payload = OnboardingResumeNotifications.buildPayload(attempt = 1, songCoverUrl = "https://x/cover.jpg")
        assertEquals(NotificationType.ONBOARDING_RESUME, payload.type)
        assertEquals("ob_resume_1", payload.itemId)
        assertEquals(NotificationChannels.CHANNEL_ONBOARDING_RESUME, payload.channelId)
        // Attempt 1 ignores the song cover and uses bundled collapsed/expanded artwork.
        assertTrue(payload.imageCandidates.isEmpty())
        assertEquals(R.drawable.img_noti_trigger_1, payload.collapsedImageRes)
        assertEquals(R.drawable.img_noti_trigger_expand_1, payload.expandedImageRes)
        assertEquals(R.string.notif_ob_resume_1_title, payload.title.resId)
        assertEquals(R.string.notif_cta_discover_now, payload.ctaText.resId)
    }

    @Test
    fun buildPayload_attempt2_uses_song_cover_when_available() {
        val payload = OnboardingResumeNotifications.buildPayload(attempt = 2, songCoverUrl = "https://x/cover.jpg")
        assertEquals(listOf("https://x/cover.jpg"), payload.imageCandidates)
        assertNull(payload.collapsedImageRes)
        assertNull(payload.expandedImageRes)
        assertEquals(R.string.notif_ob_resume_2_title, payload.title.resId)
        assertEquals(R.string.notif_cta_discover_now, payload.ctaText.resId)
    }

    @Test
    fun buildPayload_attempt2_blank_cover_yields_no_candidates() {
        val payload = OnboardingResumeNotifications.buildPayload(attempt = 2, songCoverUrl = "")
        assertTrue(payload.imageCandidates.isEmpty())
    }

    @Test
    fun buildPayload_attempt3_uses_trigger3_bundled_image_and_see_now_cta() {
        val payload = OnboardingResumeNotifications.buildPayload(attempt = 3, songCoverUrl = "https://ignored")
        assertTrue(payload.imageCandidates.isEmpty())
        assertEquals(R.drawable.img_noti_trigger_3, payload.collapsedImageRes)
        assertEquals(R.drawable.img_noti_trigger_3, payload.expandedImageRes)
        assertEquals(R.drawable.img_noti_trigger_3, payload.fallbackImageRes)
        assertEquals(R.string.notif_ob_resume_3_title, payload.title.resId)
        assertEquals(R.string.notif_cta_see_now, payload.ctaText.resId)
    }
}
