package com.maheshkatakam.vani.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** A recording, as WAV (always) and AAC/M4A (about 6x smaller, used when it encodes cleanly). */
class AudioClip(val wav: ByteArray, val m4a: ByteArray?, val durationMs: Long)

/**
 * Compresses 16-bit mono PCM to AAC in an .m4a container with the phone's hardware/software
 * encoder, so less data has to be uploaded. Returns null on any problem; callers then send WAV.
 */
object AudioEncoder {
    private const val BIT_RATE = 48_000
    private const val MAX_ENCODE_MS = 5_000L

    fun toM4a(pcm: ByteArray, offset: Int, length: Int, sampleRate: Int, cacheDir: File): ByteArray? {
        if (length < sampleRate) return null // under ~0.5 s: not worth it
        val out = File(cacheDir, "vani-${System.nanoTime()}.m4a")
        return try {
            val bytes = encode(pcm, offset, length, sampleRate, out)
            bytes.takeIf { it.size > 512 && it.size < length }
        } catch (e: Exception) {
            null
        } finally {
            out.delete()
        }
    }

    private fun encode(pcm: ByteArray, offset: Int, length: Int, sampleRate: Int, out: File): ByteArray {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            var track = -1
            var muxing = false
            val info = MediaCodec.BufferInfo()
            val end = offset + length
            var pos = offset
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + MAX_ENCODE_MS

            while (!outputDone) {
                if (SystemClock.elapsedRealtime() > deadline) throw IllegalStateException("AAC encoding took too long")
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex) ?: throw IllegalStateException("no input buffer")
                        buf.clear()
                        val n = minOf(buf.remaining(), end - pos) and 1.inv() // whole 16-bit samples only
                        val ptsUs = ((pos - offset) / 2).toLong() * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(pcm, pos, n)
                            codec.queueInputBuffer(inIndex, 0, n, ptsUs, 0)
                            pos += n
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = mux.addTrack(codec.outputFormat)
                    mux.start()
                    muxing = true
                } else if (outIndex >= 0) {
                    val ob = codec.getOutputBuffer(outIndex)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (ob != null && muxing && !isConfig && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        mux.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }
            if (!muxing) throw IllegalStateException("encoder produced no audio")
            mux.stop()
            return out.readBytes()
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { muxer?.release() }
        }
    }
}
