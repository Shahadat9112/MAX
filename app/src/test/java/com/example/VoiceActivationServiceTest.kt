package com.example

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.service.VoiceActivationService
import com.example.service.WakeTriggerEvent
import com.example.service.WakeWordCoordinator
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceActivationServiceTest {

    private lateinit var app: Application
    private var controller: ServiceController<VoiceActivationService>? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        WakeWordCoordinator.setServiceRunning(false)
        WakeWordCoordinator.setForegroundMicBusy(false)
        WakeWordCoordinator.setCallActive(false)
        WakeWordCoordinator.updateStatus("Standby")
    }

    @After
    fun tearDown() {
        runCatching { controller?.destroy() }
        controller = null
        WakeWordCoordinator.setServiceRunning(false)
        WakeWordCoordinator.setForegroundMicBusy(false)
        WakeWordCoordinator.setCallActive(false)
    }

    @Test
    fun continuousMonitoring_runsOffMainThread_andTriggersOnHeyMax() = runBlocking {
        val serviceCtrl = Robolectric.buildService(VoiceActivationService::class.java)
        controller = serviceCtrl
        val service = serviceCtrl.create().get()

        val observedAudioThreadName = AtomicReference<String?>(null)
        val observedIsMainLooper = AtomicReference<Boolean?>(null)
        val observedDetectorThreadName = AtomicReference<String?>(null)
        val streamOpenedLatch = CountDownLatch(1)
        val wakeDispatchedLatch = CountDownLatch(1)
        val receivedCommand = AtomicReference<String?>(null)
        val frameCounter = AtomicInteger(0)

        service.ioDispatcher = Dispatchers.IO
        service.onWakeActionDispatcher = { cmd ->
            receivedCommand.set(cmd)
            wakeDispatchedLatch.countDown()
        }

        // Simulate 200ms pre-roll silence, then 350ms loud speech frames ("Hey MAX, open camera"), then trailing silence
        service.audioFrameSource = VoiceActivationService.AudioFrameSource { _, _ ->
            observedAudioThreadName.set(Thread.currentThread().name)
            observedIsMainLooper.set(Looper.myLooper() == Looper.getMainLooper())
            streamOpenedLatch.countDown()

            object : VoiceActivationService.AudioFrameSession {
                override fun readFrame(buffer: ShortArray): Int {
                    val idx = frameCounter.getAndIncrement()
                    val amplitude: Short = when {
                        idx in 6..18 -> 4500 // ~416ms speech burst above VAD threshold
                        else -> {
                            if (idx > 45) Thread.sleep(20L)
                            80 // quiet ambient floor
                        }
                    }
                    buffer.fill(amplitude)
                    return buffer.size
                }

                override fun close() = Unit
            }
        }

        service.wakePhraseDetector = VoiceActivationService.WakePhraseDetector { wavBytes, _ ->
            observedDetectorThreadName.set(Thread.currentThread().name)
            // Verify valid WAV header (RIFF)
            assertTrue(wavBytes.size > 44)
            assertEquals("RIFF", String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII))
            "Hey MAX, open camera"
        }

        val collectedEvent = AtomicReference<WakeTriggerEvent?>(null)
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            collectedEvent.set(
                withTimeout(5000L) {
                    WakeWordCoordinator.wakeEvents.first()
                }
            )
        }

        serviceCtrl.startCommand(0, 0)

        assertTrue("Audio monitoring stream did not start", streamOpenedLatch.await(3, TimeUnit.SECONDS))
        assertTrue("Wake trigger did not fire for Hey MAX", wakeDispatchedLatch.await(4, TimeUnit.SECONDS))
        collectJob.join()

        assertTrue(WakeWordCoordinator.isServiceRunning.value)
        assertTrue(
            observedAudioThreadName.get()?.startsWith(VoiceActivationService.AUDIO_THREAD_NAME) == true
        )
        assertEquals(false, observedIsMainLooper.get())
        assertNotEquals(Looper.getMainLooper().thread.name, observedDetectorThreadName.get())
        assertEquals("open camera", receivedCommand.get())
        assertEquals("open camera", collectedEvent.get()?.command)
    }

    @Test
    fun nonWakeUtterance_doesNotTriggerWakeEvent() = runBlocking {
        val serviceCtrl = Robolectric.buildService(VoiceActivationService::class.java)
        controller = serviceCtrl
        val service = serviceCtrl.create().get()

        var triggered = false
        service.onWakeActionDispatcher = { triggered = true }

        val nonWakeMatched = service.processTranscriptForWakeWord("What is the weather outside?")
        assertFalse(nonWakeMatched)
        assertFalse(triggered)

        val heyMaxMatched = service.processTranscriptForWakeWord("Hey MAX")
        assertTrue(heyMaxMatched)
        assertTrue(triggered)
    }

    @Test
    fun callActive_pausesMonitoring_andResumesWhenIdle() {
        val serviceCtrl = Robolectric.buildService(VoiceActivationService::class.java)
        controller = serviceCtrl
        val service = serviceCtrl.create().get()

        val openCount = AtomicInteger(0)
        val closeLatch = CountDownLatch(1)
        val resumeLatch = CountDownLatch(1)

        service.audioFrameSource = VoiceActivationService.AudioFrameSource { _, _ ->
            val count = openCount.incrementAndGet()
            if (count >= 2) {
                resumeLatch.countDown()
            }
            object : VoiceActivationService.AudioFrameSession {
                override fun readFrame(buffer: ShortArray): Int {
                    Thread.sleep(25L)
                    buffer.fill(40)
                    return buffer.size
                }

                override fun close() {
                    closeLatch.countDown()
                }
            }
        }

        serviceCtrl.startCommand(0, 0)
        // Wait briefly for first stream to open
        val deadline = System.currentTimeMillis() + 2000L
        while (openCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L)
        }
        assertTrue(openCount.get() >= 1)

        // Simulate active phone call -> should pause and close AudioFrameSession
        WakeWordCoordinator.setCallActive(true)
        assertTrue("Monitoring should pause and close audio session during call", closeLatch.await(3, TimeUnit.SECONDS))

        // End phone call -> should resume monitoring automatically
        WakeWordCoordinator.setCallActive(false)
        assertTrue("Monitoring should resume after call ends", resumeLatch.await(3, TimeUnit.SECONDS))

        // Stop command stops service cleanly
        val stopIntent = Intent(app, VoiceActivationService::class.java).apply {
            action = VoiceActivationService.ACTION_STOP
        }
        serviceCtrl.startCommand(0, 0)
        service.onStartCommand(stopIntent, 0, 1)
        serviceCtrl.destroy()
        assertFalse(WakeWordCoordinator.isServiceRunning.value)
    }
}
