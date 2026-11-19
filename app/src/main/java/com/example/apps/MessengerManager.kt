package com.example.apps

/**
 * Privacy-first Messenger controller for MAX.
 *
 * Rules:
 * - Never reads Messenger conversations automatically.
 * - Never sends messages automatically.
 * - Only launches Messenger when explicitly asked by the user.
 */
class MessengerManager(private val launcher: AppLauncher) {

    fun openMessenger(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.MESSENGER, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.MESSENGER) != null
}
