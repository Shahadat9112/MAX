package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.MAXCore
import com.example.domain.AssistantActionType
import com.example.domain.OfflineCommandEngine
import com.example.emotion.EmotionState
import com.example.files.FileModuleStatus
import com.example.smart.SmartFeatureStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinalIntegrationTest {

    private lateinit var app: Application
    private lateinit var core: MAXCore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        core = MAXCore.getInstance(app)
    }

    @Test
    fun maxCore_exposesAllIntegratedModules() {
        assertNotNull(core.wakeWordManager)
        assertNotNull(core.speechManager)
        assertNotNull(core.ttsManager)
        assertNotNull(core.conversationManager)
        assertNotNull(core.emotionManager)
        assertNotNull(core.memoryManager)
        assertNotNull(core.permissionManager)
        assertNotNull(core.settingsManager)
        assertNotNull(core.cameraManager)
        assertNotNull(core.galleryManager)
        assertNotNull(core.internetManager)
        assertNotNull(core.phoneManager)
        assertNotNull(core.appManager)
        assertNotNull(core.fileManager)
        assertNotNull(core.smartFeatureManager)
    }

    @Test
    fun fileAndInternetModules_enforcePrivacyAndExplicitUserConsent() = runBlocking {
        // Never browse files without explicit user request
        assertNull(core.fileManager.createBrowseFilesIntent(isUserInitiated = false))
        assertNotNull(core.fileManager.createBrowseFilesIntent(isUserInitiated = true))
        assertEquals(FileModuleStatus.BROWSING, core.fileManager.fileState.value.status)

        // Never delete files without explicit confirmation
        assertFalse(core.fileManager.confirmDeleteFile(Uri.EMPTY, confirmed = false))
        assertTrue(core.fileManager.confirmDeleteFile(Uri.EMPTY, confirmed = true))

        // Never upload images for OCR without uploadConsentGranted
        val ocrBlocked = core.fileManager.extractTextWithOcr(
            uri = Uri.EMPTY,
            isUserInitiated = true,
            uploadConsentGranted = false,
            isOnline = true
        )
        assertTrue(ocrBlocked.isFailure)

        // Never browse internet automatically
        val searchBlocked = core.internetManager.openWebSearch("weather", isUserInitiated = false)
        assertTrue(searchBlocked.isFailure)
    }

    @Test
    fun smartFeatureManager_handlesRemindersNotesTranslationAndSuggestions() = runBlocking {
        val reminderRes = core.smartFeatureManager.createReminder(
            rawCommand = "MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও।",
            isUserInitiated = true,
            openClockUi = false
        )
        assertTrue(reminderRes.isSuccess)
        val reminder = reminderRes.getOrThrow()
        assertEquals(8, reminder.hour)
        assertEquals(0, reminder.minute)
        assertEquals(1, reminder.dayOffset)

        val noteRes = core.smartFeatureManager.saveNote(
            content = "Buy milk and bread",
            isUserInitiated = true,
            isVoiceNote = true
        )
        assertTrue(noteRes.isSuccess)
        assertTrue(noteRes.getOrThrow().isVoiceNote)

        assertEquals(
            "en",
            core.smartFeatureManager.translationManager.inferTargetLanguage("এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো")
        )
        assertEquals(
            "bn",
            core.smartFeatureManager.translationManager.inferTargetLanguage("Translate this to Bengali")
        )

        core.emotionManager.evaluateAndSetEmotion("I am feeling happy and glad")
        assertEquals(EmotionState.HAPPY, core.emotionManager.currentEmotion.value)
        val suggestion = core.smartFeatureManager.getContextualSuggestion(isBengali = true)
        assertTrue(suggestion.isNotBlank())
        assertEquals(SmartFeatureStatus.IDLE, core.smartFeatureManager.smartState.value.status)

        core.releaseTransientTaskResources()
        assertEquals(FileModuleStatus.IDLE, core.fileManager.fileState.value.status)
    }

    @Test
    fun offlineCommandEngine_parsesSmartAndFileVoiceCommands() {
        val tr = OfflineCommandEngine.tryParse("MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো।")
        assertEquals(AssistantActionType.TRANSLATE, tr?.action)

        val rem = OfflineCommandEngine.tryParse("MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও।")
        assertEquals(AssistantActionType.SET_REMINDER, rem?.action)

        val note = OfflineCommandEngine.tryParse("MAX, একটা নোট লিখে রাখো।")
        assertEquals(AssistantActionType.ADD_NOTE, note?.action)

        val cal = OfflineCommandEngine.tryParse("MAX, আজকের ক্যালেন্ডার দেখাও।")
        assertEquals(AssistantActionType.SHOW_CALENDAR, cal?.action)

        val files = OfflineCommandEngine.tryParse("MAX, open files")
        assertEquals(AssistantActionType.OPEN_FILES, files?.action)
    }

    @Test
    fun allRequestedCommands_areRecognizedAndHandledProperly() {
        // Camera commands
        assertEquals(AssistantActionType.TAKE_PHOTO, OfflineCommandEngine.tryParse("MAX, take a photo")?.action)
        assertEquals(AssistantActionType.OPEN_SELFIE, OfflineCommandEngine.tryParse("MAX, take a selfie")?.action)
        assertEquals(AssistantActionType.OPEN_SELFIE, OfflineCommandEngine.tryParse("MAX, সেলফি তোলো")?.action)
        assertEquals(AssistantActionType.RECORD_VIDEO, OfflineCommandEngine.tryParse("MAX, record video")?.action)
        assertEquals(AssistantActionType.SCAN_QR, OfflineCommandEngine.tryParse("MAX, scan qr")?.action)

        // Gallery & Files
        assertEquals(AssistantActionType.OPEN_GALLERY, OfflineCommandEngine.tryParse("MAX, গ্যালারি খোলো")?.action)
        assertEquals(AssistantActionType.OPEN_GALLERY, OfflineCommandEngine.tryParse("MAX, find photos")?.action)
        assertEquals(AssistantActionType.OPEN_FILES, OfflineCommandEngine.tryParse("MAX, পিডিএফ পড়ো")?.action)

        // Phone control commands
        assertEquals(AssistantActionType.CALL_CONTACT, OfflineCommandEngine.tryParse("MAX, call Karim")?.action)
        assertEquals(AssistantActionType.FIND_CONTACT, OfflineCommandEngine.tryParse("MAX, find contact Rahim")?.action)
        assertEquals(AssistantActionType.SEND_SMS, OfflineCommandEngine.tryParse("MAX, send sms to Hasan saying hello")?.action)
        assertEquals(AssistantActionType.SET_ALARM, OfflineCommandEngine.tryParse("MAX, set alarm for 7 am")?.action)
        assertEquals(AssistantActionType.SET_TIMER, OfflineCommandEngine.tryParse("MAX, set timer for 5 minutes")?.action)
        assertEquals(AssistantActionType.STOPWATCH_CONTROL, OfflineCommandEngine.tryParse("MAX, start stopwatch")?.action)
        assertEquals(AssistantActionType.TOGGLE_FLASHLIGHT, OfflineCommandEngine.tryParse("MAX, turn on flashlight")?.action)
        assertEquals("40", core.phoneManager.calculate("২৫ + ১৫").getOrNull())

        // App control commands (English + Bengali)
        val wa = OfflineCommandEngine.tryParse("MAX, WhatsApp খোলো")
        assertEquals(AssistantActionType.OPEN_APP, wa?.action)
        assertEquals("WhatsApp", wa?.targetName)
        assertEquals(AssistantActionType.OPEN_APP, OfflineCommandEngine.tryParse("MAX, YouTube খোলো")?.action)
        assertEquals(AssistantActionType.OPEN_APP, OfflineCommandEngine.tryParse("MAX, Maps খোলো")?.action)
        assertEquals(AssistantActionType.OPEN_APP, OfflineCommandEngine.tryParse("MAX, Chrome খোলো")?.action)
        assertEquals(AssistantActionType.OPEN_APP, OfflineCommandEngine.tryParse("MAX, হোয়াটসঅ্যাপ খোলো")?.action)

        // Smart features & web search
        assertEquals(AssistantActionType.VOICE_NOTE, OfflineCommandEngine.tryParse("MAX, save a voice note")?.action)
        assertEquals(AssistantActionType.AI_SUGGESTION, OfflineCommandEngine.tryParse("MAX, suggest something")?.action)
        assertEquals(AssistantActionType.WEB_SEARCH, OfflineCommandEngine.tryParse("MAX, search google for weather")?.action)
        assertEquals(
            "আমি ভালো আছি",
            core.smartFeatureManager.translationManager.extractPayload(
                "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো: আমি ভালো আছি"
            )
        )
    }
}
