package com.example.tts

import android.content.Context
import com.example.device.VoiceTtsManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Independent manager encapsulating Text-to-Speech output in English and Bengali,
 * natural male voice selection, and utterance completion callbacks.
 */
class TTSManager(context: Context) {
    private val ttsManager = VoiceTtsManager(context.applicationContext)

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    var onSpeechCompleted: (() -> Unit)?
        get() = ttsManager.onSpeechCompleted
        set(value) {
            ttsManager.onSpeechCompleted = value
        }

    fun speak(text: String, languageHint: String = "en") {
        ttsManager.speak(text, languageHint)
    }

    fun stop() {
        ttsManager.stop()
    }

    fun shutdown() {
        ttsManager.shutdown()
    }
}
