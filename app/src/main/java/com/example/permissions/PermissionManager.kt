package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Independent manager responsible for querying Android runtime permission states
 * and determining which permissions are missing for microphone, contacts, phone,
 * SMS, camera, gallery/media, files, location, notifications, and internet.
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

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasFilePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasInternetPermission(): Boolean = hasPermission(Manifest.permission.INTERNET)

    fun missingPermissionsForBackgroundWake(): List<String> {
        val needed = mutableListOf<String>()
        if (!hasMicrophonePermission()) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
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
            Manifest.permission.CAMERA to hasCameraPermission(),
            Manifest.permission.INTERNET to hasInternetPermission(),
            Manifest.permission.ACCESS_FINE_LOCATION to hasLocationPermission()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            map[Manifest.permission.POST_NOTIFICATIONS] = hasNotificationPermission()
            map[Manifest.permission.READ_MEDIA_IMAGES] = hasMediaPermission()
        } else {
            map[Manifest.permission.READ_EXTERNAL_STORAGE] = hasFilePermission()
        }
        return map
    }
}
