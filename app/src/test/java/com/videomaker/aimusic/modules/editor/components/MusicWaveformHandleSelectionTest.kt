package com.videomaker.aimusic.modules.editor.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicWaveformHandleSelectionTest {

    @Test
    fun `resolveDragStartSelection returns start when tap is clearly closer to start`() {
        val selection = resolveDragStartSelection(
            tapX = 100f,
            startHandleX = 95f,
            endHandleX = 145f,
            touchTargetPx = 80f,
            ambiguityThresholdPx = 12f
        )

        assertEquals(DragStartSelection.Start, selection)
    }

    @Test
    fun `resolveDragStartSelection returns end when tap is clearly closer to end`() {
        val selection = resolveDragStartSelection(
            tapX = 140f,
            startHandleX = 90f,
            endHandleX = 135f,
            touchTargetPx = 80f,
            ambiguityThresholdPx = 12f
        )

        assertEquals(DragStartSelection.End, selection)
    }

    @Test
    fun `resolveDragStartSelection returns ambiguous when distance difference is under threshold`() {
        val selection = resolveDragStartSelection(
            tapX = 100f,
            startHandleX = 92f,
            endHandleX = 108f,
            touchTargetPx = 80f,
            ambiguityThresholdPx = 12f
        )

        assertEquals(DragStartSelection.Ambiguous, selection)
    }

    @Test
    fun `resolveDragStartSelection returns none when both handles are out of touch target`() {
        val selection = resolveDragStartSelection(
            tapX = 300f,
            startHandleX = 100f,
            endHandleX = 120f,
            touchTargetPx = 60f,
            ambiguityThresholdPx = 12f
        )

        assertEquals(DragStartSelection.None, selection)
    }

    @Test
    fun `resolveHandleByDragDirection returns null before commit threshold`() {
        val selectedHandle = resolveHandleByDragDirection(
            accumulatedDx = 3.9f,
            directionCommitThresholdPx = 4f
        )

        assertNull(selectedHandle)
    }

    @Test
    fun `resolveHandleByDragDirection returns start for negative direction`() {
        val selectedHandle = resolveHandleByDragDirection(
            accumulatedDx = -4f,
            directionCommitThresholdPx = 4f
        )

        assertEquals(DragHandle.START, selectedHandle)
    }

    @Test
    fun `resolveHandleByDragDirection returns end for positive direction`() {
        val selectedHandle = resolveHandleByDragDirection(
            accumulatedDx = 4f,
            directionCommitThresholdPx = 4f
        )

        assertEquals(DragHandle.END, selectedHandle)
    }
}
