package com.example.emotion

import java.util.Locale

/**
 * Detects conversation mood and emotional intent from user text input in English and Bengali.
 */
object EmotionDetector {

    private val happyKeywords = setOf(
        "happy", "great", "awesome", "wonderful", "glad", "good", "alhamdulillah", "আলহামদুলিল্লাহ", "খুশি", "ভালো", "দারুণ", "অসাধারণ"
    )

    private val sadOrWorriedKeywords = setOf(
        "sad", "worried", "upset", "bad", "tired", "problem", "difficult", "stress", "দুঃখিত", "মন খারাপ", "টেনশন", "সমস্যা", "কষ্ট", "ক্লান্ত"
    )

    private val helpKeywords = setOf(
        "help", "assist", "how", "need", "please", "sahajjo", "সাহায্য", "কীভাবে", "করব", "দরকার"
    )

    private val excitedKeywords = setOf(
        "wow", "amazing", "fantastic", "exciting", "দুর্দান্ত", "বাহ"
    )

    /**
     * Analyzes user text input and determines the corresponding [EmotionState].
     */
    fun detectEmotion(input: String): EmotionState {
        val lower = input.lowercase(Locale.getDefault())

        val isHappy = happyKeywords.any { lower.contains(it) }
        val isSad = sadOrWorriedKeywords.any { lower.contains(it) }
        val isHelp = helpKeywords.any { lower.contains(it) }
        val isExcited = excitedKeywords.any { lower.contains(it) }

        return when {
            isExcited -> EmotionState.EXCITED
            isHappy -> EmotionState.HAPPY
            isSad -> EmotionState.CARING
            isHelp -> EmotionState.SUPPORTIVE
            lower.contains("sorry") || lower.contains("maf") || lower.contains("ক্ষমা") -> EmotionState.SORRY
            else -> EmotionState.CALM
        }
    }
}
