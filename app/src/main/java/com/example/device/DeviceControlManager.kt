package com.example.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceControlManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchEnabled = false

    fun openInstalledApp(appQuery: String): Result<String> {
        val query = appQuery.trim().lowercase()
        if (query.isEmpty()) return Result.failure(IllegalArgumentException("App name is empty"))

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val apps = resolveInfos.map { info ->
            val label = info.loadLabel(pm).toString()
            label to info.activityInfo.packageName
        }

        // Exact match -> startsWith -> contains -> fuzzy Bengali/common alias
        val normalizedQuery = normalizeAppAlias(query)
        val matched = apps.firstOrNull { (label, _) -> label.equals(normalizedQuery, ignoreCase = true) }
            ?: apps.firstOrNull { (label, _) -> label.lowercase().startsWith(normalizedQuery) }
            ?: apps.firstOrNull { (label, _) -> label.lowercase().contains(normalizedQuery) }
            ?: apps.firstOrNull { (label, pkg) ->
                pkg.lowercase().contains(normalizedQuery.replace(" ", "")) ||
                    normalizedQuery.contains(label.lowercase())
            }

        if (matched == null) {
            return Result.failure(IllegalStateException("App '$appQuery' not found on device."))
        }

        val launchIntent = pm.getLaunchIntentForPackage(matched.second)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return Result.failure(IllegalStateException("Cannot launch '${matched.first}'."))

        context.startActivity(launchIntent)
        return Result.success(matched.first)
    }

    private fun normalizeAppAlias(query: String): String {
        return when (query) {
            "ইউটিউব" -> "youtube"
            "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে" -> "whatsapp"
            "ক্রোম" -> "chrome"
            "গ্যালারি", "ফটোস" -> "photos"
            "ক্যালকুলেটর" -> "calculator"
            "ঘড়ি", "ক্লক" -> "clock"
            "ফোন", "ডায়ালার" -> "phone"
            "মেসেজ", "মেসেজেস" -> "messages"
            "ম্যাপ", "ম্যাপস", "গুগল ম্যাপ" -> "maps"
            "ফেসবুক" -> "facebook"
            else -> query
        }
    }

    fun adjustVolume(direction: Int): Int {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        return getVolumePercent()
    }

    fun setVolumePercent(percent: Int): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            target,
            AudioManager.FLAG_SHOW_UI
        )
        return getVolumePercent()
    }

    fun getVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return ((current * 100f) / max).toInt()
    }

    fun openSettings(section: String?): Boolean {
        val action = when (section?.lowercase()) {
            "wifi", "wi-fi", "ওয়াইফাই" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "ব্লুটুথ" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "audio", "volume", "সাউন্ড" -> Settings.ACTION_SOUND_SETTINGS
            "display", "brightness", "ডিসপ্লে" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery", "ব্যাটারি" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "application", "অ্যাপস" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
    }

    fun toggleFlashlight(): Result<Boolean> {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.failure(IllegalStateException("No flashlight available on this device."))

            torchEnabled = !torchEnabled
            cameraManager.setTorchMode(cameraId, torchEnabled)
            Result.success(torchEnabled)
        } catch (e: Exception) {
            Log.e("DeviceControlManager", "Failed to toggle flashlight", e)
            Result.failure(e)
        }
    }

    fun getCurrentTimeMessage(isBengali: Boolean): String {
        val now = Date()
        return if (isBengali) {
            val bnLocale = Locale.forLanguageTag("bn-BD")
            val timeStr = SimpleDateFormat("h:mm a", bnLocale).format(now)
            val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", bnLocale).format(now)
            "এখন সময় $timeStr। আজ $dateStr।"
        } else {
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
            "It's currently $timeStr on $dateStr."
        }
    }

    fun getBatteryStatusMessage(isBengali: Boolean): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = batteryManager.isCharging
        return if (isBengali) {
            val state = if (charging) "চার্জিং হচ্ছে" else "চার্জে নেই"
            "আপনার ফোনের ব্যাটারি এখন $level শতাংশ ($state)।"
        } else {
            val state = if (charging) "currently charging" else "not charging"
            "Your battery is at $level percent and $state."
        }
    }
}
