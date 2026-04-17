package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.VideoTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ViralTemplateResolverTest {

    @Test
    fun `resolve returns top template by use count`() {
        val templates = listOf(
            template(id = "t1", useCount = 1_200_000L),
            template(id = "t2", useCount = 900_000L)
        )

        val result = ViralTemplateResolver.resolve(
            featuredTemplates = templates,
            snapshot = null,
            currentLocalDate = "2026-04-15"
        )

        assertEquals("t1", result?.template?.id)
        assertEquals(2, result?.collageSources?.size)
    }

    @Test
    fun `resolve still returns top template when use counts are low`() {
        val templates = listOf(
            template(id = "t1", useCount = 9_000L),
            template(id = "t2", useCount = 4_500L)
        )

        val result = ViralTemplateResolver.resolve(
            featuredTemplates = templates,
            snapshot = null,
            currentLocalDate = "2026-04-15"
        )

        assertEquals("t1", result?.template?.id)
    }

    @Test
    fun `resolve still returns candidate even when snapshot has same template and cap handles duplicates`() {
        val result = ViralTemplateResolver.resolve(
            featuredTemplates = listOf(template(id = "t1", useCount = 2_000_000L)),
            snapshot = ViralTemplateResolver.DailySnapshot(
                localDate = "2026-04-15",
                templateId = "t1",
                usageCount = 1_900_000L
            ),
            currentLocalDate = "2026-04-15"
        )

        assertNotNull(result)
        assertEquals("t1", result?.template?.id)
    }

    private fun template(id: String, useCount: Long): VideoTemplate {
        return VideoTemplate(
            id = id,
            name = id,
            thumbnailPath = "https://example.com/$id.jpg",
            songId = 1L,
            effectSetId = "fx",
            useCount = useCount
        )
    }
}
