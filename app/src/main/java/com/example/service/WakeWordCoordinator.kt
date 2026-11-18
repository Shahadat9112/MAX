package com.example.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class WakeTriggerEvent(
    val command: String,
    val timestamp: Long = System.currentTimeMillis()
)

object WakeWordCoordinator {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isForegroundMicBusy = MutableStateFlow(false)
    val isForegroundMicBusy: StateFlow<Boolean> = _isForegroundMicBusy.asStateFlow()

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val _lastHeardStatus = MutableStateFlow("Standby")
    val lastHeardStatus: StateFlow<String> = _lastHeardStatus.asStateFlow()

    private val _wakeEvents = MutableSharedFlow<WakeTriggerEvent>(extraBufferCapacity = 4)
    val wakeEvents: SharedFlow<WakeTriggerEvent> = _wakeEvents.asSharedFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setForegroundMicBusy(busy: Boolean) {
        _isForegroundMicBusy.value = busy
    }

    fun setCallActive(active: Boolean) {
        _isCallActive.value = active
    }

    fun updateStatus(status: String) {
        _lastHeardStatus.value = status
    }

    fun emitWakeTrigger(command: String) {
        _wakeEvents.tryEmit(WakeTriggerEvent(command))
    }
}
