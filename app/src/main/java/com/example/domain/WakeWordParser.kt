package com.example.domain

sealed interface WakeWordResult {
    data class Triggered(val command: String) : WakeWordResult
    data object NotDetected : WakeWordResult
}

object WakeWordParser {
    private val wakeTokens = setOf("max", "macs", "maks", "ম্যাক্স", "মেক্স", "মাক্স")
    private val optionalPrefixes = listOf(
        "hey", "ok", "okay", "hi", "hello", "yo",
        "হে", "ওকে", "হাই", "হ্যালো"
    )

    private val wakeWithPrefixPattern = Regex(
        pattern = """^\s*(?:(hey|ok|okay|hi|hello|yo|হে|ওকে|হাই|হ্যালো)[\s,]+)?(max|macs|maks|ম্যাক্স|মেক্স|মাক্স)(?:[\s,.:!?]+(.*))?$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Accepts both "MAX" and "Hey MAX" / "OK MAX" as valid continuous wake-word triggers.
     */
    fun parse(transcript: String): WakeWordResult {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return WakeWordResult.NotDetected

        val match = wakeWithPrefixPattern.find(trimmed)
        if (match != null) {
            val remainder = match.groupValues.getOrNull(3)?.trim().orEmpty()
            return WakeWordResult.Triggered(remainder)
        }

        val words = trimmed.lowercase().split(Regex("""[\s,.:!?]+""")).filter { it.isNotBlank() }
        val maxIndex = words.indexOfFirst { it in wakeTokens }
        if (maxIndex >= 0) {
            val remainder = words.drop(maxIndex + 1).joinToString(" ")
            return WakeWordResult.Triggered(remainder)
        }

        return WakeWordResult.NotDetected
    }

    fun stripOptionalWakeWord(input: String): String {
        val trimmed = input.trim()
        val match = wakeWithPrefixPattern.find(trimmed)
        if (match != null) {
            val remainder = match.groupValues.getOrNull(3)?.trim().orEmpty()
            if (remainder.isNotEmpty()) return remainder
        }
        val cleaned = trimmed.replace(
            Regex("""^\s*(?:(?:hey|ok|okay|hi|hello|yo|হে|ওকে|하이|হ্যালো)[\s,]+)?(?:max|macs|maks|ম্যাক্স|মেক্স|মাক্স)[\s,.:!?]*""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        return cleaned.ifEmpty { trimmed }
    }

    fun isBengaliText(text: String): Boolean =
        text.any { it in '\u0980'..'\u09FF' }
}
