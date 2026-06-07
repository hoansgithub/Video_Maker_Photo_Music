package com.videomaker.aimusic.modules.onboardingsurvey

import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingSurveyGateTest {

    private val featureKey = RemoteConfigKeys.ONBOARDING_FEATURE_SELECTION_ENABLED
    private val platformKey = RemoteConfigKeys.ONBOARDING_PLATFORM_SELECTION_ENABLED
    private val aiLevelKey = RemoteConfigKeys.ONBOARDING_AI_LEVEL_ENABLED

    @Test
    fun `all keys absent defaults to all three steps in order`() {
        val rc = FakeRemoteConfig()
        assertEquals(
            listOf(OnboardingSurveyStep.FEATURE, OnboardingSurveyStep.PLATFORM, OnboardingSurveyStep.AI_LEVEL),
            OnboardingSurveyGate.enabledSteps(rc)
        )
        assertTrue(OnboardingSurveyGate.isAnyEnabled(rc))
    }

    @Test
    fun `ai level off keeps feature and platform`() {
        val rc = FakeRemoteConfig(mapOf(aiLevelKey to false))
        assertEquals(
            listOf(OnboardingSurveyStep.FEATURE, OnboardingSurveyStep.PLATFORM),
            OnboardingSurveyGate.enabledSteps(rc)
        )
    }

    @Test
    fun `feature off keeps platform and ai level`() {
        val rc = FakeRemoteConfig(mapOf(featureKey to false))
        assertEquals(
            listOf(OnboardingSurveyStep.PLATFORM, OnboardingSurveyStep.AI_LEVEL),
            OnboardingSurveyGate.enabledSteps(rc)
        )
    }

    @Test
    fun `platform off keeps feature and ai level`() {
        val rc = FakeRemoteConfig(mapOf(platformKey to false))
        assertEquals(
            listOf(OnboardingSurveyStep.FEATURE, OnboardingSurveyStep.AI_LEVEL),
            OnboardingSurveyGate.enabledSteps(rc)
        )
    }

    @Test
    fun `all off yields empty and isAnyEnabled false`() {
        val rc = FakeRemoteConfig(mapOf(featureKey to false, platformKey to false, aiLevelKey to false))
        assertTrue(OnboardingSurveyGate.enabledSteps(rc).isEmpty())
        assertFalse(OnboardingSurveyGate.isAnyEnabled(rc))
    }

    @Test
    fun `nextStep walks the enabled list then ends`() {
        val steps = listOf(OnboardingSurveyStep.FEATURE, OnboardingSurveyStep.PLATFORM)
        assertEquals(OnboardingSurveyStep.PLATFORM, OnboardingSurveyGate.nextStep(steps, OnboardingSurveyStep.FEATURE))
        assertNull(OnboardingSurveyGate.nextStep(steps, OnboardingSurveyStep.PLATFORM))
    }

    @Test
    fun `nextStep with single step ends immediately`() {
        val steps = listOf(OnboardingSurveyStep.PLATFORM)
        assertNull(OnboardingSurveyGate.nextStep(steps, OnboardingSurveyStep.PLATFORM))
    }

    @Test
    fun `nextStep with null or unknown current returns null`() {
        val steps = listOf(OnboardingSurveyStep.FEATURE)
        assertNull(OnboardingSurveyGate.nextStep(steps, null))
        assertNull(OnboardingSurveyGate.nextStep(emptyList(), OnboardingSurveyStep.FEATURE))
    }
}
