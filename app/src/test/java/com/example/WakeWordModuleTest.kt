package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.core.MAXCore
import com.example.wakeword.VoiceConversationConnector
import com.example.wakeword.WakeWordConfig
import com.example.wakeword.WakeWordDetectionEvent
import com.example.wakeword.WakeWordEngine
import com.example.wakeword.WakeWordListener
import com.example.wakeword.WakeWordManager
import com.example.wakeword.WakeWordState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WakeWordModuleTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun wakeWordConfig_enforcesMaxAndZeroRecordingDefaults() {
        val config = WakeWordConfig()
        assertEquals("MAX", config.wakeWord)
        assertTrue(config.strictZeroRecording)
        assertTrue(config.autoActivateSpeechOnWake)
        assertTrue(config.pauseDuringPhoneCalls)
        assertTrue(config.maxWakeWindowMs <= 2_500L)
    }

    @Test
    fun wakeWordManager_waitsForMaxFirst_thenActivatesSpeechAndListenersOnDetection() {
        val fakeEngine = FakeWakeWordEngine()
        val manager = WakeWordManager(app, customEngine = fakeEngine)

        var detectedEvent: WakeWordDetectionEvent? = null
        var readyCalled = false
        var speechRequestedSource: String? = null
        var conversationEndedCalled = false

        manager.addListener(object : WakeWordListener {
            override fun onWakeWordDetected(event: WakeWordDetectionEvent) {
                detectedEvent = event
            }
        })

        manager.attachConversationConnector(object : VoiceConversationConnector {
            override fun onReadyForConversation(event: WakeWordDetectionEvent) {
                readyCalled = true
            }

            override fun onSpeechRecognitionRequested(triggerSource: String) {
                speechRequestedSource = triggerSource
            }

            override fun onConversationEnded() {
                conversationEndedCalled = true
            }
        })

        // Step 1: Start standby -> must wait for "MAX" only, NOT activate speech immediately
        manager.startService()
        assertEquals(WakeWordState.WAITING_FOR_WAKE_WORD, manager.wakeState.value)
        assertFalse(readyCalled)
        assertEquals(null, speechRequestedSource)

        // Step 2: Non-wake speech -> ignored; stays in standby
        assertFalse(manager.processWakeCandidate("open youtube please"))
        assertEquals(WakeWordState.WAITING_FOR_WAKE_WORD, manager.wakeState.value)
        assertEquals(null, detectedEvent)
        assertEquals(null, speechRequestedSource)

        // Step 3: "MAX" detected -> notifies listener, prepares conversation, requests speech recognition
        assertTrue(manager.processWakeCandidate("MAX"))
        assertNotNull(detectedEvent)
        assertEquals("MAX", detectedEvent?.wakeWord)
        assertEquals("", detectedEvent?.trailingCommand)
        assertEquals(WakeWordState.WAKE_DETECTED, manager.wakeState.value)
        assertTrue(readyCalled)
        assertEquals("wake_word:MAX", speechRequestedSource)

        // Step 4: Conversation finishes -> standby resumes
        manager.notifyConversationEnded()
        assertTrue(conversationEndedCalled)
        assertEquals(WakeWordState.WAITING_FOR_WAKE_WORD, manager.wakeState.value)
    }

    @Test
    fun maxCore_receivesWakeEventAndNotifiesConnector() = runBlocking {
        val core = MAXCore.getInstance(app)
        var connectorReady = false
        var connectorSpeechTriggered = false

        val connector = object : VoiceConversationConnector {
            override fun onReadyForConversation(event: WakeWordDetectionEvent) {
                connectorReady = true
            }

            override fun onSpeechRecognitionRequested(triggerSource: String) {
                connectorSpeechTriggered = triggerSource == "wake_word:MAX"
            }
        }
        core.attachVoiceConversationConnector(connector)

        var emitted: WakeWordDetectionEvent? = null
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            emitted = withTimeout(3_000L) {
                core.wakeDetectionEvents.first()
            }
        }

        core.handleWakeWordTriggered("")
        collectJob.join()

        assertNotNull(emitted)
        assertEquals("MAX", emitted?.wakeWord)
        assertTrue(connectorReady)
        assertTrue(connectorSpeechTriggered)

        core.wakeWordManager.detachConversationConnector(connector)
    }

    private class FakeWakeWordEngine : WakeWordEngine {
        override val config: WakeWordConfig = WakeWordConfig()
        private val _state = MutableStateFlow(WakeWordState.STOPPED)
        override val state: StateFlow<WakeWordState> = _state

        override fun startListening(listener: WakeWordListener) {
            _state.value = WakeWordState.WAITING_FOR_WAKE_WORD
        }

        override fun stopListening() {
            _state.value = WakeWordState.STOPPED
        }

        override fun pause() {
            _state.value = WakeWordState.PAUSED_FOR_SPEECH
        }

        override fun resume() {
            _state.value = WakeWordState.WAITING_FOR_WAKE_WORD
        }
    }
}
