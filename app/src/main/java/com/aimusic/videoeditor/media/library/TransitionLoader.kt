package com.aimusic.videoeditor.media.library

import android.content.Context
import com.aimusic.videoeditor.domain.model.Transition
import com.aimusic.videoeditor.domain.model.TransitionCategory

/**
 * TransitionLoader - Loads GLSL transition shaders from assets
 *
 * Parses .glsl files with metadata comments in the format:
 * ```
 * // @id: transition_id
 * // @name: Display Name
 * // @category: CATEGORY
 * // @premium: true/false
 *
 * vec4 transition(vec2 uv) {
 *     // shader code
 * }
 * ```
 */
class TransitionLoader(private val context: Context) {

    companion object {
        private const val TRANSITIONS_PATH = "transitions"
        private const val GLSL_EXTENSION = ".glsl"

        // Metadata comment patterns
        private val ID_PATTERN = Regex("""//\s*@id:\s*(\S+)""")
        private val NAME_PATTERN = Regex("""//\s*@name:\s*(.+)""")
        private val CATEGORY_PATTERN = Regex("""//\s*@category:\s*(\S+)""")
        private val PREMIUM_PATTERN = Regex("""//\s*@premium:\s*(true|false)""")
    }

    private var cachedTransitions: List<Transition>? = null

    /**
     * Load all transitions from assets
     */
    fun loadAll(): List<Transition> {
        cachedTransitions?.let { return it }

        val transitions = mutableListOf<Transition>()
        val assetManager = context.assets

        // Get all category directories
        val categories = assetManager.list(TRANSITIONS_PATH) ?: return emptyList()

        for (category in categories) {
            val categoryPath = "$TRANSITIONS_PATH/$category"
            val files = assetManager.list(categoryPath) ?: continue

            for (file in files) {
                if (!file.endsWith(GLSL_EXTENSION)) continue

                val filePath = "$categoryPath/$file"
                val transition = loadTransition(filePath)
                if (transition != null) {
                    transitions.add(transition)
                }
            }
        }

        cachedTransitions = transitions
        return transitions
    }

    /**
     * Load a single transition from a file path
     */
    private fun loadTransition(filePath: String): Transition? {
        return try {
            val content = context.assets.open(filePath).bufferedReader().use { it.readText() }
            parseTransition(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse GLSL content into a Transition object
     */
    private fun parseTransition(content: String): Transition? {
        // Extract metadata from comments
        val id = ID_PATTERN.find(content)?.groupValues?.get(1) ?: return null
        val name = NAME_PATTERN.find(content)?.groupValues?.get(1)?.trim() ?: return null
        val categoryStr = CATEGORY_PATTERN.find(content)?.groupValues?.get(1) ?: return null
        val isPremium = PREMIUM_PATTERN.find(content)?.groupValues?.get(1) == "true"

        // Parse category
        val category = try {
            TransitionCategory.valueOf(categoryStr)
        } catch (e: IllegalArgumentException) {
            return null
        }

        // Extract shader code (everything after metadata comments)
        val shaderCode = extractShaderCode(content)

        return Transition(
            id = id,
            name = name,
            category = category,
            isPremium = isPremium,
            shaderCode = shaderCode
        )
    }

    /**
     * Extract shader code from GLSL content, removing metadata comments
     */
    private fun extractShaderCode(content: String): String {
        val lines = content.lines()
        val codeLines = mutableListOf<String>()
        var foundCode = false

        for (line in lines) {
            // Skip metadata comment lines at the top
            if (!foundCode) {
                val trimmed = line.trim()
                if (trimmed.startsWith("// @") || trimmed.isEmpty()) {
                    continue
                }
                foundCode = true
            }

            codeLines.add(line)
        }

        return codeLines.joinToString("\n").trim()
    }

    /**
     * Get transition by ID
     */
    fun getById(id: String): Transition? = loadAll().find { it.id == id }

    /**
     * Get transitions by category
     */
    fun getByCategory(category: TransitionCategory): List<Transition> =
        loadAll().filter { it.category == category }

    /**
     * Get free transitions only
     */
    fun getFree(): List<Transition> = loadAll().filter { !it.isPremium }

    /**
     * Get premium transitions only
     */
    fun getPremium(): List<Transition> = loadAll().filter { it.isPremium }

    /**
     * Get default transition (crossfade)
     */
    fun getDefault(): Transition = getById("rgb_split") ?: loadAll().first()

    /**
     * Get transitions grouped by category
     */
    fun getGroupedByCategory(): Map<TransitionCategory, List<Transition>> =
        loadAll().groupBy { it.category }

    /**
     * Clear cached transitions (useful for hot reload during development)
     */
    fun clearCache() {
        cachedTransitions = null
    }
}
