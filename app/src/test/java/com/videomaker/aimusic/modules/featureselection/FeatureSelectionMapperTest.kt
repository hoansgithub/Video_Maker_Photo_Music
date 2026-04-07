package com.videomaker.aimusic.modules.featureselection

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSelectionMapperTest {

    @Test
    fun `photos to video maps to songs tab`() {
        assertEquals(1, mapFeatureToInitialTab("photos_to_video"))
    }

    @Test
    fun `music video instant maps to gallery tab`() {
        assertEquals(0, mapFeatureToInitialTab("music_video_instant"))
    }

    @Test
    fun `unknown feature falls back to gallery tab`() {
        assertEquals(0, mapFeatureToInitialTab("unknown"))
    }
}
