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

    private var hasShownExportPopupInCurrentSession = false
    private var hasShownTemplatePreviewerPopupInCurrentSession = false

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
        return preferencesManager.getNotificationPermissionRequestCount() == 0
    }

    fun shouldShowExportContextualPopup(context: Context): Boolean {
        if (isNotificationGranted(context)) {
            onSystemPermissionGranted()
            return false
        }
        return !hasShownExportPopupInCurrentSession
    }

    fun markExportContextualPopupShown() {
        hasShownExportPopupInCurrentSession = true
    }

    fun shouldShowTemplatePreviewerContextualPopup(context: Context): Boolean {
        if (isNotificationGranted(context)) {
            onSystemPermissionGranted()
            return false
        }
        return !hasShownTemplatePreviewerPopupInCurrentSession
    }

    fun markTemplatePreviewerContextualPopupShown() {
        hasShownTemplatePreviewerPopupInCurrentSession = true
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
        hasShownExportPopupInCurrentSession = false
        hasShownTemplatePreviewerPopupInCurrentSession = false
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
