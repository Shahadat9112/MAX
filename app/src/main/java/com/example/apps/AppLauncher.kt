package com.example.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.permissions.PrivacyController

/**
 * Android version-compatible app discovery and launcher utility.
 *
 * Rules:
 * - Never opens any app automatically in the background.
 * - Requires explicit user intent (`isUserInitiated == true`) before calling `startActivity`.
 * - Handles missing apps gracefully across API levels and prepares alternative suggestions
 *   (e.g., Play Store page or web alternative) only when requested.
 */
class AppLauncher(context: Context) {
    private val appContext = context.applicationContext

    fun isPackageInstalled(packageName: String): Boolean {
        val pm = appContext.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun resolveInstalledPackage(app: SupportedApp): String? {
        if (isPackageInstalled(app.primaryPackage)) return app.primaryPackage
        return app.fallbackPackages.firstOrNull { isPackageInstalled(it) }
    }

    /**
     * Launches a [SupportedApp] strictly when explicitly requested by the user.
     */
    fun launchSupportedApp(
        app: SupportedApp,
        isUserInitiated: Boolean = true
    ): AppLaunchOutcome {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return AppLaunchOutcome.Failed("App launch requires explicit user command.")
        }

        val installedPkg = resolveInstalledPackage(app)
        if (installedPkg == null) {
            return AppLaunchOutcome.NotInstalled(
                app = app,
                suggestedAlternative = app.webFallbackUrl,
                playStoreUri = "https://play.google.com/store/apps/details?id=${app.primaryPackage}"
            )
        }

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(installedPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AppLaunchOutcome.NotInstalled(
            app = app,
            suggestedAlternative = app.webFallbackUrl,
            playStoreUri = "https://play.google.com/store/apps/details?id=$installedPkg"
        )

        return runCatching {
            appContext.startActivity(launchIntent)
            AppLaunchOutcome.Launched(app.displayName, installedPkg)
        }.getOrElse { err ->
            AppLaunchOutcome.Failed(err.localizedMessage ?: "Unable to open ${app.displayName}.")
        }
    }

    /**
     * Opens an arbitrary installed app by name, preserving full launcher discovery compatibility
     * across Android versions (API 26–36).
     */
    fun launchInstalledAppByQuery(
        appQuery: String,
        isUserInitiated: Boolean = true
    ): Result<String> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("App launch requires explicit user command."))
        }

        val query = appQuery.trim().lowercase()
        if (query.isEmpty()) return Result.failure(IllegalArgumentException("App name is empty"))

        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val apps = resolveInfos.map { info ->
            info.loadLabel(pm).toString() to info.activityInfo.packageName
        }

        val matched = apps.firstOrNull { (label, _) -> label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { (label, _) -> label.lowercase().startsWith(query) }
            ?: apps.firstOrNull { (label, _) -> label.lowercase().contains(query) }
            ?: apps.firstOrNull { (label, pkg) ->
                pkg.lowercase().contains(query.replace(" ", "")) ||
                    query.contains(label.lowercase())
            }
            ?: return Result.failure(IllegalStateException("App '$appQuery' is not installed on this device."))

        val launchIntent = pm.getLaunchIntentForPackage(matched.second)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return Result.failure(IllegalStateException("Cannot launch '${matched.first}'."))

        return runCatching {
            appContext.startActivity(launchIntent)
            matched.first
        }
    }

    /**
     * Opens a safe web fallback or Play Store page ONLY after the user explicitly confirms.
     */
    fun openUrl(url: String, isUserConfirmed: Boolean): Boolean {
        if (!PrivacyController.assertUserInitiatedAction(isUserConfirmed) || !isUserConfirmed) {
            return false
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
