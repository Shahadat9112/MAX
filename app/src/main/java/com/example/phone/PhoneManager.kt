package com.example.phone

import android.content.Context
import com.example.domain.ContactMatch
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central facade for MAX's Phone Control System (`phone/` package).
 *
 * Composes independent sub-managers:
 * - [CallManager]
 * - [SMSManager]
 * - [ContactsManager]
 * - [AlarmManager]
 * - [TimerManager]
 * - [StopwatchManager]
 * - [CalculatorManager]
 * - [FlashlightManager]
 *
 * Enforces explicit user confirmation, permission checks via [PermissionManager],
 * and strict privacy guarantees (never reads contacts, call logs, or SMS automatically).
 */
class PhoneManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext

    val callManager = CallManager(appContext, permissionManager)
    val smsManager = SMSManager(appContext, permissionManager)
    val contactsManager = ContactsManager(appContext, permissionManager)
    val alarmManager = AlarmManager(appContext)
    val timerManager = TimerManager(appContext)
    val stopwatchManager = StopwatchManager()
    val calculatorManager = CalculatorManager(appContext)
    val flashlightManager = FlashlightManager(appContext)

    private val _phoneState = MutableStateFlow(PhoneState())
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    fun findContacts(query: String, isUserInitiated: Boolean): List<ContactMatch> {
        _phoneState.update { it.copy(status = PhoneModuleStatus.SEARCHING_CONTACTS) }
        val matches = contactsManager.findContacts(query, isUserInitiated)
        _phoneState.update { it.copy(status = PhoneModuleStatus.IDLE) }
        return matches
    }

    fun placeConfirmedCall(phoneNumber: String, isUserConfirmed: Boolean): Result<Boolean> {
        _phoneState.update { it.copy(status = PhoneModuleStatus.CALLING) }
        val result = callManager.placeCall(phoneNumber, isUserConfirmed)
        _phoneState.update {
            it.copy(
                status = if (result.isSuccess) PhoneModuleStatus.IDLE else PhoneModuleStatus.ERROR,
                lastActionMessage = result.exceptionOrNull()?.localizedMessage
            )
        }
        return result
    }

    fun sendConfirmedSms(
        phoneNumber: String,
        messageBody: String,
        isUserConfirmed: Boolean
    ): Result<Unit> {
        _phoneState.update { it.copy(status = PhoneModuleStatus.MESSAGING) }
        val result = smsManager.sendSms(phoneNumber, messageBody, isUserConfirmed)
        _phoneState.update {
            it.copy(
                status = if (result.isSuccess) PhoneModuleStatus.IDLE else PhoneModuleStatus.ERROR,
                lastActionMessage = result.exceptionOrNull()?.localizedMessage
            )
        }
        return result
    }

    fun setAlarm(hour: Int, minute: Int, label: String? = null, isUserInitiated: Boolean = true): Result<String> {
        val result = alarmManager.setAlarm(hour, minute, label, isUserInitiated)
        result.onSuccess { formatted ->
            _phoneState.update {
                it.copy(
                    status = PhoneModuleStatus.ALARM_ACTIVE,
                    activeAlarmSummary = formatted
                )
            }
        }
        return result
    }

    fun startTimer(durationSeconds: Int, label: String? = null, isUserInitiated: Boolean = true): Result<Int> {
        val result = timerManager.startTimer(durationSeconds, label, isUserInitiated)
        result.onSuccess { secs ->
            _phoneState.update {
                it.copy(
                    status = PhoneModuleStatus.TIMER_RUNNING,
                    activeTimerSeconds = secs
                )
            }
        }
        return result
    }

    fun controlStopwatch(action: String, isUserInitiated: Boolean = true): String {
        val elapsedStr = when (action.lowercase()) {
            "start" -> {
                stopwatchManager.start(isUserInitiated)
                stopwatchManager.formatElapsed()
            }
            "pause", "stop" -> {
                stopwatchManager.pause(isUserInitiated)
                stopwatchManager.formatElapsed()
            }
            "resume" -> {
                stopwatchManager.resume(isUserInitiated)
                stopwatchManager.formatElapsed()
            }
            "lap" -> {
                stopwatchManager.lap(isUserInitiated)
                stopwatchManager.formatElapsed()
            }
            "reset" -> {
                stopwatchManager.reset(isUserInitiated)
                stopwatchManager.formatElapsed(0L)
            }
            else -> stopwatchManager.formatElapsed()
        }
        _phoneState.update {
            it.copy(
                status = if (stopwatchManager.isRunning.value) PhoneModuleStatus.STOPWATCH_RUNNING else PhoneModuleStatus.STOPWATCH_PAUSED,
                isStopwatchRunning = stopwatchManager.isRunning.value,
                stopwatchElapsedMs = stopwatchManager.elapsedMs.value,
                stopwatchLapsMs = stopwatchManager.lapsMs.value
            )
        }
        return elapsedStr
    }

    fun calculate(expression: String): Result<String> {
        _phoneState.update { it.copy(status = PhoneModuleStatus.CALCULATING) }
        val res = calculatorManager.evaluate(expression)
        res.onSuccess { value ->
            _phoneState.update {
                it.copy(
                    status = PhoneModuleStatus.IDLE,
                    lastCalculationResult = value
                )
            }
        }.onFailure {
            _phoneState.update { it.copy(status = PhoneModuleStatus.ERROR) }
        }
        return res
    }

    fun toggleFlashlight(isUserInitiated: Boolean = true): Result<Boolean> {
        val res = flashlightManager.toggleFlashlight(isUserInitiated)
        res.onSuccess { on ->
            _phoneState.update {
                it.copy(
                    status = if (on) PhoneModuleStatus.FLASHLIGHT_ON else PhoneModuleStatus.IDLE,
                    isFlashlightOn = on
                )
            }
        }
        return res
    }

    /** Releases active hardware or transient timing resources after completing tasks. */
    fun release() {
        flashlightManager.release()
        timerManager.release()
        stopwatchManager.release()
        _phoneState.value = PhoneState()
    }
}
