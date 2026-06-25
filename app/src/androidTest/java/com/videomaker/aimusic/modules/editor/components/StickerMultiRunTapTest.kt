package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videomaker.aimusic.domain.model.StickerPlacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for: after add sticker → add text → add another sticker, the older items can no
 * longer be tapped, because the newest sticker's full-screen touch layer (a separate top z-run)
 * blocked everything beneath it.
 *
 * The fix uses ONE [StickerGestureLayer] for ALL stickers, with draw-only [StickerImagesLayer]
 * runs interleaved above it. This test stacks them exactly like [OverlayInterleaveLayer] does and
 * verifies every sticker — old and new, in different z-runs — is selectable, and empty taps
 * deselect.
 */
@RunWith(AndroidJUnit4::class)
class StickerMultiRunTapTest {

    @get:Rule
    val rule = createComposeRule()

    private val oldSticker = StickerPlacement(
        instanceId = "old",
        stickerId = "s1",
        assetUrl = "old.png",
        centerXNorm = 0.25f,
        centerYNorm = 0.5f,
        widthFractionOfVideo = 1f / 3f,
        zIndex = 0
    )
    private val newSticker = StickerPlacement(
        instanceId = "new",
        stickerId = "s2",
        assetUrl = "new.png",
        centerXNorm = 0.75f,
        centerYNorm = 0.5f,
        widthFractionOfVideo = 1f / 3f,
        zIndex = 2 // a text run would be z=1 between them
    )

    /** Mirrors OverlayInterleaveLayer's stack: gesture (all) + interleaved draw + chrome. */
    private fun setContentWithStack(onSelected: (String?) -> Unit, selectedStart: String?) {
        rule.setContent {
            val all = listOf(oldSticker, newSticker)
            val aspect = remember { mutableStateMapOf<String, Float>() }
            var selected by remember { mutableStateOf(selectedStart) }
            Box(Modifier.requiredSize(300.dp).testTag("host")) {
                StickerGestureLayer(
                    stickers = all,
                    selectedInstanceId = selected,
                    aspectRatios = aspect,
                    onSelect = { selected = it; onSelected(it) },
                    onTransform = {},
                    onDelete = {},
                    onDoubleTapTopMost = {},
                    onRequestEdit = {},
                    onLiveChange = {},
                    interactive = true,
                    modifier = Modifier.fillMaxSize()
                )
                // Interleaved draw: old sticker run, (text run placeholder), new sticker run.
                StickerImagesLayer(listOf(oldSticker), null, aspect, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize()) // stand-in for a TextRun (draw-only here)
                StickerImagesLayer(listOf(newSticker), null, aspect, Modifier.fillMaxSize())
            }
        }
    }

    @Test
    fun tappingOldSticker_inLowerRun_selectsIt() {
        var selectedId: String? = "new"
        setContentWithStack(onSelected = { selectedId = it }, selectedStart = "new")
        rule.onNodeWithTag("host").performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals("old", selectedId)
    }

    @Test
    fun tappingNewSticker_inTopRun_selectsIt() {
        var selectedId: String? = null
        setContentWithStack(onSelected = { selectedId = it }, selectedStart = null)
        rule.onNodeWithTag("host").performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals("new", selectedId)
    }

    @Test
    fun tappingEmptySpace_keepsSelection() {
        var selectedId: String? = "old"
        setContentWithStack(onSelected = { selectedId = it }, selectedStart = "old")
        // Dead-center is empty (stickers are left/right of it). Deselect-on-empty is disabled,
        // so a single tap on empty space must NOT clear the selection.
        // (Two taps + clock advance so the second isn't misread as a double-tap by the test clock.)
        val host = rule.onNodeWithTag("host")
        host.performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rule.mainClock.advanceTimeBy(1000)
        host.performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals("old", selectedId)
    }
}
