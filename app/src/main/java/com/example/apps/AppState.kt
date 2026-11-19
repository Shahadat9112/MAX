package com.example.apps

/**
 * Supported third-party and core system apps managed by MAX's App Control System.
 */
enum class SupportedApp(
    val displayName: String,
    val bengaliName: String,
    val primaryPackage: String,
    val fallbackPackages: List<String> = emptyList(),
    val webFallbackUrl: String,
    val aliases: Set<String>
) {
    WHATSAPP(
        displayName = "WhatsApp",
        bengaliName = "হোয়াটসঅ্যাপ",
        primaryPackage = "com.whatsapp",
        fallbackPackages = listOf("com.whatsapp.w4b"),
        webFallbackUrl = "https://web.whatsapp.com",
        aliases = setOf("whatsapp", "whats app", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "ওয়াটসঅ্যাপ")
    ),
    MESSENGER(
        displayName = "Messenger",
        bengaliName = "মেসেঞ্জার",
        primaryPackage = "com.facebook.orca",
        fallbackPackages = listOf("com.facebook.mlite"),
        webFallbackUrl = "https://www.messenger.com",
        aliases = setOf("messenger", "fb messenger", "মেসেঞ্জার", "ম্যাসেঞ্জার")
    ),
    FACEBOOK(
        displayName = "Facebook",
        bengaliName = "ফেসবুক",
        primaryPackage = "com.facebook.katana",
        fallbackPackages = listOf("com.facebook.lite"),
        webFallbackUrl = "https://www.facebook.com",
        aliases = setOf("facebook", "fb", "ফেসবুক", "এফবি")
    ),
    CHROME(
        displayName = "Chrome",
        bengaliName = "ক্রোম",
        primaryPackage = "com.android.chrome",
        fallbackPackages = listOf("com.chrome.beta", "com.chrome.dev"),
        webFallbackUrl = "https://www.google.com",
        aliases = setOf("chrome", "google chrome", "browser", "ক্রোম", "ব্রাউজার")
    ),
    YOUTUBE(
        displayName = "YouTube",
        bengaliName = "ইউটিউব",
        primaryPackage = "com.google.android.youtube",
        fallbackPackages = listOf("com.google.android.apps.youtube.kids"),
        webFallbackUrl = "https://www.youtube.com",
        aliases = setOf("youtube", "you tube", "yt", "ইউটিউব", "ইউটিউবে")
    ),
    GMAIL(
        displayName = "Gmail",
        bengaliName = "জিমেইল",
        primaryPackage = "com.google.android.gm",
        fallbackPackages = listOf("com.google.android.gm.lite"),
        webFallbackUrl = "https://mail.google.com",
        aliases = setOf("gmail", "google mail", "email", "mail", "জিমেইল", "ইমেইল")
    ),
    MAPS(
        displayName = "Google Maps",
        bengaliName = "গুগল ম্যাপস",
        primaryPackage = "com.google.android.apps.maps",
        fallbackPackages = listOf("com.google.android.apps.mapslite"),
        webFallbackUrl = "https://maps.google.com",
        aliases = setOf("maps", "google maps", "map", "ম্যাপ", "ম্যাপস", "গুগল ম্যাপ", "গুগল ম্যাপস")
    );

    companion object {
        fun fromQuery(rawQuery: String): SupportedApp? {
            val clean = rawQuery.trim().lowercase()
            if (clean.isEmpty()) return null
            return entries.firstOrNull { app ->
                app.displayName.equals(clean, ignoreCase = true) ||
                    app.aliases.any { alias -> clean == alias || clean.contains(alias) }
            }
        }
    }
}

/**
 * Operational states for MAX's App Control System.
 */
enum class AppModuleStatus {
    IDLE,
    AWAITING_CONFIRMATION,
    OPENING,
    OPENED,
    NOT_INSTALLED,
    SUGGESTING_ALTERNATIVE,
    ERROR
}

/**
 * Outcome returned when launching or preparing an app action.
 */
sealed interface AppLaunchOutcome {
    data class Launched(val appName: String, val packageName: String) : AppLaunchOutcome
    data class NeedsConfirmation(val app: SupportedApp, val actionDescription: String) : AppLaunchOutcome
    data class NotInstalled(
        val app: SupportedApp,
        val suggestedAlternative: String,
        val playStoreUri: String
    ) : AppLaunchOutcome
    data class Failed(val reason: String) : AppLaunchOutcome
}

/**
 * Immutable state snapshot exposed by [AppManager].
 */
data class AppState(
    val status: AppModuleStatus = AppModuleStatus.IDLE,
    val lastRequestedApp: String? = null,
    val lastOpenedPackage: String? = null,
    val suggestedAlternative: String? = null,
    val statusMessage: String? = null
)
