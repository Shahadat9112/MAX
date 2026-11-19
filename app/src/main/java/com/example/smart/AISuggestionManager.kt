package com.example.smart

import com.example.emotion.EmotionState

/**
 * Provides thoughtful, non-intrusive, emotion-aware suggestions for Sanju
 * tailored to the active mood and time/context without ever interrupting privacy.
 */
object AISuggestionManager {

    fun generateContextualSuggestion(
        emotionState: EmotionState,
        isBengali: Boolean
    ): String {
        return when (emotionState) {
            EmotionState.CARING, EmotionState.SORRY -> if (isBengali) {
                "সানজু, মন হালকা করতে চাইলে একটু বিশ্রাম নিতে পারেন বা পছন্দের কোনো শান্ত মিউজিক শুনতে পারেন।"
            } else {
                "Sanju, taking a brief pause or listening to something calming might help you unwind."
            }

            EmotionState.EXCITED, EmotionState.HAPPY -> if (isBengali) {
                "দারুণ লাগছে শুনে! আজকের গুরুত্বপূর্ণ কাজগুলোর একটা ছোট নোট বা রিমাইন্ডার সেট করে রাখবেন নাকি?"
            } else {
                "Glad to hear that! Would you like to jot down a quick note or set a reminder for your plans?"
            }

            EmotionState.SUPPORTIVE -> if (isBengali) {
                "আমি সাথেই আছি সানজু। চাইলে যেকোনো ডকুমেন্ট, অনুবাদ বা রিমাইন্ডারের কাজে আমাকে বলতে পারেন।"
            } else {
                "I'm right here to help, Sanju. Feel free to ask for translations, reminders, notes, or document help."
            }

            EmotionState.CALM, EmotionState.RESPECTFUL -> if (isBengali) {
                "কোনো অনুবাদ, নোট লেখা, রিমাইন্ডার বা ফাইল দেখার প্রয়োজন হলে আমাকে নির্দ্বিধায় বলুন সানজু।"
            } else {
                "Whenever you need help with notes, translations, reminders, or documents, just let me know, Sanju."
            }
        }
    }
}
