package com.example.apps

/**
 * Privacy-first Google Maps controller for MAX.
 *
 * Rules:
 * - Never accesses GPS or location automatically in the background.
 * - Only opens Google Maps when explicitly requested by the user.
 */
class MapsManager(private val launcher: AppLauncher) {

    fun openMaps(isUserInitiated: Boolean = true): AppLaunchOutcome {
        return launcher.launchSupportedApp(SupportedApp.MAPS, isUserInitiated)
    }

    fun isInstalled(): Boolean = launcher.resolveInstalledPackage(SupportedApp.MAPS) != null
}
