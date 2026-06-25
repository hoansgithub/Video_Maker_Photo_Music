package com.videomaker.aimusic.modules.editor.overlay

import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayZOrderTest {

    private fun text(id: String, z: Int) =
        TextOverlay(id = id, text = id, zIndex = z)

    private fun sticker(id: String, z: Int) =
        StickerPlacement(instanceId = id, stickerId = id, assetUrl = "u", zIndex = z)

    @Test
    fun `groups contiguous same-type items into runs sorted by z`() {
        val runs = buildOverlayRuns(
            textOverlays = listOf(text("t1", 1), text("t2", 2)),
            stickers = listOf(sticker("s0", 0), sticker("s3", 3))
        )
        // Expect: sticker(s0) | text(t1,t2) | sticker(s3)
        assertEquals(3, runs.size)
        assertEquals(listOf("s0"), (runs[0] as OverlayRun.StickerRun).stickers.map { it.instanceId })
        assertEquals(listOf("t1", "t2"), (runs[1] as OverlayRun.TextRun).overlays.map { it.id })
        assertEquals(listOf("s3"), (runs[2] as OverlayRun.StickerRun).stickers.map { it.instanceId })
    }

    @Test
    fun `equal z puts text below sticker`() {
        val runs = buildOverlayRuns(
            textOverlays = listOf(text("t", 0)),
            stickers = listOf(sticker("s", 0))
        )
        assertEquals(2, runs.size)
        assertEquals(listOf("t"), (runs[0] as OverlayRun.TextRun).overlays.map { it.id })
        assertEquals(listOf("s"), (runs[1] as OverlayRun.StickerRun).stickers.map { it.instanceId })
    }

    @Test
    fun `empty inputs yield no runs and combined max is -1`() {
        assertEquals(emptyList<OverlayRun>(), buildOverlayRuns(emptyList(), emptyList()))
        assertEquals(-1, combinedMaxZIndex(emptyList(), emptyList()))
    }

    @Test
    fun `combined max spans both lists`() {
        assertEquals(
            5,
            combinedMaxZIndex(listOf(text("t", 5)), listOf(sticker("s", 2)))
        )
    }
}
