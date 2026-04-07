package com.videomaker.aimusic.core.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.videomaker.aimusic.BuildConfig
import java.util.Locale

/**
 * Provides device information for feedback submission.
 * Uses Android ID as anonymous device identifier.
 */
class DeviceInfoProvider(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Unique device identifier (Android ID).
     * Unique per device + app signing key. No permissions required.
     */
    val deviceId: String by lazy { getAndroidId() }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    /** Device model name (e.g., "Google Pixel 7 Pro") */
    val deviceModel: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    /** Android OS version string (e.g., "14") */
    val osVersion: String
        get() = Build.VERSION.RELEASE

    /** Current locale (e.g., "en_US") */
    val locale: String
        get() = Locale.getDefault().toString()

    /** App version name from BuildConfig (e.g., "1.2.0") */
    val appVersion: String
        get() = BuildConfig.VERSION_NAME

    /** App version code from BuildConfig (e.g., 5) */
    val appVersionCode: Int
        get() = BuildConfig.VERSION_CODE
}
