package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.MaxAiService
import com.example.data.UserPreferencesRepository
import com.example.device.NetworkMonitor
import com.example.domain.PrivacyGuard
import com.example.domain.VoiceLanguage
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import com.example.wakeword.WakeWordConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Foreground background service responsible for continuous audio monitoring for the
 * "Hey MAX" / "MAX" wake word.
 *
 * All PCM audio capture, RMS voice-activity detection (VAD), pre-roll ring buffering,
 * WAV encoding, wake-word verification, telephony state tracking, and service lifecycle
 * orchestration execute on dedicated background threads/dispatchers completely independent
 * of the Android main UI thread.
 */
class VoiceActivationService : Service() {

    /** Abstraction for reading 16-bit PCM frames off the main UI thread (enables unit testing). */
    fun interface AudioFrameSource {
        fun openStream(sampleRate: Int, frameSizeSamples: Int): AudioFrameSession?
    }

    interface AudioFrameSession {
        fun readFrame(buffer: ShortArray): Int
        fun close()
    }

    /** Abstraction for classifying a captured speech WAV segment off the main UI thread. */
    fun interface WakePhraseDetector {
        suspend fun detectTranscript(wavBytes: ByteArray, preferredLanguage: VoiceLanguage): String?
    }

    private val audioThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, AUDIO_THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, WORKER_THREAD_NAME).apply {
            isDaemon = true
        }
    }

    private val audioDispatcher: ExecutorCoroutineDispatcher = audioThreadExecutor.asCoroutineDispatcher()
    private val workerDispatcher: ExecutorCoroutineDispatcher = workerExecutor.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(SupervisorJob() + workerDispatcher)

    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var networkMonitor: NetworkMonitor? = null
    private var aiService: MaxAiService? = null
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    @Volatile
    private var isMonitoringRequested = false

    @Volatile
    private var currentLanguage: VoiceLanguage = VoiceLanguage.AUTO

    @VisibleForTesting
    internal var wakeConfig: WakeWordConfig = WakeWordConfig()

    @VisibleForTesting
    internal var strictPrivacyMode: Boolean = true

    private var monitoringJob: Job? = null
    private var coordinatorJob: Job? = null
    private var prefsJob: Job? = null

    private var lastEvaluationTimestampMs = 0L

    @VisibleForTesting
    internal var audioFrameSource: AudioFrameSource = AudioRecordFrameSource()

    @VisibleForTesting
    internal var wakePhraseDetector: WakePhraseDetector = WakePhraseDetector { wavBytes, lang ->
        // Zero-Leak Privacy Shield: never transmit background ambient audio to cloud AI when strictPrivacyMode is ON.
        if (strictPrivacyMode) {
            null
        } else {
            val online = networkMonitor?.checkCurrentConnectivity() == true
            if (!online) {
                null
            } else {
                aiService?.detectWakeWordInAudio(wavBytes, lang)
            }
        }
    }

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    internal var onWakeActionDispatcher: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        callbackThread = HandlerThread("VoiceActivationCallbackThread").apply { start() }
        callbackHandler = Handler(callbackThread!!.looper)

        networkMonitor = NetworkMonitor(applicationContext)
        aiService = MaxAiService()
        createNotificationChannel()
        acquireWakeLock()
        WakeWordCoordinator.setServiceRunning(true)
        registerCallStateObserverOffMainThread()

        prefsJob = serviceScope.launch {
            UserPreferencesRepository(applicationContext).preferencesFlow
                .map { it.preferredLanguage to it.strictPrivacyMode }
                .distinctUntilChanged()
                .collectLatest { (lang, strictPrivacy) ->
                    currentLanguage = lang
                    strictPrivacyMode = strictPrivacy
                    if (isMonitoringRequested && monitoringJob?.isActive == true) {
                        WakeWordCoordinator.updateStatus(
                            if (strictPrivacy) "Privacy Shield ON • Local audio guard"
                            else "Listening for \"Hey MAX\"…"
                        )
                    }
                }
        }

        coordinatorJob = serviceScope.launch {
            combine(
                WakeWordCoordinator.isForegroundMicBusy,
                WakeWordCoordinator.isCallActive
            ) { micBusy, callActive -> micBusy || callActive }
                .distinctUntilChanged()
                .collectLatest { paused ->
                    if (paused) {
                        stopAudioMonitoringLoop()
                        if (WakeWordCoordinator.isCallActive.value) {
                            WakeWordCoordinator.updateStatus("Paused during phone call")
                        } else {
                            WakeWordCoordinator.updateStatus("Paused for active mic")
                        }
                    } else if (isMonitoringRequested) {
                        startAudioMonitoringLoop()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing; stopping VoiceActivationService.")
            WakeWordCoordinator.updateStatus("Microphone permission needed")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isMonitoringRequested = true
        if (!WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
            startAudioMonitoringLoop()
        }

        return START_STICKY
    }

    private fun registerCallStateObserverOffMainThread() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        telephonyManager = tm

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        val inCall = state == TelephonyManager.CALL_STATE_OFFHOOK ||
                            state == TelephonyManager.CALL_STATE_RINGING
                        WakeWordCoordinator.setCallActive(inCall)
                    }
                }
                tm.registerTelephonyCallback(workerExecutor, callback)
                telephonyCallback = callback
            }
        } else {
            callbackHandler?.post {
                runCatching {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Suppress("DEPRECATION")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            super.onCallStateChanged(state, phoneNumber)
                            val inCall = state == TelephonyManager.CALL_STATE_OFFHOOK ||
                                state == TelephonyManager.CALL_STATE_RINGING
                            WakeWordCoordinator.setCallActive(inCall)
                        }
                    }
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                    legacyPhoneStateListener = listener
                }
            }
        }
    }

    private fun unregisterCallStateObserver() {
        val tm = telephonyManager
        if (tm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { cb ->
                    runCatching { tm.unregisterTelephonyCallback(cb) }
                }
                telephonyCallback = null
            } else {
                legacyPhoneStateListener?.let { listener ->
                    callbackHandler?.post {
                        @Suppress("DEPRECATION")
                        runCatching { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
                    }
                }
                legacyPhoneStateListener = null
            }
        }
        telephonyManager = null
        WakeWordCoordinator.setCallActive(false)
    }

    @VisibleForTesting
    internal fun startAudioMonitoringLoop() {
        if (monitoringJob?.isActive == true) return
        if (!isMonitoringRequested) return
        if (WakeWordCoordinator.isForegroundMicBusy.value || WakeWordCoordinator.isCallActive.value) return

        monitoringJob = serviceScope.launch(audioDispatcher) {
            // Confirm execution is strictly independent of the Android main UI thread
            check(Looper.myLooper() != Looper.getMainLooper()) {
                "Continuous audio monitoring must not run on the main UI thread"
            }

            val sampleRate = wakeConfig.sampleRateHz
            val frameDurationMs = wakeConfig.frameDurationMs
            val frameSizeSamples = (sampleRate * frameDurationMs) / 1000
            val frameBuffer = ShortArray(frameSizeSamples)
            val preRollMaxFrames = wakeConfig.preRollMs / frameDurationMs
            val preRollFrames = ArrayDeque<ShortArray>(preRollMaxFrames + 1)

            while (isActive &&
                isMonitoringRequested &&
                !WakeWordCoordinator.isForegroundMicBusy.value &&
                !WakeWordCoordinator.isCallActive.value
            ) {
                val session = runCatching {
                    audioFrameSource.openStream(sampleRate, frameSizeSamples)
                }.getOrNull()

                if (session == null) {
                    WakeWordCoordinator.updateStatus("Waiting for microphone…")
                    delay(1200L)
                    continue
                }

                WakeWordCoordinator.updateStatus(
                    if (strictPrivacyMode) "Privacy Shield ON • Local audio guard"
                    else "Listening for \"Hey MAX\"…"
                )
                preRollFrames.clear()

                var adaptiveNoiseFloor = INITIAL_NOISE_FLOOR_RMS
                var consecutiveSpeechFrames = 0
                var capturingUtterance = false
                var speechFrameCount = 0
                var silenceDurationMs = 0L
                var utteranceStartMs = 0L
                val pcmOut = ByteArrayOutputStream()

                try {
                    while (isActive &&
                        isMonitoringRequested &&
                        !WakeWordCoordinator.isForegroundMicBusy.value &&
                        !WakeWordCoordinator.isCallActive.value
                    ) {
                        val read = session.readFrame(frameBuffer)
                        if (read <= 0) {
                            if (read < 0) break
                            continue
                        }

                        val rms = computeRms(frameBuffer, read)
                        val speechThreshold = max(MIN_SPEECH_RMS, adaptiveNoiseFloor * SPEECH_MULTIPLIER)
                        val isSpeechFrame = rms >= speechThreshold

                        if (!capturingUtterance) {
                            // Exponential moving average updates ambient noise floor while idle
                            if (!isSpeechFrame) {
                                adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.94f) + (rms * 0.06f)
                                consecutiveSpeechFrames = 0
                            } else {
                                consecutiveSpeechFrames++
                            }

                            preRollFrames.addLast(frameBuffer.copyOf(read))
                            if (preRollFrames.size > preRollMaxFrames) {
                                preRollFrames.removeFirst()
                            }

                            val cooldownElapsed =
                                System.currentTimeMillis() - lastEvaluationTimestampMs >= wakeConfig.cooldownMs

                            if (consecutiveSpeechFrames >= ONSET_CONSECUTIVE_FRAMES && cooldownElapsed) {
                                capturingUtterance = true
                                utteranceStartMs = System.currentTimeMillis()
                                speechFrameCount = consecutiveSpeechFrames
                                silenceDurationMs = 0L
                                pcmOut.reset()
                                for (preFrame in preRollFrames) {
                                    writePcmLe16(pcmOut, preFrame, preFrame.size)
                                }
                                preRollFrames.clear()
                            }
                        } else {
                            writePcmLe16(pcmOut, frameBuffer, read)
                            val frameMs = (read * 1000L) / sampleRate

                            if (isSpeechFrame) {
                                speechFrameCount++
                                silenceDurationMs = 0L
                            } else {
                                silenceDurationMs += frameMs
                            }

                            val utteranceDurationMs = System.currentTimeMillis() - utteranceStartMs
                            // Cap wake inspection to short wake-phrase window; never record long conversations
                            if (silenceDurationMs >= TRAILING_SILENCE_MS ||
                                utteranceDurationMs >= wakeConfig.maxWakeWindowMs
                            ) {
                                val capturedPcm = pcmOut.toByteArray()
                                val speechDurationMs = speechFrameCount * frameDurationMs.toLong()
                                capturingUtterance = false
                                consecutiveSpeechFrames = 0
                                speechFrameCount = 0
                                silenceDurationMs = 0L
                                pcmOut.reset()

                                if (speechDurationMs >= MIN_SPEECH_DURATION_MS &&
                                    capturedPcm.size >= MIN_PCM_BYTES
                                ) {
                                    lastEvaluationTimestampMs = System.currentTimeMillis()
                                    val wavBytes = encodePcmToWav(capturedPcm, sampleRate)
                                    val triggered = try {
                                        evaluateAudioSegmentOffMainThread(wavBytes)
                                    } finally {
                                        PrivacyGuard.wipeBuffer(wavBytes)
                                    }
                                    if (triggered) {
                                        // Brief pause after wake activation so foreground mic/TTS takes over cleanly
                                        delay(900L)
                                    }
                                }
                                PrivacyGuard.wipeBuffer(capturedPcm)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Background audio monitoring loop error", e)
                    delay(800L)
                } finally {
                    PrivacyGuard.wipeBuffer(frameBuffer)
                    for (frame in preRollFrames) {
                        PrivacyGuard.wipeBuffer(frame)
                    }
                    preRollFrames.clear()
                    runCatching { session.close() }
                }
            }
        }
    }

    private fun stopAudioMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    @VisibleForTesting
    internal suspend fun evaluateAudioSegmentOffMainThread(wavBytes: ByteArray): Boolean {
        val transcript = withContext(ioDispatcher) {
            runCatching {
                wakePhraseDetector.detectTranscript(wavBytes, currentLanguage)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Wake phrase detector failed", err)
            }.getOrNull()
        } ?: return false

        return processTranscriptForWakeWord(transcript)
    }

    @VisibleForTesting
    internal fun processTranscriptForWakeWord(transcript: String): Boolean {
        return when (val parsed = WakeWordParser.parse(transcript)) {
            is WakeWordResult.Triggered -> {
                onWakeDetected(parsed.command)
                true
            }
            WakeWordResult.NotDetected -> false
        }
    }

    private fun onWakeDetected(command: String) {
        playWakeChimeOffMainThread()
        WakeWordCoordinator.updateStatus("Hey MAX activated!")
        WakeWordCoordinator.emitWakeTrigger(command)

        val customHandler = onWakeActionDispatcher
        if (customHandler != null) {
            customHandler.invoke(command)
        } else {
            MaxAssistantController.getInstance(applicationContext).handleWakeWordTriggered(command)
        }
    }

    private fun playWakeChimeOffMainThread() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            callbackHandler?.postDelayed({ runCatching { tone.release() } }, 300L)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MaxAi::VoiceActivationPartialLock"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuously monitors audio for the \"Hey MAX\" wake word in the background."
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceActivationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.wake_service_notification_title))
            .setContentText(getString(R.string.wake_service_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isMonitoringRequested) {
            val restartIntent = Intent(applicationContext, VoiceActivationService::class.java)
            ContextCompat.startForegroundService(applicationContext, restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isMonitoringRequested = false
        stopAudioMonitoringLoop()
        prefsJob?.cancel()
        coordinatorJob?.cancel()
        unregisterCallStateObserver()
        networkMonitor?.unregister()
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        WakeWordCoordinator.setServiceRunning(false)
        WakeWordCoordinator.updateStatus("Standby")
        serviceScope.cancel()
        audioDispatcher.close()
        workerDispatcher.close()
        audioThreadExecutor.shutdownNow()
        workerExecutor.shutdownNow()
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class AudioRecordFrameSource : AudioFrameSource {
        @SuppressLint("MissingPermission")
        override fun openStream(sampleRate: Int, frameSizeSamples: Int): AudioFrameSession? {
            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameSizeSamples * 4)

            val sources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            for (source in sources) {
                val recorder = runCatching {
                    AudioRecord(
                        source,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufBytes * 2
                    )
                }.getOrNull() ?: continue

                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    val started = runCatching {
                        recorder.startRecording()
                        recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    }.getOrDefault(false)

                    if (started) {
                        return object : AudioFrameSession {
                            override fun readFrame(buffer: ShortArray): Int =
                                recorder.read(buffer, 0, buffer.size)

                            override fun close() {
                                runCatching { recorder.stop() }
                                runCatching { recorder.release() }
                            }
                        }
                    }
                }
                runCatching { recorder.release() }
            }
            return null
        }
    }

    companion object {
        private const val TAG = "VoiceActivationService"
        internal const val AUDIO_THREAD_NAME = "VoiceActivationAudioThread"
        private const val WORKER_THREAD_NAME = "VoiceActivationWorkThread"
        private const val CHANNEL_ID = "max_wake_word_channel"
        private const val NOTIFICATION_ID = 4201

        private const val SAMPLE_RATE = 16_000
        private const val FRAME_DURATION_MS = 32
        private const val PRE_ROLL_MS = 960
        private const val ONSET_CONSECUTIVE_FRAMES = 3
        private const val TRAILING_SILENCE_MS = 550L
        private const val MAX_UTTERANCE_MS = 3600L
        private const val MIN_SPEECH_DURATION_MS = 220L
        private const val MIN_PCM_BYTES = 4096
        private const val EVALUATION_COOLDOWN_MS = 1400L

        private const val INITIAL_NOISE_FLOOR_RMS = 260f
        private const val MIN_SPEECH_RMS = 520f
        private const val SPEECH_MULTIPLIER = 2.5f

        const val ACTION_STOP = "com.example.action.STOP_WAKE_SERVICE"
        const val ACTION_WAKE_TRIGGERED = "com.example.action.WAKE_TRIGGERED"
        const val EXTRA_WAKE_COMMAND = "extra_wake_command"

        fun start(context: Context) {
            val intent = Intent(context, VoiceActivationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceActivationService::class.java))
        }

        internal fun computeRms(samples: ShortArray, count: Int): Float {
            if (count <= 0) return 0f
            var sumSq = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble()
                sumSq += s * s
            }
            return sqrt(sumSq / count).toFloat()
        }

        private fun writePcmLe16(out: ByteArrayOutputStream, samples: ShortArray, count: Int) {
            for (i in 0 until count) {
                val v = samples[i].toInt()
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }
        }

        internal fun encodePcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(totalDataLen)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(2.toShort())
                putShort(16.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(pcmData.size)
            }.array()
            return header + pcmData
        }
    }
}
