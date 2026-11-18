package com.example.utils

import com.example.domain.PrivacyGuard
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Stateless audio DSP and buffer hygiene utilities used across speech capture and wake-word monitoring.
 */
object AudioUtils {

    /**
     * Computes root-mean-square (RMS) energy across [count] 16-bit PCM samples.
     */
    fun computeRms(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sumSq = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sumSq += s * s
        }
        return sqrt(sumSq / count).toFloat()
    }

    /**
     * Wraps raw 16-bit mono PCM audio data in a standard 44-byte RIFF/WAVE header.
     */
    fun encodePcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
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

    /**
     * Zero-wipes transient byte audio buffers in volatile RAM after processing.
     */
    fun wipeBuffer(bytes: ByteArray?) {
        PrivacyGuard.wipeBuffer(bytes)
    }

    /**
     * Zero-wipes transient PCM short sample buffers in volatile RAM after processing.
     */
    fun wipeBuffer(samples: ShortArray?) {
        PrivacyGuard.wipeBuffer(samples)
    }
}
