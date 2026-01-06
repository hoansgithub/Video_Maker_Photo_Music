package com.aimusic.videoeditor.media.library

import com.aimusic.videoeditor.R
import com.aimusic.videoeditor.domain.model.TransitionSet

/**
 * TransitionSetLibrary - Provides all available transition sets
 *
 * Each set contains multiple transitions with a common visual theme.
 * When a set is selected, transitions are cycled through for variety.
 */
object TransitionSetLibrary {

    // Get transitions from the shader library
    private val shaderLib = TransitionShaderLibrary

    private val sets by lazy {
        listOf(
            TransitionSet(
                id = "classic",
                name = "Classic",
                description = "Timeless transitions: fade, dissolve, wipes",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false,
                transitions = listOfNotNull(
                    shaderLib.getById("fade"),
                    shaderLib.getById("fade_color"),
                    shaderLib.getById("fade_grayscale"),
                    shaderLib.getById("wipe_left"),
                    shaderLib.getById("wipe_right"),
                    shaderLib.getById("wipe_diagonal"),
                    shaderLib.getById("slide_left"),
                    shaderLib.getById("slide_right")
                )
            ),
            TransitionSet(
                id = "geometric",
                name = "Geometric",
                description = "Shape-based: circle, diamond, star",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false,
                transitions = listOfNotNull(
                    shaderLib.getById("circle"),
                    shaderLib.getById("diamond"),
                    shaderLib.getById("heart"),
                    shaderLib.getById("star"),
                    shaderLib.getById("blinds")
                )
            ),
            TransitionSet(
                id = "cinematic",
                name = "Cinematic",
                description = "Movie-style: light leak, film burn, doorway",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = true,
                transitions = listOfNotNull(
                    shaderLib.getById("light_leak"),
                    shaderLib.getById("doorway"),
                    shaderLib.getById("page_flip"),
                    shaderLib.getById("film_burn")
                )
            ),
            TransitionSet(
                id = "creative",
                name = "Creative",
                description = "Artistic: pixelize, glitch, swirl, ripple",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = true,
                transitions = listOfNotNull(
                    shaderLib.getById("pixelize"),
                    shaderLib.getById("ripple"),
                    shaderLib.getById("swirl"),
                    shaderLib.getById("glitch")
                )
            ),
            TransitionSet(
                id = "minimal",
                name = "Minimal",
                description = "Subtle, elegant transitions",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false,
                transitions = listOfNotNull(
                    shaderLib.getById("fade"),
                    shaderLib.getById("blur"),
                    shaderLib.getById("zoom_crossover")
                )
            ),
            TransitionSet(
                id = "dynamic",
                name = "Dynamic",
                description = "High-energy transitions",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false,
                transitions = listOfNotNull(
                    shaderLib.getById("zoom_in"),
                    shaderLib.getById("zoom_out"),
                    shaderLib.getById("zoom_rotate"),
                    shaderLib.getById("slide_up"),
                    shaderLib.getById("slide_down"),
                    shaderLib.getById("rotate")
                )
            ),
            TransitionSet(
                id = "3d",
                name = "3D Effects",
                description = "3D cube, flip, page curl, fold",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false, // TODO: Set back to true for production
                transitions = listOfNotNull(
                    shaderLib.getById("cube_3d"),
                    shaderLib.getById("flip_3d"),
                    shaderLib.getById("fold_3d"),
                    shaderLib.getById("page_curl_3d"),
                    shaderLib.getById("roll_3d"),
                    shaderLib.getById("revolve_3d")
                )
            ),
            TransitionSet(
                id = "retro",
                name = "Retro & Glitch",
                description = "VHS, glitch, chromatic, TV static effects",
                thumbnailRes = R.drawable.ic_launcher_foreground,
                isPremium = false, // TODO: Set back to true for production
                transitions = listOfNotNull(
                    shaderLib.getById("glitch_memories"),
                    shaderLib.getById("vhs"),
                    shaderLib.getById("chromatic"),
                    shaderLib.getById("tv_static"),
                    shaderLib.getById("dreamy"),
                    shaderLib.getById("kaleidoscope"),
                    shaderLib.getById("mosaic_tiles"),
                    shaderLib.getById("crosshatch"),
                    shaderLib.getById("luminance_melt"),
                    shaderLib.getById("wind"),
                    shaderLib.getById("water_drop"),
                    shaderLib.getById("squares_wire")
                )
            )
        )
    }

    fun getAll(): List<TransitionSet> = sets

    fun getById(id: String): TransitionSet? = sets.find { it.id == id }

    fun getDefault(): TransitionSet = sets.first()

    fun getFree(): List<TransitionSet> = sets.filter { !it.isPremium }

    fun getPremium(): List<TransitionSet> = sets.filter { it.isPremium }
}
