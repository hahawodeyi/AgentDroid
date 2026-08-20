package com.appia.ai.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.appia.ai.service.ServiceBridge

object PermissionChecker {

    fun isGranted(context: Context, permission: ToolPermission?): Boolean {
        permission ?: return true
        if (permission.manifestPermission == ToolPermission.ACCESSIBILITY) {
            return ServiceBridge.isReady.value
        }
        if (permission.manifestPermission == Manifest.permission.POST_NOTIFICATIONS) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, permission.manifestPermission) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        return ContextCompat.checkSelfPermission(context, permission.manifestPermission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
