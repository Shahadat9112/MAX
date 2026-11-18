package com.example.domain

/**
 * Central zero-leak privacy guard for MAX AI.
 *
 * Ensures:
 * 1) Sensitive personal identifiers (phone numbers, emails, OTPs/PINs/passwords, card/account numbers)
 *    are redacted before any prompt or history is transmitted to external AI services.
 * 2) Local device-control results (contacts, SMS bodies, call confirmations, battery, camera actions)
 *    never leave the device inside AI conversation history.
 * 3) Raw PCM/WAV audio buffers in RAM are zero-wiped immediately after processing.
 */
object PrivacyGuard {

    private val emailRegex = Regex(
        """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""
    )

    private val internationalPhoneRegex = Regex(
        """(?<!\w)\+\d{1,3}[\s.-]?(?:\(?\d{2,5}\)?[\s.-]?){1,4}\d{2,5}(?!\w)"""
    )

    private val cardOrAccountRegex = Regex(
        """\b\d(?:[\s-]?\d){12,18}\b"""
    )

    // Matches local phone numbers (7 to 12 digits with spaces, dashes, or parens)
    private val phoneRegex = Regex(
        """(?<!\w)(?:\(?\d{2,5}\)?[\s.-]?){2,4}\d{2,4}(?!\w)"""
    )

    // Matches explicit inline secrets like "otp is 482910", "pin: 1234", "password = mypass", "ওটিপি ১২৩৪"
    private val inlineSecretRegex = Regex(
        """(?i)\b(otp|pin|passcode|password|cvv|secret|api[_\s-]?key|token|ওটিপি|পিন|পাসওয়ার্ড)\b(\s*(?:is|are|was|হলো|হল|:|=|-)\s*|\s+)([^\s,.;!?]{3,32})"""
    )

    private val privateActionBadges = setOf(
        "Confirm Call",
        "Calling",
        "Compose SMS",
        "Review SMS",
        "SMS Sent",
        "Contacts",
        "Permission",
        "Offline Mode",
        "Camera",
        "Photo Saved",
        "Battery",
        "Clock",
        "Flashlight",
        "Settings",
        "App Launcher"
    )

    fun sanitizeForExternalAi(input: String): String {
        if (input.isBlank()) return input
        var sanitized = input
        sanitized = inlineSecretRegex.replace(sanitized) { match ->
            val label = match.groupValues[1]
            val separator = match.groupValues[2]
            "$label$separator[SECRET_REDACTED]"
        }
        sanitized = emailRegex.replace(sanitized, "[EMAIL_REDACTED]")
        sanitized = internationalPhoneRegex.replace(sanitized, "[PHONE_REDACTED]")
        sanitized = cardOrAccountRegex.replace(sanitized, "[ACCOUNT_REDACTED]")
        sanitized = phoneRegex.replace(sanitized) { match ->
            val digitCount = match.value.count { it.isDigit() }
            if (digitCount in 7..12) "[PHONE_REDACTED]" else match.value
        }
        return sanitized
    }

    fun shouldExcludeFromCloudHistory(
        actionBadge: String?,
        isOffline: Boolean,
        isError: Boolean
    ): Boolean {
        if (isOffline || isError) return true
        if (actionBadge != null && actionBadge in privateActionBadges) return true
        if (actionBadge != null && actionBadge.startsWith("Volume", ignoreCase = true)) return true
        return false
    }

    fun wipeBuffer(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    fun wipeBuffer(samples: ShortArray?) {
        samples?.fill(0)
    }
}
