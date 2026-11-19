package com.example.gallery

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller managing gallery browsing, photo selection, image search, and OCR workflows.
 */
class GalleryController {
    private val _galleryState = MutableStateFlow(GalleryState.CLOSED)
    val galleryState: StateFlow<GalleryState> = _galleryState.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<Uri?>(null)
    val selectedPhoto: StateFlow<Uri?> = _selectedPhoto.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GalleryPhotoItem>>(emptyList())
    val searchResults: StateFlow<List<GalleryPhotoItem>> = _searchResults.asStateFlow()

    fun selectPhoto(uri: Uri) {
        _selectedPhoto.value = uri
        _galleryState.value = GalleryState.PHOTO_SELECTED
    }

    fun setSearchResults(results: List<GalleryPhotoItem>) {
        _searchResults.value = results
        _galleryState.value = GalleryState.SEARCHING
    }

    fun setOcrProcessing() {
        _galleryState.value = GalleryState.OCR_PROCESSING
    }

    fun reset() {
        _galleryState.value = GalleryState.CLOSED
        _selectedPhoto.value = null
        _searchResults.value = emptyList()
    }
}
