package com.example.phone

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Countdown Timer controller for MAX using Android's standard [AlarmClock.ACTION_SET_TIMER].
 *
 * Keeps battery usage minimal by delegating timing to the system Clock app and only
 * activating when explicitly requested by the user.
 */
class TimerManager(context: Context) {
    private val appContext = context.applicationContext

    private val _lastTimerSeconds = MutableStateFlow<Int?>(null)
    val lastTimerSeconds: StateFlow<Int?> = _lastTimerSeconds.asStateFlow()

    /**
     * Starts a countdown timer for [durationSeconds] strictly upon user request.
     */
    fun startTimer(
        durationSeconds: Int,
        label: String? = null,
        isUserInitiated: Boolean = true
    ): Result<Int> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Timer creation requires explicit user request."))
        }
        if (durationSeconds <= 0) {
            return Result.failure(IllegalArgumentException("Timer duration must be positive."))
        }

        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                if (!label.isNullOrBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            _lastTimerSeconds.value = durationSeconds
            durationSeconds
        }
    }

    fun release() {
        _lastTimerSeconds.value = null
    }
}
