package com.example.permissions

import android.Manifest

/**
 * Handles explanation rationales and user consent flows for requested permissions in MAX,
 * ensuring users always understand why a permission is required and retain final control.
 */
object PermissionRequestHandler {

    fun getRationale(permission: String, isBengali: Boolean): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> if (isBengali) {
                "ভয়েস কমান্ড ও ওয়াক ওয়ার্ড শোনার জন্য মাইক্রোফোন অনুমতি প্রয়োজন।"
            } else {
                "Microphone permission is required to listen for voice commands and wake words."
            }
            Manifest.permission.CAMERA -> if (isBengali) {
                "ছবি তোলা বা ক্যামেরা খোলার জন্য ক্যামেরা অনুমতি প্রয়োজন।"
            } else {
                "Camera permission is required to take photos or open the camera."
            }
            Manifest.permission.READ_CONTACTS -> if (isBengali) {
                "কল করা বা এসএমএস পাঠানোর জন্য কন্টাক্ট অ্যাক্সেস প্রয়োজন।"
            } else {
                "Contacts permission is required to look up contacts for calls and messaging."
            }
            Manifest.permission.CALL_PHONE -> if (isBengali) {
                "সরাসরি ফোন কল করার জন্য কল অনুমতি প্রয়োজন।"
            } else {
                "Phone permission is required to place direct phone calls."
            }
            Manifest.permission.SEND_SMS -> if (isBengali) {
                "এসএমএস পাঠানোর জন্য এসএমএস অনুমতি প্রয়োজন।"
            } else {
                "SMS permission is required to send text messages."
            }
            else -> if (isBengali) {
                "MAX-এর এই ফিচারটি চালানোর জন্য অনুমতি প্রয়োজন।"
            } else {
                "Permission is required to run this feature."
            }
        }
    }
}
