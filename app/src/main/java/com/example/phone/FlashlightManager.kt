package com.example.phone

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware-safe Flashlight controller for MAX.
 *
 * Rules:
 * - Never enables the flashlight automatically without explicit user command.
 * - Handles missing flash hardware and OEM Camera2 quirks gracefully without crashing.
 * - Releases/turns off the torch safely when [release] is called.
 */
class FlashlightManager(context: Context) {
    private val appContext = context.applicationContext
    private val systemCameraManager: CameraManager? = runCatching {
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }.getOrNull()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    fun setFlashlight(
        enabled: Boolean,
        isUserInitiated: Boolean = true
    ): Result<Boolean> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Flashlight control requires explicit user request."))
        }
        val camMgr = systemCameraManager
            ?: return Result.failure(IllegalStateException("Camera service unavailable on this device."))

        return runCatching {
            val cameraId = camMgr.cameraIdList.firstOrNull { id ->
                val chars = camMgr.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw IllegalStateException("No flashlight available on this device.")

            camMgr.setTorchMode(cameraId, enabled)
            _isTorchOn.value = enabled
            enabled
        }.onFailure { e ->
            Log.w(TAG, "Unable to change flashlight state", e)
        }
    }

    fun toggleFlashlight(isUserInitiated: Boolean = true): Result<Boolean> {
        return setFlashlight(enabled = !_isTorchOn.value, isUserInitiated = isUserInitiated)
    }

    fun release() {
        if (_isTorchOn.value) {
            runCatching {
                setFlashlight(enabled = false, isUserInitiated = true)
            }
        }
    }

    companion object {
        private const val TAG = "FlashlightManager"
    }
}
