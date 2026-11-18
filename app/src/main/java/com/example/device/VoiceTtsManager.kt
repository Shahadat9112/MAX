package com.example.device

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
 * Manages bilingual Android Text-to-Speech synthesis with warm male voice and pitch tuning.
 */
class VoiceTtsManager(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtterance: Pair<String, String>? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var onSpeechCompleted: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setSpeechRate(0.96f)
                tts?.setPitch(0.90f) // Warm, deep male voice pitch
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        mainHandler.post { onSpeechCompleted?.invoke() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        mainHandler.post { onSpeechCompleted?.invoke() }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "TTS utterance errorCode=$errorCode")
                        _isSpeaking.value = false
                        mainHandler.post { onSpeechCompleted?.invoke() }
                    }
                })

                pendingUtterance?.let { (queuedText, queuedLang) ->
                    pendingUtterance = null
                    speak(queuedText, queuedLang)
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed with status $status")
                _isSpeaking.value = false
                pendingUtterance = null
                mainHandler.post { onSpeechCompleted?.invoke() }
            }
        }
    }

    fun speak(text: String, languageHint: String = "en") {
        val engine = tts
        if (text.isBlank()) {
            _isSpeaking.value = false
            mainHandler.post { onSpeechCompleted?.invoke() }
            return
        }

        if (engine == null || !isInitialized) {
            pendingUtterance = text to languageHint
            return
        }

        val isBengali = languageHint == "bn" || WakeWordParser.isBengaliText(text)
        val locale = if (isBengali) {
            val bd = Locale("bn", "BD")
            val indian = Locale("bn", "IN")
            when {
                engine.isLanguageAvailable(bd) >= TextToSpeech.LANG_AVAILABLE -> bd
                engine.isLanguageAvailable(indian) >= TextToSpeech.LANG_AVAILABLE -> indian
                else -> Locale.US
            }
        } else {
            Locale.US
        }

        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Requested TTS locale $locale unavailable (code=$langResult); falling back to default.")
            engine.setLanguage(Locale.getDefault())
        } else {
            selectNaturalMaleVoice(engine, locale)
        }

        val cleanedText = text
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()

        _isSpeaking.value = true
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech.speak returned ERROR")
            _isSpeaking.value = false
            mainHandler.post { onSpeechCompleted?.invoke() }
        }
    }

    private fun selectNaturalMaleVoice(engine: TextToSpeech, targetLocale: Locale) {
        runCatching {
            val bestVoice = engine.voices
                ?.filter { it.locale.language == targetLocale.language }
                ?.sortedWith(
                    compareByDescending<Voice> { 
                        val name = it.name.lowercase()
                        name.contains("male") || name.contains("man") || name.contains("sfg#male") || name.contains("male-1") || name.contains("male-2")
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

    fun stop() {
        pendingUtterance = null
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingUtterance = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "VoiceTtsManager"
    }
}
