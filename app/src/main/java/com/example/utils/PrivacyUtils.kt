package com.example.utils

import com.example.domain.PrivacyGuard
import com.example.domain.WakeWordParser

/**
 * Stateless text, script, and privacy sanitization helpers shared across MAX AI layers.
 */
object PrivacyUtils {

    /** Redacts personal identifiers (phones, emails, OTPs, PINs, cards) before outbound AI requests. */
    fun sanitizeForExternalAi(input: String): String =
        PrivacyGuard.sanitizeForExternalAi(input)

    /** Returns true when a message represents private device-local state that must stay off cloud history. */
    fun shouldExcludeFromCloudHistory(
        actionBadge: String?,
        isOffline: Boolean,
        isError: Boolean
    ): Boolean = PrivacyGuard.shouldExcludeFromCloudHistory(actionBadge, isOffline, isError)

    /** Detects whether the supplied text contains Bengali Unicode characters. */
    fun isBengaliText(text: String): Boolean = WakeWordParser.isBengaliText(text)

    /** Checks if a target string represents a literal dialable phone number rather than a contact name. */
    fun isPhoneNumberLiteral(target: String): Boolean {
        val cleaned = target.trim()
        return cleaned.count { it.isDigit() } >= 3 && cleaned.none { it.isLetter() }
    }
}
