package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.UserPreferencesRepository
import com.example.device.NetworkMonitor
import com.example.device.VoiceRecognitionManager
import com.example.domain.VoiceLanguage
import com.example.domain.WakeWordParser
import com.example.domain.WakeWordResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MaxWakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkMonitor: NetworkMonitor? = null
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var isListeningLoopActive = false
    private var busyJob: Job? = null
    private var prefsJob: Job? = null
    private var currentLanguage: VoiceLanguage = VoiceLanguage.AUTO
    private var wakeLocaleIndex = 0
    private var wakeTriggeredThisUtterance = false

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(applicationContext)
        createNotificationChannel()
        acquireWakeLock()
        WakeWordCoordinator.setServiceRunning(true)
        setupPhoneStateListener()

        prefsJob = serviceScope.launch {
            UserPreferencesRepository(applicationContext).preferencesFlow
                .map { it.preferredLanguage }
                .distinctUntilChanged()
                .collectLatest { lang ->
                    val changed = currentLanguage != lang
                    currentLanguage = lang
                    wakeLocaleIndex = 0
                    if (changed && isListeningLoopActive && !WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
                        scheduleRestart(250L)
                    }
                }
        }

        busyJob = serviceScope.launch {
            combine(
                WakeWordCoordinator.isForegroundMicBusy,
                WakeWordCoordinator.isCallActive
            ) { busy, callActive -> busy || callActive }.collectLatest { paused ->
                if (paused) {
                    pauseRecognizer()
                    if (WakeWordCoordinator.isCallActive.value) {
                        WakeWordCoordinator.updateStatus("Paused during phone call")
                    }
                } else if (isListeningLoopActive) {
                    scheduleRestart(350L)
                }
            }
        }
    }

    private fun setupPhoneStateListener() {
        runCatching {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    super.onCallStateChanged(state, phoneNumber)
                    val inCall = state == TelephonyManager.CALL_STATE_OFFHOOK ||
                        state == TelephonyManager.CALL_STATE_RINGING
                    WakeWordCoordinator.setCallActive(inCall)
                }
            }
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            phoneStateListener = listener
        }
    }

    private fun teardownPhoneStateListener() {
        runCatching {
            if (phoneStateListener != null && telephonyManager != null) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
            phoneStateListener = null
            telephonyManager = null
            WakeWordCoordinator.setCallActive(false)
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
            Log.w(TAG, "RECORD_AUDIO permission missing; stopping wake-word service.")
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

        isListeningLoopActive = true
        if (!WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
            scheduleRestart(200L)
        }

        return START_STICKY
    }

    private fun startWakeListening() {
        if (!isListeningLoopActive || WakeWordCoordinator.isForegroundMicBusy.value || WakeWordCoordinator.isCallActive.value) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            WakeWordCoordinator.updateStatus("Tap mic to talk (background STT unavailable)")
            return
        }

        destroyRecognizer()
        wakeTriggeredThisUtterance = false

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        WakeWordCoordinator.updateStatus("Listening for \"MAX\" / \"Hey MAX\"…")
                        scheduleWatchdog()
                    }

                    override fun onBeginningOfSpeech() {
                        scheduleWatchdog()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        cancelWatchdog()
                        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        ) {
                            val candidates = VoiceRecognitionManager.resolveLocaleCandidates(currentLanguage)
                            if (candidates.size > 1) {
                                wakeLocaleIndex = (wakeLocaleIndex + 1) % candidates.size
                            }
                        }
                        val delay = when (error) {
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 900L
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                stopSelf()
                                return
                            }
                            else -> 450L
                        }
                        if (isListeningLoopActive && !WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
                            scheduleRestart(delay)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        cancelWatchdog()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleMatches(matches)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val first = matches?.firstOrNull().orEmpty()
                        if (first.isNotBlank() && !wakeTriggeredThisUtterance) {
                            when (val parsed = WakeWordParser.parse(first)) {
                                is WakeWordResult.Triggered -> {
                                    val wordCount = first.trim().split(Regex("\\s+")).size
                                    if (wordCount == 1 && parsed.command.isEmpty()) {
                                        wakeTriggeredThisUtterance = true
                                        onWakeDetected("")
                                    }
                                }
                                WakeWordResult.NotDetected -> Unit
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val isOffline = networkMonitor?.checkCurrentConnectivity() == false
            val candidates = VoiceRecognitionManager.resolveLocaleCandidates(currentLanguage)
            val localeTag = candidates.getOrElse(wakeLocaleIndex % candidates.size) {
                currentLanguage.bcp47Tag
            }
            val intent = VoiceRecognitionManager.buildRecognizerIntent(
                context = this,
                language = currentLanguage,
                localeTag = localeTag,
                preferOffline = isOffline,
                enableLanguageDetection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    currentLanguage == VoiceLanguage.AUTO
            )

            speechRecognizer?.startListening(intent)
            scheduleWatchdog()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake speech recognizer", e)
            scheduleRestart(1200L)
        }
    }

    private fun handleMatches(matches: ArrayList<String>?) {
        if (wakeTriggeredThisUtterance) {
            if (isListeningLoopActive && !WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
                scheduleRestart(500L)
            }
            return
        }

        val transcript = matches?.firstOrNull()?.trim().orEmpty()
        var woke = false

        if (transcript.isNotEmpty()) {
            when (val parsed = WakeWordParser.parse(transcript)) {
                is WakeWordResult.Triggered -> {
                    woke = true
                    onWakeDetected(parsed.command)
                }
                WakeWordResult.NotDetected -> Unit
            }
        }

        if (!woke && isListeningLoopActive && !WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
            scheduleRestart(350L)
        }
    }

    private fun onWakeDetected(command: String) {
        pauseRecognizer()
        playWakeChime()
        WakeWordCoordinator.updateStatus("MAX activated!")
        WakeWordCoordinator.emitWakeTrigger(command)

        MaxAssistantController.getInstance(applicationContext).handleWakeWordTriggered(command)

        if (isListeningLoopActive && !WakeWordCoordinator.isCallActive.value) {
            scheduleRestart(1800L)
        }
    }

    private fun playWakeChime() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            mainHandler.postDelayed({ runCatching { tone.release() } }, 300L)
        }
    }

    private fun scheduleWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, 14_000L)
    }

    private fun cancelWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
    }

    private val watchdogRunnable = Runnable {
        if (isListeningLoopActive && !WakeWordCoordinator.isForegroundMicBusy.value && !WakeWordCoordinator.isCallActive.value) {
            Log.d(TAG, "Watchdog recycling background SpeechRecognizer")
            startWakeListening()
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private val restartRunnable = Runnable {
        startWakeListening()
    }

    private fun pauseRecognizer() {
        cancelWatchdog()
        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MaxAi::WakeWordPartialLock"
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
                description = "Keeps MAX AI listening for the \"MAX\" wake word in the background."
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
            Intent(this, MaxWakeWordService::class.java).apply { action = ACTION_STOP },
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
        if (isListeningLoopActive) {
            val restartIntent = Intent(applicationContext, MaxWakeWordService::class.java)
            ContextCompat.startForegroundService(applicationContext, restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isListeningLoopActive = false
        mainHandler.removeCallbacksAndMessages(null)
        prefsJob?.cancel()
        busyJob?.cancel()
        destroyRecognizer()
        teardownPhoneStateListener()
        networkMonitor?.unregister()
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        WakeWordCoordinator.setServiceRunning(false)
        WakeWordCoordinator.updateStatus("Standby")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MaxWakeWordService"
        private const val CHANNEL_ID = "max_wake_word_channel"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.example.action.STOP_WAKE_SERVICE"
        const val ACTION_WAKE_TRIGGERED = "com.example.action.WAKE_TRIGGERED"
        const val EXTRA_WAKE_COMMAND = "extra_wake_command"

        fun start(context: Context) {
            val intent = Intent(context, MaxWakeWordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MaxWakeWordService::class.java))
        }
    }
}
