package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.domain.model.StickerPlacement
import com.videomaker.aimusic.modules.editor.EditorViewModel
import com.videomaker.aimusic.modules.editor.overlay.OverlayRun
import com.videomaker.aimusic.modules.editor.overlay.buildOverlayRuns

/**
 * Renders text overlays and stickers interleaved by shared z-index (add order).
 *
 * Each contiguous same-type "run" is one child layer; children compose in z order so draw
 * order == z order. Because a [StickerOverlayLayer] run only consumes a touch when it hits
 * one of *its* stickers/handles, taps fall through non-hitting sticker runs to the text
 * beneath — keeping text tappable while stickers are present.
 *
 * The host [Box] is sized by the caller to the video rect; every run uses [fillMaxSize] so
 * all runs share identical coordinates (required for z/position parity with the export).
 */
@Composable
fun OverlayInterleaveLayer(
    editorViewModel: EditorViewModel,
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
    val textOverlays by editorViewModel.textOverlays.collectAsStateWithLifecycle()
    val selectedTextId by editorViewModel.selectedTextOverlayId.collectAsStateWithLifecycle()
    val fontPresets by editorViewModel.fontPresets.collectAsStateWithLifecycle()
    val downloadedFontIds by editorViewModel.downloadedFontIds.collectAsStateWithLifecycle()

    val runs = buildOverlayRuns(textOverlays, stickers)

    Box(modifier = modifier.fillMaxSize()) {
        runs.forEach { run ->
            when (run) {
                is OverlayRun.TextRun -> {
                    TextOverlayCanvasContent(
                        textOverlays = run.overlays,
                        selectedId = selectedTextId,
                        fontPresets = fontPresets,
                        downloadedFontIds = downloadedFontIds,
                        onLoadFont = editorViewModel::downloadFontIfNeeded,
                        onSelectText = { id ->
                            editorViewModel.setSelectedTextOverlayId(id)
                            if (id != null) onTextTapped(id)
                        },
                        onUpdateText = { id, x, y, rot, sc ->
                            editorViewModel.updateTextOverlay(
                                id = id,
                                xPercentage = x,
                                yPercentage = y,
                                rotation = rot,
                                scale = sc
                            )
                        },
                        onRemoveText = editorViewModel::removeTextOverlay,
                        onDoubleTapText = onDoubleTapText,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is OverlayRun.StickerRun -> {
                    StickerOverlayLayer(
                        stickers = run.stickers,
                        selectedInstanceId = selectedStickerId,
                        onSelect = onStickerSelect,
                        onTransform = onStickerTransform,
                        onDelete = onStickerDelete,
                        onDoubleTapTopMost = onStickerDoubleTapTopMost,
                        onRequestEdit = onStickerRequestEdit,
                        interactive = stickerInteractive,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
