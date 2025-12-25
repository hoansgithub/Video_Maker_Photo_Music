package co.alcheclub.video.maker.photo.music.di

import co.alcheclub.lib.acccore.di.Module
import co.alcheclub.lib.acccore.di.androidContext
import co.alcheclub.lib.acccore.di.get
import co.alcheclub.lib.acccore.di.module
import co.alcheclub.lib.acccore.di.viewModel
import co.alcheclub.video.maker.photo.music.core.data.local.PreferencesManager
import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepository
import co.alcheclub.video.maker.photo.music.modules.onboarding.repository.OnboardingRepositoryImpl
import co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import co.alcheclub.video.maker.photo.music.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
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

    // Repository implementations
    single<OnboardingRepository> { OnboardingRepositoryImpl(it.get()) }

    // Future: Add more data sources
    // single { AppDatabase.getInstance(androidContext()) }
    // single { it.get<AppDatabase>().projectDao() }
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

    // Future: Add more use cases
    // factory { CreateProjectUseCase(it.get()) }
    // factory { GetProjectsUseCase(it.get()) }
    // factory { ExportVideoUseCase(it.get()) }
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
val presentationModule = module {
    // Root ViewModel for Single-Activity Architecture
    viewModel {
        RootViewModel(
            it.get<CheckOnboardingStatusUseCase>(),
            it.get<CompleteOnboardingUseCase>()
        )
    }

    // Future: Add more ViewModels
    // viewModel { HomeViewModel(it.get()) }
    // viewModel { EditorViewModel(it.get()) }
    // viewModel { ProjectsViewModel(it.get()) }
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
    domainModule,
    presentationModule
)
