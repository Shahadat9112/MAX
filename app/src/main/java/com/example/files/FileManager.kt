package com.example.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central facade for MAX's Privacy-First Files System (`files/` package).
 *
 * Composes:
 * - [FileBrowser] (Storage Access Framework file picker)
 * - [PDFReader] (Android `PdfRenderer` read-only PDF handler)
 * - [DocumentReader] (bounded UTF-8 document reader)
 * - [ImageReader] (memory-safe image decoder)
 * - [OCRManager] (explicitly consented text extraction)
 *
 * Strictly enforces:
 * - Never reads or browses user files automatically.
 * - Never deletes or modifies files without explicit confirmation.
 * - Never uploads or shares files without permission.
 */
class FileManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext

    val fileBrowser = FileBrowser(appContext)
    val pdfReader = PDFReader(appContext)
    val documentReader = DocumentReader(appContext)
    val imageReader = ImageReader(appContext)
    val ocrManager = OCRManager(appContext, imageReader)

    private val _fileState = MutableStateFlow(FileState())
    val fileState: StateFlow<FileState> = _fileState.asStateFlow()

    fun createBrowseFilesIntent(isUserInitiated: Boolean): Intent? {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return null
        }
        _fileState.update { it.copy(status = FileModuleStatus.BROWSING) }
        return fileBrowser.createBrowseIntent(isUserInitiated = true)
    }

    suspend fun openSelectedFile(
        uri: Uri,
        isUserInitiated: Boolean = true
    ): Result<FileState> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Opening files requires explicit user request."))
        }

        val entry = fileBrowser.inspectDocument(uri, isUserInitiated = true)
            ?: return Result.failure(IllegalStateException("Unable to inspect selected file."))

        return when {
            entry.mimeType == "application/pdf" || entry.displayName.endsWith(".pdf", ignoreCase = true) -> {
                _fileState.update { it.copy(status = FileModuleStatus.READING_PDF, selectedDocument = entry) }
                pdfReader.inspectPdf(uri, pageIndex = 0, isUserInitiated = true).map { pdfRes ->
                    val next = _fileState.value.copy(
                        status = FileModuleStatus.IDLE,
                        selectedDocument = entry,
                        pdfPageCount = pdfRes.pageCount,
                        statusMessage = "Loaded PDF '${entry.displayName}' (${pdfRes.pageCount} pages)."
                    )
                    _fileState.value = next
                    next
                }.onFailure { err ->
                    _fileState.update { it.copy(status = FileModuleStatus.ERROR, statusMessage = err.localizedMessage) }
                }
            }

            entry.mimeType.startsWith("image/") -> {
                _fileState.update { it.copy(status = FileModuleStatus.READING_IMAGE, selectedDocument = entry) }
                imageReader.loadDownscaledBitmap(uri, isUserInitiated = true).map { bmp ->
                    val dims = "${bmp.width}x${bmp.height}"
                    bmp.recycle()
                    val next = _fileState.value.copy(
                        status = FileModuleStatus.IDLE,
                        selectedDocument = entry,
                        statusMessage = "Image '${entry.displayName}' ready ($dims)."
                    )
                    _fileState.value = next
                    next
                }.onFailure { err ->
                    _fileState.update { it.copy(status = FileModuleStatus.ERROR, statusMessage = err.localizedMessage) }
                }
            }

            else -> {
                _fileState.update { it.copy(status = FileModuleStatus.READING_DOCUMENT, selectedDocument = entry) }
                documentReader.readTextDocument(uri, isUserInitiated = true).map { text ->
                    val next = _fileState.value.copy(
                        status = FileModuleStatus.IDLE,
                        selectedDocument = entry,
                        extractedTextPreview = text.take(500),
                        statusMessage = "Document '${entry.displayName}' loaded."
                    )
                    _fileState.value = next
                    next
                }.onFailure { err ->
                    _fileState.update { it.copy(status = FileModuleStatus.ERROR, statusMessage = err.localizedMessage) }
                }
            }
        }
    }

    suspend fun extractTextWithOcr(
        uri: Uri,
        isUserInitiated: Boolean,
        uploadConsentGranted: Boolean,
        isOnline: Boolean
    ): Result<String> {
        _fileState.update { it.copy(status = FileModuleStatus.RUNNING_OCR) }
        val res = ocrManager.extractTextFromImage(
            uri = uri,
            isUserInitiated = isUserInitiated,
            uploadConsentGranted = uploadConsentGranted,
            isOnline = isOnline
        )
        res.onSuccess { text ->
            _fileState.update {
                it.copy(
                    status = FileModuleStatus.IDLE,
                    extractedTextPreview = text.take(500),
                    statusMessage = "OCR text extraction completed."
                )
            }
        }.onFailure { err ->
            _fileState.update {
                it.copy(
                    status = FileModuleStatus.ERROR,
                    statusMessage = err.localizedMessage
                )
            }
        }
        return res
    }

    /**
     * Never deletes or alters user files without explicit confirmation (`confirmed == true`).
     */
    fun confirmDeleteFile(uri: Uri, confirmed: Boolean): Boolean {
        if (!confirmed || !PrivacyController.assertUserInitiatedAction(confirmed)) {
            return false
        }
        return true
    }

    fun release() {
        _fileState.value = FileState()
    }
}
