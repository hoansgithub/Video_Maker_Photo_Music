package com.videomaker.aimusic.modules.root

import com.videomaker.aimusic.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchGateTest {

    @Test
    fun `when language incomplete then language route wins`() {
        val route = resolveStartupRoute(
            SetupProgress(
                needsLanguageSelection = true,
                needsOnboarding = false,
                needsFeatureSelection = false
            )
        )
        assertEquals(AppRoute.LanguageSelection, route)
    }

    @Test
    fun `when onboarding incomplete then onboarding route`() {
        val route = resolveStartupRoute(
            SetupProgress(
                needsLanguageSelection = false,
                needsOnboarding = true,
                needsFeatureSelection = false
            )
        )
        assertEquals(AppRoute.Onboarding, route)
    }

    @Test
    fun `when feature incomplete then feature route`() {
        val route = resolveStartupRoute(
            SetupProgress(
                needsLanguageSelection = false,
                needsOnboarding = false,
                needsFeatureSelection = true
            )
        )
        assertEquals(AppRoute.FeatureSelection, route)
    }

    @Test
    fun `when all complete then home route`() {
        val route = resolveStartupRoute(
            SetupProgress(
                needsLanguageSelection = false,
                needsOnboarding = false,
                needsFeatureSelection = false
            )
        )
        assertEquals(AppRoute.Home(), route)
    }
}
