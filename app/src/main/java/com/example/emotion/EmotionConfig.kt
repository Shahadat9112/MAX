package com.example.emotion

import com.example.tts.SpeechStyle

/**
 * Expressive emotional states supported by MAX's Emotion System.
 */
enum class EmotionState(val defaultSpeechStyle: SpeechStyle) {
    HAPPY(SpeechStyle.FRIENDLY),
    CALM(SpeechStyle.CALM),
    CARING(SpeechStyle.CARING),
    SUPPORTIVE(SpeechStyle.HELPFUL),
    EXCITED(SpeechStyle.FRIENDLY),
    SORRY(SpeechStyle.CARING),
    RESPECTFUL(SpeechStyle.CALM)
}

/**
 * Response styling parameters shaped by the current [EmotionState].
 */
data class ResponseStyle(
    val emotion: EmotionState,
    val speechStyle: SpeechStyle,
    val toneModifier: String,
    val warmthFactor: Float
)
