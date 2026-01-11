package com.aimusic.videoeditor.media.library

import android.content.Context
import com.aimusic.videoeditor.domain.model.TransitionSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TransitionSetLibrary - Loads effect sets from JSON
 *
 * Each set contains multiple transitions with a common visual theme.
 * When a set is selected, transitions are cycled through for variety.
 *
 * Effect sets are loaded from assets/effect_sets.json
 */
object TransitionSetLibrary {

    private var context: Context? = null
    private var cachedSets: List<TransitionSet>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initialize the library with application context.
     * Must be called before using other methods.
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Load effect sets from JSON file
     */
    private fun loadSets(): List<TransitionSet> {
        cachedSets?.let { return it }

        val ctx = context ?: throw IllegalStateException(
            "TransitionSetLibrary not initialized. Call init(context) first."
        )

        return try {
            val jsonString = ctx.assets.open("effect_sets.json")
                .bufferedReader()
                .use { it.readText() }

            val effectSetsJson = json.decodeFromString<List<EffectSetJson>>(jsonString)
            val sets = effectSetsJson.map { jsonSet ->
                TransitionSet(
                    id = jsonSet.id,
                    name = jsonSet.name,
                    description = jsonSet.description,
                    thumbnailUrl = jsonSet.thumbnailUrl,
                    isPremium = jsonSet.isPremium,
                    isActive = jsonSet.isActive,
                    transitions = jsonSet.transitionIds.mapNotNull { id ->
                        TransitionShaderLibrary.getById(id)
                    }
                )
            }.filter { it.transitions.isNotEmpty() && it.isActive }

            cachedSets = sets
            sets
        } catch (e: Exception) {
            android.util.Log.e("TransitionSetLibrary", "Failed to load effect sets", e)
            emptyList()
        }
    }

    fun getAll(): List<TransitionSet> = loadSets()

    fun getById(id: String): TransitionSet? = loadSets().find { it.id == id }

    fun getDefault(): TransitionSet = loadSets().firstOrNull()
        ?: TransitionSet(
            id = "default",
            name = "Default",
            description = "Default transitions",
            thumbnailUrl = "",
            isPremium = false,
            transitions = listOfNotNull(TransitionShaderLibrary.getDefault())
        )

    fun getFree(): List<TransitionSet> = loadSets().filter { !it.isPremium }

    fun getPremium(): List<TransitionSet> = loadSets().filter { it.isPremium }

    /**
     * Clear cached sets (useful for development hot reload)
     */
    fun clearCache() {
        cachedSets = null
    }
}

// ============================================
// JSON DATA CLASSES
// ============================================

@Serializable
private data class EffectSetJson(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String = "",
    val isPremium: Boolean = false,
    val isActive: Boolean = true,
    val transitionIds: List<String>
)
