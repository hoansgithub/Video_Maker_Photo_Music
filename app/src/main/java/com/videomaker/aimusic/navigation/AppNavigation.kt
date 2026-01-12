package com.videomaker.aimusic.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import com.videomaker.aimusic.modules.settings.SettingsScreen
import com.videomaker.aimusic.modules.picker.AssetPickerScreen
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsScreen
import com.videomaker.aimusic.modules.projects.ProjectsViewModel

/**
 * AppNavigation - Main navigation host for Single-Activity Architecture
 *
 * Handles:
 * - Route setup with slide animations
 * - Screen transitions
 *
 * @param startWithOnboarding If true, starts at Onboarding screen; otherwise starts at Home
 * @param onOnboardingComplete Called when onboarding is completed to mark it in preferences
 */
@Composable
fun AppNavigation(
    startWithOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Determine start destination based on flag from Intent
    // DEMO MODE: Start directly at Projects screen
    val startDestination: AppRoute = AppRoute.Projects
    // TODO: Restore after demo
    // val startDestination: AppRoute = if (startWithOnboarding) AppRoute.Onboarding else AppRoute.Home

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            // ============================================
            // ROOT LEVEL ROUTES
            // ============================================

            composable<AppRoute.Onboarding> {
                OnboardingScreen(
                    onComplete = {
                        // Mark onboarding as complete in preferences
                        onOnboardingComplete()
                        // Navigate to Home, clearing back stack
                        navController.navigate(AppRoute.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }

            // ============================================
            // HOME LEVEL ROUTES
            // ============================================

            composable<AppRoute.Home> {
                HomeScreen(
                    onCreateClick = {
                        navController.navigate(AppRoute.AssetPicker())
                    },
                    onMyProjectsClick = {
                        navController.navigate(AppRoute.Projects)
                    },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings)
                    }
                )
            }

            // ============================================
            // CREATE FLOW ROUTES (Placeholder for now)
            // ============================================

            composable<AppRoute.AssetPicker> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.AssetPicker>()
                // Create AssetPickerViewModel with projectId using factory
                val pickerViewModelFactory: AssetPickerViewModelFactory = ACCDI.get()
                val pickerViewModel: AssetPickerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            require(modelClass.isAssignableFrom(AssetPickerViewModel::class.java)) {
                                "Unknown ViewModel class: ${modelClass.name}"
                            }
                            return pickerViewModelFactory.create(route.projectId) as T
                        }
                    }
                )

                AssetPickerScreen(
                    viewModel = pickerViewModel,
                    onNavigateToEditor = { projectId ->
                        navController.navigate(AppRoute.Editor(projectId)) {
                            popUpTo(AppRoute.Home) { inclusive = false }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onAssetsAdded = { navController.popBackStack() }
                )
            }

            composable<AppRoute.Editor> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.Editor>()
                // Create EditorViewModel with projectId using factory wrapper from ACCDI
                val editorViewModelFactory: EditorViewModelFactory = ACCDI.get()
                val editorViewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            require(modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                                "Unknown ViewModel class: ${modelClass.name}"
                            }
                            return editorViewModelFactory.create(route.projectId) as T
                        }
                    }
                )

                EditorScreen(
                    viewModel = editorViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPreview = { projectId ->
                        navController.navigate(AppRoute.Preview(projectId))
                    },
                    onNavigateToExport = { projectId ->
                        navController.navigate(AppRoute.Export(projectId))
                    },
                    onNavigateToAddAssets = { projectId ->
                        navController.navigate(AppRoute.AssetPicker(projectId))
                    }
                )
            }

            composable<AppRoute.Preview> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.Preview>()
                // TODO: Implement PreviewScreen
                PlaceholderScreen(
                    title = "Preview: ${route.projectId}",
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoute.Export> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.Export>()
                // Create ExportViewModel with projectId using factory wrapper from ACCDI
                val exportViewModelFactory: ExportViewModelFactory = ACCDI.get()
                val exportViewModel: ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            require(modelClass.isAssignableFrom(ExportViewModel::class.java)) {
                                "Unknown ViewModel class: ${modelClass.name}"
                            }
                            return exportViewModelFactory.create(route.projectId) as T
                        }
                    }
                )

                ExportScreen(
                    viewModel = exportViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ============================================
            // PROJECTS ROUTES
            // ============================================

            composable<AppRoute.Projects> {
                // Create ProjectsViewModel using factory
                val projectsViewModelFactory: ProjectsViewModelFactory = ACCDI.get()
                val projectsViewModel: ProjectsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            require(modelClass.isAssignableFrom(ProjectsViewModel::class.java)) {
                                "Unknown ViewModel class: ${modelClass.name}"
                            }
                            return projectsViewModelFactory.create() as T
                        }
                    }
                )

                ProjectsScreen(
                    viewModel = projectsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditor = { projectId ->
                        navController.navigate(AppRoute.Editor(projectId))
                    },
                    onCreateClick = {
                        navController.navigate(AppRoute.AssetPicker())
                    }
                )
            }

            // ============================================
            // SETTINGS ROUTES
            // ============================================

            composable<AppRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
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
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(16.dp)
        )
        androidx.compose.material3.Button(onClick = onBack) {
            androidx.compose.material3.Text("Go Back")
        }
    }
}
