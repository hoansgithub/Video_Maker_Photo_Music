package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.domain.model.EffectSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TransitionSetLibrary - Resolves effect set IDs to EffectSet objects with transitions.
 *
 * Priority: Supabase (remote cache) -> Local JSON (fallback).
 * Remote cache is populated by EffectSetRepositoryImpl after fetching from Supabase.
 * Local JSON serves as offline fallback only.
 */
object TransitionSetLibrary {

    private var context: Context? = null

    /** Local JSON cache (offline fallback). */
    private var localCache: List<EffectSet>? = null

    /** Remote cache from Supabase — prioritized over local JSON. Thread-safe via volatile + immutable map. */
    @Volatile
    private var remoteCache: Map<String, EffectSet> = emptyMap()

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
     * Register effect sets fetched from Supabase.
     * Only registers sets that have resolved transitions.
     * Thread-safe: synchronized to prevent lost updates from concurrent page loads.
     */
    @Synchronized
    fun registerRemote(effectSets: List<EffectSet>) {
        val updated = remoteCache.toMutableMap()
        effectSets
            .filter { it.transitions.isNotEmpty() }
            .forEach { updated[it.id] = it }
        remoteCache = updated.toMap()
    }

    /**
     * Load effect sets from local JSON file (offline fallback).
     */
    private fun loadLocalSets(): List<EffectSet> {
        localCache?.let { return it }

        val ctx = context ?: throw IllegalStateException(
            "TransitionSetLibrary not initialized. Call init(context) first."
        )

        return try {
            val jsonString = ctx.assets.open("effect_sets.json")
                .bufferedReader()
                .use { it.readText() }

            val effectSetsJson = json.decodeFromString<List<EffectSetJson>>(jsonString)
            val sets = effectSetsJson.map { jsonSet ->
                EffectSet(
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

            localCache = sets
            sets
        } catch (e: Exception) {
            android.util.Log.e("TransitionSetLibrary", "Failed to load local effect sets", e)
            emptyList()
        }
    }

    fun getAll(): List<EffectSet> {
        // Merge: remote overrides local by ID
        val local = loadLocalSets().associateBy { it.id }
        return (local + remoteCache).values.toList()
    }

    /**
     * Lookup by ID: remote first, then local fallback.
     */
    fun getById(id: String): EffectSet? =
        remoteCache[id] ?: loadLocalSets().find { it.id == id }

    fun getDefault(): EffectSet {
        val remote = remoteCache.values.firstOrNull()
        if (remote != null) return remote
        return loadLocalSets().firstOrNull()
            ?: EffectSet(
                id = "default",
                name = "Default",
                description = "Default transitions",
                thumbnailUrl = "",
                isPremium = false,
                transitions = listOfNotNull(TransitionShaderLibrary.getDefault())
            )
    }

    fun getFree(): List<EffectSet> = getAll().filter { !it.isPremium }

    fun getPremium(): List<EffectSet> = getAll().filter { it.isPremium }

    /**
     * Clear all caches (useful for development hot reload).
     */
    fun clearCache() {
        localCache = null
        remoteCache = emptyMap()
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
