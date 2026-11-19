package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.apps.AppLaunchOutcome
import com.example.apps.AppManager
import com.example.apps.AppModuleStatus
import com.example.apps.SupportedApp
import com.example.domain.AssistantActionType
import com.example.domain.OfflineCommandEngine
import com.example.permissions.PermissionManager
import com.example.phone.PhoneManager
import com.example.phone.PhoneModuleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhoneAndAppControlModuleTest {

    private lateinit var app: Application
    private lateinit var permissionManager: PermissionManager
    private lateinit var phoneManager: PhoneManager
    private lateinit var appManager: AppManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        permissionManager = PermissionManager(app)
        phoneManager = PhoneManager(app, permissionManager)
        appManager = AppManager(app, permissionManager)
    }

    @Test
    fun phoneManager_blocksUnconfirmedSensitiveActions() {
        val callResult = phoneManager.placeConfirmedCall("01712345678", isUserConfirmed = false)
        assertTrue(callResult.isFailure)

        val smsResult = phoneManager.sendConfirmedSms("01712345678", "Hello", isUserConfirmed = false)
        assertTrue(smsResult.isFailure)

        val alarmResult = phoneManager.setAlarm(7, 30, isUserInitiated = false)
        assertTrue(alarmResult.isFailure)

        val torchResult = phoneManager.toggleFlashlight(isUserInitiated = false)
        assertTrue(torchResult.isFailure)

        val contactsResult = phoneManager.findContacts("Father", isUserInitiated = false)
        assertTrue(contactsResult.isEmpty())
    }

    @Test
    fun calculatorAndStopwatch_workAccuratelyOffline() {
        val calcResult = phoneManager.calculate("(25 + 15) * 2")
        assertTrue(calcResult.isSuccess)
        assertEquals("80", calcResult.getOrNull())

        var fakeNow = 1000L
        val customStopwatch = com.example.phone.StopwatchManager { fakeNow }
        assertTrue(customStopwatch.start(isUserInitiated = true))
        fakeNow = 3500L
        assertEquals(2500L, customStopwatch.pause(isUserInitiated = true))
        assertEquals("00:02.5", customStopwatch.formatElapsed())
        customStopwatch.reset(isUserInitiated = true)
        assertEquals(0L, customStopwatch.elapsedMs.value)

        phoneManager.controlStopwatch("start", isUserInitiated = true)
        assertEquals(PhoneModuleStatus.STOPWATCH_RUNNING, phoneManager.phoneState.value.status)
        phoneManager.release()
        assertEquals(PhoneModuleStatus.IDLE, phoneManager.phoneState.value.status)
    }

    @Test
    fun appManager_resolvesBanglaAndEnglishAppCommands_andBlocksAutoLaunch() {
        assertEquals(SupportedApp.WHATSAPP, SupportedApp.fromQuery("WhatsApp"))
        assertEquals(SupportedApp.YOUTUBE, SupportedApp.fromQuery("YouTube"))
        assertEquals(SupportedApp.MAPS, SupportedApp.fromQuery("Maps"))
        assertEquals(SupportedApp.CHROME, SupportedApp.fromQuery("Chrome"))
        assertEquals(SupportedApp.MESSENGER, SupportedApp.fromQuery("মেসেঞ্জার"))
        assertEquals(SupportedApp.FACEBOOK, SupportedApp.fromQuery("ফেসবুক"))
        assertEquals(SupportedApp.GMAIL, SupportedApp.fromQuery("জিমেইল"))

        // Auto background open without user initiation must fail
        val blocked = appManager.openApp("WhatsApp", isUserInitiated = false, isBengali = true)
        assertTrue(blocked is AppLaunchOutcome.Failed)
        assertEquals(AppModuleStatus.ERROR, appManager.appState.value.status)

        // When WhatsApp isn't installed in Robolectric sandbox, MAX suggests alternative cleanly
        val notInstalled = appManager.openApp("WhatsApp", isUserInitiated = true, isBengali = true)
        assertTrue(notInstalled is AppLaunchOutcome.NotInstalled)
        val formattedMsg = appManager.formatOutcomeMessage(notInstalled, isBengali = true)
        assertTrue(formattedMsg.contains("web.whatsapp.com"))

        // Sensitive action prompt warns user before proceeding
        val confirmPrompt = appManager.buildSensitiveActionConfirmationMessage(
            SupportedApp.WHATSAPP,
            "Send message to Rahim",
            isBengali = true
        )
        assertTrue(confirmPrompt.contains("অনুমতি ছাড়া"))
        assertEquals(AppModuleStatus.AWAITING_CONFIRMATION, appManager.appState.value.status)
    }

    @Test
    fun offlineCommandEngine_parsesRequiredBanglaAppAndPhoneCommands() {
        val wa = OfflineCommandEngine.tryParse("MAX, WhatsApp খোলো")
        assertEquals(AssistantActionType.OPEN_APP, wa?.action)
        assertEquals("WhatsApp", wa?.targetName)

        val yt = OfflineCommandEngine.tryParse("MAX, YouTube খোলো")
        assertEquals(AssistantActionType.OPEN_APP, yt?.action)
        assertEquals("YouTube", yt?.targetName)

        val maps = OfflineCommandEngine.tryParse("MAX, Maps খোলো")
        assertEquals(AssistantActionType.OPEN_APP, maps?.action)
        assertEquals("Maps", maps?.targetName)

        val chrome = OfflineCommandEngine.tryParse("MAX, Chrome খোলো")
        assertEquals(AssistantActionType.OPEN_APP, chrome?.action)
        assertEquals("Chrome", chrome?.targetName)

        val alarm = OfflineCommandEngine.tryParse("MAX, set alarm 7:30 am")
        assertEquals(AssistantActionType.SET_ALARM, alarm?.action)

        val timer = OfflineCommandEngine.tryParse("MAX, set timer 5 minutes")
        assertEquals(AssistantActionType.SET_TIMER, timer?.action)

        val calc = OfflineCommandEngine.tryParse("MAX, calculate 12 * 4")
        assertEquals(AssistantActionType.CALCULATE, calc?.action)
    }
}
