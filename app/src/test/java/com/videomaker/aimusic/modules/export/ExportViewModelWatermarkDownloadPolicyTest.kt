package com.videomaker.aimusic.modules.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportViewModelWatermarkDownloadPolicyTest {

    @Test
    fun `needs re-export when project watermark is removed but current output still has watermark`() {
        assertTrue(
            ExportViewModel.shouldPrepareWatermarkFreeOutputForDownload(
                projectIsWatermarkFree = true,
                outputIsWatermarkFree = false
            )
        )
    }

    @Test
    fun `does not re-export when project still requires watermark`() {
        assertFalse(
            ExportViewModel.shouldPrepareWatermarkFreeOutputForDownload(
                projectIsWatermarkFree = false,
                outputIsWatermarkFree = false
            )
        )
    }

    @Test
    fun `does not re-export when output is already watermark free`() {
        assertFalse(
            ExportViewModel.shouldPrepareWatermarkFreeOutputForDownload(
                projectIsWatermarkFree = true,
                outputIsWatermarkFree = true
            )
        )
    }

    @Test
    fun `download result keeps latest output path when success state changed during save`() {
        val staleSnapshot = ExportUiState.Success(
            outputPath = "/tmp/with_watermark.mp4",
            savedToGallery = false,
            saveError = null
        )
        val latestState = ExportUiState.Success(
            outputPath = "/tmp/watermark_free.mp4",
            savedToGallery = false,
            saveError = null
        )

        val merged = ExportViewModel.mergeDownloadResultState(
            snapshotState = staleSnapshot,
            latestState = latestState,
            savedToGallery = true,
            saveError = null
        )

        assertEquals("/tmp/watermark_free.mp4", merged.outputPath)
        assertTrue(merged.savedToGallery)
        assertEquals(null, merged.saveError)
    }

    @Test
    fun `download error also keeps latest output path when state changed during save`() {
        val staleSnapshot = ExportUiState.Success(
            outputPath = "/tmp/with_watermark.mp4",
            savedToGallery = false,
            saveError = null
        )
        val latestState = ExportUiState.Success(
            outputPath = "/tmp/watermark_free.mp4",
            savedToGallery = false,
            saveError = null
        )
        val expectedError = "save failed"

        val merged = ExportViewModel.mergeDownloadResultState(
            snapshotState = staleSnapshot,
            latestState = latestState,
            savedToGallery = false,
            saveError = expectedError
        )

        assertEquals("/tmp/watermark_free.mp4", merged.outputPath)
        assertFalse(merged.savedToGallery)
        assertEquals(expectedError, merged.saveError)
    }
}
