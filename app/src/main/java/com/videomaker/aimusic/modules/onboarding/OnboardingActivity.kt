package com.videomaker.aimusic.modules.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch

/**
 * OnboardingActivity — First-time user onboarding flow
 *
 * Separate Activity for onboarding because:
 * - One-time flow that does not need to live in the main navigation back stack
 * - Cleaner separation: user can never "back" into it from the main app
 * - Follows single-responsibility principle
 *
 * Flow:
 * 1. WELCOME step: HorizontalPager with pages 1-3
 * 2. GENRE_SELECTION step: genre picker
 * 3. CompleteOnboardingUseCase marks onboarding as done
 * 4. Launch MainActivity and finish this Activity
 */
class OnboardingActivity : AppCompatActivity() {

    private val completeOnboardingUseCase by lazy { ACCDI.get<CompleteOnboardingUseCase>() }

    private val onboardingViewModel: OnboardingViewModel by lazy {
        val repository = ACCDI.get<OnboardingRepository>()
        ViewModelProvider(
            this,
            createSafeViewModelFactory { OnboardingViewModel(repository) }
        )[OnboardingViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoMakerTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onExitRequested = { showExitDialog = true },
                        onComplete = { completeOnboardingAndNavigate() }
                    )
                }

                if (showExitDialog) {
                    OnboardingExitDialog(
                        onExit = { finish() },
                        onDismiss = { showExitDialog = false }
                    )
                }
            }
        }
    }

    private fun completeOnboardingAndNavigate() {
        lifecycleScope.launch {
            completeOnboardingUseCase()
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** Type-safe ViewModel factory — no unchecked cast (required by CLAUDE.md). */
private inline fun <reified VM : androidx.lifecycle.ViewModel> createSafeViewModelFactory(
    crossinline creator: () -> VM
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val vm = creator()
            if (modelClass.isAssignableFrom(vm::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return vm as T
            }
            throw IllegalArgumentException(
                "Expected ${modelClass.name}, got ${vm::class.java.name}"
            )
        }
    }
}