package com.videomaker.aimusic.media.composition

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class GPUImagePreprocessorExifOrientationTest {

    @Test
    fun `maps rotate 90 orientation to rotate 90 transform`() {
        assertEquals(
            GPUImagePreprocessor.OrientationTransform.ROTATE_90,
            GPUImagePreprocessor.mapExifOrientation(ExifInterface.ORIENTATION_ROTATE_90)
        )
    }

    @Test
    fun `maps unknown orientation to none transform`() {
        assertEquals(
            GPUImagePreprocessor.OrientationTransform.NONE,
            GPUImagePreprocessor.mapExifOrientation(-1)
        )
    }
}
