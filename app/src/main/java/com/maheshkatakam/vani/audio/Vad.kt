package com.maheshkatakam.vani.audio

import kotlin.math.log10
import kotlin.math.max

/**
 * Tiny energy-based voice activity detector. It tracks the room's noise floor and treats frames
 * clearly above it as speech. Used to stop automatically when you pause and to trim silence.
 */
class VoiceActivityDetector(private val frameMs: Int = 20) {
    var noiseFloor = -1.0
        private set
    var speechStarted = false
        private set
    var firstSpeechFrame = -1
        private set
    var lastSpeechFrame = -1
        private set
    var frameIndex = -1
        private set
    var peakRms = 0.0
        private set

    private var consecutive = 0

    /** Feed one frame's RMS (16-bit scale). Returns true if the frame looks like speech. */
    fun process(rms: Double): Boolean {
        frameIndex++
        peakRms = max(peakRms, rms)
        noiseFloor = when {
            noiseFloor < 0 -> max(rms, MIN_FLOOR)
            rms < noiseFloor -> noiseFloor * 0.9 + rms * 0.1
            else -> noiseFloor * 0.998 + rms * 0.002
        }.coerceAtLeast(MIN_FLOOR)

        val threshold = max(noiseFloor * 3.0, ABS_THRESHOLD)
        val isSpeech = rms > threshold
        if (isSpeech) {
            consecutive++
            if (!speechStarted && consecutive >= 3) {
                speechStarted = true
                firstSpeechFrame = frameIndex - consecutive + 1
            }
            if (speechStarted) lastSpeechFrame = frameIndex
        } else {
            consecutive = 0
        }
        return isSpeech
    }

    fun msSinceLastSpeech(): Int =
        if (lastSpeechFrame < 0) Int.MAX_VALUE else (frameIndex - lastSpeechFrame) * frameMs

    /** True if there was speech, or at least something clearly louder than the background. */
    fun hasMeaningfulAudio(): Boolean = speechStarted || peakRms > max(noiseFloor * 2.5, 400.0)

    companion object {
        private const val MIN_FLOOR = 20.0
        private const val ABS_THRESHOLD = 150.0

        /** Maps RMS to 0..1 for the on-screen waveform (-55 dBFS → 0, -10 dBFS → 1). */
        fun level(rms: Double): Float {
            if (rms <= 1.0) return 0f
            val db = 20 * log10(rms / 32767.0)
            return ((db + 55.0) / 45.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
