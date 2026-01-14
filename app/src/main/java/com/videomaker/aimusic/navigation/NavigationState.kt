package com.videomaker.aimusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * NavigationState - Holds the navigation back stack for the app
 *
 * Since this app has NO bottom navigation (linear flow), we use a single back stack.
 * The back stack is a SnapshotStateList that NavDisplay observes for changes.
 *
 * This approach differs from multi-tab navigation where you'd maintain
 * separate back stacks per tab.
 *
 * Navigation 3 Key Concepts:
 * - You own the back stack (SnapshotStateList)
 * - Navigation = add/remove from list
 * - State changes trigger recomposition
 */
@Stable
class NavigationState(
    val backStack: SnapshotStateList<AppRoute>
) {
    /**
     * Current top route on the back stack
     */
    val currentRoute: AppRoute?
        get() = backStack.lastOrNull()

    /**
     * Check if back stack has more than one entry
     */
    val canGoBack: Boolean
        get() = backStack.size > 1
}

/**
 * Remember NavigationState with proper state preservation
 *
 * Uses rememberSaveable with a custom saver to handle:
 * - Configuration changes (rotation)
 * - Process death recovery via serialization
 *
 * @param startRoute The initial route when app starts
 */
@Composable
fun rememberNavigationState(
    startRoute: AppRoute
): NavigationState {
    val backStack = rememberSaveable(
        saver = backStackSaver()
    ) {
        mutableStateListOf(startRoute)
    }

    return NavigationState(backStack = backStack)
}

/**
 * Custom saver for the back stack that preserves state across config changes
 * and process death using Parcelable serialization.
 *
 * Uses safe cast with filterIsInstance to prevent ClassCastException if
 * saved state is corrupted or incompatible after app update.
 */
private fun backStackSaver(): Saver<SnapshotStateList<AppRoute>, Any> {
    return listSaver(
        save = { it.toList() },
        restore = { saved ->
            // Safe cast - filters to only valid AppRoute instances
            // Returns empty list if types don't match (graceful degradation)
            val list = (saved as? List<*>)?.filterIsInstance<AppRoute>().orEmpty()
            mutableStateListOf<AppRoute>().apply { addAll(list) }
        }
    )
}
