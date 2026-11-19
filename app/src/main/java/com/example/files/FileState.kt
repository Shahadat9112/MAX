package com.example.files

import android.net.Uri

/**
 * Operational states for MAX's Privacy-First Files System (`files/`).
 */
enum class FileModuleStatus {
    IDLE,
    BROWSING,
    AWAITING_CONFIRMATION,
    READING_PDF,
    READING_DOCUMENT,
    READING_IMAGE,
    RUNNING_OCR,
    PERMISSION_REQUIRED,
    ERROR
}

/**
 * Read-only metadata snapshot for a user-selected document or image.
 */
data class MaxDocumentEntry(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

/**
 * Immutable state snapshot exposed by [FileManager].
 */
data class FileState(
    val status: FileModuleStatus = FileModuleStatus.IDLE,
    val selectedDocument: MaxDocumentEntry? = null,
    val pdfPageCount: Int = 0,
    val extractedTextPreview: String? = null,
    val statusMessage: String? = null
)
