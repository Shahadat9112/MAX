package com.example.phone

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.permissions.PrivacyController

/**
 * Alarm controller for MAX using Android's standard [AlarmClock] intents.
 *
 * Rules:
 * - Never creates, edits, or dismisses alarms automatically.
 * - Always respects manufacturer Clock app variations by launching with `EXTRA_SKIP_UI = false`
 *   unless explicitly confirmed, keeping user visibility and final control intact.
 */
class AlarmManager(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Opens the system Clock alarm setter pre-filled with the requested [hour] (0-23),
     * [minute] (0-59), and optional [label], strictly upon explicit user request.
     */
    fun setAlarm(
        hour: Int,
        minute: Int,
        label: String? = null,
        isUserInitiated: Boolean = true
    ): Result<String> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Alarm creation requires explicit user request."))
        }
        if (hour !in 0..23 || minute !in 0..59) {
            return Result.failure(IllegalArgumentException("Invalid alarm time: $hour:$minute"))
        }

        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (!label.isNullOrBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }
                // Keep UI visible so the user retains final confirmation across OEM Clock apps
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            String.format("%02d:%02d", hour, minute)
        }
    }

    /**
     * Opens the device's existing Alarms screen without changing any alarm state.
     */
    fun showAlarms(isUserInitiated: Boolean = true): Result<Unit> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Opening alarms requires explicit user request."))
        }
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }
}
