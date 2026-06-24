package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the key assumption behind the fix: a DRAW-ONLY sibling (no pointerInput) stacked ON
 * TOP does NOT block a gesture layer beneath it. If true, we can put one full-screen sticker
 * gesture layer at the bottom and draw the interleaved images/text (draw-only) above it.
 */
@RunWith(AndroidJUnit4::class)
class DrawOnlyBlockingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun drawOnlyTopSibling_doesNotBlock_bottomGestureLayer() {
        var bottomGotDown = false

        rule.setContent {
            Box(Modifier.requiredSize(300.dp).testTag("host")) {
                // Bottom: a gesture layer
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        bottomGotDown = true
                    }
                })
                // Top: a DRAW-ONLY full-screen sibling (just a background, no pointerInput)
                Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f)))
            }
        }

        rule.onNodeWithTag("host").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rule.waitForIdle()

        assertTrue(
            "bottom gesture layer should receive the tap through a draw-only top sibling",
            bottomGotDown
        )
    }
}
