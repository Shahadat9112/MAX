package com.example.smart

import com.example.domain.PrivacyGuard
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Translation controller supporting Bengali <-> English and multi-language translation
 * (e.g., "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো।").
 *
 * Enforces PII redaction via [PrivacyGuard.sanitizeForExternalAi] before cloud inference.
 */
class TranslationManager {
    private val model by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    /**
     * Parses direction hints from commands such as "বাংলা থেকে ইংরেজিতে অনুবাদ করো"
     * and translates [sourceText] cleanly.
     */
    suspend fun translate(
        sourceText: String,
        targetLanguage: String = "en",
        isOnline: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanText = PrivacyGuard.sanitizeForExternalAi(sourceText).trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No text provided to translate."))
        }

        if (!isOnline) {
            val offlineMsg = if (targetLanguage == "bn") {
                "অফলাইনে পূর্ণাঙ্গ অনুবাদ সুবিধা সীমিত। অনুগ্রহ করে ইন্টারনেট সংযোগ চালু করুন।"
            } else {
                "Full translation requires an internet connection."
            }
            return@withContext Result.failure(IllegalStateException(offlineMsg))
        }

        runCatching {
            val targetName = if (targetLanguage == "bn") "natural Bengali (বাংলা)" else "natural English"
            val prompt = "Translate the following text into $targetName accurately and naturally without extra commentary:\n\n$cleanText"
            val response = withTimeout(30_000L) {
                model.generateContent(prompt)
            }
            val translated = response.text?.trim().orEmpty()
            if (translated.isEmpty()) {
                throw IllegalStateException("Empty translation received.")
            }
            translated
        }
    }

    fun extractPayload(commandText: String): String {
        var text = commandText.trim()
        val colonIdx = text.indexOf(':')
        if (colonIdx != -1 && colonIdx < text.lastIndex) {
            return text.substring(colonIdx + 1).trim()
        }
        val stripped = text
            .replace(Regex("""^(?:max,?\s*)?(?:এটা\s+)?(?:বাংলা থেকে ইংরেজিতে|ইংরেজি থেকে বাংলায়|ইংরেজিতে|বাংলায়)\s*অনুবাদ\s*করো[।:. ]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(?:max,?\s*)?translate\s+(?:this\s+)?(?:to|into)\s+(?:english|bengali|bangla)[.: ]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(?:max,?\s*)?translate\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return stripped
    }

    fun inferTargetLanguage(commandText: String): String {
        val lower = commandText.lowercase()
        return when {
            lower.contains("থেকে ইংরেজি") || lower.contains("ইংরেজিতে") || lower.contains("to english") || lower.contains("in english") -> "en"
            lower.contains("থেকে বাংলা") || lower.contains("বাংলায়") || lower.contains("to bengali") || lower.contains("to bangla") || lower.contains("in bengali") -> "bn"
            com.example.domain.WakeWordParser.isBengaliText(commandText) -> "en"
            else -> "bn"
        }
    }
}
