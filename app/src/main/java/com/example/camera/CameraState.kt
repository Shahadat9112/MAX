package com.example.camera

/**
 * Camera operational states.
 */
enum class CameraState {
    CLOSED,
    OPENING,
    READY,
    TAKING_PHOTO,
    RECORDING_VIDEO,
    SCANNING_QR,
    ERROR
}

data class CameraConfig(
    val useFrontCamera: Boolean = false,
    val enableVideoRecording: Boolean = true,
    val enableQrScanning: Boolean = true
)
