package com.videomaker.aimusic.media.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkOverlayEffectPlacementTest {

    @Test
    fun `placement keeps watermark on visual bottom-right after GL vertical flip`() {
        val placement = WatermarkOverlayEffect.calculateOverlayPlacement(
            inputWidth = 1000,
            inputHeight = 2000
        )

        // minDim = 1000 => margin=40, logo=120
        assertEquals(840, placement.left)
        assertEquals(960, placement.right)
        assertEquals(40, placement.top)
        assertEquals(160, placement.bottom)
    }

    @Test
    fun `placement does not mirror horizontally`() {
        val placement = WatermarkOverlayEffect.calculateOverlayPlacement(
            inputWidth = 1000,
            inputHeight = 2000
        )

        // Should still be anchored to right edge in bitmap space.
        assertEquals(840, placement.left)
    }

    @Test
    fun `logo draw should pre-flip vertically to compensate GL sampling`() {
        assertTrue(WatermarkOverlayEffect.shouldPreFlipLogoVertically())
    }
}
