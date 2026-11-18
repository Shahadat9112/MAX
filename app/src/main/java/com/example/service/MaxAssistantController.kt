package com.example.service

import android.content.Context
import android.net.Uri
import com.example.core.MAXCore
import com.example.domain.VoiceLanguage
import com.example.ui.MaxUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Backward-compatible service-package facade delegating all assistant orchestration
 * to [MAXCore] so existing service callers remain decoupled and state stays unified.
 */
class MaxAssistantController private constructor(context: Context) {
    private val core = MAXCore.getInstance(context.applicationContext)

    val permissionRequests: SharedFlow<Array<String>> = core.permissionRequests
    val uiState: StateFlow<MaxUiState> = core.uiState

    fun ensureBackgroundWakeServiceIfEnabled() = core.ensureBackgroundWakeServiceIfEnabled()
    fun handleWakeWordTriggered(command: String) = core.handleWakeWordTriggered(command)
    fun toggleMicrophone() = core.toggleMicrophone()
    fun startVoiceListening() = core.startVoiceListening()
    fun dismissBannerError() = core.dismissBannerError()
    fun submitUserInput(
        rawText: String,
        fromVoice: Boolean = false,
        spokenLanguageHint: String? = null
    ) = core.submitUserInput(rawText, fromVoice, spokenLanguageHint)

    fun openCameraScreen(autoCapture: Boolean, language: String = "en", isOffline: Boolean = false) =
        core.openCameraScreen(autoCapture, language, isOffline)

    fun onPhotoSaved(uri: Uri) = core.onPhotoSaved(uri)
    fun onCameraError(errorMsg: String) = core.onCameraError(errorMsg)
    fun closeCamera() = core.closeCamera()
    fun confirmPendingCall() = core.confirmPendingCall()
    fun readSmsAloud(messageBody: String, language: String) = core.readSmsAloud(messageBody, language)
    fun confirmPendingSms(editedBody: String) = core.confirmPendingSms(editedBody)
    fun cancelPendingAction() = core.cancelPendingAction()
    fun toggleTts() = core.toggleTts()
    fun setLanguage(language: VoiceLanguage) = core.setLanguage(language)
    fun cycleLanguage() = core.cycleLanguage()
    fun setAutoListen(enabled: Boolean) = core.setAutoListen(enabled)
    fun setStrictPrivacyMode(enabled: Boolean) = core.setStrictPrivacyMode(enabled)
    fun setSaveHistoryConsent(consented: Boolean) = core.setSaveHistoryConsent(consented)
    fun eraseAllLocalData() = core.eraseAllLocalData()
    fun toggleBackgroundWakeWord(enabled: Boolean) = core.toggleBackgroundWakeWord(enabled)
    fun clearConversation() = core.clearConversation()
    fun requestPermissions(permissions: Array<String>) = core.requestPermissions(permissions)
    fun onPermissionsUpdated(results: Map<String, Boolean>) = core.onPermissionsUpdated(results)
    fun refreshPermissionHealth() = core.refreshPermissionHealth()
    fun hasPermission(permission: String): Boolean = core.hasPermission(permission)

    companion object {
        @Volatile
        private var INSTANCE: MaxAssistantController? = null

        fun getInstance(context: Context): MaxAssistantController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MaxAssistantController(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }
}
