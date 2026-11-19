package com.example.gallery

import android.net.Uri

/**
 * Gallery operational states.
 */
enum class GalleryState {
    CLOSED,
    BROWSING,
    PHOTO_SELECTED,
    SEARCHING,
    OCR_PROCESSING
}

data class GalleryPhotoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val timestamp: Long
)
