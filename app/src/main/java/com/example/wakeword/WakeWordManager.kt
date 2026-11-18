package com.example.wakeword

import android.content.Context
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import com.example.service.VoiceActivationService
import com.example.service.WakeTriggerEvent
import com.example.service.WakeWordCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Independent manager for the MAX Wake Word System.
 *
 * Responsibilities:
 * - Keeps MAX in wake-word-only standby (`WAITING_FOR_WAKE_WORD`) without immediately launching
 *   full speech recognition or recording conversations.
 * - Coordinates [WakeWordConfig], [WakeWordListener] callbacks, optional [WakeWordEngine] backends,
 *   and [VoiceConversationConnector] hooks for future voice conversation features.
 * - Emits detection events cleanly to [com.example.core.MAXCore] when `"MAX"` is heard.
 */
class WakeWordManager(
    context: Context,
    val config: WakeWordConfig = WakeWordConfig(),
    private val customEngine: WakeWordEngine? = null
) {
    private val appContext = context.applicationContext

    private val listeners = CopyOnWriteArraySet<WakeWordListener>()
    private val conversationConnectors = CopyOnWriteArraySet<VoiceConversationConnector>()

    private val _wakeState = MutableStateFlow(WakeWordState.STOPPED)
    val wakeState: StateFlow<WakeWordState> = _wakeState.asStateFlow()

    val isServiceRunning: StateFlow<Boolean> = WakeWordCoordinator.isServiceRunning
    val isForegroundMicBusy: StateFlow<Boolean> = WakeWordCoordinator.isForegroundMicBusy
    val isCallActive: StateFlow<Boolean> = WakeWordCoordinator.isCallActive
    val lastHeardStatus: StateFlow<String> = WakeWordCoordinator.lastHeardStatus
    val wakeEvents: SharedFlow<WakeTriggerEvent> = WakeWordCoordinator.wakeEvents

    private val internalListener = object : WakeWordListener {
        override fun onWakeWordDetected(event: WakeWordDetectionEvent) {
            dispatchWakeDetected(event)
        }

        override fun onWakeStateChanged(state: WakeWordState) {
            updateWakeState(state)
        }

        override fun onWakeError(reason: String) {
            updateStatus(reason)
            listeners.forEach { it.onWakeError(reason) }
        }
    }

    /** Registers a [WakeWordListener] (such as MAXCore) to receive "MAX" wake events. */
    fun addListener(listener: WakeWordListener) {
        listeners.add(listener)
    }

    /** Removes a previously registered [WakeWordListener]. */
    fun removeListener(listener: WakeWordListener) {
        listeners.remove(listener)
    }

    /** Attaches a [VoiceConversationConnector] for future real-time voice conversation features. */
    fun attachConversationConnector(connector: VoiceConversationConnector) {
        conversationConnectors.add(connector)
    }

    /** Detaches a [VoiceConversationConnector]. */
    fun detachConversationConnector(connector: VoiceConversationConnector) {
        conversationConnectors.remove(connector)
    }

    /**
     * Starts wake-word-only standby monitoring (never starts full speech recognition immediately).
     */
    fun startService() {
        updateWakeState(WakeWordState.WAITING_FOR_WAKE_WORD)
        if (customEngine != null) {
            customEngine.startListening(internalListener)
        } else {
            VoiceActivationService.start(appContext)
        }
    }

    /** Stops background wake-word monitoring. */
    fun stopService() {
        customEngine?.stopListening() ?: VoiceActivationService.stop(appContext)
        updateWakeState(WakeWordState.STOPPED)
    }

    /** Ensures wake-word standby is active when enabled and microphone permission is granted. */
    fun ensureRunningIfPermitted(enabled: Boolean, hasRecordAudioPermission: Boolean) {
        if (enabled && hasRecordAudioPermission && !isServiceRunning.value && _wakeState.value == WakeWordState.STOPPED) {
            startService()
        }
    }

    /**
     * Evaluates a short wake-phrase transcript strictly against `"MAX"` rules,
     * triggering listeners and speech handoff only when `"MAX"` is detected.
     */
    fun processWakeCandidate(transcript: String): Boolean {
        return when (val result = parseTranscript(transcript)) {
            is WakeWordResult.Triggered -> {
                val event = WakeWordDetectionEvent(
                    wakeWord = config.wakeWord,
                    trailingCommand = result.command
                )
                dispatchWakeDetected(event)
                true
            }
            WakeWordResult.NotDetected -> false
        }
    }

    /**
     * Dispatches a confirmed `"MAX"` detection event to registered [WakeWordListener]s
     * and attached [VoiceConversationConnector]s.
     */
    fun dispatchWakeDetected(event: WakeWordDetectionEvent) {
        updateWakeState(WakeWordState.WAKE_DETECTED)
        listeners.forEach { it.onWakeWordDetected(event) }
        conversationConnectors.forEach { connector ->
            connector.onReadyForConversation(event)
            if (config.autoActivateSpeechOnWake && event.trailingCommand.isBlank()) {
                connector.onSpeechRecognitionRequested("wake_word:${event.wakeWord}")
            }
        }
    }

    /** Signals completion of a voice turn so standby wake listening can resume. */
    fun notifyConversationEnded() {
        conversationConnectors.forEach { it.onConversationEnded() }
        if (isServiceRunning.value || customEngine != null) {
            customEngine?.resume()
            updateWakeState(WakeWordState.WAITING_FOR_WAKE_WORD)
        }
    }

    fun setForegroundMicBusy(busy: Boolean) {
        WakeWordCoordinator.setForegroundMicBusy(busy)
        if (busy) {
            customEngine?.pause()
            if (_wakeState.value != WakeWordState.STOPPED) {
                updateWakeState(WakeWordState.PAUSED_FOR_SPEECH)
            }
        } else if (isServiceRunning.value || customEngine != null) {
            if (!isCallActive.value) {
                customEngine?.resume()
                updateWakeState(WakeWordState.WAITING_FOR_WAKE_WORD)
            }
        }
    }

    fun setCallActive(active: Boolean) {
        WakeWordCoordinator.setCallActive(active)
        if (active) {
            customEngine?.pause()
            if (_wakeState.value != WakeWordState.STOPPED) {
                updateWakeState(WakeWordState.PAUSED_FOR_CALL)
            }
        } else if (isServiceRunning.value || customEngine != null) {
            if (!isForegroundMicBusy.value) {
                customEngine?.resume()
                updateWakeState(WakeWordState.WAITING_FOR_WAKE_WORD)
            }
        }
    }

    fun updateStatus(status: String) {
        WakeWordCoordinator.updateStatus(status)
    }

    fun parseTranscript(transcript: String): WakeWordResult =
        WakeWordParser.parse(transcript)

    fun stripOptionalWakeWord(input: String): String =
        WakeWordParser.stripOptionalWakeWord(input)

    private fun updateWakeState(newState: WakeWordState) {
        if (_wakeState.value != newState) {
            _wakeState.value = newState
            listeners.forEach { it.onWakeStateChanged(newState) }
        }
    }
}
