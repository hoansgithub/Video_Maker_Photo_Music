package com.videomaker.aimusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Returns a [NestedScrollConnection] that invokes [onScroll] whenever a descendant
 * scrollable reports vertical movement.
 *
 * Used to temporarily hide the music player CTA while the user scrolls the list behind
 * the player to discover other songs. Apply via `Modifier.nestedScroll(connection)` on an
 * ancestor of the scrollable content. Horizontal movement (pagers, tab rows) is ignored.
 */
@Composable
fun rememberHideOnScrollConnection(onScroll: () -> Unit): NestedScrollConnection {
    val currentOnScroll by rememberUpdatedState(onScroll)
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) currentOnScroll()
                return Offset.Zero
            }
        }
    }
}
