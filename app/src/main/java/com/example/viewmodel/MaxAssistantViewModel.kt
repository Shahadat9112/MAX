package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.core.MAXCore
import com.example.domain.VoiceLanguage
import com.example.ui.MaxUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MVVM ViewModel in the [com.example.viewmodel] package exposing reactive [MaxUiState]
 * and permission request streams from [MAXCore] to Jetpack Compose UI screens.
 */
class MaxAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val core = MAXCore.getInstance(application.applicationContext)

    val uiState: StateFlow<MaxUiState> = core.uiState
    val permissionRequests: SharedFlow<Array<String>> = core.permissionRequests
    val mediaPickerRequests: SharedFlow<Unit> = core.mediaPickerRequests
    val fileBrowserRequests: SharedFlow<Array<String>> = core.fileBrowserRequests
    val videoCaptureRequests: SharedFlow<Unit> = core.videoCaptureRequests

    fun handleWakeWordTriggered(command: String) = core.handleWakeWordTriggered(command)
    fun toggleMicrophone() = core.toggleMicrophone()
    fun submitUserInput(rawText: String, fromVoice: Boolean = false) =
        core.submitUserInput(rawText, fromVoice)

    fun toggleTts() = core.toggleTts()
    fun setLanguage(language: VoiceLanguage) = core.setLanguage(language)
    fun cycleLanguage() = core.cycleLanguage()
    fun setAutoListen(enabled: Boolean) = core.setAutoListen(enabled)
    fun setStrictPrivacyMode(enabled: Boolean) = core.setStrictPrivacyMode(enabled)
    fun setSaveHistoryConsent(consented: Boolean) = core.setSaveHistoryConsent(consented)
    fun eraseAllLocalData() = core.eraseAllLocalData()
    fun toggleBackgroundWakeWord(enabled: Boolean) = core.toggleBackgroundWakeWord(enabled)
    fun openCameraScreen(autoCapture: Boolean) = core.openCameraScreen(autoCapture)
    fun onPhotoSaved(uri: Uri) = core.onPhotoSaved(uri)
    fun onGalleryPhotoSelected(uri: Uri) = core.onGalleryPhotoSelected(uri)
    fun onFileSelected(uri: Uri) = core.onFileSelected(uri)
    fun onVideoRecorded(success: Boolean) = core.onVideoRecorded(success)
    fun onCameraError(errorMsg: String) = core.onCameraError(errorMsg)
    fun closeCamera() = core.closeCamera()
    fun confirmPendingCall() = core.confirmPendingCall()
    fun confirmPendingSms(editedBody: String) = core.confirmPendingSms(editedBody)
    fun readSmsAloud(messageBody: String, language: String) =
        core.readSmsAloud(messageBody, language)

    fun cancelPendingAction() = core.cancelPendingAction()
    fun clearConversation() = core.clearConversation()
    fun dismissBannerError() = core.dismissBannerError()
    fun requestPermissions(permissions: Array<String>) = core.requestPermissions(permissions)
    fun onPermissionsUpdated(results: Map<String, Boolean>) = core.onPermissionsUpdated(results)
    fun refreshPermissionHealth() = core.refreshPermissionHealth()
    fun ensureBackgroundWakeService() = core.ensureBackgroundWakeServiceIfEnabled()
}
