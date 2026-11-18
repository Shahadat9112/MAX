package com.example.wakeword

import android.content.Context
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import com.example.service.VoiceActivationService
import com.example.service.WakeTriggerEvent
import com.example.service.WakeWordCoordinator
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Independent manager responsible for background wake-word activation lifecycle,
 * wake-phrase parsing ("MAX" / "Hey MAX"), and microphone/call coordination signals.
 */
class WakeWordManager(context: Context) {
    private val appContext = context.applicationContext

    val isServiceRunning: StateFlow<Boolean> = WakeWordCoordinator.isServiceRunning
    val isForegroundMicBusy: StateFlow<Boolean> = WakeWordCoordinator.isForegroundMicBusy
    val isCallActive: StateFlow<Boolean> = WakeWordCoordinator.isCallActive
    val lastHeardStatus: StateFlow<String> = WakeWordCoordinator.lastHeardStatus
    val wakeEvents: SharedFlow<WakeTriggerEvent> = WakeWordCoordinator.wakeEvents

    fun startService() {
        VoiceActivationService.start(appContext)
    }

    fun stopService() {
        VoiceActivationService.stop(appContext)
    }

    fun ensureRunningIfPermitted(enabled: Boolean, hasRecordAudioPermission: Boolean) {
        if (enabled && hasRecordAudioPermission && !isServiceRunning.value) {
            startService()
        }
    }

    fun setForegroundMicBusy(busy: Boolean) {
        WakeWordCoordinator.setForegroundMicBusy(busy)
    }

    fun setCallActive(active: Boolean) {
        WakeWordCoordinator.setCallActive(active)
    }

    fun updateStatus(status: String) {
        WakeWordCoordinator.updateStatus(status)
    }

    fun parseTranscript(transcript: String): WakeWordResult =
        WakeWordParser.parse(transcript)

    fun stripOptionalWakeWord(input: String): String =
        WakeWordParser.stripOptionalWakeWord(input)
}
