package com.example.wakeword

/**
 * Immutable configuration for the MAX Wake Word System.
 *
 * Enforces privacy-first defaults: only a short, volatile RAM audio window is inspected
 * to detect the `"MAX"` wake word without recording conversations, persisting audio files,
 * or requesting camera/gallery/storage permissions.
 */
data class WakeWordConfig(
    /** Primary wake word required to activate MAX. */
    val wakeWord: String = DEFAULT_WAKE_WORD,
    /** Accepted spoken variants (English & Bengali) normalized to [wakeWord]. */
    val acceptedWakeAliases: Set<String> = DEFAULT_ALIASES,
    /** Audio sample rate in Hz used by the background wake-word listener. */
    val sampleRateHz: Int = 16_000,
    /** Frame size duration in milliseconds for energy/VAD inspection. */
    val frameDurationMs: Int = 32,
    /** Short rolling RAM pre-roll duration so the onset of "MAX" is not clipped. */
    val preRollMs: Int = 800,
    /** Maximum single wake-phrase window (ms) so full conversations are never recorded during standby. */
    val maxWakeWindowMs: Long = 2_200L,
    /** Minimum cooldown (ms) between wake-word evaluations. */
    val cooldownMs: Long = 1_400L,
    /** Guarantees audio is processed strictly in volatile RAM and zero-wiped immediately. */
    val strictZeroRecording: Boolean = true,
    /** Automatically triggers full SpeechManager recognition once "MAX" is detected. */
    val autoActivateSpeechOnWake: Boolean = true,
    /** Automatically pauses wake-word listening during active phone calls. */
    val pauseDuringPhoneCalls: Boolean = true
) {
    companion object {
        const val DEFAULT_WAKE_WORD = "MAX"

        val DEFAULT_ALIASES = setOf(
            "max",
            "hey max",
            "ok max",
            "okay max",
            "hi max",
            "ম্যাক্স",
            "হে ম্যাক্স"
        )
    }
}
