package com.videomaker.aimusic.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.videomaker.aimusic.widget.appwidget.WidgetActions
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import org.koin.compose.koinInject
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.di.AssetPickerViewModelFactory
import com.videomaker.aimusic.di.EditorViewModelFactory
import com.videomaker.aimusic.di.ExportViewModelFactory
import com.videomaker.aimusic.di.GalleryViewModelFactory
// import com.videomaker.aimusic.di.MusicPickerViewModelFactory // Commented out - using Supabase only
import com.videomaker.aimusic.di.ProjectsViewModelFactory
import com.videomaker.aimusic.di.SongsViewModelFactory
import com.videomaker.aimusic.di.SuggestedSongsListViewModelFactory
import com.videomaker.aimusic.di.WeeklyRankingListViewModelFactory
import com.videomaker.aimusic.di.TemplateListViewModelFactory
import com.videomaker.aimusic.di.TemplatePreviewerViewModelFactory
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchViewModelFactory
import com.videomaker.aimusic.di.UninstallViewModelFactory
import com.videomaker.aimusic.di.WidgetViewModelFactory
import com.videomaker.aimusic.modules.settings.UninstallViewModel
import com.videomaker.aimusic.widget.WidgetViewModel
import com.videomaker.aimusic.modules.editor.EditorScreen
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.modules.export.ExportScreen
import com.videomaker.aimusic.modules.export.ExportViewModel
import com.videomaker.aimusic.modules.templatelist.TemplateListScreen
import com.videomaker.aimusic.modules.templatelist.TemplateListViewModel
import com.videomaker.aimusic.modules.home.HomeScreen
import com.videomaker.aimusic.modules.picker.AssetPickerScreen
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.language.LanguageSelectionScreen
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.settings.SettingsScreen
import com.videomaker.aimusic.modules.settings.UninstallScreen
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerScreen
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerViewModel
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchScreen
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchViewModel
import com.videomaker.aimusic.widget.WidgetScreen

private val slideAnimSpec = tween<IntOffset>(300)

/**
 * Safe back navigation helper - prevents NavDisplay crash from empty backstack
 * CRITICAL: NavDisplay requires at least 1 route in backstack at all times
 */
private fun <T> MutableList<T>.safeRemoveLast(): Boolean {
    return if (size > 1) {
        removeLastOrNull() != null
    } else {
        false // Don't remove the last route - would crash NavDisplay
    }
}

/**
 * AppNavigation — Navigation 3 (1.0.0 stable) host for MainActivity
 *
 * Architecture matches android-short-drama-app:
 * - rememberNavBackStack: Navigation 3 built-in back stack (handles serialization + config changes)
 * - entryProvider { entry<T> }: Type-safe DSL replaces manual NavEntry when-blocks
 * - rememberSaveableStateHolderNavEntryDecorator: Preserves rememberSaveable state
 *   (scroll position, pager index, etc.) when navigating back
 * - predictivePopTransitionSpec: Supports Android 14+ predictive-back gesture
 *
 * Onboarding is NOT a route here — it lives in OnboardingActivity (separate one-time flow).
 */
@SuppressLint("ContextCastToActivity")
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    initialHomeTab: Int = 0,
    pendingDeepLink: Intent? = null,
    onDeepLinkConsumed: () -> Unit = {},
    navigateToUninstall: Boolean = false,
    onUninstallNavigationConsumed: () -> Unit = {}
) {
    val activity = LocalContext.current as? Activity
    val backStack = rememberNavBackStack(AppRoute.Home(initialTab = initialHomeTab.coerceIn(0, 2)))

    // Handle "Uninstall App" shortcut tap
    LaunchedEffect(navigateToUninstall) {
        if (navigateToUninstall) {
            backStack.add(AppRoute.ConfirmUninstall)
            onUninstallNavigationConsumed()
        }
    }

    // Handle deep-link intents from home screen widgets
    LaunchedEffect(pendingDeepLink) {
        val intent = pendingDeepLink ?: return@LaunchedEffect
        when (intent.action) {
            WidgetActions.ACTION_OPEN_SEARCH -> {
                backStack.add(AppRoute.UnifiedSearch(SearchSection.TEMPLATES))
            }
            WidgetActions.ACTION_OPEN_TRENDING_TEMPLATE -> {
                backStack.add(AppRoute.TemplateList())
            }
            WidgetActions.ACTION_OPEN_TRENDING_SONG -> {
                backStack.add(AppRoute.UnifiedSearch(SearchSection.MUSIC))
            }
            WidgetActions.ACTION_OPEN_TEMPLATE_DETAIL -> {
                val templateId = intent.getStringExtra(WidgetActions.EXTRA_TEMPLATE_ID)
                if (!templateId.isNullOrBlank()) {
                    backStack.add(AppRoute.TemplatePreviewer(
                        templateId = templateId,
                        imageUris = emptyList()
                    ))
                }
            }
            WidgetActions.ACTION_OPEN_TEMPLATE_WITH_SONG -> {
                val songId = intent.getLongExtra(WidgetActions.EXTRA_SONG_ID, -1L)
                if (songId != -1L) {
                    backStack.add(AppRoute.TemplatePreviewer(
                        templateId = "",
                        imageUris = emptyList(),
                        overrideSongId = songId
                    ))
                }
            }
            WidgetActions.ACTION_OPEN_SONG_PLAYER -> {
                val songId = intent.getLongExtra(WidgetActions.EXTRA_SONG_ID, -1L)
                if (songId != -1L) {
                    backStack.apply {
                        clear()
                        add(AppRoute.Home(initialTab = 1, initialSongId = songId))
                    }
                }
            }
            WidgetActions.ACTION_CREATE_VIDEO -> {
                backStack.add(AppRoute.AssetPicker())
            }
        }
        onDeepLinkConsumed()
    }

    NavDisplay(
        modifier = modifier.fillMaxSize(),
        backStack = backStack,
        // Decorators for proper ViewModel lifecycle and state preservation
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),  // Preserves rememberSaveable state
            rememberViewModelStoreNavEntryDecorator()        // Scopes ViewModels to NavEntry
        ),
        onBack = {
            // At root (Home) — exit the app; otherwise pop
            if (backStack.size > 1) {
                backStack.safeRemoveLast()
            } else {
                activity?.finish()
            }
        },
        // Push: new screen slides in from right, current shifts left
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = slideAnimSpec) togetherWith
                slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = slideAnimSpec)
        },
        // Pop: current screen slides out to right, previous slides in from left
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = slideAnimSpec) togetherWith
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec)
        },
        // Android 14+ predictive back gesture — same as pop
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = slideAnimSpec) togetherWith
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec)
        },
        entryProvider = entryProvider {

            // ============================================
            // HOME LEVEL
            // ============================================
            entry<AppRoute.Home> { route ->
                val galleryFactory = koinInject<GalleryViewModelFactory>()
                val galleryViewModel: GalleryViewModel = viewModel(
                    key = "gallery",
                    factory = createSafeViewModelFactory { galleryFactory.create() }
                )
                val songsFactory = koinInject<SongsViewModelFactory>()
                val songsViewModel: SongsViewModel = viewModel(
                    key = "songs",
                    factory = createSafeViewModelFactory { songsFactory.create() }
                )
                val projectsFactory = koinInject<ProjectsViewModelFactory>()
                val projectsViewModel: ProjectsViewModel = viewModel(
                    key = "projects",
                    factory = createSafeViewModelFactory { projectsFactory.create() }
                )
                // Auto-open MusicPlayerBottomSheet when launched from widget song tap
                LaunchedEffect(route.initialSongId) {
                    if (route.initialSongId != -1L) {
                        songsViewModel.onSongClickById(route.initialSongId)
                    }
                }

                HomeScreen(
                    galleryViewModel = galleryViewModel,
                    songsViewModel = songsViewModel,
                    projectsViewModel = projectsViewModel,
                    initialTab = route.initialTab,
                    onCreateClick = { backStack.add(AppRoute.AssetPicker()) },
                    onSettingsClick = { backStack.add(AppRoute.Settings) },
                    onNavigateToSearch = { backStack.add(AppRoute.UnifiedSearch(SearchSection.TEMPLATES)) },
                    onNavigateToSongSearch = { backStack.add(AppRoute.UnifiedSearch(SearchSection.MUSIC)) },
                    onNavigateToSuggestedSongsList = { backStack.add(AppRoute.SuggestedSongsList) },
                    onNavigateToWeeklyRankingList = { backStack.add(AppRoute.WeeklyRankingList) },
                    onNavigateToTemplateDetail = { templateId ->
                        // NEW FLOW: Browse templates first, THEN select images
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList() // Sample images mode
                        ))
                    },
                    onNavigateToAllTemplates = { selectedVibeTagId ->
                        // Navigate to template list with selected tag filter
                        backStack.add(AppRoute.TemplateList(selectedVibeTagId))
                    },
                    onNavigateToAssetPicker = { songId ->
                        // Song-to-video flow: browse templates with override song
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "", // Top-ranked
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    },
                    onNavigateToAllSongs = { backStack.add(AppRoute.SuggestedSongsList) },
                    onProjectClick = { projectId ->
                        backStack.add(AppRoute.Editor(projectId))
                    }
                )
            }

            entry<AppRoute.UnifiedSearch> { route ->
                val factory = koinInject<UnifiedSearchViewModelFactory>()
                val unifiedSearchViewModel: UnifiedSearchViewModel = viewModel(
                    key = "unified_search_${route.initialSection}",
                    factory = createSafeViewModelFactory { factory.create(route.initialSection) }
                )
                UnifiedSearchScreen(
                    viewModel = unifiedSearchViewModel,
                    onNavigateToTemplateDetail = { templateId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList()
                        ))
                    },
                    onNavigateToSongDetail = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    },
                    onNavigateBack = { backStack.safeRemoveLast() }
                )
            }

            entry<AppRoute.SuggestedSongsList> {
                val factory = koinInject<SuggestedSongsListViewModelFactory>()
                val suggestedSongsViewModel: com.videomaker.aimusic.modules.suggestedsongs.SuggestedSongsListViewModel = viewModel(
                    key = "suggested_songs_list",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                com.videomaker.aimusic.modules.suggestedsongs.SuggestedSongsListScreen(
                    viewModel = suggestedSongsViewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToAssetPicker = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "", // Top-ranked template
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    }
                )
            }

            entry<AppRoute.WeeklyRankingList> {
                val factory = koinInject<WeeklyRankingListViewModelFactory>()
                val weeklyRankingViewModel: com.videomaker.aimusic.modules.weeklyranking.WeeklyRankingListViewModel = viewModel(
                    key = "weekly_ranking_list",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                com.videomaker.aimusic.modules.weeklyranking.WeeklyRankingListScreen(
                    viewModel = weeklyRankingViewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToAssetPicker = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "", // Top-ranked template
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    }
                )
            }

            // ============================================
            // CREATE FLOW
            // ============================================
            entry<AppRoute.AssetPicker> { route ->
                val factory: AssetPickerViewModelFactory = koinInject()
                val pickerViewModel: AssetPickerViewModel = viewModel(
                    key = "asset_picker_${route.projectId}_${route.templateId}_${route.overrideSongId}_${route.aspectRatio}",
                    factory = createSafeViewModelFactory {
                        factory.create(
                            projectId = route.projectId,
                            templateId = route.templateId,
                            overrideSongId = route.overrideSongId,
                            aspectRatio = route.aspectRatio
                        )
                    }
                )
                AssetPickerScreen(
                    viewModel = pickerViewModel,
                    onNavigateToEditor = { projectId ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home()
                            clear()
                            add(home)
                            add(AppRoute.Editor(projectId))
                        }
                    },
                    onNavigateToEditorWithData = { initialData ->
                        // NEW FLOW: Navigate directly to Editor with template data
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home()
                            clear()
                            add(home)
                            add(AppRoute.Editor(projectId = null, initialData = initialData))
                        }
                    },
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onAssetsAdded = { backStack.safeRemoveLast() },
                    onNavigateToTemplatePreviewer = { templateId, imageUris, overrideSongId ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home()
                            clear()
                            add(home)
                            add(AppRoute.TemplatePreviewer(
                                templateId = templateId,
                                imageUris = imageUris,
                                overrideSongId = overrideSongId
                            ))
                        }
                    }
                )
            }

            entry<AppRoute.Editor> { route ->
                val factory: EditorViewModelFactory = koinInject()
                // val musicPickerFactory: MusicPickerViewModelFactory = koinInject()
                val editorViewModel: EditorViewModel = viewModel(
                    key = "editor_${route.projectId ?: route.initialData.hashCode()}",
                    factory = createSafeViewModelFactory {
                        factory.create(route.projectId, route.initialData)
                    }
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    // musicPickerViewModelFactory = musicPickerFactory,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToPreview = { projectId ->
                        backStack.add(AppRoute.Preview(projectId))
                    },
                    onNavigateToExport = { projectId, quality ->
                        backStack.add(AppRoute.Export(projectId, quality))
                    },
                    onNavigateToAddAssets = { projectId ->
                        backStack.add(AppRoute.AssetPicker(projectId))
                    }
                )
            }

            entry<AppRoute.Preview> { route ->
                PlaceholderScreen(
                    title = "Preview: ${route.projectId}",
                    onBack = { backStack.safeRemoveLast() }
                )
            }

            entry<AppRoute.Export> { route ->
                val factory: ExportViewModelFactory = koinInject()
                val exportViewModel: ExportViewModel = viewModel(
                    key = "export_${route.projectId}_${route.quality}",
                    factory = createSafeViewModelFactory { factory.create(route.projectId, route.quality) }
                )
                ExportScreen(
                    viewModel = exportViewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToHomeMyVideos = {
                        backStack.apply {
                            clear()
                            add(AppRoute.Home(initialTab = 2)) // Tab 2 = My Videos
                        }
                    },
                    onNavigateToTemplateDetail = { templateId ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home()
                            clear()
                            add(home)
                            add(AppRoute.TemplatePreviewer(
                                templateId = templateId,
                                imageUris = emptyList(),
                                overrideSongId = -1L
                            ))
                        }
                    }
                )
            }

            // ============================================
            // TEMPLATE FLOW
            // ============================================
            entry<AppRoute.TemplateList> { route ->
                val factory: TemplateListViewModelFactory = koinInject()
                val viewModel: TemplateListViewModel = viewModel(
                    key = "template_list_${route.selectedVibeTagId}",
                    factory = createSafeViewModelFactory {
                        factory.create(route.selectedVibeTagId)
                    }
                )
                TemplateListScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToTemplatePreviewer = { templateId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList()
                        ))
                    }
                )
            }

            entry<AppRoute.TemplatePreviewer> { route ->
                val factory: TemplatePreviewerViewModelFactory = koinInject()
                val audioCache: AudioPreviewCache = koinInject()
                val viewModel: TemplatePreviewerViewModel = viewModel(
                    key = "template_previewer_${route.templateId}_${route.overrideSongId}",
                    factory = createSafeViewModelFactory {
                        factory.create(
                            templateId = route.templateId,
                            imageUris = route.imageUris,
                            overrideSongId = route.overrideSongId
                        )
                    }
                )
                TemplatePreviewerScreen(
                    viewModel = viewModel,
                    audioDataSourceFactory = audioCache.cacheDataSourceFactory,
                    onNavigateToAssetPicker = { template, overrideSongId, aspectRatio ->
                        // User selected a template with aspect ratio, now pick images
                        // Pass templateId, overrideSongId (if song-to-video mode), and selected aspectRatio
                        // AssetPickerViewModel will use overrideSongId as priority over template's song
                        backStack.add(AppRoute.AssetPicker(
                            templateId = template.id,
                            overrideSongId = overrideSongId,
                            aspectRatio = aspectRatio
                        ))
                    },
                    onNavigateBack = { backStack.safeRemoveLast() }
                )
            }

            // ============================================
            // SETTINGS
            // ============================================
            entry<AppRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToLanguageSettings = { backStack.add(AppRoute.LanguageSettings) },
                    onNavigateToWidgetScreen = { backStack.add(AppRoute.WidgetScreen) }
                )
            }

            entry<AppRoute.ConfirmUninstall> {
                val factory: UninstallViewModelFactory = koinInject()
                val uninstallViewModel: UninstallViewModel = viewModel(
                    key = "confirm_uninstall",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                UninstallScreen(
                    viewModel = uninstallViewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToTemplatePreviewer = { templateId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList()
                        ))
                    },
                    onNavigateToTemplates = { backStack.add(AppRoute.TemplateList()) },
                    onNavigateToAllSongs = { backStack.add(AppRoute.SuggestedSongsList) },
                    onNavigateToTemplatePreviewerWithSong = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    }
                )
            }

            entry<AppRoute.LanguageSettings> {
                val saveLanguage: SaveLanguagePreferenceUseCase = koinInject()
                val applyLanguage: ApplyLanguageUseCase = koinInject()
                val coroutineScope = rememberCoroutineScope()

                LanguageSelectionScreen(
                    showBackButton = true,
                    onBackClick = { backStack.safeRemoveLast() },
                    onLanguageSelected = { languageCode ->
                        coroutineScope.launch {
                            saveLanguage(languageCode)
                        }
                    },
                    onContinue = {
                        applyLanguage()
                        activity?.recreate()
                        backStack.safeRemoveLast()
                    }
                )
            }

            entry<AppRoute.WidgetScreen> {
                val factory: WidgetViewModelFactory = koinInject()
                val widgetViewModel: WidgetViewModel = viewModel(
                    key = "widget_screen",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                WidgetScreen(
                    viewModel = widgetViewModel,
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToTemplatePreviewer = { templateId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList()
                        ))
                    },
                    onNavigateToSearch = { backStack.add(AppRoute.UnifiedSearch(SearchSection.TEMPLATES)) },
                    onNavigateToTemplatePreviewerWithSong = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    }
                )
            }
        }
    )
}

/**
 * Placeholder screen for routes not yet implemented
 */
@Composable
private fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Go Back") }
    }
}
