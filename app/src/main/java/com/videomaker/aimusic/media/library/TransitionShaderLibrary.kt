package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.model.TransitionCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * TransitionShaderLibrary - Facade for accessing GLSL transition shaders
 *
 * This library loads transitions from external .glsl files in assets/transitions/
 * Each shader file contains metadata comments and the GLSL transition function.
 *
 * Based on GL Transitions specification (https://gl-transitions.com/)
 * Each shader implements a transition(vec2 uv) function that blends
 * between getFromColor(uv) and getToColor(uv) based on progress (0.0-1.0)
 *
 * Shader Requirements:
 * - progress: float uniform (0.0 = from, 1.0 = to)
 * - ratio: float uniform (width/height)
 * - getFromColor(vec2 uv): returns source texture color
 * - getToColor(vec2 uv): returns destination texture color
 *
 * Usage:
 * 1. Call TransitionShaderLibrary.init(context) at app startup
 * 2. Use getAll(), getById(), etc. to access transitions
 */
object TransitionShaderLibrary {

    private var loader: TransitionLoader? = null

    // Scope for background loading - tied to app lifecycle
    // Uses SupervisorJob so one failure doesn't cancel others
    // Lazily initialized and cached for reuse
    private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() {
            val currentScope = _scope
            if (currentScope != null && currentScope.coroutineContext[Job]?.isActive == true) {
                return currentScope
            }
            // Create new scope with SupervisorJob
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            _scope = newScope
            return newScope
        }

    // Pre-computed cached values for fast access
    @Volatile private var cachedGroupedByCategory: Map<TransitionCategory, List<Transition>>? = null

    /**
     * Initialize the library with application context.
     * Must be called before using other methods (typically in Application.onCreate)
     */
    fun init(context: Context) {
        loader = TransitionLoader(context.applicationContext)
    }

    /**
     * Pre-load transitions in background thread.
     * Call this at app startup to avoid lag when settings panel opens.
     */
    fun preload() {
        scope.launch {
            try {
                val transitions = getLoader().loadAll()
                // Pre-compute grouped map
                cachedGroupedByCategory = transitions.groupBy { it.category }
                android.util.Log.d("TransitionShaderLibrary", "Preloaded ${transitions.size} transitions")
            } catch (e: Exception) {
                android.util.Log.e("TransitionShaderLibrary", "Failed to preload", e)
            }
        }
    }

    /**
     * Get all available transitions
     */
    fun getAll(): List<Transition> = getLoader().loadAll()

    /**
     * Get transition by ID
     */
    fun getById(id: String): Transition? = getLoader().getById(id)

    /**
     * Get transitions by category
     */
    fun getByCategory(category: TransitionCategory): List<Transition> =
        getLoader().getByCategory(category)

    /**
     * Get free transitions only
     */
    fun getFree(): List<Transition> = getLoader().getFree()

    /**
     * Get premium transitions only
     */
    fun getPremium(): List<Transition> = getLoader().getPremium()

    /**
     * Get default transition (crossfade)
     */
    fun getDefault(): Transition = getLoader().getDefault()

    /**
     * Get transitions grouped by category (uses pre-cached value if available)
     */
    fun getGroupedByCategory(): Map<TransitionCategory, List<Transition>> {
        // Return cached value if available (from preload)
        cachedGroupedByCategory?.let { return it }
        // Otherwise compute and cache
        val grouped = getLoader().getGroupedByCategory()
        cachedGroupedByCategory = grouped
        return grouped
    }

    /**
     * Clear cached transitions and cancel pending operations.
     * Useful for development/testing or app shutdown.
     */
    fun clearCache() {
        // Cancel any pending preload operations
        _scope?.cancel()
        _scope = null

        loader?.clearCache()
        cachedGroupedByCategory = null
    }

    private fun getLoader(): TransitionLoader {
        return loader ?: throw IllegalStateException(
            "TransitionShaderLibrary not initialized. Call init(context) first."
        )
    }
}
