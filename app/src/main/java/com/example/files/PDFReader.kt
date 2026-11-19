package com.example.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.permissions.PrivacyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Metadata and safe downscaled preview result from reading a user-selected PDF.
 */
data class PdfReadResult(
    val pageCount: Int,
    val renderedPageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int
)

/**
 * Memory-optimized PDF reader built on Android's native [PdfRenderer] (API 21+).
 *
 * Rules:
 * - Read-only access only when user explicitly selects a PDF.
 * - Immediately closes [PdfRenderer.Page], [PdfRenderer], and `ParcelFileDescriptor`
 *   to keep RAM and battery footprint minimal across low-end devices.
 */
class PDFReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun inspectPdf(
        uri: Uri,
        pageIndex: Int = 0,
        isUserInitiated: Boolean = true
    ): Result<PdfReadResult> = withContext(Dispatchers.IO) {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return@withContext Result.failure(SecurityException("PDF reading requires explicit user request."))
        }

        runCatching {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Unable to open PDF descriptor.")

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val total = renderer.pageCount
                    if (total <= 0) {
                        throw IllegalStateException("PDF contains no readable pages.")
                    }
                    val safeIndex = pageIndex.coerceIn(0, total - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val maxDim = 1024
                        val scale = minOf(
                            maxDim.toFloat() / page.width.coerceAtLeast(1),
                            maxDim.toFloat() / page.height.coerceAtLeast(1),
                            1.5f
                        )
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } finally {
                            bitmap.recycle()
                        }
                        PdfReadResult(
                            pageCount = total,
                            renderedPageIndex = safeIndex,
                            pageWidth = page.width,
                            pageHeight = page.height
                        )
                    }
                }
            }
        }
    }
}
