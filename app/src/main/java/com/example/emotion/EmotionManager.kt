package com.example.emotion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Independent manager tracking MAX's active [EmotionState] and providing [ResponseStyle] adaptation.
 */
class EmotionManager {
    private val _currentEmotion = MutableStateFlow(EmotionState.CALM)
    val currentEmotion: StateFlow<EmotionState> = _currentEmotion.asStateFlow()

    private val _responseStyle = MutableStateFlow(
        ResponseStyle(
            emotion = EmotionState.CALM,
            speechStyle = EmotionState.CALM.defaultSpeechStyle,
            toneModifier = "calm_and_composed",
            warmthFactor = 1.0f
        )
    )
    val responseStyle: StateFlow<ResponseStyle> = _responseStyle.asStateFlow()

    /**
     * Evaluates user input or context, updates the current emotion, and computes the appropriate [ResponseStyle].
     */
    fun evaluateAndSetEmotion(userInput: String) {
        val detected = EmotionDetector.detectEmotion(userInput)
        _currentEmotion.value = detected

        val style = when (detected) {
            EmotionState.HAPPY -> ResponseStyle(detected, detected.defaultSpeechStyle, "positive_and_uplifting", 1.2f)
            EmotionState.CALM -> ResponseStyle(detected, detected.defaultSpeechStyle, "calm_and_composed", 1.0f)
            EmotionState.CARING -> ResponseStyle(detected, detected.defaultSpeechStyle, "sympathetic_and_gentle", 1.4f)
            EmotionState.SUPPORTIVE -> ResponseStyle(detected, detected.defaultSpeechStyle, "encouraging_and_helpful", 1.3f)
            EmotionState.EXCITED -> ResponseStyle(detected, detected.defaultSpeechStyle, "energetic_and_joyful", 1.2f)
            EmotionState.SORRY -> ResponseStyle(detected, detected.defaultSpeechStyle, "apologetic_and_caring", 1.3f)
            EmotionState.RESPECTFUL -> ResponseStyle(detected, detected.defaultSpeechStyle, "polite_and_calm", 1.1f)
        }
        _responseStyle.value = style
    }

    fun resetEmotion() {
        _currentEmotion.value = EmotionState.CALM
        _responseStyle.value = ResponseStyle(
            EmotionState.CALM,
            EmotionState.CALM.defaultSpeechStyle,
            "calm_and_composed",
            1.0f
        )
    }
}
