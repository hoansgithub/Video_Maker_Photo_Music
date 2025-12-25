package co.alcheclub.video.maker.photo.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.alcheclub.video.maker.photo.music.modules.home.HomeScreen
import co.alcheclub.video.maker.photo.music.modules.onboarding.OnboardingScreen
import co.alcheclub.video.maker.photo.music.modules.root.LoadingScreen
import co.alcheclub.video.maker.photo.music.modules.root.RootNavigationEvent
import co.alcheclub.video.maker.photo.music.modules.root.RootViewModel

/**
 * AppNavigation - Main navigation host for Single-Activity Architecture
 *
 * Handles:
 * - Route setup with slide animations
 * - Navigation event collection from RootViewModel
 * - Screen transitions
 */
@Composable
fun AppNavigation(
    rootViewModel: RootViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Collect navigation events from RootViewModel
    LaunchedEffect(Unit) {
        rootViewModel.navigationEvent.collect { event ->
            when (event) {
                is RootNavigationEvent.NavigateTo -> {
                    when (event.route) {
                        is AppRoute.Home -> {
                            navController.navigate(event.route) {
                                // Clear back stack when navigating to Home
                                popUpTo(AppRoute.Loading) { inclusive = true }
                            }
                        }
                        is AppRoute.Onboarding -> {
                            navController.navigate(event.route) {
                                popUpTo(AppRoute.Loading) { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate(event.route)
                        }
                    }
                }
                is RootNavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Loading,
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

            composable<AppRoute.Loading> {
                val isLoading by rootViewModel.isLoading.collectAsStateWithLifecycle()
                val loadingMessage by rootViewModel.loadingMessage.collectAsStateWithLifecycle()

                LoadingScreen(
                    isLoading = isLoading,
                    message = loadingMessage
                )
            }

            composable<AppRoute.Onboarding> {
                OnboardingScreen(
                    onComplete = {
                        rootViewModel.completeOnboarding()
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
                    }
                )
            }

            // ============================================
            // CREATE FLOW ROUTES (Placeholder for now)
            // ============================================

            composable<AppRoute.AssetPicker> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.AssetPicker>()
                // TODO: Implement AssetPickerScreen
                PlaceholderScreen(
                    title = "Asset Picker",
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoute.Editor> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.Editor>()
                // TODO: Implement EditorScreen
                PlaceholderScreen(
                    title = "Editor: ${route.projectId}",
                    onBack = { navController.popBackStack() }
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
                // TODO: Implement ExportScreen
                PlaceholderScreen(
                    title = "Export: ${route.projectId}",
                    onBack = { navController.popBackStack() }
                )
            }

            // ============================================
            // PROJECTS ROUTES (Placeholder for now)
            // ============================================

            composable<AppRoute.Projects> {
                // TODO: Implement ProjectsScreen
                PlaceholderScreen(
                    title = "My Projects",
                    onBack = { navController.popBackStack() }
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
