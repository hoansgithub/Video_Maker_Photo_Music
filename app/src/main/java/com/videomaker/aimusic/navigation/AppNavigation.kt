package com.videomaker.aimusic.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import com.videomaker.aimusic.di.AssetPickerViewModelFactory
import com.videomaker.aimusic.di.EditorViewModelFactory
import com.videomaker.aimusic.di.ExportViewModelFactory
import com.videomaker.aimusic.di.ProjectsViewModelFactory
import com.videomaker.aimusic.modules.editor.EditorScreen
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.export.ExportScreen
import com.videomaker.aimusic.modules.export.ExportViewModel
import com.videomaker.aimusic.modules.home.HomeScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingScreen
import com.videomaker.aimusic.modules.picker.AssetPickerScreen
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsScreen
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.settings.SettingsScreen

/**
 * AppNavigation - Main navigation host for Single-Activity Architecture
 *
 * Navigation 3 Implementation:
 * - Uses NavDisplay instead of NavHost
 * - Back stack is a SnapshotStateList owned by NavigationState
 * - Navigator class handles all navigation events
 * - Entry provider resolves routes to composables
 *
 * Key differences from Navigation 2.x:
 * - You own the back stack (not the library)
 * - Navigation = list manipulation (add/remove)
 * - Route arguments accessed directly from key parameter
 * - No toRoute<T>() needed - key IS the route
 *
 * @param startWithOnboarding If true, starts at Onboarding; otherwise Home
 * @param onOnboardingComplete Called when onboarding is completed
 */
@Composable
fun AppNavigation(
    startWithOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine start destination
    val startRoute: AppRoute = if (startWithOnboarding) {
        AppRoute.Onboarding
    } else {
        AppRoute.Home
    }

    // Create navigation state with persistent back stack
    val navigationState = rememberNavigationState(startRoute = startRoute)

    // Create navigator for handling navigation events
    val navigator = remember(navigationState) {
        Navigator(navigationState)
    }

    // Animation specs matching Nav2 behavior (300ms slide + fade)
    val forwardTransition: ContentTransform = (
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = tween(300))
    ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { -it / 3 }
        ) + fadeOut(animationSpec = tween(300))
    )

    val backTransition: ContentTransform = (
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { -it / 3 }
        ) + fadeIn(animationSpec = tween(300))
    ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = tween(300))
    )

    Box(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigationState.backStack,
            onBack = { navigator.goBack() },
            transitionSpec = { forwardTransition },
            popTransitionSpec = { backTransition },
            entryProvider = { route ->
                // NavEntry's content block is @Composable
                NavEntry(route) {
                    RouteContent(
                        route = route,
                        navigator = navigator,
                        onOnboardingComplete = onOnboardingComplete
                    )
                }
            }
        )
    }
}

/**
 * Renders the content for a given route.
 * This is a @Composable function that handles all route-specific rendering.
 */
@Composable
private fun RouteContent(
    route: AppRoute,
    navigator: Navigator,
    onOnboardingComplete: () -> Unit
) {
    when (route) {
        // ============================================
        // ROOT LEVEL ROUTES
        // ============================================
        is AppRoute.Loading -> {
            // Loading screen - typically handled by splash
        }

        is AppRoute.LanguageSelection -> {
            // Handled by separate Activity
        }

        is AppRoute.Onboarding -> {
            OnboardingScreen(
                onComplete = {
                    onOnboardingComplete()
                    navigator.clearAndNavigate(AppRoute.Home)
                }
            )
        }

        // ============================================
        // HOME LEVEL ROUTES
        // ============================================
        is AppRoute.Home -> {
            HomeScreen(
                onCreateClick = {
                    navigator.navigate(AppRoute.AssetPicker())
                },
                onMyProjectsClick = {
                    navigator.navigate(AppRoute.Projects)
                },
                onSettingsClick = {
                    navigator.navigate(AppRoute.Settings)
                }
            )
        }

        // ============================================
        // CREATE FLOW ROUTES
        // ============================================
        is AppRoute.AssetPicker -> {
            // Key factory and ViewModel to projectId for proper lifecycle
            val factory = remember(route.projectId) { ACCDI.get<AssetPickerViewModelFactory>() }
            val pickerViewModel: AssetPickerViewModel = viewModel(
                key = "asset_picker_${route.projectId}",
                factory = createSafeViewModelFactory { factory.create(route.projectId) }
            )

            AssetPickerScreen(
                viewModel = pickerViewModel,
                onNavigateToEditor = { projectId ->
                    navigator.navigatePopToHome(AppRoute.Editor(projectId))
                },
                onNavigateBack = { navigator.goBack() },
                onAssetsAdded = { navigator.goBack() }
            )
        }

        is AppRoute.Editor -> {
            // Key factory and ViewModel to projectId for proper lifecycle
            val factory = remember(route.projectId) { ACCDI.get<EditorViewModelFactory>() }
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor_${route.projectId}",
                factory = createSafeViewModelFactory { factory.create(route.projectId) }
            )

            EditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = { navigator.goBack() },
                onNavigateToPreview = { projectId ->
                    navigator.navigate(AppRoute.Preview(projectId))
                },
                onNavigateToExport = { projectId ->
                    navigator.navigate(AppRoute.Export(projectId))
                },
                onNavigateToAddAssets = { projectId ->
                    navigator.navigate(AppRoute.AssetPicker(projectId))
                }
            )
        }

        is AppRoute.Preview -> {
            PlaceholderScreen(
                title = "Preview: ${route.projectId}",
                onBack = { navigator.goBack() }
            )
        }

        is AppRoute.Export -> {
            // Key factory and ViewModel to projectId for proper lifecycle
            val factory = remember(route.projectId) { ACCDI.get<ExportViewModelFactory>() }
            val exportViewModel: ExportViewModel = viewModel(
                key = "export_${route.projectId}",
                factory = createSafeViewModelFactory { factory.create(route.projectId) }
            )

            ExportScreen(
                viewModel = exportViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }

        // ============================================
        // PROJECTS ROUTES
        // ============================================
        is AppRoute.Projects -> {
            val factory = remember { ACCDI.get<ProjectsViewModelFactory>() }
            val projectsViewModel: ProjectsViewModel = viewModel(
                factory = createSafeViewModelFactory { factory.create() }
            )

            ProjectsScreen(
                viewModel = projectsViewModel,
                onNavigateBack = { navigator.goBack() },
                onNavigateToEditor = { projectId ->
                    navigator.navigate(AppRoute.Editor(projectId))
                },
                onCreateClick = {
                    navigator.navigate(AppRoute.AssetPicker())
                }
            )
        }

        // ============================================
        // SETTINGS ROUTES
        // ============================================
        is AppRoute.Settings -> {
            SettingsScreen(
                onNavigateBack = { navigator.goBack() }
            )
        }
    }
}

/**
 * Creates a type-safe ViewModelProvider.Factory.
 *
 * Uses type validation to prevent ClassCastException at runtime.
 * Throws IllegalArgumentException with helpful message if type mismatch.
 *
 * @param creator Lambda that creates the ViewModel instance
 */
private inline fun <reified VM : androidx.lifecycle.ViewModel> createSafeViewModelFactory(
    crossinline creator: () -> VM
): androidx.lifecycle.ViewModelProvider.Factory {
    return object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val viewModel = creator()

            // Type-safe cast with validation
            if (modelClass.isAssignableFrom(viewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            } else {
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}, expected: ${viewModel::class.java.name}"
                )
            }
        }
    }
}

/**
 * Placeholder screen for routes not yet implemented
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}
