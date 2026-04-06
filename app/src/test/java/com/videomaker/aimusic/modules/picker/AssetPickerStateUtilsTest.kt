package com.videomaker.aimusic.modules.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPickerStateUtilsTest {

    @Test
    fun `skip reload when full permission already loaded`() {
        val state = AssetPickerUiState.WithAssets.AllPermission(
            assets = emptyList(),
            filteredAssets = emptyList()
        )

        assertTrue(shouldSkipPermissionReload(state, isLimited = false))
        assertFalse(shouldSkipPermissionReload(state, isLimited = true))
    }

    @Test
    fun `skip reload when limited permission already loaded`() {
        val state = AssetPickerUiState.WithAssets.LimitPermission(
            assets = emptyList(),
            filteredAssets = emptyList()
        )

        assertTrue(shouldSkipPermissionReload(state, isLimited = true))
        assertFalse(shouldSkipPermissionReload(state, isLimited = false))
    }

    @Test
    fun `do not skip reload for non loaded states`() {
        assertFalse(shouldSkipPermissionReload(AssetPickerUiState.Initial, isLimited = false))
        assertFalse(shouldSkipPermissionReload(AssetPickerUiState.Loading, isLimited = false))
        assertFalse(shouldSkipPermissionReload(AssetPickerUiState.DeniedPermission, isLimited = false))
    }

    @Test
    fun `mergeDistinctByKey keeps first occurrence and order`() {
        data class Item(val key: String, val value: Int)

        val merged = mergeDistinctByKey(
            listOf(Item("a", 1), Item("b", 2)),
            listOf(Item("b", 200), Item("c", 3)),
            keySelector = { it.key }
        )

        assertEquals(
            listOf(Item("a", 1), Item("b", 2), Item("c", 3)),
            merged
        )
    }
}
