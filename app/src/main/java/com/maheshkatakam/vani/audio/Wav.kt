package com.maheshkatakam.vani.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    /** Wraps 16-bit mono PCM (little-endian) in a WAV header. */
    fun encode(pcm: ByteArray, offset: Int, length: Int, sampleRate: Int): ByteArray {
        val out = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + length)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16) // PCM chunk size
        out.putShort(1) // PCM format
        out.putShort(1) // mono
        out.putInt(sampleRate)
        out.putInt(sampleRate * 2) // byte rate
        out.putShort(2) // block align
        out.putShort(16) // bits per sample
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(length)
        out.put(pcm, offset, length)
        return out.array()
    }

    /**
     * Byte range to keep: from a little before the first speech frame to a little after the last,
     * so silence at the start and end isn't uploaded (and can't make speech models hallucinate).
     */
    fun speechRange(
        totalBytes: Int,
        firstSpeechFrame: Int,
        lastSpeechFrame: Int,
        bytesPerFrame: Int,
        padBeforeFrames: Int,
        padAfterFrames: Int,
    ): IntRange {
        if (firstSpeechFrame < 0 || lastSpeechFrame < firstSpeechFrame) return 0 until totalBytes
        val start = ((firstSpeechFrame - padBeforeFrames).coerceAtLeast(0) * bytesPerFrame).coerceAtMost(totalBytes)
        val end = ((lastSpeechFrame + 1 + padAfterFrames) * bytesPerFrame).coerceAtMost(totalBytes)
        return if (end <= start) 0 until totalBytes else start until end
    }
}
