package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Independent manager responsible for querying Android runtime permission states
 * and determining which permissions are missing for microphone, contacts, phone, SMS, camera, and notifications.
 */
class PermissionManager(context: Context) {
    private val appContext = context.applicationContext

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasMicrophonePermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasContactsPermission(): Boolean = hasPermission(Manifest.permission.READ_CONTACTS)

    fun hasCallPermission(): Boolean = hasPermission(Manifest.permission.CALL_PHONE)

    fun hasSmsPermission(): Boolean = hasPermission(Manifest.permission.SEND_SMS)

    fun hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)

    fun missingPermissionsForBackgroundWake(): List<String> {
        val needed = mutableListOf<String>()
        if (!hasMicrophonePermission()) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed
    }

    fun computePermissionHealth(): Map<String, Boolean> {
        val map = linkedMapOf(
            Manifest.permission.RECORD_AUDIO to hasMicrophonePermission(),
            Manifest.permission.READ_CONTACTS to hasContactsPermission(),
            Manifest.permission.CALL_PHONE to hasCallPermission(),
            Manifest.permission.SEND_SMS to hasSmsPermission(),
            Manifest.permission.CAMERA to hasCameraPermission()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            map[Manifest.permission.POST_NOTIFICATIONS] =
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
        return map
    }
}
