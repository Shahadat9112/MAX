package com.example.speech

import android.content.Context
import com.example.device.VoiceRecognitionManager
import com.example.domain.SpeechRecognitionResult
import com.example.domain.VoiceLanguage
import kotlinx.coroutines.flow.StateFlow

/**
 * Independent manager encapsulating bilingual (English & Bengali) voice input capture,
 * partial transcript streaming, audio energy levels, and offline audio fallback.
 */
class SpeechManager(context: Context) {
    private val recognitionManager = VoiceRecognitionManager(context.applicationContext)

    val isListening: StateFlow<Boolean> = recognitionManager.isListening
    val partialText: StateFlow<String> = recognitionManager.partialText
    val audioLevel: StateFlow<Float> = recognitionManager.audioLevel
    val activeLocaleTag: StateFlow<String> = recognitionManager.activeLocaleTag

    fun startListening(
        language: VoiceLanguage,
        preferOffline: Boolean,
        onResult: (SpeechRecognitionResult) -> Unit,
        onAudioFallback: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        recognitionManager.startListening(
            language = language,
            preferOffline = preferOffline,
            onResult = onResult,
            onAudioFallback = onAudioFallback,
            onError = onError
        )
    }

    fun stopListening() {
        recognitionManager.stopListening()
    }

    fun destroy() {
        recognitionManager.destroy()
    }
}
