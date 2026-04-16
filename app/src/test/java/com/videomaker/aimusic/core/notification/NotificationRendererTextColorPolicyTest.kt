package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRendererTextColorPolicyTest {

    @Test
    fun `dark mode uses white title and body text`() {
        val colors = NotificationRenderer.resolveNotificationTextColors(isNightMode = true)

        assertEquals(0xFFFFFFFF.toInt(), colors.titleColor)
        assertEquals(0xFFFFFFFF.toInt(), colors.bodyColor)
    }

    @Test
    fun `light mode keeps existing title and body colors`() {
        val colors = NotificationRenderer.resolveNotificationTextColors(isNightMode = false)

        assertEquals(0xFF171D1B.toInt(), colors.titleColor)
        assertEquals(0xFF3F4946.toInt(), colors.bodyColor)
    }
}
