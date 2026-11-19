package com.example.phone

import android.Manifest
import android.content.Context
import com.example.device.PhoneCallManager
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController

/**
 * Privacy-first call controller for MAX.
 *
 * Rules:
 * - Never reads call history automatically.
 * - Never dials or places a call automatically without explicit user request and confirmation.
 * - Falls back gracefully to the system dialer (`ACTION_DIAL`) when `CALL_PHONE` is not granted.
 */
class CallManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val phoneCallManager = PhoneCallManager(context.applicationContext)

    fun hasCallPermission(): Boolean = permissionManager.hasCallPermission()

    fun requiredPermission(): String = Manifest.permission.CALL_PHONE

    /**
     * Initiates a phone call or opens the system dialer ONLY after explicit user request
     * and confirmation.
     */
    fun placeCall(
        phoneNumber: String,
        isUserConfirmed: Boolean
    ): Result<Boolean> {
        if (!PrivacyController.assertUserInitiatedAction(isUserConfirmed) || !isUserConfirmed) {
            return Result.failure(SecurityException("Call requires explicit user confirmation."))
        }
        return phoneCallManager.makeCall(phoneNumber)
    }
}
