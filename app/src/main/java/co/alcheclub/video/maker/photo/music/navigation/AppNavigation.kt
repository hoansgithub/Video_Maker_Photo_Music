package co.alcheclub.video.maker.photo.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.di.viewModel
import co.alcheclub.video.maker.photo.music.di.AssetPickerViewModelFactory
import co.alcheclub.video.maker.photo.music.di.EditorViewModelFactory
import co.alcheclub.video.maker.photo.music.di.ExportViewModelFactory
import co.alcheclub.video.maker.photo.music.di.ProjectsViewModelFactory
import co.alcheclub.video.maker.photo.music.modules.editor.EditorScreen
import co.alcheclub.video.maker.photo.music.modules.editor.EditorViewModel
import co.alcheclub.video.maker.photo.music.modules.export.ExportScreen
import co.alcheclub.video.maker.photo.music.modules.export.ExportViewModel
import co.alcheclub.video.maker.photo.music.modules.home.HomeScreen
import co.alcheclub.video.maker.photo.music.modules.language.LanguageSelectionScreen
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.ApplyLanguageUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.GetSelectedLanguageUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import co.alcheclub.video.maker.photo.music.core.data.local.LanguageManager
import co.alcheclub.video.maker.photo.music.modules.onboarding.OnboardingScreen
import co.alcheclub.video.maker.photo.music.modules.settings.SettingsScreen
import co.alcheclub.video.maker.photo.music.modules.picker.AssetPickerScreen
import co.alcheclub.video.maker.photo.music.modules.picker.AssetPickerViewModel
import co.alcheclub.video.maker.photo.music.modules.projects.ProjectsScreen
import co.alcheclub.video.maker.photo.music.modules.projects.ProjectsViewModel
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
    // Collect navigation events from RootViewModel - StateFlow-based (Google recommended pattern)
    val navigationEvent by rootViewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is RootNavigationEvent.NavigateTo -> {
                    when (event.route) {
                        is AppRoute.Home -> {
                            navController.navigate(event.route) {
                                // Clear entire back stack - prevents going back to Loading or Onboarding
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                        is AppRoute.LanguageSelection -> {
                            navController.navigate(event.route) {
                                // Clear Loading from back stack
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                        is AppRoute.Onboarding -> {
                            navController.navigate(event.route) {
                                // Clear Language Selection from back stack
                                popUpTo(navController.graph.id) { inclusive = true }
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
            rootViewModel.onNavigationHandled()
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

            composable<AppRoute.LanguageSelection> {
                val languageManager = remember { ACCDI.get<LanguageManager>() }
                val getSelectedLanguageUseCase = remember { ACCDI.get<GetSelectedLanguageUseCase>() }
                val saveLanguagePreferenceUseCase = remember { ACCDI.get<SaveLanguagePreferenceUseCase>() }
                val applyLanguageUseCase = remember { ACCDI.get<ApplyLanguageUseCase>() }
                val completeLanguageSelectionUseCase = remember { ACCDI.get<CompleteLanguageSelectionUseCase>() }
                val currentLanguage = remember { getSelectedLanguageUseCase() }

                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { languageCode ->
                        // Save preference only, don't apply (no Activity recreation)
                        saveLanguagePreferenceUseCase(languageCode)
                    },
                    onContinue = {
                        // 1. Mark selection complete FIRST (persists to SharedPreferences)
                        completeLanguageSelectionUseCase()

                        // 2. Set pending recreation flag (so RootViewModel knows to navigate after recreation)
                        languageManager.setPendingLocaleRecreation()

                        // 3. Apply language (triggers Activity recreation)
                        // After recreation, RootViewModel will check pending flag and navigate
                        applyLanguageUseCase()
                    },
                    // Provide localized string preview function
                    getLocalizedString = { resId, languageCode ->
                        languageManager.getLocalizedString(resId, languageCode)
                    }
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
                    }
                )
            }

            // ============================================
            // SETTINGS ROUTES
            // ============================================

            composable<AppRoute.Settings> {
                val getSelectedLanguageUseCase = remember { ACCDI.get<GetSelectedLanguageUseCase>() }
                val languageManager = remember { ACCDI.get<LanguageManager>() }
                val currentLanguageCode = remember { getSelectedLanguageUseCase() }
                val currentLanguageName = remember(currentLanguageCode) { languageManager.getLanguageDisplayName(currentLanguageCode) }

                SettingsScreen(
                    currentLanguageName = currentLanguageName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLanguage = {
                        navController.navigate(AppRoute.LanguageSettings)
                    }
                )
            }

            composable<AppRoute.LanguageSettings> {
                val languageManager = remember { ACCDI.get<LanguageManager>() }
                val getSelectedLanguageUseCase = remember { ACCDI.get<GetSelectedLanguageUseCase>() }
                val saveLanguagePreferenceUseCase = remember { ACCDI.get<SaveLanguagePreferenceUseCase>() }
                val applyLanguageUseCase = remember { ACCDI.get<ApplyLanguageUseCase>() }
                val currentLanguage = remember { getSelectedLanguageUseCase() }

                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    showBackButton = true,
                    onLanguageSelected = { languageCode ->
                        // Save preference only for preview
                        saveLanguagePreferenceUseCase(languageCode)
                    },
                    onContinue = {
                        // 1. Set pending recreation flag
                        languageManager.setPendingLocaleRecreation()

                        // 2. Apply language (triggers Activity recreation)
                        // After recreation, RootViewModel will check pending flag and navigate to Home
                        applyLanguageUseCase()
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    // Provide localized string preview function
                    getLocalizedString = { resId, languageCode ->
                        languageManager.getLocalizedString(resId, languageCode)
                    }
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
