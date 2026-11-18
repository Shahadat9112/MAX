package com.example.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Dispatches single and multipart SMS text messages after verifying SEND_SMS permission.
 */
class SmsHelper(private val context: Context) {

    fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun sendSms(phoneNumber: String, message: String): Result<Unit> {
        if (!hasSendSmsPermission()) {
            return Result.failure(SecurityException("SEND_SMS permission is not granted."))
        }
        val cleanNumber = phoneNumber.trim()
        val cleanMessage = message.trim()
        if (cleanNumber.isEmpty() || cleanMessage.isEmpty()) {
            return Result.failure(IllegalArgumentException("Recipient or message cannot be empty."))
        }

        return runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(cleanMessage)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, cleanMessage, null, null)
            }
        }
    }
}
