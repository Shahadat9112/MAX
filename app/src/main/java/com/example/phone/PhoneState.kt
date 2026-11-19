package com.example.phone

/**
 * Operational states for MAX's Phone Control System.
 *
 * Ensures every phone action (calls, SMS, contacts, alarms, timers, stopwatch, calculator, flashlight)
 * stays observable, cancellable, and strictly user-initiated.
 */
enum class PhoneModuleStatus {
    IDLE,
    AWAITING_CONFIRMATION,
    PERMISSION_REQUIRED,
    CALLING,
    MESSAGING,
    SEARCHING_CONTACTS,
    ALARM_ACTIVE,
    TIMER_RUNNING,
    STOPWATCH_RUNNING,
    STOPWATCH_PAUSED,
    CALCULATING,
    FLASHLIGHT_ON,
    ERROR
}

/**
 * Immutable snapshot of the [PhoneManager] runtime state.
 */
data class PhoneState(
    val status: PhoneModuleStatus = PhoneModuleStatus.IDLE,
    val isFlashlightOn: Boolean = false,
    val isStopwatchRunning: Boolean = false,
    val stopwatchElapsedMs: Long = 0L,
    val stopwatchLapsMs: List<Long> = emptyList(),
    val activeTimerSeconds: Int? = null,
    val activeAlarmSummary: String? = null,
    val lastCalculationResult: String? = null,
    val lastActionMessage: String? = null
)
