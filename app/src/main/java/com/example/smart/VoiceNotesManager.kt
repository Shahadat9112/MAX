package com.example.smart

import com.example.permissions.PrivacyController

/**
 * Voice Notes controller built on [NotesManager].
 *
 * Rules:
 * - Never records microphone audio silently in background.
 * - Captures already-recognized speech transcripts as structured voice notes ONLY
 *   after explicit user initiation.
 */
class VoiceNotesManager(
    private val notesManager: NotesManager
) {
    fun saveVoiceNote(
        transcript: String,
        isUserInitiated: Boolean = true
    ): Result<SmartNoteItem> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Voice notes require explicit user command."))
        }
        return notesManager.addNote(
            content = transcript,
            isUserInitiated = true,
            isVoiceNote = true
        )
    }
}
