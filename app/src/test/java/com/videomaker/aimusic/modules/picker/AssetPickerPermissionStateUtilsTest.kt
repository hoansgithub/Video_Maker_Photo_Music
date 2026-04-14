package com.videomaker.aimusic.modules.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPickerPermissionStateUtilsTest {

    @Test
    fun `resolve mode full when full permission granted`() {
        val mode = resolvePermissionMode(
            PermissionSnapshot(fullGranted = true, limitedGranted = true)
        )

        assertEquals(PermissionMode.FULL, mode)
    }

    @Test
    fun `resolve mode limited when only limited granted`() {
        val mode = resolvePermissionMode(
            PermissionSnapshot(fullGranted = false, limitedGranted = true)
        )

        assertEquals(PermissionMode.LIMITED, mode)
    }

    @Test
    fun `resolve mode denied when no permission granted`() {
        val mode = resolvePermissionMode(
            PermissionSnapshot(fullGranted = false, limitedGranted = false)
        )

        assertEquals(PermissionMode.DENIED, mode)
    }

    @Test
    fun `request dialog when permission transitions from granted to denied`() {
        val shouldRequest = shouldRequestPermissionDialog(
            previousMode = PermissionMode.FULL,
            newMode = PermissionMode.DENIED
        )

        assertTrue(shouldRequest)
    }

    @Test
    fun `do not request dialog repeatedly when still denied`() {
        val shouldRequest = shouldRequestPermissionDialog(
            previousMode = PermissionMode.DENIED,
            newMode = PermissionMode.DENIED
        )

        assertFalse(shouldRequest)
    }

    @Test
    fun `prune selected uris by available uri set on limited reload`() {
        val retained = retainSelectedUrisAfterReload(
            selectedUris = setOf("a", "b", "c"),
            availableUris = setOf("a", "c")
        )

        assertEquals(setOf("a", "c"), retained)
    }

    @Test
    fun `prompt decision is settings when blocked even if denied`() {
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = PermissionMode.DENIED,
            blockedAfterSecondAttempt = true,
            limitedUpsellShownThisSession = false
        )

        assertEquals(FullPermissionPromptDecision.SHOW_SETTINGS, decision)
    }

    @Test
    fun `prompt decision is promo for denied when unblocked`() {
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = PermissionMode.DENIED,
            blockedAfterSecondAttempt = false,
            limitedUpsellShownThisSession = false
        )

        assertEquals(FullPermissionPromptDecision.SHOW_PROMO, decision)
    }

    @Test
    fun `prompt decision is promo for limited when unblocked and not shown in session`() {
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = PermissionMode.LIMITED,
            blockedAfterSecondAttempt = false,
            limitedUpsellShownThisSession = false
        )

        assertEquals(FullPermissionPromptDecision.SHOW_PROMO, decision)
    }

    @Test
    fun `prompt decision is none for limited when already shown in session`() {
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = PermissionMode.LIMITED,
            blockedAfterSecondAttempt = false,
            limitedUpsellShownThisSession = true
        )

        assertEquals(FullPermissionPromptDecision.NONE, decision)
    }

    @Test
    fun `prompt decision is none when full granted`() {
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = PermissionMode.FULL,
            blockedAfterSecondAttempt = false,
            limitedUpsellShownThisSession = false
        )

        assertEquals(FullPermissionPromptDecision.NONE, decision)
    }
}
