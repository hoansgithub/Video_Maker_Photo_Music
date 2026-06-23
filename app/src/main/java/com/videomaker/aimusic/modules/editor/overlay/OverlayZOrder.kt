package com.videomaker.aimusic.modules.editor.overlay

import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.domain.model.TextOverlay

/** An interleaved overlay rendering layer: a contiguous run of same-type items in z order. */
sealed interface OverlayRun {
    data class TextRun(val overlays: List<TextOverlay>) : OverlayRun
    data class StickerRun(val stickers: List<StickerPlacement>) : OverlayRun
}

// Internal unified item used only for sorting.
private sealed interface ZItem {
    val z: Int
    val typeRank: Int // tie-break: text(0) below sticker(1)

    data class T(val o: TextOverlay) : ZItem {
        override val z = o.zIndex
        override val typeRank = 0
    }

    data class S(val p: StickerPlacement) : ZItem {
        override val z = p.zIndex
        override val typeRank = 1
    }
}

/**
 * Merge text + stickers into z-ordered, maximal contiguous same-type runs.
 * Stable: equal (z, typeRank) keeps original list order.
 */
fun buildOverlayRuns(
    textOverlays: List<TextOverlay>,
    stickers: List<StickerPlacement>
): List<OverlayRun> {
    val items = ArrayList<ZItem>(textOverlays.size + stickers.size)
    textOverlays.forEach { items.add(ZItem.T(it)) }
    stickers.forEach { items.add(ZItem.S(it)) }
    if (items.isEmpty()) return emptyList()

    val sorted = items.sortedWith(compareBy({ it.z }, { it.typeRank }))

    val runs = ArrayList<OverlayRun>()
    var i = 0
    while (i < sorted.size) {
        val isText = sorted[i] is ZItem.T
        var j = i
        while (j < sorted.size && (sorted[j] is ZItem.T) == isText) j++
        val slice = sorted.subList(i, j)
        runs.add(
            if (isText) OverlayRun.TextRun(slice.map { (it as ZItem.T).o })
            else OverlayRun.StickerRun(slice.map { (it as ZItem.S).p })
        )
        i = j
    }
    return runs
}

/** Highest z across both lists, or -1 if both are empty. */
fun combinedMaxZIndex(
    textOverlays: List<TextOverlay>,
    stickers: List<StickerPlacement>
): Int {
    val maxText = textOverlays.maxOfOrNull { it.zIndex } ?: Int.MIN_VALUE
    val maxSticker = stickers.maxOfOrNull { it.zIndex } ?: Int.MIN_VALUE
    val max = maxOf(maxText, maxSticker)
    return if (max == Int.MIN_VALUE) -1 else max
}
