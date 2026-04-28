package com.videomaker.aimusic.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardPopupAnalyticsContractTest {

    @Test
    fun `normalizePreviousAction trims whitespace`() {
        assertEquals(
            "use_to_create",
            RewardPopupAnalyticsContract.normalizePreviousAction("  use_to_create  ")
        )
    }

    @Test
    fun `normalizeBtnType accepts yes no exit case insensitive`() {
        assertEquals("yes", RewardPopupAnalyticsContract.normalizeBtnType("YES"))
        assertEquals("no", RewardPopupAnalyticsContract.normalizeBtnType(" No "))
        assertEquals("exit", RewardPopupAnalyticsContract.normalizeBtnType("eXiT"))
    }

    @Test
    fun `normalizeBtnType rejects unknown and blank values`() {
        assertNull(RewardPopupAnalyticsContract.normalizeBtnType(""))
        assertNull(RewardPopupAnalyticsContract.normalizeBtnType("   "))
        assertNull(RewardPopupAnalyticsContract.normalizeBtnType("cancel"))
    }
}
