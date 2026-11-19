package com.example.smart

/**
 * Operational states for MAX's Smart Features System (`smart/`).
 */
enum class SmartFeatureStatus {
    IDLE,
    TRANSLATING,
    CREATING_REMINDER,
    SAVING_NOTE,
    VIEWING_CALENDAR,
    RECORDING_VOICE_NOTE,
    GENERATING_SUGGESTION,
    ERROR
}

data class SmartNoteItem(
    val id: Long,
    val content: String,
    val createdAtMs: Long,
    val isVoiceNote: Boolean = false
)

data class SmartReminderItem(
    val id: Long,
    val title: String,
    val hour: Int,
    val minute: Int,
    val dayOffset: Int,
    val formattedTime: String
)

/**
 * Immutable state snapshot exposed across MAX's Smart Features System.
 */
data class SmartFeatureState(
    val status: SmartFeatureStatus = SmartFeatureStatus.IDLE,
    val notes: List<SmartNoteItem> = emptyList(),
    val reminders: List<SmartReminderItem> = emptyList(),
    val lastTranslation: String? = null,
    val lastSuggestion: String? = null,
    val statusMessage: String? = null
)
