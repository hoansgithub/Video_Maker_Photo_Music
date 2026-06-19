package com.videomaker.aimusic.modules.editor

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.media.audio.HookStartTimePolicy

internal fun resolveDefaultMusicTrimStartMs(song: MusicSong?): Long {
    return HookStartTimePolicy.resolve(
        hookStartTimeMs = song?.hookStartTimes?.firstOrNull() ?: 0L,
        durationMs = song?.durationMs?.toLong()
    )
}
