package com.videomaker.aimusic.modules.export

import org.junit.Assert.assertFalse
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
}
