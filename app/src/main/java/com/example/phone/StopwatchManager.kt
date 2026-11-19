package com.example.phone

import android.os.SystemClock
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight, zero-wakelock Stopwatch controller for MAX.
 *
 * Uses [SystemClock.elapsedRealtime] timestamps instead of continuous background polling,
 * ensuring zero battery drain while accurately tracking elapsed time and laps.
 */
class StopwatchManager(
    private val clockProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var startMarkMs: Long = 0L
    private var accumulatedMs: Long = 0L

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _lapsMs = MutableStateFlow<List<Long>>(emptyList())
    val lapsMs: StateFlow<List<Long>> = _lapsMs.asStateFlow()

    fun start(isUserInitiated: Boolean = true): Boolean {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return false
        }
        if (_isRunning.value) return true
        startMarkMs = clockProvider()
        _isRunning.value = true
        return true
    }

    fun pause(isUserInitiated: Boolean = true): Long {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return getCurrentElapsedMs()
        }
        if (_isRunning.value) {
            accumulatedMs += (clockProvider() - startMarkMs).coerceAtLeast(0L)
            _isRunning.value = false
        }
        val total = accumulatedMs
        _elapsedMs.value = total
        return total
    }

    fun resume(isUserInitiated: Boolean = true): Boolean = start(isUserInitiated)

    fun lap(isUserInitiated: Boolean = true): List<Long> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return _lapsMs.value
        }
        val current = getCurrentElapsedMs()
        _lapsMs.value = _lapsMs.value + current
        return _lapsMs.value
    }

    fun reset(isUserInitiated: Boolean = true) {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return
        }
        startMarkMs = 0L
        accumulatedMs = 0L
        _isRunning.value = false
        _elapsedMs.value = 0L
        _lapsMs.value = emptyList()
    }

    fun getCurrentElapsedMs(): Long {
        val total = if (_isRunning.value) {
            accumulatedMs + (clockProvider() - startMarkMs).coerceAtLeast(0L)
        } else {
            accumulatedMs
        }
        _elapsedMs.value = total
        return total
    }

    fun formatElapsed(millis: Long = getCurrentElapsedMs()): String {
        val totalSec = millis / 1000
        val hrs = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        val tenths = (millis % 1000) / 100
        return if (hrs > 0) {
            String.format("%d:%02d:%02d.%d", hrs, mins, secs, tenths)
        } else {
            String.format("%02d:%02d.%d", mins, secs, tenths)
        }
    }

    fun release() {
        startMarkMs = 0L
        accumulatedMs = 0L
        _isRunning.value = false
        _elapsedMs.value = 0L
        _lapsMs.value = emptyList()
    }
}
