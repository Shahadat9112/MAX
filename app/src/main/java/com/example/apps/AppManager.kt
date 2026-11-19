package com.example.apps

import android.content.Context
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central facade for MAX's App Control System (`apps/` package).
 *
 * Composes independent controllers for:
 * - [WhatsAppManager]
 * - [MessengerManager]
 * - [FacebookManager]
 * - [ChromeManager]
 * - [YouTubeManager]
 * - [GmailManager]
 * - [MapsManager]
 * and [AppLauncher] for general installed app resolution.
 *
 * Strictly enforces:
 * - Launches apps ONLY upon explicit user request (e.g., "MAX, WhatsApp খোলো").
 * - Never sends messages, never posts updates, never reads chats/emails, and never opens apps in background.
 * - Handles missing apps cleanly and suggests web/Play Store alternatives.
 */
class AppManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val appContext = context.applicationContext
    val appLauncher = AppLauncher(appContext)

    val whatsAppManager = WhatsAppManager(appLauncher)
    val messengerManager = MessengerManager(appLauncher)
    val facebookManager = FacebookManager(appLauncher)
    val chromeManager = ChromeManager(appLauncher)
    val youTubeManager = YouTubeManager(appLauncher)
    val gmailManager = GmailManager(appLauncher)
    val mapsManager = MapsManager(appLauncher)

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    /**
     * Resolves and opens any requested app (specialized supported app or any installed launcher app)
     * strictly when [isUserInitiated] is true.
     */
    fun openApp(
        appQuery: String,
        isUserInitiated: Boolean = true,
        isBengali: Boolean = false
    ): AppLaunchOutcome {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            val outcome = AppLaunchOutcome.Failed("App launch blocked: requires explicit user command.")
            _appState.update {
                it.copy(
                    status = AppModuleStatus.ERROR,
                    statusMessage = outcome.reason
                )
            }
            return outcome
        }

        _appState.update {
            it.copy(
                status = AppModuleStatus.OPENING,
                lastRequestedApp = appQuery.trim(),
                suggestedAlternative = null
            )
        }

        val supported = SupportedApp.fromQuery(appQuery)
        if (supported != null) {
            val outcome = when (supported) {
                SupportedApp.WHATSAPP -> whatsAppManager.openWhatsApp(isUserInitiated)
                SupportedApp.MESSENGER -> messengerManager.openMessenger(isUserInitiated)
                SupportedApp.FACEBOOK -> facebookManager.openFacebook(isUserInitiated)
                SupportedApp.CHROME -> chromeManager.openChrome(isUserInitiated)
                SupportedApp.YOUTUBE -> youTubeManager.openYouTube(isUserInitiated)
                SupportedApp.GMAIL -> gmailManager.openGmail(isUserInitiated)
                SupportedApp.MAPS -> mapsManager.openMaps(isUserInitiated)
            }
            updateStateFromOutcome(outcome, isBengali)
            return outcome
        }

        // Fallback to any general installed app on the phone
        val genericResult = appLauncher.launchInstalledAppByQuery(appQuery, isUserInitiated)
        return genericResult.fold(
            onSuccess = { appLabel ->
                val outcome = AppLaunchOutcome.Launched(appLabel, appLabel)
                _appState.update {
                    it.copy(
                        status = AppModuleStatus.OPENED,
                        lastOpenedPackage = appLabel,
                        statusMessage = if (isBengali) "$appLabel খোলা হচ্ছে।" else "Opening $appLabel."
                    )
                }
                outcome
            },
            onFailure = { err ->
                val outcome = AppLaunchOutcome.Failed(err.localizedMessage ?: "App not found.")
                _appState.update {
                    it.copy(
                        status = AppModuleStatus.NOT_INSTALLED,
                        statusMessage = outcome.reason
                    )
                }
                outcome
            }
        )
    }

    /**
     * Checks whether a sensitive action inside an app (such as sending a message or posting)
     * is permitted — always requiring explicit user confirmation.
     */
    fun buildSensitiveActionConfirmationMessage(
        app: SupportedApp,
        actionDescription: String,
        isBengali: Boolean
    ): String {
        _appState.update {
            it.copy(
                status = AppModuleStatus.AWAITING_CONFIRMATION,
                lastRequestedApp = app.displayName
            )
        }
        val appName = if (isBengali) app.bengaliName else app.displayName
        return if (isBengali) {
            "আমি আপনার অনুমতি ছাড়া কোনো মেসেজ পাঠাবো না বা পোস্ট করবো না। আপনি কি $appName-এ \"$actionDescription\" করতে চান?"
        } else {
            "I never send messages or post automatically. Would you like to confirm opening $appName for \"$actionDescription\"?"
        }
    }

    /**
     * Formats a user-friendly Bangla or English spoken/chat response for any [AppLaunchOutcome],
     * including helpful alternative suggestions when a supported app is not installed.
     */
    fun formatOutcomeMessage(outcome: AppLaunchOutcome, isBengali: Boolean): String {
        return when (outcome) {
            is AppLaunchOutcome.Launched -> {
                val label = SupportedApp.fromQuery(outcome.appName)?.let {
                    if (isBengali) it.bengaliName else it.displayName
                } ?: outcome.appName
                if (isBengali) "$label খোলা হচ্ছে।" else "Opening $label."
            }
            is AppLaunchOutcome.NeedsConfirmation -> {
                val label = if (isBengali) outcome.app.bengaliName else outcome.app.displayName
                if (isBengali) {
                    "$label-এ ${outcome.actionDescription} করার আগে অনুগ্রহ করে নিশ্চিত করুন।"
                } else {
                    "Please confirm before I proceed with ${outcome.actionDescription} in $label."
                }
            }
            is AppLaunchOutcome.NotInstalled -> {
                val label = if (isBengali) outcome.app.bengaliName else outcome.app.displayName
                if (isBengali) {
                    "আপনার ফোনে $label ইনস্টল করা নেই। আপনি চাইলে আমি ব্রাউজারে ${outcome.suggestedAlternative} খুলে দিতে পারি অথবা বিকল্প অ্যাপ ব্যবহার করতে পারেন।"
                } else {
                    "$label is not installed on your device. As an alternative, you can use ${outcome.suggestedAlternative} in your browser or install it from the Play Store."
                }
            }
            is AppLaunchOutcome.Failed -> {
                if (isBengali) "অ্যাপটি খোলা যায়নি: ${outcome.reason}" else outcome.reason
            }
        }
    }

    fun release() {
        _appState.value = AppState()
    }

    private fun updateStateFromOutcome(outcome: AppLaunchOutcome, isBengali: Boolean) {
        val message = formatOutcomeMessage(outcome, isBengali)
        when (outcome) {
            is AppLaunchOutcome.Launched -> _appState.update {
                it.copy(
                    status = AppModuleStatus.OPENED,
                    lastOpenedPackage = outcome.packageName,
                    statusMessage = message
                )
            }
            is AppLaunchOutcome.NeedsConfirmation -> _appState.update {
                it.copy(
                    status = AppModuleStatus.AWAITING_CONFIRMATION,
                    statusMessage = message
                )
            }
            is AppLaunchOutcome.NotInstalled -> _appState.update {
                it.copy(
                    status = AppModuleStatus.SUGGESTING_ALTERNATIVE,
                    suggestedAlternative = outcome.suggestedAlternative,
                    statusMessage = message
                )
            }
            is AppLaunchOutcome.Failed -> _appState.update {
                it.copy(
                    status = AppModuleStatus.ERROR,
                    statusMessage = message
                )
            }
        }
    }
}
