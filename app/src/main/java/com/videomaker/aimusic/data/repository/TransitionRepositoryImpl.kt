package com.videomaker.aimusic.data.repository

import android.content.Context
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.model.TransitionCategory
import com.videomaker.aimusic.domain.repository.TransitionRepository
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Implementation of TransitionRepository using Supabase Postgrest.
 *
 * Table: transitions
 * - id: text (primary key, e.g. "rgb_split")
 * - name: text (display name)
 * - category: text (TransitionCategory enum name)
 * - fragment_shader: text (GLSL shader code)
 * - is_active: boolean
 * - is_premium: boolean
 *
 * Caches downloaded shaders to disk (/cache/shaders/{id}.glsl) for offline use.
 * Registers fetched transitions in TransitionShaderLibrary for runtime access.
 */
class TransitionRepositoryImpl(
    private val context: Context,
    private val supabaseClient: SupabaseClient
) : TransitionRepository {

    companion object {
        private const val TABLE_TRANSITIONS = "transitions"
        private const val TAG = "TransitionRepo"
    }

    private val shaderCacheDir: File by lazy {
        File(context.cacheDir, "shaders").apply { mkdirs() }
    }

    override suspend fun fetchRemoteTransitions(ids: List<String>): Result<List<Transition>> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext Result.success(emptyList())

            try {
                // Check disk cache first for each ID
                val (cached, missing) = ids.partition { hasDiskCache(it) }
                val transitions = mutableListOf<Transition>()

                // Load from disk cache
                cached.forEach { id ->
                    loadFromDiskCache(id)?.let { transitions.add(it) }
                }

                // Fetch missing from Supabase
                if (missing.isNotEmpty()) {
                    val dtos = supabaseClient.from(TABLE_TRANSITIONS)
                        .select {
                            filter {
                                isIn("id", missing)
                                eq("is_active", true)
                            }
                            limit(missing.size.toLong())
                        }
                        .decodeList<TransitionDto>()

                    val fetched = dtos.mapNotNull { it.toTransition() }
                    // Cache to disk
                    fetched.forEach { saveToDiskCache(it) }
                    transitions.addAll(fetched)
                }

                // Register in TransitionShaderLibrary
                if (transitions.isNotEmpty()) {
                    TransitionShaderLibrary.registerRemote(transitions)
                }

                Result.success(transitions)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to fetch transitions: ${e.message}", e)

                // Fallback: try loading all requested IDs from disk cache
                val fallback = ids.mapNotNull { loadFromDiskCache(it) }
                if (fallback.isNotEmpty()) {
                    TransitionShaderLibrary.registerRemote(fallback)
                    Result.success(fallback)
                } else {
                    Result.failure(Exception("Failed to fetch transitions", e))
                }
            }
        }

    override suspend fun fetchAllRemoteTransitions(): Result<List<Transition>> =
        withContext(Dispatchers.IO) {
            try {
                val dtos = supabaseClient.from(TABLE_TRANSITIONS)
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                        limit(500)
                    }
                    .decodeList<TransitionDto>()

                val transitions = dtos.mapNotNull { it.toTransition() }

                // Cache all to disk
                transitions.forEach { saveToDiskCache(it) }

                // Register in TransitionShaderLibrary
                if (transitions.isNotEmpty()) {
                    TransitionShaderLibrary.registerRemote(transitions)
                }

                Result.success(transitions)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to fetch all transitions: ${e.message}", e)
                Result.failure(Exception("Failed to fetch all transitions", e))
            }
        }

    // ============================================
    // DISK CACHE
    // ============================================

    private fun hasDiskCache(id: String): Boolean {
        return File(shaderCacheDir, "$id.glsl").exists()
    }

    private fun loadFromDiskCache(id: String): Transition? {
        return try {
            val file = File(shaderCacheDir, "$id.glsl")
            if (!file.exists()) return null

            val content = file.readText()
            parseShaderFile(id, content)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load cached shader $id: ${e.message}")
            null
        }
    }

    private fun saveToDiskCache(transition: Transition) {
        try {
            val file = File(shaderCacheDir, "${transition.id}.glsl")
            // Store metadata as comments + shader code
            val content = buildString {
                appendLine("// @id: ${transition.id}")
                appendLine("// @name: ${transition.name}")
                appendLine("// @category: ${transition.category.name}")
                appendLine("// @premium: ${transition.isPremium}")
                appendLine()
                append(transition.shaderCode)
            }
            file.writeText(content)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to cache shader ${transition.id}: ${e.message}")
        }
    }

    /**
     * Parse a cached shader file with metadata comments back into a Transition.
     * Uses the same format as TransitionLoader for consistency.
     */
    private fun parseShaderFile(id: String, content: String): Transition? {
        val nameMatch = Regex("""//\s*@name:\s*(.+)""").find(content)
        val categoryMatch = Regex("""//\s*@category:\s*(\S+)""").find(content)
        val premiumMatch = Regex("""//\s*@premium:\s*(true|false)""").find(content)

        val name = nameMatch?.groupValues?.get(1)?.trim() ?: id
        val category = try {
            TransitionCategory.valueOf(categoryMatch?.groupValues?.get(1) ?: "CREATIVE")
        } catch (e: IllegalArgumentException) {
            TransitionCategory.CREATIVE
        }
        val isPremium = premiumMatch?.groupValues?.get(1) == "true"

        // Extract shader code (everything after metadata comments)
        val lines = content.lines()
        val codeLines = mutableListOf<String>()
        var foundCode = false
        for (line in lines) {
            if (!foundCode) {
                val trimmed = line.trim()
                if (trimmed.startsWith("// @") || trimmed.isEmpty()) continue
                foundCode = true
            }
            codeLines.add(line)
        }
        val shaderCode = codeLines.joinToString("\n").trim()
        if (shaderCode.isEmpty()) return null

        return Transition(
            id = id,
            name = name,
            category = category,
            shaderCode = shaderCode,
            isPremium = isPremium
        )
    }
}

// ============================================
// SUPABASE DTO
// ============================================

/**
 * DTO for the Supabase `transitions` table.
 * Maps remote transition rows to the Transition domain model.
 */
@Serializable
data class TransitionDto(
    val id: String,
    val name: String,
    val category: String = "CREATIVE",
    @SerialName("fragment_shader")
    val fragmentShader: String,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("is_premium")
    val isPremium: Boolean = false
) {
    /**
     * Convert DTO to domain model.
     * Returns null if shader code is empty or category is invalid.
     */
    fun toTransition(): Transition? {
        if (fragmentShader.isBlank()) return null

        val transitionCategory = try {
            TransitionCategory.valueOf(category)
        } catch (e: IllegalArgumentException) {
            TransitionCategory.CREATIVE
        }

        return Transition(
            id = id,
            name = name,
            category = transitionCategory,
            shaderCode = fragmentShader,
            isPremium = isPremium
        )
    }
}
