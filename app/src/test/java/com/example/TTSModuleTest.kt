package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.tts.SpeechStyle
import com.example.tts.TTSManagerWrapper
import com.example.tts.VoiceConfig
import com.example.tts.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TTSModuleTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun voiceConfig_defaultsAndStylesAreCorrect() {
        val config = VoiceConfig()
        assertEquals(SpeechStyle.FRIENDLY, config.speechStyle)
        assertTrue(config.speechRate < 1.0f)
        assertTrue(config.pitch < 1.0f)
        assertTrue(config.addNaturalPauses)
    }

    @Test
    fun ttsManagerWrapper_initializesAndExposesControls() {
        val tts = TTSManagerWrapper(app)
        assertNotNull(tts.isSpeaking)
        assertNotNull(tts.voiceState)
        assertEquals(VoiceState.IDLE, tts.voiceState.value)

        // Test control functions execution without throwing
        tts.setSpeechSpeed(0.95f)
        tts.setPitch(0.92f)
        tts.setSpeechStyle(SpeechStyle.CARING)
        tts.speak("হ্যালো ম্যাক্স, কেমন আছো?", "bn")
        tts.pauseSpeaking()
        assertEquals(VoiceState.PAUSED, tts.voiceState.value)
        tts.resumeSpeaking()
        tts.stopSpeaking()
        assertEquals(VoiceState.STOPPED, tts.voiceState.value)
        tts.shutdown()
    }
}
