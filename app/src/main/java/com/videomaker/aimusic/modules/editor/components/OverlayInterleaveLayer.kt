package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.modules.editor.TextOverlayViewModel
import com.videomaker.aimusic.modules.editor.overlay.OverlayRun
import com.videomaker.aimusic.modules.editor.overlay.buildOverlayRuns

/**
 * Renders text overlays and stickers interleaved by shared z-index (add order).
 *
 * Stacking (bottom → top):
 *  1. [StickerGestureLayer] — ONE full-screen touch handler for ALL stickers. It draws nothing,
 *     so it never blocks the layers above; and being a single layer, no sticker run can block
 *     another (the bug this fixes: a newer sticker's full-screen layer swallowed taps meant for
 *     older stickers AND for text).
 *  2. Interleaved DRAW: per z-run, either draw-only sticker images ([StickerImagesLayer]) or the
 *     text canvas. Emitting one layer per run keeps draw order == z order (parity with the export
 *     in CompositionFactory). The text canvas has only small per-item touch nodes, so it doesn't
 *     block the gesture layer either; its taps select/edit text as before.
 *  3. [StickerChromeLayer] — the selection box + handles for the selected sticker, on top.
 *
 * The host [Box] is sized by the caller to the video rect; every layer uses [fillMaxSize] so they
 * share identical coordinates (required for z/position parity with the export).
 */
@Composable
fun OverlayInterleaveLayer(
    textOverlayViewModel: TextOverlayViewModel,
    stickers: List<StickerPlacement>,
    selectedStickerId: String?,
    onStickerSelect: (String?) -> Unit,
    onStickerTransform: (StickerPlacement) -> Unit,
    onStickerDelete: (String) -> Unit,
    onStickerDoubleTapTopMost: () -> Unit,
    onStickerRequestEdit: (String) -> Unit,
    stickerInteractive: Boolean,
    onDoubleTapText: (String) -> Unit,
    // Fired on a single tap of a text overlay — lets the host switch panels (e.g. close the
    // sticker panel and open the text panel) when the other overlay type is tapped.
    onTextTapped: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textOverlays by textOverlayViewModel.textOverlays.collectAsStateWithLifecycle()
    val selectedTextId by textOverlayViewModel.selectedTextOverlayId.collectAsStateWithLifecycle()
    val fontPresets by textOverlayViewModel.fontPresets.collectAsStateWithLifecycle()
    val downloadedFontIds by textOverlayViewModel.downloadedFontIds.collectAsStateWithLifecycle()

    val runs = buildOverlayRuns(textOverlays, stickers)

    // Aspect ratios captured as sticker images load — shared so the gesture layer (which renders
    // no images) and the chrome layer have the right box geometry for every sticker.
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }
    // In-progress transform of the sticker being manipulated. Written by the gesture layer, read
    // by the image + chrome layers so a drag/pinch previews instantly without committing to the
    // editor 60x/second. Hoisted here (vs inside one run) so it survives the draw/gesture split.
    var liveSticker by remember { mutableStateOf<StickerPlacement?>(null) }
    val selectedSticker = stickers.firstOrNull { it.instanceId == selectedStickerId }

    Box(modifier = modifier.fillMaxSize()) {
        // 1) Single full-screen gesture handler for ALL stickers (bottom, draws nothing).
        StickerGestureLayer(
            stickers = stickers,
            selectedInstanceId = selectedStickerId,
            aspectRatios = aspectRatios,
            onSelect = onStickerSelect,
            onTransform = onStickerTransform,
            onDelete = onStickerDelete,
            onDoubleTapTopMost = onStickerDoubleTapTopMost,
            onRequestEdit = onStickerRequestEdit,
            onLiveChange = { liveSticker = it },
            interactive = stickerInteractive,
            modifier = Modifier.fillMaxSize()
        )

        // 2) Interleaved draw: sticker images + text, in z order (draw-only sticker images never
        //    block the gesture layer; text keeps its own small touch nodes).
        runs.forEach { run ->
            when (run) {
                is OverlayRun.TextRun -> {
                    TextOverlayCanvasContent(
                        textOverlays = run.overlays,
                        selectedId = selectedTextId,
                        fontPresets = fontPresets,
                        downloadedFontIds = downloadedFontIds,
                        onLoadFont = textOverlayViewModel::downloadFontIfNeeded,
                        onSelectText = { id ->
                            textOverlayViewModel.setSelectedTextOverlayId(id)
                            if (id != null) onTextTapped(id)
                        },
                        onUpdateText = { id, x, y, rot, sc ->
                            textOverlayViewModel.updateTextOverlay(
                                id = id,
                                xPercentage = x,
                                yPercentage = y,
                                rotation = rot,
                                scale = sc
                            )
                        },
                        onRemoveText = textOverlayViewModel::removeTextOverlay,
                        onDoubleTapText = onDoubleTapText,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is OverlayRun.StickerRun -> {
                    StickerImagesLayer(
                        stickers = run.stickers,
                        live = liveSticker,
                        aspectRatios = aspectRatios,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 3) Selection chrome for the selected sticker, on top (only while editing stickers).
        if (stickerInteractive) {
            StickerChromeLayer(
                selected = selectedSticker,
                live = liveSticker,
                aspectRatios = aspectRatios,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
