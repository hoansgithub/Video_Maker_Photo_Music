package com.videomaker.aimusic.modules.root

import com.videomaker.aimusic.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for simplified onboarding flow.
 *
 * Simplified logic:
 * - Onboarding incomplete → start from LanguageSelection
 * - Onboarding complete → go to Home
 */
class LaunchGateTest {

    @Test
    fun `when onboarding incomplete then language selection route`() {
        val route = resolveStartupRoute(
            SetupProgress(onboardingComplete = false)
        )
        assertEquals(AppRoute.LanguageSelection, route)
    }

    @Test
    fun `when onboarding complete then home route`() {
        val route = resolveStartupRoute(
            SetupProgress(onboardingComplete = true)
        )
        assertEquals(AppRoute.Home(), route)
    }
}
