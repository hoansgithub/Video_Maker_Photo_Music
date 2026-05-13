package com.videomaker.aimusic.modules.picker

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Channel-based cache for passing selected asset URIs from AssetPicker back to EditorScreen
 * when in editing mode (ImagesBottomSheet flow).
 *
 * Uses Channel for one-time events (Google pattern):
 * - Buffered: Won't lose emissions if collector is temporarily inactive
 * - Thread-safe: Multiple senders/receivers supported
 * - No replay: Each selection consumed once
 */
internal object AssetSelectionCache {
    private val _selectionChannel = Channel<List<String>>(Channel.BUFFERED)

    val selectionFlow = _selectionChannel.receiveAsFlow()

    fun setSelection(uris: List<String>) {
        _selectionChannel.trySend(uris)
    }
}
