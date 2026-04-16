package com.videomaker.aimusic.core.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.videomaker.aimusic.core.data.local.PreferencesManager

class NotificationPermissionCoordinator(
    private val preferencesManager: PreferencesManager
) {
    // In-memory process-lifetime flag:
    // reset only when process is killed and recreated.
    private var hasConsumedPermissionUiSlotInCurrentProcess = false

    private fun tryConsumePermissionUiSlotInCurrentProcess(): Boolean {
        if (hasConsumedPermissionUiSlotInCurrentProcess) return false
        hasConsumedPermissionUiSlotInCurrentProcess = true
        return true
    }

    fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldRequestHomeFirstTimePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isNotificationGranted(context)) {
            onSystemPermissionGranted()
            return false
        }
        if (preferencesManager.getNotificationPermissionRequestCount() != 0) return false
        return tryConsumePermissionUiSlotInCurrentProcess()
    }

    fun shouldShowExportContextualPopup(context: Context): Boolean {
        if (isNotificationGranted(context)) {
            onSystemPermissionGranted()
            return false
        }
        return tryConsumePermissionUiSlotInCurrentProcess()
    }

    fun shouldShowTemplatePreviewerContextualPopup(context: Context): Boolean {
        if (isNotificationGranted(context)) {
            onSystemPermissionGranted()
            return false
        }
        return tryConsumePermissionUiSlotInCurrentProcess()
    }

    fun canRequestSystemPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isNotificationGranted(context)) return false
        return !preferencesManager.isNotificationPermissionBlockedAfterSecondDeny()
    }

    fun shouldShowSettingsGuide(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isNotificationGranted(context)) return false
        return preferencesManager.isNotificationPermissionBlockedAfterSecondDeny()
    }

    fun onSystemPermissionGranted() {
        preferencesManager.clearNotificationPermissionStateOnGrant()
    }

    fun onSystemPermissionResult(granted: Boolean) {
        val newRequestCount = preferencesManager.getNotificationPermissionRequestCount() + 1
        preferencesManager.setNotificationPermissionRequestCount(newRequestCount)
        if (granted) {
            onSystemPermissionGranted()
        } else if (newRequestCount >= 2) {
            preferencesManager.setNotificationPermissionBlockedAfterSecondDeny(true)
        }
    }

    fun buildOpenAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
    }
}
