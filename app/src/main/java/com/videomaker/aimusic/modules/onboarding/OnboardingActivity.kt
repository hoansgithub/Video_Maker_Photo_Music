package com.videomaker.aimusic.modules.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
 * - Follows single-responsibility principle (ref: android-short-drama-app)
 *
 * Flow:
 * 1. User completes onboarding pages (genre selection, feature intro, etc.)
 * 2. OnboardingScreen calls onComplete()
 * 3. CompleteOnboardingUseCase marks onboarding as done
 * 4. Launch MainActivity and finish this Activity
 */
class OnboardingActivity : AppCompatActivity() {

    private val completeOnboardingUseCase by lazy { ACCDI.get<CompleteOnboardingUseCase>() }

    private val onboardingViewModel: OnboardingViewModel by lazy {
        val repository = ACCDI.get<OnboardingRepository>()
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(repository) as T
            }
        })[OnboardingViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoMakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onComplete = { completeOnboardingAndNavigate() }
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
