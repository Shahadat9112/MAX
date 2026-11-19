package com.example.apps

/**
 * Privacy-first Chrome controller for MAX.
 *
 * Rules:
 * - Never opens websites or browses internet in the background automatically.
 * - Launches Chrome strictly upon explicit user request.
 */
class ChromeManager(private val launcher: AppLauncher) {

    fun openChrome(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.CHROME, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.CHROME) != null
}
