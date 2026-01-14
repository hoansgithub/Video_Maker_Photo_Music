package com.videomaker.aimusic.navigation

import androidx.compose.runtime.Stable

/**
 * Navigator - Handles all navigation actions
 *
 * Encapsulates navigation logic for:
 * - Forward navigation (add to stack)
 * - Back navigation (remove from stack)
 * - Clear and navigate (for onboarding completion)
 * - Pop to specific destination
 *
 * Following unidirectional data flow pattern:
 * Events -> Navigator -> State changes -> UI observes
 *
 * Navigation 3 Key Insight:
 * Navigation is just list manipulation. Adding = navigate forward.
 * Removing = navigate back. The UI observes state changes.
 *
 * LIFECYCLE WARNING:
 * This class should ONLY be instantiated within a @Composable context
 * using remember(navigationState). Never store Navigator in a ViewModel,
 * singleton, or static reference - it holds a reference to NavigationState
 * which is tied to the composition lifecycle.
 *
 * Example usage:
 * ```
 * val navigationState = rememberNavigationState(startRoute)
 * val navigator = remember(navigationState) { Navigator(navigationState) }
 * ```
 */
@Stable
class Navigator(
    private val state: NavigationState
) {
    /**
     * Navigate to a new route (push onto stack)
     */
    fun navigate(route: AppRoute) {
        state.backStack.add(route)
    }

    /**
     * Go back one step (pop from stack)
     * @return true if successfully popped, false if at root or pop failed
     */
    fun goBack(): Boolean {
        return if (state.canGoBack) {
            // Check removeLastOrNull result for thread-safety
            state.backStack.removeLastOrNull() != null
        } else {
            false
        }
    }

    /**
     * Pop back stack until the predicate returns true
     * Used for popUpTo behavior
     *
     * @param inclusive If true, also removes the matching route
     * @param predicate Function to test each route
     */
    fun popBackStackUntil(
        inclusive: Boolean = false,
        predicate: (AppRoute) -> Boolean
    ) {
        while (state.backStack.size > 1) {
            val current = state.backStack.lastOrNull() ?: break

            if (predicate(current)) {
                if (inclusive) {
                    state.backStack.removeLastOrNull()
                }
                break
            }

            state.backStack.removeLastOrNull()
        }
    }

    /**
     * Navigate to route, clearing back stack up to (but not including) Home
     * Used when navigating from AssetPicker to Editor
     *
     * Example: [Home, AssetPicker] -> navigatePopToHome(Editor) -> [Home, Editor]
     */
    fun navigatePopToHome(route: AppRoute) {
        popBackStackUntil(inclusive = false) { it is AppRoute.Home }
        navigate(route)
    }

    /**
     * Clear entire back stack and set new start destination
     * Used after onboarding completion
     *
     * Example: [Onboarding] -> clearAndNavigate(Home) -> [Home]
     *
     * Note: Uses apply block to ensure atomic clear+add operation
     */
    fun clearAndNavigate(route: AppRoute) {
        state.backStack.apply {
            clear()
            add(route)
        }
    }

    /**
     * Replace current route without adding to back stack
     * Used for transitions like Loading -> Home
     *
     * Example: [Home, Loading] -> replace(Editor) -> [Home, Editor]
     */
    fun replace(route: AppRoute) {
        state.backStack.apply {
            removeLastOrNull()
            add(route)
        }
    }
}
