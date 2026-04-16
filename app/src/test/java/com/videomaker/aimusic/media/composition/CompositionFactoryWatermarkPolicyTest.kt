package com.videomaker.aimusic.media.composition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionFactoryWatermarkPolicyTest {

    @Test
    fun `export applies watermark when project is not watermark free`() {
        val shouldApply = CompositionFactory.shouldApplyWatermark(
            forExport = true,
            isWatermarkFree = false
        )

        assertTrue(shouldApply)
    }

    @Test
    fun `export skips watermark when project is watermark free`() {
        val shouldApply = CompositionFactory.shouldApplyWatermark(
            forExport = true,
            isWatermarkFree = true
        )

        assertFalse(shouldApply)
    }

    @Test
    fun `preview never applies watermark regardless of project state`() {
        assertFalse(
            CompositionFactory.shouldApplyWatermark(
                forExport = false,
                isWatermarkFree = false
            )
        )
        assertFalse(
            CompositionFactory.shouldApplyWatermark(
                forExport = false,
                isWatermarkFree = true
            )
        )
    }
}
