package com.example.tts

import com.example.domain.VoiceLanguage

/**
 * Enumeration of expressive speaking styles supported by MAX's TTS system.
 */
enum class SpeechStyle(val label: String) {
    FRIENDLY("Friendly"),
    CALM("Calm"),
    HELPFUL("Helpful"),
    CARING("Caring")
}

/**
 * Runtime states of the MAX TTS speech engine.
 */
enum class VoiceState {
    IDLE,
    SPEAKING,
    PAUSED,
    STOPPED,
    ERROR
}

/**
 * Immutable configuration for MAX's voice synthesis.
 *
 * Designed to avoid robotic speech by configuring natural speech rate (slower, clearer)
 * and pitch tuning, with extensible multi-language support.
 */
data class VoiceConfig(
    val defaultLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val speechRate: Float = 0.92f, // Clear, deliberate speed with natural pauses
    val pitch: Float = 0.90f,      // Warm, deep, human-like voice tone
    val speechStyle: SpeechStyle = SpeechStyle.FRIENDLY,
    val addNaturalPauses: Boolean = true
)
