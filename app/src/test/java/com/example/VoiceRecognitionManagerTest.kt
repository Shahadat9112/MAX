package com.example

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.example.device.VoiceRecognitionManager
import com.example.domain.SpeechRecognitionResult
import com.example.domain.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceRecognitionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun resolveLocaleCandidates_handlesEnglishBengaliAndAuto() {
        val bnCandidates = VoiceRecognitionManager.resolveLocaleCandidates(
            language = VoiceLanguage.BENGALI,
            deviceLocale = Locale.forLanguageTag("en-US")
        )
        assertEquals(listOf("bn-BD", "bn-IN", "bn"), bnCandidates)

        val enCandidates = VoiceRecognitionManager.resolveLocaleCandidates(
            language = VoiceLanguage.ENGLISH,
            deviceLocale = Locale.forLanguageTag("en-GB")
        )
        assertEquals("en-GB", enCandidates.first())
        assertTrue(enCandidates.contains("en-US"))

        val autoFromEn = VoiceRecognitionManager.resolveLocaleCandidates(
            language = VoiceLanguage.AUTO,
            deviceLocale = Locale.forLanguageTag("en-US")
        )
        assertEquals(listOf("en-US", "bn-BD", "bn-IN"), autoFromEn)

        val autoFromBn = VoiceRecognitionManager.resolveLocaleCandidates(
            language = VoiceLanguage.AUTO,
            deviceLocale = Locale.forLanguageTag("bn-BD")
        )
        assertEquals(listOf("bn-BD", "bn-IN", "en-US"), autoFromBn)
    }

    @Test
    fun buildRecognizerIntent_setsEnglishAndBengaliExtrasProperly() {
        val bnIntent = VoiceRecognitionManager.buildRecognizerIntent(
            context = context,
            language = VoiceLanguage.BENGALI,
            localeTag = "bn-BD",
            preferOffline = true,
            enableLanguageDetection = false
        )
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, bnIntent.action)
        assertEquals("bn-BD", bnIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("bn-BD", bnIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertEquals(context.packageName, bnIntent.getStringExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE))
        assertTrue(bnIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertTrue(bnIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))

        val autoIntent = VoiceRecognitionManager.buildRecognizerIntent(
            context = context,
            language = VoiceLanguage.AUTO,
            localeTag = "en-US",
            preferOffline = false,
            enableLanguageDetection = true
        )
        assertEquals("en-US", autoIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(autoIntent.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false))
        val allowed = autoIntent.getStringArrayListExtra(
            RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES
        )
        assertEquals(arrayListOf("en-US", "bn-BD", "bn-IN"), allowed)
        assertFalse(autoIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun selectBestTranscript_andLanguageResolution_preferBengaliWhenAppropriate() {
        val matches = listOf("koyta baje", "কয়টা বাজে")
        val selectedBn = VoiceRecognitionManager.selectBestTranscript(
            matches = matches,
            language = VoiceLanguage.BENGALI,
            detectedLanguageHint = null
        )
        assertEquals("কয়টা বাজে", selectedBn)
        assertEquals(
            "bn",
            VoiceRecognitionManager.resolveLanguageCode(selectedBn, null, "bn-BD")
        )

        val selectedEn = VoiceRecognitionManager.selectBestTranscript(
            matches = listOf("MAX open camera"),
            language = VoiceLanguage.ENGLISH,
            detectedLanguageHint = "en-US"
        )
        assertEquals("MAX open camera", selectedEn)
        assertEquals(
            "en",
            VoiceRecognitionManager.resolveLanguageCode(selectedEn, "en-US", "en-US")
        )
    }

    @Test
    fun speechRecognizer_capturesEnglishAndBengaliCommands_withFallbackLocaleRetry() {
        var latestRecognizer: SpeechRecognizer? = null
        val factory = object : VoiceRecognitionManager.RecognizerFactory {
            override fun isRecognitionAvailable(context: Context): Boolean = true
            override fun isOnDeviceRecognitionAvailable(context: Context): Boolean = false
            override fun createSpeechRecognizer(context: Context, preferOnDevice: Boolean): SpeechRecognizer {
                return SpeechRecognizer.createSpeechRecognizer(context).also {
                    latestRecognizer = it
                }
            }
        }

        val manager = VoiceRecognitionManager(context, factory)
        var captured: SpeechRecognitionResult? = null
        var errorMessage: String? = null

        // 1) Start in Bengali mode -> simulate bn-BD unsupported -> automatic retry with bn-IN -> success
        manager.startListening(
            language = VoiceLanguage.BENGALI,
            preferOffline = true,
            onResult = { captured = it },
            onAudioFallback = {},
            onError = { errorMessage = it }
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(latestRecognizer)
        assertEquals("bn-BD", manager.activeLocaleTag.value)

        val firstRecognizer = requireNotNull(latestRecognizer)
        val shadowFirst = shadowOf(firstRecognizer)
        shadowFirst.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        shadowOf(Looper.getMainLooper()).idle()

        // Should automatically retry with bn-IN without reporting an error
        assertNull(errorMessage)
        assertEquals("bn-IN", manager.activeLocaleTag.value)

        val retryRecognizer = requireNotNull(latestRecognizer)
        val shadowRetry = shadowOf(retryRecognizer)
        shadowRetry.triggerOnReadyForSpeech(Bundle())
        shadowRetry.triggerOnRmsChanged(8f)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isListening.value)
        assertTrue(manager.audioLevel.value > 0f)

        val partialBundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("কয়টা"))
        }
        shadowRetry.triggerOnPartialResults(partialBundle)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("কয়টা", manager.partialText.value)

        val finalBundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("কয়টা বাজে"))
        }
        shadowRetry.triggerOnResults(finalBundle)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(manager.isListening.value)
        assertNotNull(captured)
        assertEquals("কয়টা বাজে", captured?.text)
        assertEquals("bn", captured?.detectedLanguage)
        assertEquals("bn-IN", captured?.localeTag)

        // 2) Start in English mode -> verify en-US capture
        captured = null
        errorMessage = null
        manager.startListening(
            language = VoiceLanguage.ENGLISH,
            preferOffline = false,
            onResult = { captured = it },
            onAudioFallback = {},
            onError = { errorMessage = it }
        )
        shadowOf(Looper.getMainLooper()).idle()

        val enRecognizer = requireNotNull(latestRecognizer)
        val shadowEn = shadowOf(enRecognizer)
        shadowEn.triggerOnReadyForSpeech(Bundle())
        shadowEn.triggerOnResults(
            Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("MAX take a picture"))
            }
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(errorMessage)
        assertEquals("MAX take a picture", captured?.text)
        assertEquals("en", captured?.detectedLanguage)
    }
}
