package com.videomaker.aimusic.di

import androidx.work.WorkManager
import co.alcheclub.lib.acccore.di.Module
import co.alcheclub.lib.acccore.di.androidContext
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.di.module
import co.alcheclub.lib.acccore.di.viewModel
import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.data.local.database.ProjectDatabase
import com.videomaker.aimusic.data.remote.SupabaseClientProvider
import com.videomaker.aimusic.data.repository.ExportRepositoryImpl
import com.videomaker.aimusic.data.repository.ProjectRepositoryImpl
import com.videomaker.aimusic.data.repository.SongRepositoryImpl
import com.videomaker.aimusic.data.repository.TemplateRepositoryImpl
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.ClearSongCacheUseCase
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.GetSongsByGenreUseCase
import com.videomaker.aimusic.domain.usecase.GetStationSongsUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.GetWeeklyRankingSongsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.domain.usecase.DeleteProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetAllProjectsUseCase
import com.videomaker.aimusic.domain.usecase.GetProjectUseCase
import com.videomaker.aimusic.domain.usecase.RemoveAssetUseCase
import com.videomaker.aimusic.domain.usecase.ReorderAssetsUseCase
import com.videomaker.aimusic.domain.usecase.SearchSongsUseCase
import com.videomaker.aimusic.domain.usecase.SearchTemplatesUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.ApplyLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.GetSelectedLanguageUseCase
import com.videomaker.aimusic.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
// Note: Language use cases are still registered for LanguageSelectionActivity (ACCDI.get)
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.media.composition.CompositionFactory
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.export.ExportViewModel
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepositoryImpl
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import android.content.Context
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.musicpicker.MusicPickerViewModel
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.root.RootViewModel
import com.videomaker.aimusic.modules.gallerysearch.GallerySearchViewModel
import com.videomaker.aimusic.modules.templatepreviewer.TemplatePreviewerViewModel

/**
 * ACCDI Dependency Injection Modules
 *
 * This file defines all application dependencies using ACCDI.
 * Modules are split by architectural layer for better organization.
 *
 * Usage in VideoMakerApplication.kt:
 * ACCDI.start(this) {
 *     modules(dataModule, domainModule, presentationModule)
 * }
 */

// ========== DATA LAYER MODULE ==========
/**
 * Data Module
 *
 * Contains:
 * - Data sources (SharedPreferences, Database, Network)
 * - Repository implementations
 *
 * Scope: Singleton - Lives for entire app lifetime
 */
val dataModule = module {
    // Shared data sources
    single { ApiCacheManager(androidContext()) }
    single { PreferencesManager(androidContext()) }
    single { LanguageManager(androidContext()) }

    // WorkManager
    single { WorkManager.getInstance(androidContext()) }

    // Project Database
    single { ProjectDatabase.getInstance(androidContext()) }
    single { it.get<ProjectDatabase>().projectDao() }
    single { it.get<ProjectDatabase>().assetDao() }

    // Supabase client (singleton)
    single { SupabaseClientProvider.instance }

    // Region provider (singleton - derived from language + device locale)
    single {
        RegionProvider(
            languageManager = it.get(),
            preferencesManager = it.get()
        )
    }

    // Repository implementations
    single<OnboardingRepository> { OnboardingRepositoryImpl(it.get()) }
    single<ProjectRepository> { ProjectRepositoryImpl(it.get(), it.get()) }
    single<ExportRepository> { ExportRepositoryImpl(it.get()) }
    single<SongRepository> { SongRepositoryImpl(it.get(), it.get(), regionProvider = it.get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(it.get(), it.get(), regionProvider = it.get()) }
}

// ========== MEDIA LAYER MODULE ==========
/**
 * Media Module
 *
 * Contains:
 * - Media processing utilities (CompositionFactory)
 * - Image loading (Coil ImageLoader)
 *
 * Scope: Singleton - Expensive to create
 */
val mediaModule = module {
    single { CompositionFactory(androidContext()) }
    single { AudioPreviewCache(androidContext()) }

    // Coil ImageLoader singleton
    // Gets the ImageLoader from ImageLoaderFactory (VideoMakerApplication)
    single<coil.ImageLoader> {
        (androidContext().applicationContext as? coil.ImageLoaderFactory)?.newImageLoader()
            ?: coil.ImageLoader.Builder(androidContext())
                .crossfade(true)
                .build()
    }
}

// ========== DOMAIN LAYER MODULE ==========
/**
 * Domain Module
 *
 * Contains:
 * - Use Cases (business logic)
 * - Domain models
 *
 * Scope: Factory - New instance each time (stateless)
 */
val domainModule = module {
    // Onboarding use cases
    factory { CheckOnboardingStatusUseCase(it.get()) }
    factory { CompleteOnboardingUseCase(it.get()) }

    // Language use cases
    factory { CheckLanguageSelectedUseCase(it.get()) }
    factory { CompleteLanguageSelectionUseCase(it.get()) }
    factory { GetSelectedLanguageUseCase(it.get()) }
    factory { SaveLanguagePreferenceUseCase(it.get()) }
    factory { ApplyLanguageUseCase(it.get()) }

    // Project use cases
    factory { CreateProjectUseCase(it.get()) }
    factory { GetProjectUseCase(it.get()) }
    factory { GetAllProjectsUseCase(it.get()) }
    factory { UpdateProjectSettingsUseCase(it.get()) }
    factory { ReorderAssetsUseCase(it.get()) }
    factory { AddAssetsUseCase(it.get()) }
    factory { RemoveAssetUseCase(it.get()) }
    factory { DeleteProjectUseCase(it.get()) }

    // Song use cases — single because they are stateless; factory instances held by singleton
    // factories would violate the factory lifecycle contract if use cases ever become stateful
    single { GetSuggestedSongsUseCase(it.get(), it.get()) }
    single { GetWeeklyRankingSongsUseCase(it.get()) }
    single { GetStationSongsUseCase(it.get()) }
    single { GetGenresUseCase(it.get()) }
    single { GetSongsByGenreUseCase(it.get()) }
    single { ClearSongCacheUseCase(it.get()) }
    single { SearchSongsUseCase(it.get()) }
    single { SearchTemplatesUseCase(it.get()) }
}

// ========== PRESENTATION LAYER MODULE ==========
/**
 * Presentation Module
 *
 * Contains:
 * - ViewModels (UI state management)
 *
 * Scope: ViewModel - Tied to Activity/Fragment lifecycle
 *        (Survives configuration changes like rotation)
 */

/**
 * Factory wrapper for AssetPickerViewModel to support projectId parameter.
 * - projectId = null: Create new project mode
 * - projectId = "...": Add to existing project mode
 */
class AssetPickerViewModelFactory(
    private val application: android.app.Application,
    private val createProjectUseCase: CreateProjectUseCase,
    private val addAssetsUseCase: AddAssetsUseCase
) {
    fun create(projectId: String? = null, templateId: String? = null): AssetPickerViewModel {
        return AssetPickerViewModel(
            context = application,
            createProjectUseCase = createProjectUseCase,
            addAssetsUseCase = addAssetsUseCase,
            projectId = projectId,
            templateId = templateId
        )
    }
}

/**
 * Factory wrapper for EditorViewModel to avoid type erasure issues with ACCDI.
 * Using a dedicated class instead of lambda type (String) -> EditorViewModel
 * because ACCDI cannot distinguish between different lambda types at runtime.
 */
class EditorViewModelFactory(
    private val getProjectUseCase: GetProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val reorderAssetsUseCase: ReorderAssetsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase
) {
    fun create(projectId: String): EditorViewModel {
        return EditorViewModel(
            projectId = projectId,
            getProjectUseCase = getProjectUseCase,
            updateSettingsUseCase = updateSettingsUseCase,
            reorderAssetsUseCase = reorderAssetsUseCase,
            addAssetsUseCase = addAssetsUseCase,
            removeAssetUseCase = removeAssetUseCase
        )
    }
}

/**
 * Factory wrapper for ExportViewModel to avoid type erasure issues with ACCDI.
 */
class ExportViewModelFactory(
    private val exportRepository: ExportRepository
) {
    fun create(projectId: String): ExportViewModel {
        return ExportViewModel(
            projectId = projectId,
            exportRepository = exportRepository
        )
    }
}

/**
 * Factory wrapper for ProjectsViewModel.
 */
class ProjectsViewModelFactory(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase
) {
    fun create(): ProjectsViewModel {
        return ProjectsViewModel(
            getAllProjectsUseCase = getAllProjectsUseCase,
            deleteProjectUseCase = deleteProjectUseCase
        )
    }
}

/**
 * Factory wrapper for MusicPickerViewModel.
 *
 * Uses ContentResolver instead of Context to avoid memory leaks.
 * ViewModels outlive Activity lifecycles, so holding Context references
 * would prevent garbage collection.
 */
class MusicPickerViewModelFactory(
    private val contentResolver: android.content.ContentResolver
) {
    fun create(): MusicPickerViewModel {
        return MusicPickerViewModel(contentResolver = contentResolver)
    }
}

/**
 * Factory wrapper for GalleryViewModel.
 *
 * Uses Application (not Activity) context which is safe in ViewModels.
 * Application instance lives for the entire app lifetime, so no leak risk.
 */
class GalleryViewModelFactory(
    private val application: android.app.Application,
    private val imageLoader: coil.ImageLoader,
    private val templateRepository: TemplateRepository
) {
    fun create(): GalleryViewModel {
        return GalleryViewModel(
            application = application,
            imageLoader = imageLoader,
            templateRepository = templateRepository
        )
    }
}

/**
 * Factory wrapper for SongsViewModel.
 */
class SongsViewModelFactory(
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val getWeeklyRankingSongsUseCase: GetWeeklyRankingSongsUseCase,
    private val getStationSongsUseCase: GetStationSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSongsByGenreUseCase: GetSongsByGenreUseCase,
    private val clearSongCacheUseCase: ClearSongCacheUseCase
) {
    fun create(): SongsViewModel = SongsViewModel(
        getSuggestedSongsUseCase = getSuggestedSongsUseCase,
        getWeeklyRankingSongsUseCase = getWeeklyRankingSongsUseCase,
        getStationSongsUseCase = getStationSongsUseCase,
        getGenresUseCase = getGenresUseCase,
        getSongsByGenreUseCase = getSongsByGenreUseCase,
        clearSongCacheUseCase = clearSongCacheUseCase
    )
}

/**
 * Factory wrapper for TemplatePreviewerViewModel to support templateId + imageUris parameters.
 */
class TemplatePreviewerViewModelFactory(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateProjectSettingsUseCase: UpdateProjectSettingsUseCase
) {
    fun create(templateId: String, imageUris: List<String>): TemplatePreviewerViewModel {
        return TemplatePreviewerViewModel(
            initialTemplateId = templateId,
            imageUrisStr = imageUris,
            templateRepository = templateRepository,
            songRepository = songRepository,
            createProjectUseCase = createProjectUseCase,
            updateProjectSettingsUseCase = updateProjectSettingsUseCase
        )
    }
}

/**
 * Factory wrapper for SearchViewModel.
 */
class GallerySearchViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val searchTemplatesUseCase: SearchTemplatesUseCase,
    private val getGenresUseCase: GetGenresUseCase
) {
    fun create(): GallerySearchViewModel {
        return GallerySearchViewModel(
            preferencesManager = preferencesManager,
            searchSongsUseCase = searchSongsUseCase,
            searchTemplatesUseCase = searchTemplatesUseCase,
            getGenresUseCase = getGenresUseCase
        )
    }
}

val presentationModule = module {
    // Root ViewModel for RootViewActivity (handles loading, Firebase, navigation)
    viewModel {
        RootViewModel(
            checkOnboardingStatusUseCase = it.get<CheckOnboardingStatusUseCase>(),
            checkLanguageSelectedUseCase = it.get<CheckLanguageSelectedUseCase>(),
            remoteConfig = it.get<RemoteConfig>()  // Firebase Remote Config (from firebaseModule)
        )
    }

    // Asset Picker ViewModel factory (needs projectId parameter)
    single {
        AssetPickerViewModelFactory(
            application = (androidContext().applicationContext as? android.app.Application)
                ?: error("applicationContext is not an Application instance"),
            createProjectUseCase = it.get(),
            addAssetsUseCase = it.get()
        )
    }

    // Editor ViewModel factory (singleton - stateless factory)
    single {
        EditorViewModelFactory(
            getProjectUseCase = it.get(),
            updateSettingsUseCase = it.get(),
            reorderAssetsUseCase = it.get(),
            addAssetsUseCase = it.get(),
            removeAssetUseCase = it.get()
        )
    }

    // Export ViewModel factory (singleton - stateless factory)
    single {
        ExportViewModelFactory(
            exportRepository = it.get()
        )
    }

    // Projects ViewModel factory (singleton - stateless factory)
    single {
        ProjectsViewModelFactory(
            getAllProjectsUseCase = it.get(),
            deleteProjectUseCase = it.get()
        )
    }

    // Music Picker ViewModel factory (singleton - stateless factory)
    // Uses ContentResolver to avoid Context memory leaks in ViewModel
    single {
        MusicPickerViewModelFactory(
            contentResolver = androidContext().contentResolver
        )
    }

    // Gallery ViewModel factory (singleton - stateless factory)
    // Uses Application context (safe) + ImageLoader for Coil image preloading
    single {
        GalleryViewModelFactory(
            application = (androidContext().applicationContext as? android.app.Application)
                ?: error("applicationContext is not an Application instance"),
            imageLoader = it.get(),
            templateRepository = it.get()
        )
    }

    // Gallery Search ViewModel factory (singleton - stateless factory)
    single {
        GallerySearchViewModelFactory(
            preferencesManager = it.get(),
            searchSongsUseCase = it.get(),
            searchTemplatesUseCase = it.get(),
            getGenresUseCase = it.get()
        )
    }

    // Songs ViewModel factory (singleton - stateless factory)
    single {
        SongsViewModelFactory(
            getSuggestedSongsUseCase = it.get(),
            getWeeklyRankingSongsUseCase = it.get(),
            getStationSongsUseCase = it.get(),
            getGenresUseCase = it.get(),
            getSongsByGenreUseCase = it.get(),
            clearSongCacheUseCase = it.get()
        )
    }

    // Template Previewer ViewModel factory (singleton - stateless factory)
    single {
        TemplatePreviewerViewModelFactory(
            templateRepository = it.get(),
            songRepository = it.get(),
            createProjectUseCase = it.get(),
            updateProjectSettingsUseCase = it.get()
        )
    }
}

// ========== ALL MODULES ==========
/**
 * Convenience property that combines all modules
 *
 * Use this if you want to register all modules at once:
 * ACCDI.start(this) { modules(*allModules) }
 */
val allModules = arrayOf(
    dataModule,
    mediaModule,
    domainModule,
    presentationModule
)
