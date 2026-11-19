package com.example.apps

/**
 * Privacy-first YouTube controller for MAX.
 *
 * Rules:
 * - Never plays videos or tracks watch history automatically.
 * - Opens YouTube strictly when explicitly asked by the user.
 */
class YouTubeManager(private val launcher: AppLauncher) {

    fun openYouTube(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.YOUTUBE, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.YOUTUBE) != null
}
