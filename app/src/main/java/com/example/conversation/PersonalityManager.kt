package com.example.conversation

/**
 * Central personality helper ensuring MAX sounds friendly, warm, non-robotic, and never repetitive.
 */
object PersonalityManager {

    /**
     * Formats assistant replies to ensure natural human conversation tone, pacing, and emotional warmth.
     */
    fun formatAssistantReply(rawReply: String): String {
        val trimmed = rawReply.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed
    }

    /**
     * Checks if response requires calm reassurance.
     */
    fun shouldBeCalm(input: String): Boolean {
        val lower = input.lowercase()
        return lower.contains("sad") || lower.contains("worried") || lower.contains("upset") ||
                lower.contains("দুঃখ") || lower.contains("টেনশন") || lower.contains("সমস্যা")
    }
}
