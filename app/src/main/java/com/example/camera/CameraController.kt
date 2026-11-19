package com.example.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller managing camera mode switching (photo, selfie, video, QR scan) and UI state.
 */
class CameraController {
    private val _cameraState = MutableStateFlow(CameraState.CLOSED)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _isSelfieMode = MutableStateFlow(false)
    val isSelfieMode: StateFlow<Boolean> = _isSelfieMode.asStateFlow()

    fun setCameraState(state: CameraState) {
        _cameraState.value = state
    }

    fun toggleSelfieMode() {
        _isSelfieMode.value = !_isSelfieMode.value
    }

    fun reset() {
        _cameraState.value = CameraState.CLOSED
        _isSelfieMode.value = false
    }
}
