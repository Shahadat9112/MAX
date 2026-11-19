package com.example.camera

import android.content.Context
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Independent manager enforcing MAX camera rules:
 * - Never opens automatically.
 * - Only opens after explicit user command and permission verification via [PermissionManager].
 * - Supports photo capture, selfie mode, video recording, and QR code scanning.
 * - Closes resources properly after task completion.
 */
class CameraManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    val controller = CameraController()

    fun requestOpenCameraController(autoCapture: Boolean): Boolean {
        if (!permissionManager.hasCameraPermission()) {
            return false
        }
        _isCameraActive.value = true
        controller.setCameraState(if (autoCapture) CameraState.TAKING_PHOTO else CameraState.READY)
        return true
    }

    fun startVideoRecording(): Boolean {
        if (!_isCameraActive.value) return false
        controller.setCameraState(CameraState.RECORDING_VIDEO)
        return true
    }

    fun startQrScanning(): Boolean {
        if (!_isCameraActive.value) return false
        controller.setCameraState(CameraState.SCANNING_QR)
        return true
    }

    fun closeCamera() {
        _isCameraActive.value = false
        controller.reset()
    }
}
