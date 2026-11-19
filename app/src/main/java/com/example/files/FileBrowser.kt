package com.example.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.permissions.PrivacyController

/**
 * Privacy-first file picker & metadata resolver using Android's Storage Access Framework (SAF).
 *
 * Rules:
 * - Never scans internal/external storage in the background.
 * - Uses `Intent.ACTION_OPEN_DOCUMENT` so the user explicitly picks which single file MAX can view.
 */
class FileBrowser(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Builds a Storage Access Framework [Intent] filtered by optional [mimeTypes]
     * strictly upon explicit user request.
     */
    fun createBrowseIntent(
        mimeTypes: Array<String> = arrayOf("application/pdf", "text/*", "image/*"),
        isUserInitiated: Boolean = true
    ): Intent? {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return null
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Reads display name, MIME type, and size for a user-selected [uri] without modifying the file.
     */
    fun inspectDocument(
        uri: Uri,
        isUserInitiated: Boolean = true
    ): MaxDocumentEntry? {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return null
        }
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var name = "Selected File"
        var size = -1L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
        }

        return MaxDocumentEntry(
            uri = uri,
            displayName = name,
            mimeType = mimeType,
            sizeBytes = size
        )
    }
}
