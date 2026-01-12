package com.aimusic.videoeditor.di

import androidx.work.WorkManager
import co.alcheclub.lib.acccore.di.Module
import co.alcheclub.lib.acccore.di.androidContext
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.di.module
import co.alcheclub.lib.acccore.di.viewModel
import com.aimusic.videoeditor.core.data.local.PreferencesManager
import com.aimusic.videoeditor.core.data.local.LanguageManager
import com.aimusic.videoeditor.data.local.database.ProjectDatabase
import com.aimusic.videoeditor.data.repository.ExportRepositoryImpl
import com.aimusic.videoeditor.data.repository.ProjectRepositoryImpl
import com.aimusic.videoeditor.domain.repository.ExportRepository
import com.aimusic.videoeditor.domain.repository.ProjectRepository
import com.aimusic.videoeditor.domain.usecase.AddAssetsUseCase
import com.aimusic.videoeditor.domain.usecase.CreateProjectUseCase
import com.aimusic.videoeditor.domain.usecase.DeleteProjectUseCase
import com.aimusic.videoeditor.domain.usecase.GetAllProjectsUseCase
import com.aimusic.videoeditor.domain.usecase.GetProjectUseCase
import com.aimusic.videoeditor.domain.usecase.RemoveAssetUseCase
import com.aimusic.videoeditor.domain.usecase.ReorderAssetsUseCase
import com.aimusic.videoeditor.domain.usecase.UpdateProjectSettingsUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.ApplyLanguageUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.GetSelectedLanguageUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.InitializeLanguageUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.SetLanguageUseCase
// Note: Language use cases are still registered for LanguageSelectionActivity (ACCDI.get)
import com.aimusic.videoeditor.media.composition.CompositionFactory
import com.aimusic.videoeditor.modules.editor.EditorViewModel
import com.aimusic.videoeditor.modules.export.ExportViewModel
import com.aimusic.videoeditor.modules.onboarding.repository.OnboardingRepository
import com.aimusic.videoeditor.modules.onboarding.repository.OnboardingRepositoryImpl
import com.aimusic.videoeditor.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.aimusic.videoeditor.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import android.content.Context
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.aimusic.videoeditor.modules.gallery.GalleryViewModel
import com.aimusic.videoeditor.modules.musicpicker.MusicPickerViewModel
import com.aimusic.videoeditor.modules.picker.AssetPickerViewModel
import com.aimusic.videoeditor.modules.projects.ProjectsViewModel
import com.aimusic.videoeditor.modules.root.RootViewModel

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
    single { PreferencesManager(androidContext()) }
    single { LanguageManager(androidContext()) }

    // WorkManager
    single { WorkManager.getInstance(androidContext()) }

    // Project Database
    single { ProjectDatabase.getInstance(androidContext()) }
    single { it.get<ProjectDatabase>().projectDao() }
    single { it.get<ProjectDatabase>().assetDao() }

    // Repository implementations
    single<OnboardingRepository> { OnboardingRepositoryImpl(it.get()) }
    single<ProjectRepository> { ProjectRepositoryImpl(it.get(), it.get()) }
    single<ExportRepository> { ExportRepositoryImpl(it.get()) }
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
    factory { SetLanguageUseCase(it.get()) }
    factory { SaveLanguagePreferenceUseCase(it.get()) }
    factory { ApplyLanguageUseCase(it.get()) }
    factory { InitializeLanguageUseCase(it.get()) }

    // Project use cases
    factory { CreateProjectUseCase(it.get()) }
    factory { GetProjectUseCase(it.get()) }
    factory { GetAllProjectsUseCase(it.get()) }
    factory { UpdateProjectSettingsUseCase(it.get()) }
    factory { ReorderAssetsUseCase(it.get()) }
    factory { AddAssetsUseCase(it.get()) }
    factory { RemoveAssetUseCase(it.get()) }
    factory { DeleteProjectUseCase(it.get()) }
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
    private val context: Context,
    private val createProjectUseCase: CreateProjectUseCase,
    private val addAssetsUseCase: AddAssetsUseCase
) {
    fun create(projectId: String? = null): AssetPickerViewModel {
        return AssetPickerViewModel(
            context = context,
            createProjectUseCase = createProjectUseCase,
            addAssetsUseCase = addAssetsUseCase,
            projectId = projectId
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
    private val imageLoader: coil.ImageLoader
) {
    fun create(): GalleryViewModel {
        return GalleryViewModel(
            application = application,
            imageLoader = imageLoader
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
            context = androidContext(),
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
            application = androidContext().applicationContext as android.app.Application,
            imageLoader = it.get()
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
