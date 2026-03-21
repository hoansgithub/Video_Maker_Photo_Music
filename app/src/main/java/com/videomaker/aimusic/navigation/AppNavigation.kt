package com.videomaker.aimusic.navigation

import android.app.Activity
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.koinInject
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.di.AssetPickerViewModelFactory
import com.videomaker.aimusic.di.EditorViewModelFactory
import com.videomaker.aimusic.di.ExportViewModelFactory
import com.videomaker.aimusic.di.GallerySearchViewModelFactory
import com.videomaker.aimusic.di.GalleryViewModelFactory
import com.videomaker.aimusic.di.SongSearchViewModelFactory
import com.videomaker.aimusic.di.MusicPickerViewModelFactory
import com.videomaker.aimusic.di.ProjectsViewModelFactory
import com.videomaker.aimusic.di.SongsViewModelFactory
import com.videomaker.aimusic.di.TemplatePreviewerViewModelFactory
import com.videomaker.aimusic.modules.editor.EditorScreen
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.modules.export.ExportScreen
import com.videomaker.aimusic.modules.export.ExportViewModel
import com.videomaker.aimusic.modules.gallerysearch.GallerySearchScreen
import com.videomaker.aimusic.modules.gallerysearch.GallerySearchViewModel
import com.videomaker.aimusic.modules.songsearch.SongSearchScreen
import com.videomaker.aimusic.modules.songsearch.SongSearchViewModel
import com.videomaker.aimusic.modules.home.HomeScreen
import com.videomaker.aimusic.modules.picker.AssetPickerScreen
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsScreen
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.settings.SettingsScreen
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerScreen
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerViewModel

private val slideAnimSpec = tween<IntOffset>(300)

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
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val backStack = rememberNavBackStack(AppRoute.Home)

    NavDisplay(
        modifier = modifier.fillMaxSize(),
        backStack = backStack,
        onBack = {
            // At root (Home) — exit the app; otherwise pop
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
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
            entry<AppRoute.Home> {
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
                HomeScreen(
                    galleryViewModel = galleryViewModel,
                    songsViewModel = songsViewModel,
                    onCreateClick = { backStack.add(AppRoute.AssetPicker()) },
                    onMyProjectsClick = { backStack.add(AppRoute.Projects) },
                    onSettingsClick = { backStack.add(AppRoute.Settings) },
                    onNavigateToSearch = { backStack.add(AppRoute.Search) },
                    onNavigateToSongSearch = { backStack.add(AppRoute.SongSearch) },
                    onNavigateToTemplateDetail = { templateId ->
                        // NEW FLOW: Browse templates first, THEN select images
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList() // Sample images mode
                        ))
                    },
                    onNavigateToAssetPicker = { songId ->
                        // Song-to-video flow: browse templates with override song
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "", // Top-ranked
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    }
                )
            }

            entry<AppRoute.Search> {
                val factory = koinInject<GallerySearchViewModelFactory>()
                val searchViewModel: GallerySearchViewModel = viewModel(
                    key = "gallery_search",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                GallerySearchScreen(
                    viewModel = searchViewModel,
                    onNavigateToTemplateDetail = { templateId ->
                        // NEW FLOW: Browse templates first, THEN select images
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = templateId,
                            imageUris = emptyList() // Sample images mode
                        ))
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            entry<AppRoute.SongSearch> {
                val factory = koinInject<SongSearchViewModelFactory>()
                val songSearchViewModel: SongSearchViewModel = viewModel(
                    key = "song_search",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                SongSearchScreen(
                    viewModel = songSearchViewModel,
                    onNavigateToSongDetail = { songId ->
                        // Song-to-video flow: browse templates with selected song
                        backStack.add(AppRoute.TemplatePreviewer(
                            templateId = "", // Top-ranked template
                            imageUris = emptyList(),
                            overrideSongId = songId
                        ))
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            // ============================================
            // CREATE FLOW
            // ============================================
            entry<AppRoute.AssetPicker> { route ->
                val factory: AssetPickerViewModelFactory = koinInject()
                val pickerViewModel: AssetPickerViewModel = viewModel(
                    key = "asset_picker_${route.projectId}_${route.templateId}_${route.overrideSongId}",
                    factory = createSafeViewModelFactory {
                        factory.create(
                            projectId = route.projectId,
                            templateId = route.templateId,
                            overrideSongId = route.overrideSongId
                        )
                    }
                )
                AssetPickerScreen(
                    viewModel = pickerViewModel,
                    onNavigateToEditor = { projectId ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home
                            clear()
                            add(home)
                            add(AppRoute.Editor(projectId))
                        }
                    },
                    onNavigateToEditorWithData = { initialData ->
                        // NEW FLOW: Navigate directly to Editor with template data
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home
                            clear()
                            add(home)
                            add(AppRoute.Editor(projectId = null, initialData = initialData))
                        }
                    },
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onAssetsAdded = { backStack.removeLastOrNull() },
                    onNavigateToTemplatePreviewer = { templateId, imageUris, overrideSongId ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home
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
                val musicPickerFactory: MusicPickerViewModelFactory = koinInject()
                val editorViewModel: EditorViewModel = viewModel(
                    key = "editor_${route.projectId ?: route.initialData.hashCode()}",
                    factory = createSafeViewModelFactory {
                        factory.create(route.projectId, route.initialData)
                    }
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    musicPickerViewModelFactory = musicPickerFactory,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToPreview = { projectId ->
                        backStack.add(AppRoute.Preview(projectId))
                    },
                    onNavigateToExport = { projectId ->
                        backStack.add(AppRoute.Export(projectId))
                    },
                    onNavigateToAddAssets = { projectId ->
                        backStack.add(AppRoute.AssetPicker(projectId))
                    }
                )
            }

            entry<AppRoute.Preview> { route ->
                PlaceholderScreen(
                    title = "Preview: ${route.projectId}",
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<AppRoute.Export> { route ->
                val factory: ExportViewModelFactory = koinInject()
                val exportViewModel: ExportViewModel = viewModel(
                    key = "export_${route.projectId}",
                    factory = createSafeViewModelFactory { factory.create(route.projectId) }
                )
                ExportScreen(
                    viewModel = exportViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            // ============================================
            // PROJECTS
            // ============================================
            entry<AppRoute.Projects> {
                val factory = koinInject<ProjectsViewModelFactory>()
                val projectsViewModel: ProjectsViewModel = viewModel(
                    key = "projects",
                    factory = createSafeViewModelFactory { factory.create() }
                )
                ProjectsScreen(
                    viewModel = projectsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToEditor = { projectId -> backStack.add(AppRoute.Editor(projectId)) },
                    onCreateClick = { backStack.add(AppRoute.AssetPicker()) }
                )
            }

            // ============================================
            // TEMPLATE FLOW
            // ============================================
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
                    onNavigateToEditor = { projectId, initialData ->
                        backStack.apply {
                            val home = firstOrNull { it is AppRoute.Home } ?: AppRoute.Home
                            clear()
                            add(home)
                            add(AppRoute.Editor(projectId = projectId, initialData = initialData))
                        }
                    },
                    onNavigateToAssetPicker = { template, overrideSongId ->
                        // NEW FLOW: User selected a template, now pick images
                        // Pass both templateId and overrideSongId (if song-to-video mode)
                        // AssetPickerViewModel will use overrideSongId as priority over template's song
                        backStack.add(AppRoute.AssetPicker(
                            templateId = template.id,
                            overrideSongId = overrideSongId
                        ))
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            // ============================================
            // SETTINGS
            // ============================================
            entry<AppRoute.Settings> {
                SettingsScreen(onNavigateBack = { backStack.removeLastOrNull() })
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
