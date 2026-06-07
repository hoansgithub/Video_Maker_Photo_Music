package com.videomaker.aimusic.modules.genretemplate

import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreTemplateGateTest {

    private val genreKey = RemoteConfigKeys.ONBOARDING_GENRE_SELECTION_ENABLED
    private val templateKey = RemoteConfigKeys.ONBOARDING_TEMPLATE_PICK_ENABLED
    private val contentKey = RemoteConfigKeys.ONBOARDING_CONTENT_EXCLUSIVE_ENABLED
    private val privacyKey = RemoteConfigKeys.ONBOARDING_MEDIA_PRIVACY_ENABLED

    @Test
    fun `all absent defaults to all four steps in order`() {
        assertEquals(
            listOf(
                GenreTemplateStep.GENRE_SELECTION, GenreTemplateStep.TEMPLATE_PICK,
                GenreTemplateStep.CONTENT_EXCLUSIVE, GenreTemplateStep.MEDIA_PRIVACY,
            ),
            GenreTemplateGate.enabledSteps(FakeRemoteConfig()),
        )
    }

    @Test
    fun `content and privacy off keeps genre and template`() {
        val rc = FakeRemoteConfig(mapOf(contentKey to false, privacyKey to false))
        assertEquals(
            listOf(GenreTemplateStep.GENRE_SELECTION, GenreTemplateStep.TEMPLATE_PICK),
            GenreTemplateGate.enabledSteps(rc),
        )
    }

    @Test
    fun `all off is empty`() {
        val rc = FakeRemoteConfig(
            mapOf(genreKey to false, templateKey to false, contentKey to false, privacyKey to false)
        )
        assertTrue(GenreTemplateGate.enabledSteps(rc).isEmpty())
        assertFalse(GenreTemplateGate.isAnyEnabled(rc))
    }

    @Test
    fun `nextStep walks the list then ends`() {
        val steps = listOf(
            GenreTemplateStep.TEMPLATE_PICK,
            GenreTemplateStep.CONTENT_EXCLUSIVE,
            GenreTemplateStep.MEDIA_PRIVACY,
        )
        assertEquals(GenreTemplateStep.CONTENT_EXCLUSIVE, GenreTemplateGate.nextStep(steps, GenreTemplateStep.TEMPLATE_PICK))
        assertEquals(GenreTemplateStep.MEDIA_PRIVACY, GenreTemplateGate.nextStep(steps, GenreTemplateStep.CONTENT_EXCLUSIVE))
        assertNull(GenreTemplateGate.nextStep(steps, GenreTemplateStep.MEDIA_PRIVACY))
    }
}
