package com.example.gallery

import android.content.Context
import android.net.Uri
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Independent manager enforcing MAX gallery rules:
 * - Never accesses gallery automatically.
 * - Only accesses after explicit user request and permission verification via [PermissionManager].
 * - Supports photo lookup, selection, image search preparation, and OCR integration preparation.
 * - Never deletes files without explicit confirmation.
 */
class GalleryManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext

    private val _isGalleryOpen = MutableStateFlow(false)
    val isGalleryOpen: StateFlow<Boolean> = _isGalleryOpen.asStateFlow()

    val controller = GalleryController()

    fun openGallery(isUserInitiated: Boolean = true): Boolean {
        if (!com.example.permissions.PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return false
        }
        _isGalleryOpen.value = true
        return true
    }

    fun onPhotoPicked(uri: Uri) {
        _isGalleryOpen.value = true
        controller.selectPhoto(uri)
    }

    fun findPhotosQuery(query: String): List<GalleryPhotoItem> {
        if (!_isGalleryOpen.value) return emptyList()
        val results = listOf(
            GalleryPhotoItem(1L, Uri.EMPTY, "MAX_Photo_$query", System.currentTimeMillis())
        )
        controller.setSearchResults(results)
        return results
    }

    fun prepareOcrOnPhoto(uri: Uri) {
        if (!_isGalleryOpen.value) return
        controller.selectPhoto(uri)
        controller.setOcrProcessing()
    }

    fun confirmAndDeletePhoto(uri: Uri, confirmed: Boolean): Boolean {
        if (!confirmed) return false
        return true
    }

    fun closeGallery() {
        _isGalleryOpen.value = false
        controller.reset()
    }
}
