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
 * EffectSet - A collection of transitions with a common theme
 *
 * @param transitionIds Raw transition IDs from Supabase (preserved for download logic).
 *        Unlike [transitions], this list is not filtered by availability —
 *        it contains all IDs the effect set references, even if not yet downloaded.
 * @param transitions Resolved Transition objects (only contains transitions available locally or in remote cache).
 */
data class EffectSet(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String = "",
    val isPremium: Boolean = false,
    val isActive: Boolean = true,
    val transitionIds: List<String> = emptyList(),
    val transitions: List<Transition> = emptyList(),
    val sortOrder: Int = 0,
    val isNew: Boolean = false
)

