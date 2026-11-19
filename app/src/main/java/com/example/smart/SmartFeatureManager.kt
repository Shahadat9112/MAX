package com.example.smart

import android.content.Context
import com.example.emotion.EmotionManager
import com.example.memory.MemoryManager
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central facade for MAX's Smart Features System (`smart/` package).
 *
 * Composes:
 * - [TranslationManager]
 * - [ReminderManager]
 * - [NotesManager]
 * - [CalendarManager]
 * - [VoiceNotesManager]
 * - [AISuggestionManager]
 *
 * Connects cleanly with [EmotionManager], [MemoryManager], [PermissionManager], and [com.example.core.MAXCore].
 */
class SmartFeatureManager(
    context: Context,
    private val permissionManager: PermissionManager,
    private val emotionManager: EmotionManager,
    private val memoryManager: MemoryManager
) {
    private val appContext = context.applicationContext

    val translationManager = TranslationManager()
    val reminderManager = ReminderManager(appContext)
    val notesManager = NotesManager()
    val calendarManager = CalendarManager(appContext)
    val voiceNotesManager = VoiceNotesManager(notesManager)

    private val _smartState = MutableStateFlow(SmartFeatureState())
    val smartState: StateFlow<SmartFeatureState> = _smartState.asStateFlow()

    suspend fun translateText(
        rawCommandOrText: String,
        targetLanguage: String = translationManager.inferTargetLanguage(rawCommandOrText),
        isOnline: Boolean
    ): Result<String> {
        _smartState.update { it.copy(status = SmartFeatureStatus.TRANSLATING) }
        val result = translationManager.translate(
            sourceText = rawCommandOrText,
            targetLanguage = targetLanguage,
            isOnline = isOnline
        )
        result.onSuccess { translated ->
            _smartState.update {
                it.copy(
                    status = SmartFeatureStatus.IDLE,
                    lastTranslation = translated,
                    statusMessage = "Translation completed."
                )
            }
        }.onFailure { err ->
            _smartState.update {
                it.copy(
                    status = SmartFeatureStatus.ERROR,
                    statusMessage = err.localizedMessage
                )
            }
        }
        return result
    }

    fun createReminder(
        rawCommand: String,
        isUserInitiated: Boolean = true,
        openClockUi: Boolean = true
    ): Result<SmartReminderItem> {
        _smartState.update { it.copy(status = SmartFeatureStatus.CREATING_REMINDER) }
        val result = reminderManager.createReminderFromCommand(
            rawCommand = rawCommand,
            isUserInitiated = isUserInitiated,
            openClockUi = openClockUi
        )
        result.onSuccess { reminder ->
            _smartState.update {
                it.copy(
                    status = SmartFeatureStatus.IDLE,
                    reminders = reminderManager.reminders.value,
                    statusMessage = "Reminder prepared for ${reminder.formattedTime}."
                )
            }
        }.onFailure { err ->
            _smartState.update {
                it.copy(
                    status = SmartFeatureStatus.ERROR,
                    statusMessage = err.localizedMessage
                )
            }
        }
        return result
    }

    fun saveNote(
        content: String,
        isUserInitiated: Boolean = true,
        isVoiceNote: Boolean = false
    ): Result<SmartNoteItem> {
        _smartState.update {
            it.copy(status = if (isVoiceNote) SmartFeatureStatus.RECORDING_VOICE_NOTE else SmartFeatureStatus.SAVING_NOTE)
        }
        val result = if (isVoiceNote) {
            voiceNotesManager.saveVoiceNote(content, isUserInitiated)
        } else {
            notesManager.addNote(content, isUserInitiated, isVoiceNote = false)
        }
        result.onSuccess {
            _smartState.update { state ->
                state.copy(
                    status = SmartFeatureStatus.IDLE,
                    notes = notesManager.notes.value,
                    statusMessage = "Note saved."
                )
            }
        }.onFailure { err ->
            _smartState.update { state ->
                state.copy(
                    status = SmartFeatureStatus.ERROR,
                    statusMessage = err.localizedMessage
                )
            }
        }
        return result
    }

    fun openTodaysCalendar(isUserInitiated: Boolean = true): Result<String> {
        _smartState.update { it.copy(status = SmartFeatureStatus.VIEWING_CALENDAR) }
        val result = calendarManager.openTodaysCalendar(isUserInitiated)
        _smartState.update {
            it.copy(
                status = if (result.isSuccess) SmartFeatureStatus.IDLE else SmartFeatureStatus.ERROR,
                statusMessage = result.getOrNull() ?: result.exceptionOrNull()?.localizedMessage
            )
        }
        return result
    }

    fun getContextualSuggestion(isBengali: Boolean): String {
        _smartState.update { it.copy(status = SmartFeatureStatus.GENERATING_SUGGESTION) }
        val suggestion = AISuggestionManager.generateContextualSuggestion(
            emotionState = emotionManager.currentEmotion.value,
            isBengali = isBengali
        )
        _smartState.update {
            it.copy(
                status = SmartFeatureStatus.IDLE,
                lastSuggestion = suggestion
            )
        }
        return suggestion
    }

    fun release() {
        _smartState.update { it.copy(status = SmartFeatureStatus.IDLE) }
    }
}
