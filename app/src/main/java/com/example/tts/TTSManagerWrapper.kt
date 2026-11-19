package com.example.tts

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Independent manager wrapping [TTSManager] for the dependency injection and modular architecture.
 *
 * Exposes full Bangla/English speech capabilities, expressive [SpeechStyle] modes, and playback controls.
 */
class TTSManagerWrapper(context: Context) {
    private val manager = TTSManager(context.applicationContext)

    val isSpeaking: StateFlow<Boolean> = manager.isSpeaking
    val voiceState: StateFlow<VoiceState> = manager.voiceState

    var onSpeechCompleted: (() -> Unit)?
        get() = manager.onSpeechCompleted
        set(value) {
            manager.onSpeechCompleted = value
        }

    fun speak(text: String, languageHint: String = "en") {
        manager.speak(text, languageHint)
    }

    fun stop() {
        manager.stopSpeaking()
    }

    fun stopSpeaking() {
        manager.stopSpeaking()
    }

    fun pauseSpeaking() {
        manager.pauseSpeaking()
    }

    fun resumeSpeaking() {
        manager.resumeSpeaking()
    }

    fun setSpeechSpeed(speed: Float) {
        manager.setSpeechSpeed(speed)
    }

    fun setPitch(pitch: Float) {
        manager.setPitch(pitch)
    }

    fun setSpeechStyle(style: SpeechStyle) {
        manager.setSpeechStyle(style)
    }

    fun shutdown() {
        manager.shutdown()
    }
}
