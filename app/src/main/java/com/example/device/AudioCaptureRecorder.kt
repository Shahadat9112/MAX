package com.example.device

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Fallback raw PCM->WAV voice recorder used automatically when Android's
 * SpeechRecognizer service is unavailable or errors out on a device/emulator.
 */
class AudioCaptureRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(
        maxDurationMs: Long = 8500L,
        onCaptured: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        stopRecording()

        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            onError("Microphone unavailable: ${e.localizedMessage}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Unable to initialize microphone recorder.")
            return
        }

        audioRecord = recorder
        _isRecording.value = true

        captureJob = scope.launch {
            val pcmOut = ByteArrayOutputStream()
            val buffer = ShortArray(minBuf / 2)
            var speechDetected = false
            var silenceDurationMs = 0L
            val startTime = System.currentTimeMillis()
            val frameDurationMs = (buffer.size * 1000L) / sampleRate

            try {
                recorder.startRecording()
                while (isActive && _isRecording.value) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sumSq += s * s
                            pcmOut.write(buffer[i].toInt() and 0xFF)
                            pcmOut.write((buffer[i].toInt() shr 8) and 0xFF)
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val normalized = (rms / 6500f).coerceIn(0f, 1f)
                        _audioLevel.value = normalized

                        if (rms > 650f) {
                            speechDetected = true
                            silenceDurationMs = 0L
                        } else if (speechDetected) {
                            silenceDurationMs += frameDurationMs
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        if ((speechDetected && silenceDurationMs >= 1400L) || elapsed >= maxDurationMs) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord loop failed", e)
                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                    _audioLevel.value = 0f
                    onError("Audio capture failed: ${e.localizedMessage}")
                }
                return@launch
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                audioRecord = null
                _isRecording.value = false
                _audioLevel.value = 0f
            }

            val pcmBytes = pcmOut.toByteArray()
            pcmOut.reset()
            com.example.domain.PrivacyGuard.wipeBuffer(buffer)
            withContext(Dispatchers.Main) {
                if (pcmBytes.size < 3200 || !speechDetected) {
                    com.example.domain.PrivacyGuard.wipeBuffer(pcmBytes)
                    onError("No voice detected. Tap mic and speak clearly.")
                } else {
                    val wav = pcmToWav(pcmBytes, sampleRate)
                    com.example.domain.PrivacyGuard.wipeBuffer(pcmBytes)
                    onCaptured(wav)
                }
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        _audioLevel.value = 0f
        captureJob?.cancel()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // PCM format
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort()) // Block align
            putShort(16.toShort()) // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        return header + pcmData
    }

    companion object {
        private const val TAG = "AudioCaptureRecorder"
    }
}
