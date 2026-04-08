package com.videomaker.aimusic.modules.featureselection

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSelectionAnalyticsTest {

    @Test
    fun `single genre is returned as is`() {
        assertEquals("photos_to_video", toGenreAnalyticsValue(listOf("photos_to_video")))
    }

    @Test
    fun `multiple genres are joined by comma`() {
        assertEquals(
            "photos_to_video,music_video_instant",
            toGenreAnalyticsValue(listOf("photos_to_video", "music_video_instant"))
        )
    }

    @Test
    fun `empty genre list returns empty string`() {
        assertEquals("", toGenreAnalyticsValue(emptyList()))
    }
}
