package com.example.files

import android.content.Context
import android.net.Uri
import com.example.permissions.PrivacyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Bounded, read-only document reader for plain-text and UTF-8 files (`.txt`, `.md`, `.csv`, `.json`).
 *
 * Caps read size cleanly to avoid OutOfMemory errors on low-end devices and never writes or deletes files.
 */
class DocumentReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun readTextDocument(
        uri: Uri,
        maxChars: Int = 16_000,
        isUserInitiated: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return@withContext Result.failure(SecurityException("Document reading requires explicit user request."))
        }

        runCatching {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected document.")
            stream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    val buffer = CharArray(2048)
                    var remaining = maxChars
                    while (remaining > 0) {
                        val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read == -1) break
                        sb.appendRange(buffer, 0, read)
                        remaining -= read
                    }
                    sb.toString().trim()
                }
            }
        }
    }
}
