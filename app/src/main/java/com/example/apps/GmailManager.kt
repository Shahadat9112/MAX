package com.example.apps

/**
 * Privacy-first Gmail controller for MAX.
 *
 * Rules:
 * - Never reads emails, inbox threads, or attachments automatically.
 * - Never sends an email automatically without user review & confirmation.
 * - Opens Gmail strictly on explicit user command.
 */
class GmailManager(private val launcher: AppLauncher) {

    fun openGmail(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.GMAIL, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.GMAIL) != null
}
