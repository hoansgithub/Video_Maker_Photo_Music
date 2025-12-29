package co.alcheclub.video.maker.photo.music.media.library

import co.alcheclub.video.maker.photo.music.R
import co.alcheclub.video.maker.photo.music.domain.model.TransitionSet

/**
 * TransitionSetLibrary - Provides all available transition sets
 *
 * Each set contains 20+ transitions with a common visual theme.
 */
object TransitionSetLibrary {

    private val sets = listOf(
        TransitionSet(
            id = "classic",
            name = "Classic",
            description = "Timeless transitions: fade, dissolve, wipes",
            thumbnailRes = R.drawable.ic_launcher_foreground, // TODO: Replace with actual thumbnail
            isPremium = false
        ),
        TransitionSet(
            id = "geometric",
            name = "Geometric",
            description = "Shape-based: circle, diamond, star, hexagon",
            thumbnailRes = R.drawable.ic_launcher_foreground,
            isPremium = false
        ),
        TransitionSet(
            id = "cinematic",
            name = "Cinematic",
            description = "Movie-style: cube, page curl, flip, fold",
            thumbnailRes = R.drawable.ic_launcher_foreground,
            isPremium = true
        ),
        TransitionSet(
            id = "creative",
            name = "Creative",
            description = "Artistic: pixelize, glitch, burn, morph",
            thumbnailRes = R.drawable.ic_launcher_foreground,
            isPremium = true
        ),
        TransitionSet(
            id = "minimal",
            name = "Minimal",
            description = "Subtle, elegant transitions for clean videos",
            thumbnailRes = R.drawable.ic_launcher_foreground,
            isPremium = false
        ),
        TransitionSet(
            id = "dynamic",
            name = "Dynamic",
            description = "High-energy transitions for action content",
            thumbnailRes = R.drawable.ic_launcher_foreground,
            isPremium = false
        )
    )

    fun getAll(): List<TransitionSet> = sets

    fun getById(id: String): TransitionSet? = sets.find { it.id == id }

    fun getDefault(): TransitionSet = sets.first()

    fun getFree(): List<TransitionSet> = sets.filter { !it.isPremium }

    fun getPremium(): List<TransitionSet> = sets.filter { it.isPremium }
}
