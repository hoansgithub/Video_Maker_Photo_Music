package com.videomaker.aimusic.domain.model

/**
 * Transition - Represents a single transition effect
 *
 * Based on GL Transitions format (https://gl-transitions.com/)
 * Each transition has a GLSL shader that blends between two textures
 * using a progress value from 0.0 to 1.0
 */
data class Transition(
    val id: String,
    val name: String,
    val category: TransitionCategory,
    val shaderCode: String,
    val defaultDurationMs: Long = 500L,
    val isPremium: Boolean = false
)

/**
 * TransitionCategory - Groups transitions by visual style
 * Order determines display order in UI (Creative, Cinematic, 3D first)
 */
enum class TransitionCategory(val displayName: String) {
    CREATIVE("Creative"),
    CINEMATIC("Cinematic"),
    THREE_D("3D Effects"),
    FADE("Fade"),
    SLIDE("Slide"),
    WIPE("Wipe"),
    ZOOM("Zoom"),
    ROTATE("Rotate"),
    BLUR("Blur"),
    GEOMETRIC("Geometric")
}

/**
 * TransitionSet - A collection of transitions with a common theme
 */
data class TransitionSet(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String = "",
    val isPremium: Boolean = false,
    val isActive: Boolean = true,
    val transitions: List<Transition> = emptyList()
)
