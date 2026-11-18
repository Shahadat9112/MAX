package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.data.ChatMessageEntity
import com.example.data.UserPreferences
import com.example.domain.AssistantStatus
import com.example.domain.PendingAction
import com.example.domain.VoiceLanguage
import com.example.service.MaxAssistantController
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class MaxUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val messages: List<ChatMessageEntity> = emptyList(),
    val partialTranscript: String = "",
    val audioLevel: Float = 0f,
    val activeSpeechLocale: String = "en-US",
    val isOnline: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val pendingAction: PendingAction? = null,
    val isCameraOpen: Boolean = false,
    val autoCaptureOnCameraOpen: Boolean = false,
    val lastCapturedPhotoUri: Uri? = null,
    val isWakeServiceRunning: Boolean = false,
    val wakeServiceStatusText: String = "Standby",
    val permissionHealth: Map<String, Boolean> = emptyMap(),
    val bannerError: String? = null
)

class MaxViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = MaxAssistantController.getInstance(application.applicationContext)

    val uiState: StateFlow<MaxUiState> = controller.uiState
    val permissionRequests: SharedFlow<Array<String>> = controller.permissionRequests

    fun handleWakeWordTriggered(command: String) = controller.handleWakeWordTriggered(command)
    fun toggleMicrophone() = controller.toggleMicrophone()
    fun submitUserInput(rawText: String, fromVoice: Boolean = false) =
        controller.submitUserInput(rawText, fromVoice)

    fun toggleTts() = controller.toggleTts()
    fun setLanguage(language: VoiceLanguage) = controller.setLanguage(language)
    fun cycleLanguage() = controller.cycleLanguage()
    fun setAutoListen(enabled: Boolean) = controller.setAutoListen(enabled)
    fun setStrictPrivacyMode(enabled: Boolean) = controller.setStrictPrivacyMode(enabled)
    fun setSaveHistoryConsent(consented: Boolean) = controller.setSaveHistoryConsent(consented)
    fun eraseAllLocalData() = controller.eraseAllLocalData()
    fun toggleBackgroundWakeWord(enabled: Boolean) = controller.toggleBackgroundWakeWord(enabled)
    fun openCameraScreen(autoCapture: Boolean) = controller.openCameraScreen(autoCapture)
    fun onPhotoSaved(uri: Uri) = controller.onPhotoSaved(uri)
    fun onCameraError(errorMsg: String) = controller.onCameraError(errorMsg)
    fun closeCamera() = controller.closeCamera()
    fun confirmPendingCall() = controller.confirmPendingCall()
    fun confirmPendingSms(editedBody: String) = controller.confirmPendingSms(editedBody)
    fun readSmsAloud(messageBody: String, language: String) =
        controller.readSmsAloud(messageBody, language)

    fun cancelPendingAction() = controller.cancelPendingAction()
    fun clearConversation() = controller.clearConversation()
    fun dismissBannerError() = controller.dismissBannerError()
    fun requestPermissions(permissions: Array<String>) = controller.requestPermissions(permissions)
    fun onPermissionsUpdated(results: Map<String, Boolean>) = controller.onPermissionsUpdated(results)
    fun refreshPermissionHealth() = controller.refreshPermissionHealth()
    fun ensureBackgroundWakeService() = controller.ensureBackgroundWakeServiceIfEnabled()
}
