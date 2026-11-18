package com.example.device

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.domain.SpeechRecognitionResult
import com.example.domain.VoiceLanguage
import com.example.domain.WakeWordParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Bilingual Android SpeechRecognizer manager supporting English and Bengali locales:
 * 1) Configures native SpeechRecognizer (including API 34+ multi-language detection in AUTO mode
 *    and API 31+ on-device recognition when offline).
 * 2) Automatically retries fallback locale variants (e.g. bn-BD -> bn-IN -> bn, or device English
 *    -> en-US -> en-GB) if a specific locale is unsupported on the device.
 * 3) Gracefully falls back to raw WAV AudioRecord + Gemini Multimodal Audio when online if the
 *    device lacks a SpeechRecognizer service or speech recognition fails due to server/client issues.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val recognizerFactory: RecognizerFactory = DefaultRecognizerFactory()
) {
    interface RecognizerFactory {
        fun isRecognitionAvailable(context: Context): Boolean
        fun isOnDeviceRecognitionAvailable(context: Context): Boolean
        fun createSpeechRecognizer(context: Context, preferOnDevice: Boolean): SpeechRecognizer
    }

    private class DefaultRecognizerFactory : RecognizerFactory {
        override fun isRecognitionAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        override fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        override fun createSpeechRecognizer(context: Context, preferOnDevice: Boolean): SpeechRecognizer {
            if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }
            return SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private val audioFallbackRecorder = AudioCaptureRecorder()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _activeLocaleTag = MutableStateFlow("en-US")
    val activeLocaleTag: StateFlow<String> = _activeLocaleTag.asStateFlow()

    private var onTextResultCallback: ((SpeechRecognitionResult) -> Unit)? = null
    private var onAudioFallbackCallback: ((ByteArray) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private var activeLanguageMode: VoiceLanguage = VoiceLanguage.AUTO
    private var activePreferOffline: Boolean = false
    private var localeAttemptQueue: MutableList<String> = mutableListOf()
    private var currentLocaleTag: String = "en-US"
    private var tryWithLanguageDetection: Boolean = false
    private var detectedLanguageHint: String? = null
    private var isFinalizingStop: Boolean = false

    private val finalizeStopTimeoutRunnable = Runnable {
        if (isFinalizingStop) {
            isFinalizingStop = false
            val partial = _partialText.value.trim()
            stopListeningInternal()
            if (partial.isNotEmpty()) {
                val lang = resolveLanguageCode(partial, detectedLanguageHint, currentLocaleTag)
                onTextResultCallback?.invoke(
                    SpeechRecognitionResult(
                        text = partial,
                        detectedLanguage = lang,
                        localeTag = currentLocaleTag
                    )
                )
            }
        }
    }

    fun startListening(
        language: VoiceLanguage,
        preferOffline: Boolean,
        onResult: (SpeechRecognitionResult) -> Unit,
        onAudioFallback: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stopListeningInternal()

            onTextResultCallback = onResult
            onAudioFallbackCallback = onAudioFallback
            onErrorCallback = onError
            activeLanguageMode = language
            activePreferOffline = preferOffline
            detectedLanguageHint = null
            isFinalizingStop = false
            _partialText.value = ""

            val candidates = resolveLocaleCandidates(language, Locale.getDefault())
            localeAttemptQueue = candidates.toMutableList()

            if (!recognizerFactory.isRecognitionAvailable(context) &&
                !(preferOffline && recognizerFactory.isOnDeviceRecognitionAvailable(context))
            ) {
                Log.w(TAG, "SpeechRecognizer unavailable; starting Gemini WAV audio capture fallback.")
                if (preferOffline) {
                    _isListening.value = false
                    onErrorCallback?.invoke(
                        "Speech recognition service is unavailable offline on this device."
                    )
                } else {
                    startAudioFallbackRecording()
                }
                return@post
            }

            tryWithLanguageDetection =
                language == VoiceLanguage.AUTO &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

            startNextLocaleAttempt()
        }
    }

    private fun startNextLocaleAttempt() {
        val nextLocale = localeAttemptQueue.removeFirstOrNull()
        if (nextLocale == null) {
            if (!activePreferOffline) {
                _partialText.value = "Listening via Voice AI…"
                startAudioFallbackRecording()
            } else {
                _isListening.value = false
                _audioLevel.value = 0f
                val msg = if (activeLanguageMode == VoiceLanguage.BENGALI) {
                    "Offline Bengali speech pack (bn-BD / bn-IN) is not installed on this device."
                } else {
                    "Offline speech pack unavailable without internet."
                }
                onErrorCallback?.invoke(msg)
            }
            return
        }

        currentLocaleTag = nextLocale
        _activeLocaleTag.value = nextLocale
        detectedLanguageHint = null
        destroySpeechRecognizerOnly()

        try {
            val useOnDevice = activePreferOffline && recognizerFactory.isOnDeviceRecognitionAvailable(context)
            speechRecognizer = recognizerFactory.createSpeechRecognizer(context, useOnDevice).apply {
                setRecognitionListener(createRecognitionListener())
            }

            val intent = buildRecognizerIntent(
                context = context,
                language = activeLanguageMode,
                localeTag = nextLocale,
                preferOffline = activePreferOffline,
                enableLanguageDetection = tryWithLanguageDetection
            )

            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer start threw exception for locale=$nextLocale", e)
            destroySpeechRecognizerOnly()
            if (localeAttemptQueue.isNotEmpty()) {
                startNextLocaleAttempt()
            } else if (!activePreferOffline) {
                startAudioFallbackRecording()
            } else {
                _isListening.value = false
                onErrorCallback?.invoke("Unable to start SpeechRecognizer: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _audioLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _audioLevel.value = 0f
            }

            override fun onLanguageDetection(results: Bundle) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)?.trim()
                    if (!detected.isNullOrEmpty()) {
                        detectedLanguageHint = detected
                        _activeLocaleTag.value = detected
                        Log.d(TAG, "Detected spoken language: $detected")
                    }
                }
            }

            override fun onError(error: Int) {
                mainHandler.removeCallbacks(finalizeStopTimeoutRunnable)
                Log.w(TAG, "SpeechRecognizer error=$error (locale=$currentLocaleTag, langDetect=$tryWithLanguageDetection)")
                destroySpeechRecognizerOnly()

                if (isFinalizingStop) {
                    isFinalizingStop = false
                    val partial = _partialText.value.trim()
                    _isListening.value = false
                    _audioLevel.value = 0f
                    _partialText.value = ""
                    if (partial.isNotEmpty()) {
                        val lang = resolveLanguageCode(partial, detectedLanguageHint, currentLocaleTag)
                        onTextResultCallback?.invoke(
                            SpeechRecognitionResult(partial, lang, currentLocaleTag)
                        )
                        return
                    }
                }

                // If API 34+ multi-language detection extra wasn't accepted by this device's recognizer,
                // retry cleanly with standard locale extra before moving on.
                if (tryWithLanguageDetection &&
                    (error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                ) {
                    tryWithLanguageDetection = false
                    localeAttemptQueue.add(0, currentLocaleTag)
                    startNextLocaleAttempt()
                    return
                }

                // Retry next locale candidate (e.g. bn-BD -> bn-IN -> bn, or en-US -> en-GB)
                val isLocaleError = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                if (isLocaleError && localeAttemptQueue.isNotEmpty()) {
                    Log.i(TAG, "Retrying next locale candidate: ${localeAttemptQueue.firstOrNull()}")
                    startNextLocaleAttempt()
                    return
                }

                // If speech service failed due to availability/language/network/busy/client while online,
                // seamlessly switch to raw WAV audio capture for Gemini multimodal voice understanding.
                val shouldFallbackToAudio = error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                if (shouldFallbackToAudio && !activePreferOffline) {
                    _partialText.value = "Listening via Voice AI…"
                    startAudioFallbackRecording()
                    return
                }

                _isListening.value = false
                _audioLevel.value = 0f
                _partialText.value = ""
                val msg = mapSpeechError(error, activeLanguageMode)
                if (msg != null) {
                    onErrorCallback?.invoke(msg)
                }
            }

            override fun onResults(results: Bundle?) {
                mainHandler.removeCallbacks(finalizeStopTimeoutRunnable)
                isFinalizingStop = false
                _isListening.value = false
                _audioLevel.value = 0f

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = selectBestTranscript(matches, activeLanguageMode, detectedLanguageHint)
                    .ifEmpty { _partialText.value.trim() }
                _partialText.value = ""

                if (best.isNotEmpty()) {
                    val lang = resolveLanguageCode(best, detectedLanguageHint, currentLocaleTag)
                    onTextResultCallback?.invoke(
                        SpeechRecognitionResult(
                            text = best,
                            detectedLanguage = lang,
                            localeTag = detectedLanguageHint ?: currentLocaleTag
                        )
                    )
                } else {
                    val prompt = if (activeLanguageMode == VoiceLanguage.BENGALI) {
                        "কথা বুঝতে পারিনি। মাইক বোতামে ট্যাপ করে স্পষ্টভাবে বলুন।"
                    } else {
                        "Didn't catch that. Please tap the mic and speak clearly."
                    }
                    onErrorCallback?.invoke(prompt)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    _partialText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startAudioFallbackRecording() {
        _isListening.value = true
        audioFallbackRecorder.startRecording(
            onCaptured = { wavBytes ->
                _isListening.value = false
                _audioLevel.value = 0f
                _partialText.value = ""
                onAudioFallbackCallback?.invoke(wavBytes)
            },
            onError = { err ->
                _isListening.value = false
                _audioLevel.value = 0f
                _partialText.value = ""
                onErrorCallback?.invoke(err)
            }
        )
    }

    fun stopListening() {
        mainHandler.post {
            if (audioFallbackRecorder.isRecording.value) {
                audioFallbackRecorder.stopRecording()
                stopListeningInternal()
                return@post
            }

            val recognizer = speechRecognizer
            if (recognizer != null && _isListening.value && _partialText.value.isNotBlank()) {
                // Ask SpeechRecognizer to finalize what the user already spoke instead of discarding it.
                isFinalizingStop = true
                _audioLevel.value = 0f
                runCatching { recognizer.stopListening() }
                mainHandler.removeCallbacks(finalizeStopTimeoutRunnable)
                mainHandler.postDelayed(finalizeStopTimeoutRunnable, 1200L)
            } else {
                stopListeningInternal()
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            stopListeningInternal()
            onTextResultCallback = null
            onAudioFallbackCallback = null
            onErrorCallback = null
        }
    }

    private fun stopListeningInternal() {
        mainHandler.removeCallbacks(finalizeStopTimeoutRunnable)
        isFinalizingStop = false
        _isListening.value = false
        _audioLevel.value = 0f
        _partialText.value = ""
        audioFallbackRecorder.stopRecording()
        destroySpeechRecognizerOnly()
    }

    private fun destroySpeechRecognizerOnly() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun mapSpeechError(code: Int, language: VoiceLanguage): String? {
        val isBn = language == VoiceLanguage.BENGALI
        return when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isBn) {
                "কোনো কথা শোনা যায়নি। মাইক ট্যাপ করে স্পষ্টভাবে বলুন।"
            } else {
                "No speech detected. Tap the mic and speak clearly."
            }
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> if (isBn) {
                "এই ডিভাইসে বাংলা স্পিচ রিকগনিশন প্যাক পাওয়া যায়নি।"
            } else {
                "Selected speech recognition language is unavailable on this device."
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech pack unavailable without internet."
            SpeechRecognizer.ERROR_CLIENT -> null
            else -> "Speech recognition error ($code). Try tapping the mic again."
        }
    }

    companion object {
        private const val TAG = "VoiceRecognitionMgr"

        fun resolveLocaleCandidates(
            language: VoiceLanguage,
            deviceLocale: Locale = Locale.getDefault()
        ): List<String> {
            val deviceTag = deviceLocale.toLanguageTag().takeIf { it.isNotBlank() }
            return when (language) {
                VoiceLanguage.BENGALI -> buildList {
                    if (deviceLocale.language == "bn" && deviceTag != null) {
                        add(deviceTag)
                    }
                    addAll(VoiceLanguage.BENGALI.localeCandidates)
                }.distinct()

                VoiceLanguage.ENGLISH -> buildList {
                    if (deviceLocale.language == "en" && deviceTag != null) {
                        add(deviceTag)
                    }
                    addAll(VoiceLanguage.ENGLISH.localeCandidates)
                }.distinct()

                VoiceLanguage.AUTO -> buildList {
                    if (deviceLocale.language == "bn") {
                        if (deviceTag != null) add(deviceTag)
                        add("bn-BD")
                        add("bn-IN")
                        add("en-US")
                    } else {
                        if (deviceLocale.language == "en" && deviceTag != null) add(deviceTag)
                        add("en-US")
                        add("bn-BD")
                        add("bn-IN")
                    }
                }.distinct()
            }
        }

        fun buildRecognizerIntent(
            context: Context,
            language: VoiceLanguage,
            localeTag: String,
            preferOffline: Boolean,
            enableLanguageDetection: Boolean = false
        ): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)

                if (language == VoiceLanguage.AUTO &&
                    enableLanguageDetection &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                        arrayListOf("en-US", "bn-BD", "bn-IN")
                    )
                }

                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
        }

        fun selectBestTranscript(
            matches: List<String>?,
            language: VoiceLanguage,
            detectedLanguageHint: String?
        ): String {
            val nonBlank = matches.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            if (nonBlank.isEmpty()) return ""

            if (language == VoiceLanguage.BENGALI || detectedLanguageHint?.startsWith("bn", ignoreCase = true) == true) {
                val bengaliMatch = nonBlank.firstOrNull { WakeWordParser.isBengaliText(it) }
                if (bengaliMatch != null) return bengaliMatch
            }
            return nonBlank.first()
        }

        fun resolveLanguageCode(
            transcript: String,
            detectedLanguageHint: String?,
            localeTag: String
        ): String {
            return when {
                WakeWordParser.isBengaliText(transcript) -> "bn"
                detectedLanguageHint?.startsWith("bn", ignoreCase = true) == true -> "bn"
                detectedLanguageHint?.startsWith("en", ignoreCase = true) == true -> "en"
                localeTag.startsWith("bn", ignoreCase = true) -> "bn"
                else -> "en"
            }
        }
    }
}
