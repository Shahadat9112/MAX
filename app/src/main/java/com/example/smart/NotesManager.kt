package com.example.smart

import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-first Notes controller for MAX.
 *
 * Handles commands such as `"MAX, একটা নোট লিখে রাখো।"`
 * Keeps notes in volatile session memory unless explicit storage consent is granted via [com.example.memory.MemoryManager].
 */
class NotesManager {
    private val idGen = AtomicLong(1L)

    private val _notes = MutableStateFlow<List<SmartNoteItem>>(emptyList())
    val notes: StateFlow<List<SmartNoteItem>> = _notes.asStateFlow()

    fun addNote(
        content: String,
        isUserInitiated: Boolean = true,
        isVoiceNote: Boolean = false
    ): Result<SmartNoteItem> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Saving notes requires explicit user request."))
        }
        val clean = content.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Note content cannot be empty."))
        }
        val item = SmartNoteItem(
            id = idGen.getAndIncrement(),
            content = clean,
            createdAtMs = System.currentTimeMillis(),
            isVoiceNote = isVoiceNote
        )
        _notes.value = listOf(item) + _notes.value
        return Result.success(item)
    }

    fun clearNotes(confirmed: Boolean): Boolean {
        if (!confirmed || !PrivacyController.assertUserInitiatedAction(confirmed)) {
            return false
        }
        _notes.value = emptyList()
        return true
    }
}
