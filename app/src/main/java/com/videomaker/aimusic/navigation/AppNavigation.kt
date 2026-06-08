package com.videomaker.aimusic.navigation

// import com.videomaker.aimusic.di.MusicPickerViewModelFactory // Commented out - using Supabase only
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.notification.NotificationDeepLinkFactory
import com.videomaker.aimusic.core.popup.TrendingPopupCoordinator
import com.videomaker.aimusic.core.popup.TrendingPopupNavEvent
import com.videomaker.aimusic.core.popup.TrendingPopupState
import com.videomaker.aimusic.core.popup.TrendingPopupTab
import com.videomaker.aimusic.di.AssetPickerViewModelFactory
import com.videomaker.aimusic.di.EditorViewModelFactory
import com.videomaker.aimusic.di.ExportViewModelFactory
import com.videomaker.aimusic.di.GalleryViewModelFactory
import com.videomaker.aimusic.di.ProjectsViewModelFactory
import com.videomaker.aimusic.di.SongsViewModelFactory
import com.videomaker.aimusic.di.SuggestedSongsListViewModelFactory
import com.videomaker.aimusic.di.TemplateListViewModelFactory
import com.videomaker.aimusic.di.TemplatePreviewerViewModelFactory
import com.videomaker.aimusic.di.UninstallViewModelFactory
import com.videomaker.aimusic.di.WeeklyRankingListViewModelFactory
import com.videomaker.aimusic.di.WidgetViewModelFactory
import com.videomaker.aimusic.modules.editor.EditorScreen
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.export.ExportScreen
import com.videomaker.aimusic.modules.export.ExportViewModel
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.home.HomeScreen
import com.videomaker.aimusic.modules.language.LanguageSelectionScreen
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.videomaker.aimusic.modules.picker.AssetPickerScreen
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.rate.RatingFeedbackPopup
import com.videomaker.aimusic.modules.rate.RatingSatisfactionPopup
import com.videomaker.aimusic.modules.rate.RatingStarsPopup
import com.videomaker.aimusic.modules.rate.RatingStep
import com.videomaker.aimusic.modules.settings.NotificationTestScreen
import com.videomaker.aimusic.modules.settings.SettingsScreen
import com.videomaker.aimusic.modules.settings.UninstallScreen
import com.videomaker.aimusic.modules.settings.UninstallViewModel
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.modules.templatelist.TemplateListScreen
import com.videomaker.aimusic.modules.templatelist.TemplateListViewModel
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerScreen
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerViewModel
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchScreen
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchViewModel
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchViewModelFactory
import com.videomaker.aimusic.modules.welcomeback.WelcomeBackScreen
import com.videomaker.aimusic.ui.components.PopupTrendingSong
import com.videomaker.aimusic.ui.components.PopupTrendingTemplate
import com.videomaker.aimusic.widget.WidgetScreen
import com.videomaker.aimusic.widget.WidgetViewModel
import com.videomaker.aimusic.widget.appwidget.WidgetActions
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val slideAnimSpec = tween<IntOffset>(300)

internal fun createVideoEntryRoute(): AppRoute.TemplatePreviewer = AppRoute.TemplatePreviewer(
    templateId = "",
    imageUris = emptyList(),
    sourceLocation = AnalyticsEvent.Value.Location.SHORTCUT_CREATE_VIDEO
)

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
    onUninstallNavigationConsumed: () -> Unit = {},
    showWelcomeBack: Boolean = false
) {
    val activity = LocalContext.current as? Activity
    // When Welcome Back must be shown on cold start, seed it on TOP of Home so HomeScreen
    // doesn't compose first (which would prematurely trigger the Trending Popup via
    // onTabFocused). After the user taps Continue, WelcomeBack pops and Home composes,
    // at which point the popup is allowed to evaluate normally.
    val backStack = if (showWelcomeBack) {
        rememberNavBackStack(
            AppRoute.Home(initialTab = initialHomeTab.coerceIn(0, 2)),
            AppRoute.WelcomeBack
        )
    } else {
        rememberNavBackStack(AppRoute.Home(initialTab = initialHomeTab.coerceIn(0, 2)))
    }

    // Trending popup rendered at the navigation level so it survives navigation pushes
    // (e.g., shortcut deep-link pushes TemplateList; Dialog must persist across that switch).
    val trendingPopupCoordinator = koinInject<TrendingPopupCoordinator>()
    val templatePopupState by trendingPopupCoordinator.templatePopup.collectAsStateWithLifecycle()
    val songPopupState by trendingPopupCoordinator.songPopup.collectAsStateWithLifecycle()
    val activePopupTab by trendingPopupCoordinator.activeTab.collectAsStateWithLifecycle()

    val ratingTriggerManager = koinInject<com.videomaker.aimusic.core.rating.RatingTriggerManager>()
    val ratingStep by ratingTriggerManager.ratingStep.collectAsStateWithLifecycle()
    val ratingSuppressed by ratingTriggerManager.isSuppressed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ratingTriggerManager.launchPlayStoreEvent.collect {
            val packageName = context.packageName
            val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=$packageName".toUri()
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        trendingPopupCoordinator.navigationEvent.collect { event ->
            when (event) {
                is TrendingPopupNavEvent.OpenTemplatePreviewer -> {
                    if (event.overrideSongId > 0L) {
                        backStack.add(
                            AppRoute.TemplatePreviewer(
                                templateId = "",
                                imageUris = emptyList(),
                                overrideSongId = event.overrideSongId,
                                sourceLocation = AnalyticsEvent.Value.Location.SONG
                            )
                        )
                    } else {
                        backStack.add(
                            AppRoute.TemplatePreviewer(
                                templateId = event.templateId,
                                imageUris = emptyList(),
                                sourceLocation = event.sourceLocation
                            )
                        )
                    }
                }
                is TrendingPopupNavEvent.OpenSongPlayer -> {
                    val homeIndex = backStack.indexOfLast { it is AppRoute.Home }
                    if (homeIndex != -1) {
                        backStack[homeIndex] = AppRoute.Home(initialTab = 1, initialSongId = event.songId)
                        while (backStack.size > homeIndex + 1) {
                            backStack.removeLast()
                        }
                    } else {
                        backStack.apply {
                            clear()
                            add(AppRoute.Home(initialTab = 1, initialSongId = event.songId))
                        }
                    }
                }
            }
        }
    }

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
        val isShortcutIntent = !intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID).isNullOrBlank()
        if (!isShortcutIntent) {
            when (intent.action) {
                WidgetActions.ACTION_OPEN_SEARCH -> {
                    Analytics.trackWidgetOpen(
                        widgetType = "smart_search",
                        widgetSize = "4x3",
                        deepLinkTarget = "search"
                    )
                }
                WidgetActions.ACTION_OPEN_TRENDING_TEMPLATE,
                WidgetActions.ACTION_OPEN_TEMPLATE_DETAIL -> {
                    Analytics.trackWidgetOpen(
                        widgetType = "recently",
                        widgetSize = "4x3",
                        deepLinkTarget = "gallery"
                    )
                }
                WidgetActions.ACTION_OPEN_TRENDING_SONG,
                WidgetActions.ACTION_OPEN_TEMPLATE_WITH_SONG,
                WidgetActions.ACTION_OPEN_SONG_PLAYER -> {
                    Analytics.trackWidgetOpen(
                        widgetType = "trending_song",
                        widgetSize = "4x3",
                        deepLinkTarget = "songs"
                    )
                }
                WidgetActions.ACTION_CREATE_VIDEO -> {
                    Analytics.trackWidgetOpen(
                        widgetType = "recently",
                        widgetSize = "4x3",
                        deepLinkTarget = "gallery"
                    )
                }
            }
        }
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
                backStack.add(createVideoEntryRoute())
            }
            NotificationDeepLinkFactory.ACTION_NOTIF_TRENDING_SONG -> {
                val songId = intent.getLongExtra(NotificationDeepLinkFactory.EXTRA_SONG_ID, -1L)
                if (songId != -1L) {
                    backStack.apply {
                        clear()
                        add(AppRoute.Home(initialTab = 1, initialSongId = songId))
                    }
                }
            }
            NotificationDeepLinkFactory.ACTION_NOTIF_VIRAL_TEMPLATE -> {
                val templateId = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_TEMPLATE_ID)
                if (!templateId.isNullOrBlank()) {
                    backStack.apply {
                        clear()
                        add(AppRoute.Home())
                        add(
                            AppRoute.TemplatePreviewer(
                                templateId = templateId,
                                imageUris = emptyList(),
                                sourceLocation = AnalyticsEvent.Value.Location.HOME_TEMPLATE
                            )
                        )
                    }
                }
            }
            NotificationDeepLinkFactory.ACTION_NOTIF_MY_VIDEO -> {
                val projectId = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_PROJECT_ID)
                val hintMode = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_HINT_MODE)
                backStack.apply {
                    clear()
                    add(
                        AppRoute.Home(
                            initialTab = 2,
                            highlightProjectId = projectId,
                            hintMode = hintMode
                        )
                    )
                }
            }
            NotificationDeepLinkFactory.ACTION_NOTIF_RESUME_TEMPLATE -> {
                val templateId = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_TEMPLATE_ID)
                val songId = intent.getLongExtra(NotificationDeepLinkFactory.EXTRA_SONG_ID, -1L)
                backStack.apply {
                    clear()
                    add(AppRoute.Home())
                    add(
                        AppRoute.TemplatePreviewer(
                            templateId = templateId.orEmpty(),
                            imageUris = emptyList(),
                            overrideSongId = songId,
                            sourceLocation = AnalyticsEvent.Value.Location.HOME_TEMPLATE
                        )
                    )
                }
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
                    highlightProjectId = route.highlightProjectId,
                    projectHintMode = route.hintMode,
                    onCreateClick = {
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            sourceLocation = AnalyticsEvent.Value.Location.GALLERY
                        ))
                    },
                    onSettingsClick = { location ->
                        backStack.add(AppRoute.Settings(settingLocation = location))
                    },
                    onNavigateToSearch = { backStack.add(AppRoute.UnifiedSearch(SearchSection.TEMPLATES)) },
                    onNavigateToSongSearch = { backStack.add(AppRoute.UnifiedSearch(SearchSection.MUSIC)) },
                    onNavigateToSuggestedSongsList = { backStack.add(AppRoute.SuggestedSongsList) },
                    onNavigateToWeeklyRankingList = { backStack.add(AppRoute.WeeklyRankingList) },
                    onNavigateToTemplateDetail = { templateId, sourceLocation ->
                        // NEW FLOW: Browse templates first, THEN select images
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList(), // Sample images mode
                            sourceLocation = sourceLocation
                        ))
                    },
                    onNavigateToAllTemplates = { selectedVibeTagId ->
                        // Navigate to template list with selected tag filter
                        backStack.add(AppRoute.TemplateList(selectedVibeTagId))
                    },
                    onNavigateToAssetPicker = { songId ->
                        // Song-to-video flow: select images, then go to editor (skip templates)
                        backStack.add(AppRoute.AssetPicker(
                            overrideSongId = songId
                        ))
                    },
                    onNavigateToAllSongs = { backStack.add(AppRoute.SuggestedSongsList) },
                    onNavigateToTemplatePreviewerWithSong = { songId ->
                        // Song-to-template flow: browse templates with selected song, then select images
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",  // Empty = start from first template
                            imageUris = emptyList(),
                            overrideSongId = songId,
                            sourceLocation = AnalyticsEvent.Value.Location.SONG
                        ))
                    },
                    onProjectClick = { projectId ->
                        backStack.add(AppRoute.Editor(projectId))
                    }
                )
            }

            entry<AppRoute.WelcomeBack> {
                WelcomeBackScreen(
                    onContinue = { backStack.safeRemoveLast() }
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
                            imageUris = emptyList(),
                            sourceLocation = AnalyticsEvent.Value.Location.SEARCH_RESULT
                        ))
                    },
                    onNavigateToSongDetail = { songId ->
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId,
                            sourceLocation = AnalyticsEvent.Value.Location.SEARCH_RESULT
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
                        // Song-to-template flow: browse templates with selected song
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId,
                            sourceLocation = AnalyticsEvent.Value.Location.SUGGESTED_SONGS
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
                        // Song-to-template flow: browse templates with selected song
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "",
                            imageUris = emptyList(),
                            overrideSongId = songId,
                            sourceLocation = AnalyticsEvent.Value.Location.WEEKLY_RANKING
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
                    key = "asset_picker_${route.projectId}_${route.templateId}_${route.overrideSongId}_${route.aspectRatio}_${route.sourceLocation}_${route.resumeDraftId}_${route.selectedAssetUris.size}_${route.isEditingMode}",
                    factory = createSafeViewModelFactory {
                        factory.create(
                            projectId = route.projectId,
                            templateId = route.templateId,
                            overrideSongId = route.overrideSongId,
                            aspectRatio = route.aspectRatio,
                            resumeDraftId = route.resumeDraftId,
                            selectedAssetUris = route.selectedAssetUris,
                            isEditingMode = route.isEditingMode
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
                                overrideSongId = overrideSongId,
                                sourceLocation = route.sourceLocation
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
                    onNavigateToAddAssets = { projectId, assetUris ->
                        backStack.add(AppRoute.AssetPicker(
                            projectId = projectId,
                            selectedAssetUris = assetUris,
                            isEditingMode = true  // Editing mode: return URIs without saving
                        ))
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
                                overrideSongId = -1L,
                                sourceLocation = AnalyticsEvent.Value.Location.RESULT_RCM
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
                val viewModel: TemplatePreviewerViewModel = viewModel(
                    key = "template_previewer_${route.templateId}_${route.overrideSongId}_${route.sourceLocation}",
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
                    sourceLocation = route.sourceLocation,
                    onNavigateToAssetPicker = { template, overrideSongId, aspectRatio ->
                        // User selected a template with aspect ratio, now pick images
                        // Pass templateId, overrideSongId (if song-to-video mode), and selected aspectRatio
                        // AssetPickerViewModel will use overrideSongId as priority over template's song
                        backStack.add(AppRoute.AssetPicker(
                            templateId = template.id,
                            overrideSongId = overrideSongId,
                            aspectRatio = aspectRatio,
                            sourceLocation = route.sourceLocation
                        ))
                    },
                    onNavigateBack = { backStack.safeRemoveLast() }
                )
            }

            // ============================================
            // SETTINGS
            // ============================================
            entry<AppRoute.Settings> { route ->
                SettingsScreen(
                    onNavigateBack = { backStack.safeRemoveLast() },
                    onNavigateToLanguageSettings = { backStack.add(AppRoute.LanguageSettings) },
                    onNavigateToWidgetScreen = { backStack.add(AppRoute.WidgetScreen) },
                    onNavigateToNotificationTest = { backStack.add(AppRoute.NotificationTest) },
                    settingLocation = route.settingLocation
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
                            imageUris = emptyList(),
                            sourceLocation = AnalyticsEvent.Value.Location.UNINSTALL
                        ))
                    },
                    onNavigateToTemplates = { backStack.add(AppRoute.TemplateList()) },
                    onNavigateToAllSongs = { backStack.add(AppRoute.SuggestedSongsList) },
                    onNavigateToSongPlayer = { songId ->
                        // Open the Songs tab and auto-play the tapped song (same pattern as
                        // widget/notification song deep-links).
                        backStack.apply {
                            clear()
                            add(AppRoute.Home(initialTab = 1, initialSongId = songId))
                        }
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
                        coroutineScope.launch {
                            applyLanguage()
                            activity?.recreate()
                            backStack.safeRemoveLast()
                        }
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
                    onNavigateToSongPlayer = { songId ->
                        // Open the Songs tab and auto-play the tapped song (same pattern as
                        // widget/notification song deep-links).
                        backStack.apply {
                            clear()
                            add(AppRoute.Home(initialTab = 1, initialSongId = songId))
                        }
                    }
                )
            }

            entry<AppRoute.NotificationTest> {
                NotificationTestScreen(
                    onNavigateBack = { backStack.safeRemoveLast() }
                )
            }
        }
    )

    // Trending popups are rendered at navigation level (outside NavDisplay) but are gated by
    // the active surface so they only appear where they belong:
    //   - the template popup ONLY on the Home Gallery tab
    //   - the song popup ONLY on the Home Songs tab
    // They must NOT overlay other routes (e.g. ConfirmUninstall) or the My Videos tab. The
    // popup's Showing state is preserved when the surface changes, so swiping/navigating away
    // hides it and returning to its own tab shows it again.
    val isHomeOnTop = backStack.lastOrNull() is AppRoute.Home

    val isTemplatePopupVisible = isHomeOnTop && activePopupTab == TrendingPopupTab.GALLERY && templatePopupState is TrendingPopupState.Showing
    val isSongPopupVisible = isHomeOnTop && activePopupTab == TrendingPopupTab.SONGS && songPopupState is TrendingPopupState.Showing
    val isTrendingPopupVisible = isTemplatePopupVisible || isSongPopupVisible

    LaunchedEffect(isTrendingPopupVisible) {
        ratingTriggerManager.setRatingSuppressed(isTrendingPopupVisible)
    }

    if (isTemplatePopupVisible) {
        (templatePopupState as? TrendingPopupState.Showing)?.let { showing ->
            PopupTrendingTemplate(
                item = showing.content,
                onCTA = { trendingPopupCoordinator.onTemplatePopupCta(showing.content) },
                onDismiss = { trendingPopupCoordinator.onTemplatePopupDismissed() }
            )
        }
    }

    if (isSongPopupVisible) {
        (songPopupState as? TrendingPopupState.Showing)?.let { showing ->
            PopupTrendingSong(
                item = showing.content,
                onCTA = { trendingPopupCoordinator.onSongPopupCta(showing.content) },
                onDismiss = { trendingPopupCoordinator.onSongPopupDismissed() }
            )
        }
    }

    // Global rating popup overlay.
    // Suppressed while a higher-priority popup (e.g. the picker permission dialog) is visible,
    // so only one popup shows at a time. The pending step is preserved and reappears once released.
    val effectiveRatingStep = if (ratingSuppressed) RatingStep.None else ratingStep
    AnimatedContent(
        targetState = effectiveRatingStep,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "rating_popup_transition"
    ) { step ->
        when (step) {
            RatingStep.Satisfaction -> RatingSatisfactionPopup(
                onNotReally = ratingTriggerManager::onNotReally,
                onGood = ratingTriggerManager::onGood,
                onDismiss = ratingTriggerManager::onRatingDismiss
            )
            RatingStep.Stars -> RatingStarsPopup(
                onLowRating = ratingTriggerManager::onLowRating,
                onHighRating = ratingTriggerManager::onHighRating,
                onDismiss = ratingTriggerManager::onRatingDismiss
            )
            RatingStep.Feedback -> RatingFeedbackPopup(
                onSubmit = ratingTriggerManager::onFeedbackSubmit,
                onDismiss = ratingTriggerManager::onRatingDismiss
            )
            RatingStep.None -> { /* No popup shown */ }
        }
    }
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
