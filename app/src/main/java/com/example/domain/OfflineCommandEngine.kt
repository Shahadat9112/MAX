package com.example.domain

/**
 * Handles basic device and utility commands completely offline (no internet required),
 * supporting both English and Bengali inputs.
 */
object OfflineCommandEngine {

    fun tryParse(rawInput: String): AiDecision? {
        val text = WakeWordParser.stripOptionalWakeWord(rawInput).trim()
        val lower = text.lowercase()
        val isBn = WakeWordParser.isBengaliText(text)
        val lang = if (isBn) "bn" else "en"

        // 1. Take Photo
        if (matchesAny(
                lower,
                "take a picture", "take a photo", "take picture", "take photo",
                "capture photo", "click a photo", "snap a photo",
                "ছবি তোলো", "ছবি তুলো", "ফটো তোলো", "ছবি ওঠাও", "ছবি তোল"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "ক্যামেরা খুলে ছবি তুলছি।" else "Opening camera to take a picture.",
                language = lang,
                action = AssistantActionType.TAKE_PHOTO
            )
        }

        // 2. Open Camera
        if (matchesAny(
                lower,
                "open camera", "launch camera", "start camera", "camera",
                "ক্যামেরা খোলো", "ক্যামেরা চালু করো", "ক্যামেরা ওপেন করো", "ক্যামেরা"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "ক্যামেরা চালু করছি।" else "Opening the camera.",
                language = lang,
                action = AssistantActionType.OPEN_CAMERA
            )
        }

        // 3. Volume Controls
        if (matchesAny(
                lower,
                "volume up", "increase volume", "turn up volume", "louder", "raise volume",
                "ভলিউম বাড়াও", "সাউন্ড বাড়াও", "আওয়াজ বাড়াও", "ভলিউম বেশি করো"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "ভলিউম বাড়াচ্ছি।" else "Increasing the volume.",
                language = lang,
                action = AssistantActionType.VOLUME_UP
            )
        }

        if (matchesAny(
                lower,
                "volume down", "decrease volume", "turn down volume", "lower volume", "quieter",
                "ভলিউম কমাও", "সাউন্ড কমাও", "আওয়াজ কমাও", "ভলিউম কম করো"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "ভলিউম কমাচ্ছি।" else "Decreasing the volume.",
                language = lang,
                action = AssistantActionType.VOLUME_DOWN
            )
        }

        if (matchesAny(lower, "unmute", "sound on", "আনমিউট", "সাউন্ড চালু")) {
            return AiDecision(
                reply = if (isBn) "সাউন্ড চালু করছি।" else "Unmuting media volume.",
                language = lang,
                action = AssistantActionType.VOLUME_UNMUTE
            )
        }

        if (matchesAny(lower, "mute", "silence", "মিউট", "সাউন্ড বন্ধ")) {
            return AiDecision(
                reply = if (isBn) "ভলিউম মিউট করছি।" else "Muting volume.",
                language = lang,
                action = AssistantActionType.VOLUME_MUTE
            )
        }

        // 4. Flashlight
        if (matchesAny(
                lower,
                "flashlight", "torch", "flash light",
                "টর্চ", "ফ্ল্যাশলাইট", "লাইট জ্বালাও", "লাইট বন্ধ"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "টর্চলাইট টগল করছি।" else "Toggling the flashlight.",
                language = lang,
                action = AssistantActionType.TOGGLE_FLASHLIGHT
            )
        }

        // 5. Time & Battery
        if (matchesAny(
                lower,
                "what time is it", "current time", "tell me the time", "what's the time", "time now",
                "কয়টা বাজে", "সময় কত", "এখন কয়টা", "টাইম কত"
            )
        ) {
            return AiDecision(
                reply = "",
                language = lang,
                action = AssistantActionType.GET_TIME
            )
        }

        if (matchesAny(
                lower,
                "battery", "battery level", "charge level", "how much battery",
                "ব্যাটারি", "চার্জ কত"
            )
        ) {
            return AiDecision(
                reply = "",
                language = lang,
                action = AssistantActionType.GET_BATTERY
            )
        }

        // 6. Settings
        if (lower.contains("settings") || lower.contains("সেটিংস") || lower.contains("সেটিং")) {
            val section = when {
                lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("ওয়াইফাই") -> "wifi"
                lower.contains("bluetooth") || lower.contains("ব্লুটুথ") -> "bluetooth"
                lower.contains("sound") || lower.contains("audio") || lower.contains("সাউন্ড") -> "sound"
                lower.contains("display") || lower.contains("brightness") || lower.contains("ডিসপ্লে") -> "display"
                else -> "general"
            }
            return AiDecision(
                reply = if (isBn) "ফোন সেটিংস খুলছি।" else "Opening phone settings.",
                language = lang,
                action = AssistantActionType.OPEN_SETTINGS,
                targetName = section
            )
        }

        // 7. Call Contact (English + Bengali flexible order)
        val callRegexEn = Regex("""^(?:please\s+)?(?:call|dial|phone|ring)\s+(?:my\s+)?(.+)$""", RegexOption.IGNORE_CASE)
        callRegexEn.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return AiDecision(
                    reply = "Looking up $name in your contacts.",
                    language = "en",
                    action = AssistantActionType.CALL_CONTACT,
                    targetName = name
                )
            }
        }
        val callRegexBnSuffix = Regex("""^(.+?)(?:-কে|\s+কে|কে)?\s*(?:কল করো|কল দাও|ফোন করো|ফোন দাও|কল কর|ফোন কর)$""")
        callRegexBnSuffix.find(text)?.let { match ->
            val name = match.groupValues[1].trim().removePrefix("আমার ").trim()
            if (name.isNotBlank()) {
                return AiDecision(
                    reply = "আপনার কন্টাক্ট লিস্টে $name খুঁজছি।",
                    language = "bn",
                    action = AssistantActionType.CALL_CONTACT,
                    targetName = name
                )
            }
        }
        val callRegexBnPrefix = Regex("""^(?:কল করো|কল দাও|ফোন করো|ফোন দাও)\s+(.+)$""")
        callRegexBnPrefix.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
                .removeSuffix("কে")
                .removePrefix("আমার ")
                .trim()
            if (name.isNotBlank()) {
                return AiDecision(
                    reply = "আপনার কন্টাক্ট লিস্টে $name খুঁজছি।",
                    language = "bn",
                    action = AssistantActionType.CALL_CONTACT,
                    targetName = name
                )
            }
        }

        // 8. Send SMS (English + Bengali flexible order)
        val smsRegexEn = Regex(
            """^(?:send\s+(?:an\s+)?(?:sms|text|message)\s+to|text|message|sms)\s+([^\s:]+(?:\s+[^\s:]+)?)(?:\s*(?:saying|that|:|-)\s+(.+))?$""",
            RegexOption.IGNORE_CASE
        )
        smsRegexEn.find(text)?.let { match ->
            val target = match.groupValues[1].trim()
            val body = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            if (target.isNotBlank()) {
                return AiDecision(
                    reply = if (body == null) {
                        "What message would you like to send to $target?"
                    } else {
                        "Preparing SMS to $target."
                    },
                    language = "en",
                    action = AssistantActionType.SEND_SMS,
                    targetName = target,
                    messageBody = body
                )
            }
        }
        val smsRegexBn = Regex("""^(.+?)(?:-কে|\s+কে|কে)?\s*(?:এসএমএস|মেসেজ|sms)\s*(?:পাঠাও|করো|দাও|পাঠিয়ে দাও)(?:\s*(?:যে|বলে|:|-)?\s*(.+))?$""", RegexOption.IGNORE_CASE)
        smsRegexBn.find(text)?.let { match ->
            val target = match.groupValues[1].trim()
            val body = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            if (target.isNotBlank()) {
                return AiDecision(
                    reply = if (body == null) {
                        "$target-কে কী মেসেজ পাঠাতে চান?"
                    } else {
                        "$target-এর জন্য মেসেজ প্রস্তুত করছি।"
                    },
                    language = "bn",
                    action = AssistantActionType.SEND_SMS,
                    targetName = target,
                    messageBody = body
                )
            }
        }

        // 9. Open Installed App
        val openAppRegexEn = Regex("""^(?:please\s+)?(?:open|launch|start)\s+(.+)$""", RegexOption.IGNORE_CASE)
        openAppRegexEn.find(text)?.let { match ->
            val appName = match.groupValues[1].trim()
            if (appName.isNotBlank()) {
                return AiDecision(
                    reply = "Opening $appName.",
                    language = "en",
                    action = AssistantActionType.OPEN_APP,
                    targetName = appName
                )
            }
        }
        val openAppRegexBn = Regex("""^(.+?)\s*(?:খোলো|খুলো|চালু করো|ওপেন করো)$""")
        openAppRegexBn.find(text)?.let { match ->
            val appName = match.groupValues[1].trim()
            if (appName.isNotBlank()) {
                return AiDecision(
                    reply = "$appName খুলছি।",
                    language = "bn",
                    action = AssistantActionType.OPEN_APP,
                    targetName = appName
                )
            }
        }

        // 10. Clear Chat
        if (matchesAny(lower, "clear chat", "clear history", "চ্যাট মুছে ফেলো", "হিস্ট্রি ক্লিয়ার করো")) {
            return AiDecision(
                reply = if (isBn) "কথোপকথনের ইতিহাস মুছে দিচ্ছি।" else "Clearing conversation history.",
                language = lang,
                action = AssistantActionType.CLEAR_CHAT
            )
        }

        // 11. Basic offline greeting / identity
        if (matchesAny(lower, "hi", "hello", "who are you", "what can you do", "হ্যালো", "তুমি কে", "কেমন আছো")) {
            return AiDecision(
                reply = if (isBn) {
                    "আমি ম্যাক্স, আপনার ব্যক্তিগত এআই অ্যাসিস্ট্যান্ট। অফলাইনে আমি সময়, ব্যাটারি, ভলিউম, টর্চলাইট, অ্যাপ খোলা, কল, এসএমএস এবং ক্যামেরা নিয়ন্ত্রণ করতে পারি।"
                } else {
                    "I'm MAX, your personal AI assistant. Even offline, I can check the time and battery, adjust volume, toggle the flashlight, open apps, place calls, send SMS, and take photos."
                },
                language = lang,
                action = AssistantActionType.NONE
            )
        }

        return null
    }

    private fun matchesAny(lowerInput: String, vararg phrases: String): Boolean {
        return phrases.any { phrase -> lowerInput.contains(phrase) }
    }
}
