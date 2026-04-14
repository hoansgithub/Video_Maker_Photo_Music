package com.videomaker.aimusic.core.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.videomaker.aimusic.core.data.local.PreferencesManager

class MediaPermissionCoordinator(
    private val preferencesManager: PreferencesManager
) {

    private var hasShownLimitedUpsellInCurrentSession = false

    fun isFullPermissionGranted(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isBlockedAfterSecondNonFull(): Boolean {
        return preferencesManager.isMediaFullPermissionBlockedAfterSecondDeny()
    }

    fun canRequestSystemPermission(context: Context): Boolean {
        if (isFullPermissionGranted(context)) {
            onFullPermissionGranted()
            return false
        }
        return !isBlockedAfterSecondNonFull()
    }

    fun shouldShowSettingsGuide(context: Context): Boolean {
        if (isFullPermissionGranted(context)) {
            onFullPermissionGranted()
            return false
        }
        return isBlockedAfterSecondNonFull()
    }

    fun hasShownLimitedUpsellThisSession(): Boolean = hasShownLimitedUpsellInCurrentSession

    fun markLimitedUpsellShownInCurrentSession() {
        hasShownLimitedUpsellInCurrentSession = true
    }

    fun onSystemPermissionResult(fullGranted: Boolean) {
        if (fullGranted) {
            onFullPermissionGranted()
            return
        }

        val newRequestCount = preferencesManager.getMediaFullPermissionRequestCount() + 1
        preferencesManager.setMediaFullPermissionRequestCount(newRequestCount)
        if (newRequestCount >= 2) {
            preferencesManager.setMediaFullPermissionBlockedAfterSecondDeny(true)
        }
    }

    fun onFullPermissionGranted() {
        preferencesManager.clearMediaFullPermissionStateOnGrant()
        hasShownLimitedUpsellInCurrentSession = false
    }

    fun buildOpenAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
    }
}
