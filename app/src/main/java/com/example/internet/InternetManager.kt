package com.example.internet

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.device.NetworkMonitor
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.StateFlow

/**
 * Privacy-first Internet & Connectivity controller for MAX (`internet/`).
 *
 * Rules:
 * - Never opens websites or browses internet automatically in the background.
 * - Exposes real-time online connectivity safely via [NetworkMonitor].
 * - Validates user initiation and [PermissionManager.hasInternetPermission] before any web search or URL launch.
 */
class InternetManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext
    private val networkMonitor = NetworkMonitor(appContext)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    fun isNetworkConnected(): Boolean = networkMonitor.checkCurrentConnectivity()

    fun hasInternetPermission(): Boolean = permissionManager.hasInternetPermission()

    /**
     * Opens a web search ONLY upon explicit user request.
     */
    fun openWebSearch(
        query: String,
        isUserInitiated: Boolean = true
    ): Result<String> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Internet browsing requires explicit user request."))
        }
        if (!hasInternetPermission()) {
            return Result.failure(SecurityException("INTERNET permission is unavailable."))
        }
        val clean = query.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Search query cannot be empty."))
        }

        val url = "https://www.google.com/search?q=${Uri.encode(clean)}"
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            url
        }
    }
}
