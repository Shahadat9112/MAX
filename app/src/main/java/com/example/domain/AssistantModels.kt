package com.example.domain

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class VoiceLanguage(
    val label: String,
    val shortLabel: String,
    val bcp47Tag: String,
    val localeCandidates: List<String>
) {
    AUTO(
        label = "Auto (EN/বাং)",
        shortLabel = "EN / বাং",
        bcp47Tag = "en-US",
        localeCandidates = listOf("en-US", "bn-BD", "bn-IN")
    ),
    ENGLISH(
        label = "English",
        shortLabel = "EN",
        bcp47Tag = "en-US",
        localeCandidates = listOf("en-US", "en-GB", "en-IN")
    ),
    BENGALI(
        label = "বাংলা",
        shortLabel = "বাং",
        bcp47Tag = "bn-BD",
        localeCandidates = listOf("bn-BD", "bn-IN", "bn")
    )
}

data class SpeechRecognitionResult(
    val text: String,
    val detectedLanguage: String,
    val localeTag: String
)

enum class AssistantActionType {
    NONE,
    OPEN_APP,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    VOLUME_UNMUTE,
    VOLUME_SET,
    OPEN_SETTINGS,
    CALL_CONTACT,
    FIND_CONTACT,
    SEND_SMS,
    OPEN_CAMERA,
    OPEN_SELFIE,
    TAKE_PHOTO,
    RECORD_VIDEO,
    SCAN_QR,
    GET_TIME,
    GET_BATTERY,
    TOGGLE_FLASHLIGHT,
    SET_ALARM,
    SET_TIMER,
    STOPWATCH_CONTROL,
    CALCULATE,
    TRANSLATE,
    SET_REMINDER,
    ADD_NOTE,
    VOICE_NOTE,
    SHOW_CALENDAR,
    AI_SUGGESTION,
    OPEN_GALLERY,
    OPEN_FILES,
    WEB_SEARCH,
    CLEAR_CHAT
}

data class AiDecision(
    val reply: String,
    val language: String = "en",
    val action: AssistantActionType = AssistantActionType.NONE,
    val targetName: String? = null,
    val messageBody: String? = null,
    val volumePercent: Int? = null
)

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

sealed interface PendingAction {
    data class ConfirmCall(
        val contact: ContactMatch,
        val language: String
    ) : PendingAction

    data class AwaitSmsBody(
        val contact: ContactMatch,
        val language: String
    ) : PendingAction

    data class ConfirmSms(
        val contact: ContactMatch,
        val messageBody: String,
        val language: String
    ) : PendingAction

    data class RequestPermission(
        val permission: String,
        val rationale: String,
        val pendingRetryCommand: String
    ) : PendingAction
}
