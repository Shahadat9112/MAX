package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.WakeWordParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Advanced TTS Manager for MAX AI in the [com.example.tts] package.
 *
 * Requirements implemented:
 * 1. Bangla support (bn-BD, bn-IN, bn) alongside English and future multi-language extensibility.
 * 2. Natural, human-like voice quality: slows speech rate and applies pitch tuning to avoid robotic speech.
 * 3. Natural pauses between sentences by inserting deliberate punctuation pacing.
 * 4. Speaking styles: Friendly, Calm, Helpful, Caring (adjusting tone pacing and warmth).
 * 5. Full controls: speak(text), stopSpeaking(), pauseSpeaking(), resumeSpeaking(), setSpeechSpeed(), setPitch().
 * 6. Compatibility across Android versions and device TTS engines with robust fallbacks.
 * 7. Strict privacy: zero access to microphone, camera, gallery, or files.
 */
class TTSManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtterance: Pair<String, String>? = null

    private var config = VoiceConfig()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentTextQueue = ""
    private var currentLanguageHint = "en"

    var onSpeechCompleted: (() -> Unit)? = null

    init {
        initTtsEngine()
    }

    private fun initTtsEngine() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureEngineSettings()
                
                pendingUtterance?.let { (text, lang) ->
                    pendingUtterance = null
                    speak(text, lang)
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed with status $status")
                _voiceState.value = VoiceState.ERROR
                _isSpeaking.value = false
                pendingUtterance = null
                mainHandler.post { onSpeechCompleted?.invoke() }
            }
        }
    }

    private fun configureEngineSettings() {
        val engine = tts ?: return
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            applyConfigToEngine(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                    _isSpeaking.value = false
                    mainHandler.post { onSpeechCompleted?.invoke() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.ERROR
                    _isSpeaking.value = false
                    mainHandler.post { onSpeechCompleted?.invoke() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "TTS utterance errorCode=$errorCode")
                    _voiceState.value = VoiceState.ERROR
                    _isSpeaking.value = false
                    mainHandler.post { onSpeechCompleted?.invoke() }
                }
            })
        }
    }

    private fun applyConfigToEngine(engine: TextToSpeech) {
        val adjustedRate = when (config.speechStyle) {
            SpeechStyle.CALM -> config.speechRate * 0.90f
            SpeechStyle.CARING -> config.speechRate * 0.94f
            else -> config.speechRate
        }
        val adjustedPitch = when (config.speechStyle) {
            SpeechStyle.FRIENDLY -> config.pitch * 1.02f
            SpeechStyle.CALM -> config.pitch * 0.95f
            else -> config.pitch
        }
        engine.setSpeechRate(adjustedRate.coerceIn(0.5f, 2.0f))
        engine.setPitch(adjustedPitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Speaks the given [text] clearly in Bangla or English, respecting [languageHint],
     * inserting natural sentence pauses, and applying the active [SpeechStyle].
     */
    fun speak(text: String, languageHint: String = "en") {
        val engine = tts
        if (text.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            _isSpeaking.value = false
            mainHandler.post { onSpeechCompleted?.invoke() }
            return
        }

        currentTextQueue = text
        currentLanguageHint = languageHint

        if (engine == null || !isInitialized) {
            pendingUtterance = text to languageHint
            return
        }

        val isBengali = languageHint == "bn" || WakeWordParser.isBengaliText(text)
        val locale = resolveLocale(engine, isBengali)

        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Requested TTS locale $locale unavailable; falling back to default.")
            engine.setLanguage(Locale.getDefault())
        } else {
            selectNaturalVoice(engine, locale)
        }

        applyConfigToEngine(engine)

        // Enhance natural pauses between sentences by inserting micro-pauses (. -> .  )
        val processedText = if (config.addNaturalPauses) {
            text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replace(Regex("([.!?;:])(\\s*)"), "$1... ")
                .replace(Regex("([,])(\\s*)"), "$1.. ")
                .trim()
        } else {
            text.replace("*", "").replace("#", "").replace("`", "").trim()
        }

        _voiceState.value = VoiceState.SPEAKING
        _isSpeaking.value = true
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech.speak returned ERROR")
            _voiceState.value = VoiceState.ERROR
            _isSpeaking.value = false
            mainHandler.post { onSpeechCompleted?.invoke() }
        }
    }

    private fun resolveLocale(engine: TextToSpeech, isBengali: Boolean): Locale {
        return if (isBengali) {
            val bd = Locale("bn", "BD")
            val indian = Locale("bn", "IN")
            val generalBn = Locale("bn")
            when {
                engine.isLanguageAvailable(bd) >= TextToSpeech.LANG_AVAILABLE -> bd
                engine.isLanguageAvailable(indian) >= TextToSpeech.LANG_AVAILABLE -> indian
                engine.isLanguageAvailable(generalBn) >= TextToSpeech.LANG_AVAILABLE -> generalBn
                else -> Locale.US
            }
        } else {
            Locale.US
        }
    }

    private fun selectNaturalVoice(engine: TextToSpeech, targetLocale: Locale) {
        runCatching {
            val bestVoice = engine.voices
                ?.filter { it.locale.language == targetLocale.language }
                ?.sortedWith(
                    compareByDescending<Voice> { 
                        val name = it.name.lowercase()
                        name.contains("natural") || name.contains("premium") || name.contains("high") || name.contains("male") || name.contains("female")
                    }
                    .thenByDescending { it.locale.country == targetLocale.country }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                )
                ?.firstOrNull()
            if (bestVoice != null) {
                engine.voice = bestVoice
            }
        }
    }

    /** Stops current speech playback immediately and clears queues. */
    fun stopSpeaking() {
        pendingUtterance = null
        runCatching { tts?.stop() }
        _voiceState.value = VoiceState.STOPPED
        _isSpeaking.value = false
    }

    /** Temporarily pauses speech playback. */
    fun pauseSpeaking() {
        runCatching { tts?.stop() }
        _voiceState.value = VoiceState.PAUSED
        _isSpeaking.value = false
    }

    /** Resumes speech playback from the last queued text. */
    fun resumeSpeaking() {
        if (_voiceState.value == VoiceState.PAUSED && currentTextQueue.isNotBlank()) {
            speak(currentTextQueue, currentLanguageHint)
        }
    }

    /** Sets speech speed (rate multiplier 0.5f to 2.0f). */
    fun setSpeechSpeed(speed: Float) {
        config = config.copy(speechRate = speed.coerceIn(0.5f, 2.0f))
        tts?.let { applyConfigToEngine(it) }
    }

    /** Sets voice pitch (tone multiplier 0.5f to 2.0f). */
    fun setPitch(pitch: Float) {
        config = config.copy(pitch = pitch.coerceIn(0.5f, 2.0f))
        tts?.let { applyConfigToEngine(it) }
    }

    /** Sets the expressive [SpeechStyle] (Friendly, Calm, Helpful, Caring). */
    fun setSpeechStyle(style: SpeechStyle) {
        config = config.copy(speechStyle = style)
        tts?.let { applyConfigToEngine(it) }
    }

    /** Completely shuts down the TTS engine to release battery and memory resources. */
    fun shutdown() {
        pendingUtterance = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        _voiceState.value = VoiceState.IDLE
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
