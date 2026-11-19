package com.example.phone

import android.Manifest
import android.content.Context
import com.example.device.SmsHelper
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController

/**
 * Privacy-first SMS controller for MAX.
 *
 * Rules:
 * - Never reads incoming SMS or inbox history automatically.
 * - Never sends an SMS automatically without explicit user review and confirmation.
 * - Verifies `SEND_SMS` permission via [PermissionManager] before sending.
 */
class SMSManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val smsHelper = SmsHelper(context.applicationContext)

    fun hasSendSmsPermission(): Boolean = permissionManager.hasSmsPermission()

    fun requiredPermission(): String = Manifest.permission.SEND_SMS

    /**
     * Sends an SMS message strictly after explicit user confirmation and permission verification.
     */
    fun sendSms(
        phoneNumber: String,
        messageBody: String,
        isUserConfirmed: Boolean
    ): Result<Unit> {
        if (!PrivacyController.assertUserInitiatedAction(isUserConfirmed) || !isUserConfirmed) {
            return Result.failure(SecurityException("SMS dispatch requires explicit user confirmation."))
        }
        if (!PrivacyController.verifyPermissionBeforeAction("SEND_SMS", hasSendSmsPermission())) {
            return Result.failure(SecurityException("SEND_SMS permission is not granted."))
        }
        return smsHelper.sendSms(phoneNumber, messageBody)
    }
}
