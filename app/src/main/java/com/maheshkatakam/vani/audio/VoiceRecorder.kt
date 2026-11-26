package com.maheshkatakam.vani.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/** Records 16 kHz mono PCM, shows a live level, detects pauses and returns a trimmed WAV. */
class VoiceRecorder(private val sampleRate: Int = 16_000) {

    class Result(val wav: ByteArray, val durationMs: Long, val hasSpeech: Boolean, val autoStopped: Boolean)

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile private var stopRequested = false
    @Volatile private var cancelled = false

    fun stop() {
        stopRequested = true
    }

    fun cancel() {
        cancelled = true
    }

    /**
     * Blocks (on an IO thread) until [stop], [cancel], a pause longer than [autoStopMs] after speech,
     * [noSpeechTimeoutMs] without any speech, or [maxMs]. Returns null if cancelled.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(autoStopMs: Int, maxMs: Long, noSpeechTimeoutMs: Long): Result? = withContext(Dispatchers.IO) {
        val frameSamples = sampleRate / 50 // 20 ms
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = max(minBuf, sampleRate) // ~0.5 s of audio
        val rec = open(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufSize)
            ?: open(MediaRecorder.AudioSource.MIC, bufSize)
            ?: throw IllegalStateException("the microphone couldn't be opened")

        val frame = ShortArray(frameSamples)
        val bytes = ByteArray(frameSamples * 2)
        val pcm = ByteArrayOutputStream(sampleRate * 2 * 20)
        val vad = VoiceActivityDetector(frameMs = 20)
        var autoStopped = false
        val started = SystemClock.elapsedRealtime()
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("the microphone is being used by another app")
            }
            while (isActive && !stopRequested && !cancelled) {
                val n = rec.read(frame, 0, frameSamples)
                if (n < 0) throw IllegalStateException("microphone read error ($n)")
                if (n == 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val v = frame[i].toInt()
                    sum += (v * v).toDouble()
                    bytes[2 * i] = (v and 0xff).toByte()
                    bytes[2 * i + 1] = ((v shr 8) and 0xff).toByte()
                }
                pcm.write(bytes, 0, n * 2)
                val rms = sqrt(sum / n)
                vad.process(rms)
                _level.value = VoiceActivityDetector.level(rms)

                val elapsed = SystemClock.elapsedRealtime() - started
                if (autoStopMs > 0 && vad.speechStarted && vad.msSinceLastSpeech() >= autoStopMs) {
                    autoStopped = true
                    break
                }
                if (!vad.speechStarted && elapsed >= noSpeechTimeoutMs) break
                if (elapsed >= maxMs) {
                    autoStopped = true
                    break
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            _level.value = 0f
        }
        if (cancelled) return@withContext null

        val all = pcm.toByteArray()
        val bytesPerFrame = frameSamples * 2
        val range = if (vad.speechStarted) {
            Wav.speechRange(all.size, vad.firstSpeechFrame, vad.lastSpeechFrame, bytesPerFrame, padBeforeFrames = 15, padAfterFrames = 25)
        } else {
            0 until all.size
        }
        val length = range.last - range.first + 1
        val wav = Wav.encode(all, range.first, length.coerceAtLeast(0), sampleRate)
        Result(
            wav = wav,
            durationMs = SystemClock.elapsedRealtime() - started,
            hasSpeech = vad.hasMeaningfulAudio() && all.isNotEmpty(),
            autoStopped = autoStopped,
        )
    }

    @SuppressLint("MissingPermission")
    private fun open(source: Int, bufSize: Int): AudioRecord? = try {
        val r = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (r.state == AudioRecord.STATE_INITIALIZED) r else {
            r.release()
            null
        }
    } catch (e: Exception) {
        null
    }
}
