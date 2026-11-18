package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

class PhoneCallManager(private val context: Context) {

    fun makeCall(phoneNumber: String): Result<Boolean> {
        val sanitized = phoneNumber.trim()
        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number is empty"))
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return runCatching {
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(sanitized)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            hasCallPermission
        }
    }
}
