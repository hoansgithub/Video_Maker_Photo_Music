package com.videomaker.aimusic.core.notification

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.videomaker.aimusic.core.data.local.PreferencesManager

class AppSessionTracker(
    private val preferencesManager: PreferencesManager
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        preferencesManager.bumpAppSessionId()
    }

    override fun onStop(owner: LifecycleOwner) {
        preferencesManager.setLastAppBackgroundAtMs(System.currentTimeMillis())
    }
}

