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

        // 2. Selfie, Video Recording, QR Scan & Open Camera
        if (matchesAny(
                lower,
                "selfie mode", "take a selfie", "open selfie", "front camera", "selfie",
                "সেলফি তোলো", "সেলফি মোড", "সেলফি", "ফ্রন্ট ক্যামেরা"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "সেলফি ক্যামেরা চালু করছি।" else "Opening selfie camera.",
                language = lang,
                action = AssistantActionType.OPEN_SELFIE
            )
        }

        if (matchesAny(
                lower,
                "record video", "start video", "video recording", "video mode", "shoot video",
                "ভিডিও করো", "ভিডিও রেকর্ডিং", "ভিডিও চালু করো", "ভিডিও"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "ভিডিও রেকর্ডিং প্রস্তুত করছি।" else "Opening video recording.",
                language = lang,
                action = AssistantActionType.RECORD_VIDEO
            )
        }

        if (matchesAny(
                lower,
                "scan qr", "qr code", "qr scanner", "scan barcode",
                "কিউআর কোড", "কিউআর স্ক্যান", "qr স্ক্যান"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "কিউআর কোড স্ক্যানার চালু করছি।" else "Opening QR code scanner.",
                language = lang,
                action = AssistantActionType.SCAN_QR
            )
        }

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

        // 7.1 Find Contact (English + Bengali)
        val findContactEn = Regex("""^(?:find|search|lookup|look up|get)\s+(?:contact|number\s+for|phone\s+number\s+for)\s+(.+)$""", RegexOption.IGNORE_CASE)
        findContactEn.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return AiDecision(
                    reply = "Searching your contacts for $name.",
                    language = "en",
                    action = AssistantActionType.FIND_CONTACT,
                    targetName = name
                )
            }
        }
        val findContactBn = Regex("""^(.+?)(?:-এর|\s+এর|\s+র)?\s*(?:নম্বর|নাম্বার|কন্টাক্ট)\s*(?:খোঁজো|দেখাও|দাও|বলো)$""")
        findContactBn.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotBlank()) {
                return AiDecision(
                    reply = "আপনার কন্টাক্ট লিস্টে $name খুঁজছি।",
                    language = "bn",
                    action = AssistantActionType.FIND_CONTACT,
                    targetName = name
                )
            }
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

        // 8.5 Gallery, Files & Smart Features (Translation, Reminder, Notes, Voice Notes, Calendar, Suggestions, Web Search)
        if (matchesAny(
                lower,
                "open gallery", "show photos", "find photos", "search photos",
                "গ্যালারি খোলো", "গ্যালারি দেখাও", "গ্যালারি", "ছবি খোঁজো"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "গ্যালারি প্রস্তুত করছি।" else "Opening photo picker.",
                language = lang,
                action = AssistantActionType.OPEN_GALLERY
            )
        }

        if (matchesAny(
                lower,
                "open files", "browse files", "read pdf", "read document", "ocr", "extract text",
                "ফাইল খোলো", "ফাইল দেখাও", "পিডিএফ", "ডকুমেন্ট", "ইমেজ থেকে লেখা", "ছবি থেকে লেখা"
            )
        ) {
            return AiDecision(
                reply = if (isBn) "নিরাপদ ফাইল ব্রাউজার খুলছি।" else "Opening file browser.",
                language = lang,
                action = AssistantActionType.OPEN_FILES
            )
        }

        if (lower.contains("translate") || lower.contains("অনুবাদ")) {
            return AiDecision(
                reply = "",
                language = lang,
                action = AssistantActionType.TRANSLATE,
                targetName = text
            )
        }

        if (lower.contains("reminder") || lower.contains("remind me") || lower.contains("রিমাইন্ডার")) {
            return AiDecision(
                reply = if (isBn) "রিমাইন্ডার প্রস্তুত করছি।" else "Preparing reminder.",
                language = lang,
                action = AssistantActionType.SET_REMINDER,
                targetName = text
            )
        }

        if (lower.contains("voice note") || lower.contains("ভয়েস নোট")) {
            return AiDecision(
                reply = if (isBn) "ভয়েস নোট সংরক্ষণ করছি।" else "Saving voice note.",
                language = lang,
                action = AssistantActionType.VOICE_NOTE,
                targetName = text
            )
        }

        if (lower.contains("take a note") || lower.contains("save a note") || lower.contains("write a note") ||
            lower.contains("নোট লিখে") || lower.contains("নোট রাখ") || lower.contains("নোট সেভ")
        ) {
            return AiDecision(
                reply = if (isBn) "নোট সংরক্ষণ করছি।" else "Saving your note.",
                language = lang,
                action = AssistantActionType.ADD_NOTE,
                targetName = text
            )
        }

        if (lower.contains("calendar") || lower.contains("ক্যালেন্ডার")) {
            return AiDecision(
                reply = if (isBn) "আজকের ক্যালেন্ডার দেখাচ্ছি।" else "Opening today's calendar.",
                language = lang,
                action = AssistantActionType.SHOW_CALENDAR
            )
        }

        if (matchesAny(
                lower,
                "suggest something", "any suggestion", "suggestions", "smart suggestion",
                "সাজেশন দাও", "পরামর্শ দাও", "কিছু পরামর্শ"
            )
        ) {
            return AiDecision(
                reply = "",
                language = lang,
                action = AssistantActionType.AI_SUGGESTION
            )
        }

        val searchRegex = Regex("""^(?:search\s+(?:google\s+)?(?:for\s+)?|google\s+|সার্চ\s*করো\s+)(.+)$""", RegexOption.IGNORE_CASE)
        searchRegex.find(text)?.let { match ->
            val query = match.groupValues[1].trim()
            if (query.isNotBlank()) {
                return AiDecision(
                    reply = if (isBn) "\"$query\" সার্চ করছি।" else "Searching for $query.",
                    language = lang,
                    action = AssistantActionType.WEB_SEARCH,
                    targetName = query
                )
            }
        }

        // 8.8 Alarm, Timer, Stopwatch & Calculator (English + Bengali) — must precede generic Open App
        if (lower.contains("alarm") || lower.contains("অ্যালার্ম") || lower.contains("এলার্ম")) {
            return AiDecision(
                reply = if (isBn) "অ্যালার্ম প্রস্তুত করছি।" else "Preparing alarm.",
                language = lang,
                action = AssistantActionType.SET_ALARM,
                targetName = text
            )
        }

        if (lower.contains("timer") || lower.contains("টাইমার")) {
            return AiDecision(
                reply = if (isBn) "টাইমার চালু করছি।" else "Setting timer.",
                language = lang,
                action = AssistantActionType.SET_TIMER,
                targetName = text
            )
        }

        if (lower.contains("stopwatch") || lower.contains("স্টপওয়াচ") || lower.contains("স্টপ ওয়াচ")) {
            val swAction = when {
                lower.contains("reset") || lower.contains("রিসেট") -> "reset"
                lower.contains("lap") || lower.contains("ল্যাপ") -> "lap"
                lower.contains("pause") || lower.contains("stop") || lower.contains("থামাও") || lower.contains("বন্ধ") -> "pause"
                lower.contains("resume") || lower.contains("আবার") -> "resume"
                else -> "start"
            }
            return AiDecision(
                reply = if (isBn) "স্টপওয়াচ নিয়ন্ত্রণ করছি।" else "Updating stopwatch.",
                language = lang,
                action = AssistantActionType.STOPWATCH_CONTROL,
                targetName = swAction
            )
        }

        val calcRegex = Regex("""^(?:calculate|compute|math|what is|হিসাব করো|হিসাব)\s+(.+)$""", RegexOption.IGNORE_CASE)
        calcRegex.find(text)?.let { match ->
            val expr = match.groupValues[1].trim()
            if (expr.any { it.isDigit() }) {
                return AiDecision(
                    reply = "",
                    language = lang,
                    action = AssistantActionType.CALCULATE,
                    targetName = expr
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
