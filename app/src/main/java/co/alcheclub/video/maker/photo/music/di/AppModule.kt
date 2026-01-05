package co.alcheclub.video.maker.photo.music.di

import androidx.work.WorkManager
import co.alcheclub.lib.acccore.di.Module
import co.alcheclub.lib.acccore.di.androidContext
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.di.module
import co.alcheclub.lib.acccore.di.viewModel
import co.alcheclub.video.maker.photo.music.core.data.local.LanguageManager
import co.alcheclub.video.maker.photo.music.core.data.local.PreferencesManager
import co.alcheclub.video.maker.photo.music.data.local.database.ProjectDatabase
import co.alcheclub.video.maker.photo.music.data.repository.ExportRepositoryImpl
import co.alcheclub.video.maker.photo.music.data.repository.ProjectRepositoryImpl
import co.alcheclub.video.maker.photo.music.domain.repository.ExportRepository
import co.alcheclub.video.maker.photo.music.domain.repository.ProjectRepository
import co.alcheclub.video.maker.photo.music.domain.usecase.AddAssetsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.CreateProjectUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.DeleteProjectUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.GetAllProjectsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.GetProjectUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.RemoveAssetUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.ReorderAssetsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.UpdateProjectSettingsUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.ApplyLanguageUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.CompleteLanguageSelectionUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.GetSelectedLanguageUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.InitializeLanguageUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.SaveLanguagePreferenceUseCase
import co.alcheclub.video.maker.photo.music.modules.language.domain.usecase.SetLanguageUseCase
import co.alcheclub.video.maker.photo.music.media.composition.CompositionFactory
import co.alcheclub.video.maker.photo.music.modules.editor.EditorViewModel
import co.alcheclub.video.maker.photo.music.modules.export.ExportViewModel
import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepository
import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepositoryImpl
import co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import android.content.Context
import co.alcheclub.video.maker.photo.music.modules.picker.AssetPickerViewModel
import co.alcheclub.video.maker.photo.music.modules.projects.ProjectsViewModel
import co.alcheclub.video.maker.photo.music.modules.root.RootViewModel

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
 *
 * Scope: Singleton - Expensive to create
 */
val mediaModule = module {
    single { CompositionFactory(androidContext()) }
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

val presentationModule = module {
    // Root ViewModel for Single-Activity Architecture
    viewModel {
        RootViewModel(
            checkOnboardingStatusUseCase = it.get<CheckOnboardingStatusUseCase>(),
            completeOnboardingUseCase = it.get<CompleteOnboardingUseCase>(),
            checkLanguageSelectedUseCase = it.get<CheckLanguageSelectedUseCase>(),
            initializeLanguageUseCase = it.get<InitializeLanguageUseCase>(),
            languageManager = it.get<LanguageManager>()
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
