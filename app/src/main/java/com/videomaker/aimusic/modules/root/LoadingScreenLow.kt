package com.videomaker.aimusic.modules.root

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.navigation.AppRoute
import org.koin.androidx.compose.koinViewModel

/**
 * Low-priority loading screen shown when HIGH priority splash ad fails.
 *
 * Reuses the same [LoadingScreen] UI but has its own [LoadingScreenLowViewModel]
 * that independently handles LOW priority ad loading.
 *
 * Flow:
 * 1. RootViewModel detects HIGH ad failure → sets showLowPriorityLoading = true
 * 2. RootViewActivity switches to this composable
 * 3. [LoadingScreenLowViewModel] loads LOW ad, shows it if ready, then navigates
 * 4. Navigation events bubble up to RootViewActivity via [onNavigate]
 */
@Composable
fun LoadingScreenLow(
    destination: AppRoute,
    isFirstOpen: Boolean,
    onNavigate: (RootNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoadingScreenLowViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingStep by viewModel.loadingStep.collectAsStateWithLifecycle()

    // Initialize LOW priority ad loading when this screen is shown
    LaunchedEffect(Unit) {
        activity?.let {
            viewModel.initializeLowPriorityAd(
                activity = it,
                destination = destination,
                isFirstOpen = isFirstOpen
            )
        } ?: run {
            // No activity — navigate directly
            onNavigate(RootNavigationEvent.NavigateTo(destination))
        }
    }

    // Collect navigation events and bubble up to RootViewActivity
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            onNavigate(event)
        }
    }

    // Same visual UI as the normal LoadingScreen
    LoadingScreen(
        isLoading = isLoading,
        loadingStep = loadingStep,
        modifier = modifier
    )
}
