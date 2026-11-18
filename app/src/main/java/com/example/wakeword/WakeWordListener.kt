package com.example.wakeword

import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle states of the MAX Wake Word System.
 */
enum class WakeWordState {
    /** Background wake listener is stopped. */
    STOPPED,
    /** Standby mode: listening strictly for the "MAX" wake word (full speech recognition inactive). */
    WAITING_FOR_WAKE_WORD,
    /** Paused because the foreground mic or TTS is currently active. */
    PAUSED_FOR_SPEECH,
    /** Paused to protect user privacy during an active phone call. */
    PAUSED_FOR_CALL,
    /** "MAX" wake word detected; handing off to MAXCore and speech recognition. */
    WAKE_DETECTED
}

/**
 * Structured event emitted whenever the "MAX" wake word is detected.
 */
data class WakeWordDetectionEvent(
    val wakeWord: String = WakeWordConfig.DEFAULT_WAKE_WORD,
    val trailingCommand: String = "",
    val detectedAtMs: Long = System.currentTimeMillis()
)

/**
 * Callback interface implemented by [com.example.core.MAXCore] or other consumers
 * to react to "MAX" wake-word detections and state transitions.
 */
interface WakeWordListener {
    /** Invoked when the wake word "MAX" is detected. */
    fun onWakeWordDetected(event: WakeWordDetectionEvent)

    /** Invoked whenever the wake-word system transitions between standby, paused, and active states. */
    fun onWakeStateChanged(state: WakeWordState) {}

    /** Invoked when wake-word monitoring encounters a recoverable or permission error. */
    fun onWakeError(reason: String) {}
}

/**
 * Clean pluggable engine abstraction for wake-word detection backends
 * (e.g., background PCM/VAD service, future on-device keyword spotter, or test doubles).
 */
interface WakeWordEngine {
    val config: WakeWordConfig
    val state: StateFlow<WakeWordState>

    fun startListening(listener: WakeWordListener)
    fun stopListening()
    fun pause()
    fun resume()
}

/**
 * Clean extension interface allowing future real-time voice conversation modules
 * (such as streaming voice chat) to connect cleanly when wake-word activation occurs.
 */
interface VoiceConversationConnector {
    /** Called when MAX finishes preparing its assistant pipeline and is ready for user speech. */
    fun onReadyForConversation(event: WakeWordDetectionEvent)

    /** Called to request activation of full speech recognition following wake detection. */
    fun onSpeechRecognitionRequested(triggerSource: String)

    /** Called when the voice conversation turn finishes and standby can resume. */
    fun onConversationEnded() {}
}
