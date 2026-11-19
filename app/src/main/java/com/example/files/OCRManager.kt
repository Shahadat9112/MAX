package com.example.files

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.permissions.PrivacyController
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Privacy-first Optical Character Recognition (OCR) manager for MAX.
 *
 * Rules:
 * - Never runs OCR or image scanning in the background.
 * - Requires both explicit user action (`isUserInitiated == true`) AND explicit upload consent
 *   (`uploadConsentGranted == true`) before calling cloud Vision AI.
 * - Immediately recycles temporary bitmaps after extraction to protect memory.
 */
class OCRManager(
    context: Context,
    private val imageReader: ImageReader = ImageReader(context.applicationContext)
) {
    private val visionModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun extractTextFromImage(
        uri: Uri,
        isUserInitiated: Boolean = true,
        uploadConsentGranted: Boolean = false,
        isOnline: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return@withContext Result.failure(SecurityException("OCR requires explicit user request."))
        }
        if (!uploadConsentGranted) {
            return@withContext Result.failure(
                SecurityException("User permission is required before analyzing an image with cloud OCR.")
            )
        }
        if (!isOnline) {
            return@withContext Result.failure(
                IllegalStateException("OCR vision extraction requires an active internet connection.")
            )
        }

        val bitmapResult = imageReader.loadDownscaledBitmap(uri, maxDimensionPx = 1024, isUserInitiated = true)
        val bitmap: Bitmap = bitmapResult.getOrElse { return@withContext Result.failure(it) }

        try {
            val response = withTimeout(30_000L) {
                visionModel.generateContent(
                    content {
                        image(bitmap)
                        text("Extract and transcribe all readable text from this image accurately without commentary.")
                    }
                )
            }
            val extracted = response.text?.trim().orEmpty()
            if (extracted.isEmpty()) {
                Result.failure(IllegalStateException("No readable text detected in the image."))
            } else {
                Result.success(extracted)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmap.recycle()
        }
    }
}
