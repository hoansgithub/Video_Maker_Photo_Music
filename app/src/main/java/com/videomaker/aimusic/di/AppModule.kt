package com.videomaker.aimusic.di

import androidx.work.WorkManager
import org.koin.core.module.Module
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.data.local.database.ProjectDatabase
import com.videomaker.aimusic.data.remote.SupabaseClientProvider
import com.videomaker.aimusic.data.repository.EffectSetRepositoryImpl
import com.videomaker.aimusic.data.repository.ExportRepositoryImpl
import com.videomaker.aimusic.data.repository.ProjectRepositoryImpl
import com.videomaker.aimusic.data.repository.SongRepositoryImpl
import com.videomaker.aimusic.data.repository.TemplateRepositoryImpl
import com.videomaker.aimusic.domain.repository.EffectSetRepository
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.ClearSongCacheUseCase
import com.videomaker.aimusic.domain.usecase.GetEffectSetsPagedUseCase
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
// import com.videomaker.aimusic.modules.musicpicker.MusicPickerViewModel
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.root.RootViewModel
import com.videomaker.aimusic.modules.gallerysearch.GallerySearchViewModel
import com.videomaker.aimusic.modules.songsearch.SongSearchViewModel
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
    single { get<ProjectDatabase>().projectDao() }
    single { get<ProjectDatabase>().assetDao() }

    // Supabase client (singleton)
    single { SupabaseClientProvider.instance }

    // Region provider (singleton - derived from language + device locale)
    single {
        RegionProvider(
            languageManager = get(),
            preferencesManager = get()
        )
    }

    // Repository implementations
    single<OnboardingRepository> { OnboardingRepositoryImpl(get()) }
    single<ProjectRepository> { ProjectRepositoryImpl(get(), get()) }
    single<ExportRepository> { ExportRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get(), get(), regionProvider = get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(get(), get(), regionProvider = get()) }
    single<EffectSetRepository> { EffectSetRepositoryImpl(get(), get(), get()) }
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
    single { CompositionFactory(androidContext(), get()) }
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
    factory { CheckOnboardingStatusUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }

    // Language use cases
    factory { CheckLanguageSelectedUseCase(get()) }
    factory { CompleteLanguageSelectionUseCase(get()) }
    factory { GetSelectedLanguageUseCase(get()) }
    factory { SaveLanguagePreferenceUseCase(get()) }
    factory { ApplyLanguageUseCase(get()) }

    // Project use cases
    factory { CreateProjectUseCase(get()) }
    factory { GetProjectUseCase(get()) }
    factory { GetAllProjectsUseCase(get()) }
    factory { UpdateProjectSettingsUseCase(get()) }
    factory { AddAssetsUseCase(get()) }
    factory { RemoveAssetUseCase(get()) }
    factory { DeleteProjectUseCase(get()) }

    // Song use cases — single because they are stateless; factory instances held by singleton
    // factories would violate the factory lifecycle contract if use cases ever become stateful
    single { GetSuggestedSongsUseCase(get(), get()) }
    single { GetWeeklyRankingSongsUseCase(get()) }
    single { GetStationSongsUseCase(get()) }
    single { GetGenresUseCase(get()) }
    single { GetSongsByGenreUseCase(get()) }
    single { ClearSongCacheUseCase(get()) }
    single { SearchSongsUseCase(get()) }
    single { SearchTemplatesUseCase(get()) }

    // Effect Set use cases
    single { GetEffectSetsPagedUseCase(get()) }
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
    private val addAssetsUseCase: AddAssetsUseCase,
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository
) {
    fun create(
        projectId: String? = null,
        templateId: String? = null,
        overrideSongId: Long = -1L,
        aspectRatio: com.videomaker.aimusic.domain.model.AspectRatio? = null
    ): AssetPickerViewModel {
        return AssetPickerViewModel(
            context = application,
            createProjectUseCase = createProjectUseCase,
            addAssetsUseCase = addAssetsUseCase,
            templateRepository = templateRepository,
            songRepository = songRepository,
            projectId = projectId,
            templateId = templateId,
            overrideSongId = overrideSongId,
            aspectRatio = aspectRatio
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
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase,
    private val songRepository: SongRepository,
    private val effectSetRepository: EffectSetRepository
) {
    fun create(
        projectId: String?,
        initialData: com.videomaker.aimusic.domain.model.EditorInitialData?
    ): EditorViewModel {
        return EditorViewModel(
            projectId = projectId,
            initialData = initialData,
            getProjectUseCase = getProjectUseCase,
            createProjectUseCase = createProjectUseCase,
            updateSettingsUseCase = updateSettingsUseCase,
            addAssetsUseCase = addAssetsUseCase,
            removeAssetUseCase = removeAssetUseCase,
            songRepository = songRepository,
            effectSetRepository = effectSetRepository
        )
    }
}

/**
 * Factory wrapper for ExportViewModel to avoid type erasure issues with ACCDI.
 */
class ExportViewModelFactory(
    private val exportRepository: ExportRepository,
    private val projectRepository: ProjectRepository
) {
    fun create(projectId: String): ExportViewModel {
        return ExportViewModel(
            projectId = projectId,
            exportRepository = exportRepository,
            projectRepository = projectRepository
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

// /**
//  * Factory wrapper for MusicPickerViewModel.
//  *
//  * Uses ContentResolver instead of Context to avoid memory leaks.
//  * ViewModels outlive Activity lifecycles, so holding Context references
//  * would prevent garbage collection.
//  */
// class MusicPickerViewModelFactory(
//     private val contentResolver: android.content.ContentResolver
// ) {
//     fun create(): MusicPickerViewModel {
//         return MusicPickerViewModel(contentResolver = contentResolver)
//     }
// }

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
    fun create(
        templateId: String,
        imageUris: List<String>,
        overrideSongId: Long = -1L
    ): TemplatePreviewerViewModel {
        return TemplatePreviewerViewModel(
            initialTemplateId = templateId,
            imageUrisStr = imageUris,
            overrideSongId = overrideSongId,
            templateRepository = templateRepository,
            songRepository = songRepository,
            createProjectUseCase = createProjectUseCase,
            updateProjectSettingsUseCase = updateProjectSettingsUseCase
        )
    }
}

/**
 * Factory wrapper for SongSearchViewModel.
 */
class SongSearchViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val getSongsByGenreUseCase: GetSongsByGenreUseCase
) {
    fun create(): SongSearchViewModel {
        return SongSearchViewModel(
            preferencesManager = preferencesManager,
            searchSongsUseCase = searchSongsUseCase,
            getGenresUseCase = getGenresUseCase,
            getSuggestedSongsUseCase = getSuggestedSongsUseCase,
            getSongsByGenreUseCase = getSongsByGenreUseCase
        )
    }
}

/**
 * Factory wrapper for SearchViewModel.
 */
class GallerySearchViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val templateRepository: TemplateRepository,
    private val searchTemplatesUseCase: SearchTemplatesUseCase
) {
    fun create(): GallerySearchViewModel {
        return GallerySearchViewModel(
            preferencesManager = preferencesManager,
            templateRepository = templateRepository,
            searchTemplatesUseCase = searchTemplatesUseCase
        )
    }
}

/**
 * Factory wrapper for EffectSetViewModel.
 */
class EffectSetViewModelFactory(
    private val getEffectSetsPagedUseCase: GetEffectSetsPagedUseCase
) {
    fun create(): com.videomaker.aimusic.modules.editor.EffectSetViewModel {
        return com.videomaker.aimusic.modules.editor.EffectSetViewModel(
            getEffectSetsPagedUseCase = getEffectSetsPagedUseCase
        )
    }
}

val presentationModule = module {
    // Root ViewModel for RootViewActivity (handles loading, Firebase, navigation)
    viewModel {
        RootViewModel(
            checkOnboardingStatusUseCase = get(),
            checkLanguageSelectedUseCase = get(),
            remoteConfig = get()  // Firebase Remote Config (from firebaseModule)
        )
    }

    // Onboarding ViewModel
    viewModel {
        com.videomaker.aimusic.modules.onboarding.OnboardingViewModel(
            onboardingRepository = get()
        )
    }

    // Song Search ViewModel
    viewModel {
        com.videomaker.aimusic.modules.songsearch.SongSearchViewModel(
            preferencesManager = get(),
            searchSongsUseCase = get(),
            getGenresUseCase = get(),
            getSuggestedSongsUseCase = get(),
            getSongsByGenreUseCase = get()
        )
    }

    // Effect Set ViewModel
    viewModel {
        com.videomaker.aimusic.modules.editor.EffectSetViewModel(
            getEffectSetsPagedUseCase = get()
        )
    }

    // Asset Picker ViewModel factory (needs projectId parameter)
    single {
        AssetPickerViewModelFactory(
            application = (androidContext().applicationContext as? android.app.Application)
                ?: error("applicationContext is not an Application instance"),
            createProjectUseCase = get(),
            addAssetsUseCase = get(),
            templateRepository = get(),
            songRepository = get()
        )
    }

    // Editor ViewModel factory (singleton - stateless factory)
    single {
        EditorViewModelFactory(
            getProjectUseCase = get(),
            createProjectUseCase = get(),
            updateSettingsUseCase = get(),
            addAssetsUseCase = get(),
            removeAssetUseCase = get(),
            songRepository = get(),
            effectSetRepository = get()
        )
    }

    // Export ViewModel factory (singleton - stateless factory)
    single {
        ExportViewModelFactory(
            exportRepository = get(),
            projectRepository = get()
        )
    }

    // Projects ViewModel factory (singleton - stateless factory)
    single {
        ProjectsViewModelFactory(
            getAllProjectsUseCase = get(),
            deleteProjectUseCase = get()
        )
    }

    // Music Picker ViewModel factory (singleton - stateless factory)
    // Uses ContentResolver to avoid Context memory leaks in ViewModel
    // single {
    //     MusicPickerViewModelFactory(
    //         contentResolver = androidContext().contentResolver
    //     )
    // }

    // Gallery ViewModel factory (singleton - stateless factory)
    // Uses Application context (safe) + ImageLoader for Coil image preloading
    single {
        GalleryViewModelFactory(
            application = (androidContext().applicationContext as? android.app.Application)
                ?: error("applicationContext is not an Application instance"),
            imageLoader = get(),
            templateRepository = get()
        )
    }

    // Gallery Search ViewModel factory (singleton - stateless factory)
    single {
        GallerySearchViewModelFactory(
            preferencesManager = get(),
            templateRepository = get(),
            searchTemplatesUseCase = get()
        )
    }

    // Songs ViewModel factory (singleton - stateless factory)
    single {
        SongsViewModelFactory(
            getSuggestedSongsUseCase = get(),
            getWeeklyRankingSongsUseCase = get(),
            getStationSongsUseCase = get(),
            getGenresUseCase = get(),
            getSongsByGenreUseCase = get(),
            clearSongCacheUseCase = get()
        )
    }

    // Song Search ViewModel factory (singleton - stateless factory)
    single {
        SongSearchViewModelFactory(
            preferencesManager = get(),
            searchSongsUseCase = get(),
            getGenresUseCase = get(),
            getSuggestedSongsUseCase = get(),
            getSongsByGenreUseCase = get()
        )
    }

    // Template Previewer ViewModel factory (singleton - stateless factory)
    single {
        TemplatePreviewerViewModelFactory(
            templateRepository = get(),
            songRepository = get(),
            createProjectUseCase = get(),
            updateProjectSettingsUseCase = get()
        )
    }

    // Effect Set ViewModel factory (singleton - stateless factory)
    single {
        EffectSetViewModelFactory(
            getEffectSetsPagedUseCase = get()
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
