package com.example.apps

/**
 * Privacy-first WhatsApp controller for MAX.
 *
 * Rules:
 * - Never reads WhatsApp chats, notifications, or contact databases automatically.
 * - Never sends WhatsApp messages automatically.
 * - Opens WhatsApp only upon explicit user request; sensitive messaging actions
 *   always require explicit user confirmation first.
 */
class WhatsAppManager(private val launcher: AppLauncher) {

    fun openWhatsApp(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.WHATSAPP, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.WHATSAPP) != null

    /**
     * Prepares a WhatsApp message prompt that requires confirmation before opening/sending.
     */
    fun prepareMessagePrompt(recipient: String, isBengali: Boolean): String {
        return if (isBengali) {
            "আমি নিজে থেকে মেসেজ পাঠাবো না। আপনি কি $recipient-কে পাঠানোর জন্য WhatsApp খুলতে চান?"
        } else {
            "I won't send messages automatically. Would you like me to open WhatsApp for $recipient?"
        }
    }
}
