package com.example.apps

/**
 * Privacy-first Facebook controller for MAX.
 *
 * Rules:
 * - Never posts status updates, comments, or likes automatically.
 * - Never accesses private feed or profile data.
 * - Opens Facebook strictly on explicit user request.
 */
class FacebookManager(private val launcher: AppLauncher) {

    fun openFacebook(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.FACEBOOK, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.FACEBOOK) != null
}
