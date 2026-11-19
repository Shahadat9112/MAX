package com.example.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.permissions.PrivacyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-safe, downscaling image reader for user-selected photos and document scans.
 *
 * Uses `inJustDecodeBounds` + `inSampleSize` so large photos never cause memory spikes
 * on budget Android devices.
 */
class ImageReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun loadDownscaledBitmap(
        uri: Uri,
        maxDimensionPx: Int = 1280,
        isUserInitiated: Boolean = true
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return@withContext Result.failure(SecurityException("Image access requires explicit user request."))
        }

        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: throw IllegalStateException("Unable to open image stream.")

            val sampleSize = calculateInSampleSize(
                boundsOpts.outWidth,
                boundsOpts.outHeight,
                maxDimensionPx,
                maxDimensionPx
            )

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalStateException("Unable to decode selected image.")

            bitmap
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
