package com.videomaker.aimusic.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Creates a type-safe ViewModelProvider.Factory.
 *
 * Uses type validation to prevent ClassCastException at runtime.
 * Throws IllegalArgumentException with helpful message if type mismatch.
 *
 * @param creator Lambda that creates the ViewModel instance
 */
internal inline fun <reified VM : ViewModel> createSafeViewModelFactory(
    crossinline creator: () -> VM
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val viewModel = creator()

            if (modelClass.isAssignableFrom(viewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            } else {
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}, expected: ${viewModel::class.java.name}"
                )
            }
        }
    }
}
